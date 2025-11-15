/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.lynkco.internal.handler;

import static org.openhab.binding.lynkco.internal.LynkcoBindingConstants.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.lynkco.internal.LynkcoBridgeConfiguration;
import org.openhab.binding.lynkco.internal.api.LynkcoAPI;
import org.openhab.binding.lynkco.internal.api.LynkcoAPI.LoginResponse;
import org.openhab.binding.lynkco.internal.api.LynkcoAPI.TokenResponse;
import org.openhab.binding.lynkco.internal.api.LynkcoApiException;
import org.openhab.binding.lynkco.internal.api.LynkcoTokenManager;
import org.openhab.binding.lynkco.internal.discovery.LynkcoDiscoveryService;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link LynkcoBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class LynkcoBridgeHandler extends BaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(LynkcoBridgeHandler.class);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_BRIDGE);

    private final Gson gson;
    private final HttpClient httpClient;
    private final Map<String, LynkcoDTO> lynkcoThings = new ConcurrentHashMap<>();

    private @Nullable LynkcoAPI api;
    private LynkcoTokenManager tokenManager;
    private @Nullable LoginResponse pendingLoginResponse;

    public LynkcoBridgeHandler(Bridge bridge, HttpClient httpClient, Gson gson) {
        super(bridge);
        this.httpClient = httpClient;
        this.gson = gson;
        this.tokenManager = new LynkcoTokenManager(getThing(), httpClient);
    }

    @Override
    public void initialize() {
        logger.debug("initialize: this: {}", this);
        LynkcoBridgeConfiguration config = getConfigAs(LynkcoBridgeConfiguration.class);

        this.api = new LynkcoAPI(config, gson, httpClient, tokenManager);

        if (config.email.isEmpty() || config.password.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Configuration of email and password are mandatory");
        } else {
            updateStatus(ThingStatus.UNKNOWN);
            scheduler.execute(this::initializeAuthentication);
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        logger.debug("handleConfigurationUpdate: {}", configurationParameters.toString());

        // Handle redirect URL update
        if (configurationParameters.containsKey("redirectUrl")) {
            String redirectUrl = (String) configurationParameters.get("redirectUrl");
            if (redirectUrl != null && !redirectUrl.trim().isEmpty()) {
                logger.info("Redirect URL provided, processing authentication...");
                // Update configuration first
                super.handleConfigurationUpdate(configurationParameters);
                // Then handle authentication
                scheduler.execute(this::handleRedirectUrlAuthentication);
                return;
            }
        }

        super.handleConfigurationUpdate(configurationParameters);
        logger.debug("handleConfigurationUpdate: api: {}", api);
    }

    public Map<String, LynkcoDTO> getLynkcoThings() {
        return lynkcoThings;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(LynkcoDiscoveryService.class);
    }

    @Override
    public void dispose() {
        logger.debug("dispose");
        api = null;
        pendingLoginResponse = null;
    }

    public @Nullable LynkcoAPI getLynkcoAPI() {
        return api;
    }

    private void initializeAuthentication() {
        logger.debug("initializeAuthentication");

        LynkcoBridgeConfiguration config = getConfigAs(LynkcoBridgeConfiguration.class);

        // Check if user has provided redirect URL
        if (config.redirect != null && !config.redirect.trim().isEmpty()) {
            logger.info("Redirect URL provided, attempting to complete authentication...");
            handleRedirectUrlAuthentication();
            return;
        }

        // Check if we have a valid refresh token
        if (tokenManager.hasValidRefreshToken()) {
            logger.debug("Valid refresh token found, attempting token refresh");
            if (tokenManager.refreshTokens()) {
                updateStatus(ThingStatus.ONLINE);
                return;
            }
            logger.warn("Token refresh failed, need to re-authenticate");
        } else {
            logger.error("Refresh token is null, re-authenticate");
        }

        // Start login flow to get the URL user needs to visit
        startManualLoginFlow();
    }

    private void startManualLoginFlow() {
        logger.debug("Starting manual login flow");

        scheduler.execute(() -> {
            try {
                // Generate PKCE pair directly here
                String[] codeVerifierChallenge = generatePkcePair();
                String codeVerifier = codeVerifierChallenge[0];
                String codeChallenge = codeVerifierChallenge[1];

                // Save codeVerifier to thing properties so it survives dispose/reinit
                thing.setProperty("codeVerifier", codeVerifier);

                // Build the login URL directly
                String loginUrl = "https://login.lynkco.com/lynkcoprod.onmicrosoft.com/b2c_1a_signin_mfa/oauth2/v2.0/authorize"
                        + "?response_type=code"
                        + "&scope=https://lynkcoprod.onmicrosoft.com/mobile-app-web-api/mobile.read%20https://lynkcoprod.onmicrosoft.com/mobile-app-web-api/mobile.write%20profile%20offline_access"
                        + "&code_challenge=" + codeChallenge + "&code_challenge_method=S256" + "&redirect_uri="
                        + java.net.URLEncoder.encode(
                                "msauth://prod.lynkco.app.crisp.prod/2jmj7l5rSw0yVb%2FvlWAYkK%2FYBwk%3D",
                                java.nio.charset.StandardCharsets.UTF_8)
                        + "&client_id=c3e13a0c-8ba7-4ea5-9a21-ecd75830b9e9";

                // Update status with instructions
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "Please visit this URL to complete login (including MFA), then copy the 'msauth://...' URL from your browser and paste it in the 'Redirect URL' configuration field: "
                                + loginUrl);

                logger.warn("===========================================");
                logger.warn("MANUAL LOGIN REQUIRED");
                logger.warn("Please open this URL in your web browser:");
                logger.warn(loginUrl);
                logger.warn(
                        "After logging in (including MFA code), your browser will try to redirect to an 'msauth://...' URL");
                logger.warn("Copy that entire URL and paste it into the 'Redirect URL' configuration field in OpenHAB");
                logger.warn("===========================================");

            } catch (Exception e) {
                logger.error("Failed to generate login URL: {}", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Failed to generate login URL: " + e.getMessage());
            }
        });
    }

    // Helper method to generate PKCE pair
    private String[] generatePkcePair() throws NoSuchAlgorithmException {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        return new String[] { codeVerifier, codeChallenge };
    }

    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifierBytes = new byte[32];
        secureRandom.nextBytes(codeVerifierBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);
    }

    private String generateCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private void handleRedirectUrlAuthentication() {
        logger.debug("Processing redirect URL authentication");

        scheduler.execute(() -> {
            try {
                if (api == null) {
                    logger.error("API is null");
                    return;
                }

                // Get codeVerifier from thing properties
                String codeVerifier = thing.getProperties().get("codeVerifier");
                if (codeVerifier == null) {
                    logger.error("No code verifier found. Please restart the login flow.");
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "No pending login found. Please remove the Redirect URL and restart.");
                    return;
                }

                LynkcoBridgeConfiguration config = getConfigAs(LynkcoBridgeConfiguration.class);
                String redirectUrl = config.redirect.trim();
                logger.debug("Processing redirect URL: {}", redirectUrl);

                TokenResponse tokenResponse = api.getTokensFromRedirectUri(redirectUrl, codeVerifier);

                if (tokenResponse.success) {
                    logger.info("Successfully obtained tokens from redirect URL!");
                    tokenManager.updateTokens(tokenResponse.authToken, tokenResponse.refreshToken);

                    // Clear the redirect URL and code verifier
                    Configuration newConfig = editConfiguration();
                    newConfig.put("redirect", "");
                    updateConfiguration(newConfig);

                    thing.setProperty("codeVerifier", null);

                    updateStatus(ThingStatus.ONLINE);
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Failed to get tokens: " + tokenResponse.errorMessage);
                }

            } catch (LynkcoApiException e) {
                logger.error("Failed to process redirect URL: {}", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Invalid redirect URL: " + e.getMessage());
            }
        });
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_STATUS.equals(channelUID.getId()) && command instanceof RefreshType) {
            return;
        }
    }
}
