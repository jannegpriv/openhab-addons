/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
    private boolean waitingForMfa = false;
    private @Nullable LoginResponse pendingMfaResponse;

    public LynkcoBridgeHandler(Bridge bridge, HttpClient httpClient, Gson gson) {
        super(bridge);
        this.httpClient = httpClient;
        this.gson = gson;
        this.tokenManager = new LynkcoTokenManager(getThing(), httpClient);
    }

    @Override
    public void initialize() {
        logger.debug("initialize: this: {} waitingForMfa: {}", this, waitingForMfa);
        if (pendingMfaResponse != null) {
            logger.debug("initialize, pending MFS ...");
            return;
        }
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
        if (configurationParameters.containsKey("mfa") && waitingForMfa) {
            String mfaCode = (String) configurationParameters.get("mfa");

            if (mfaCode != null && !mfaCode.isEmpty() && api != null) {
                if (pendingMfaResponse != null) {
                    processMfaCode(mfaCode, pendingMfaResponse);
                } else {
                    logger.debug("handleConfigurationUpdate pendingMfaResponse is null");
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Pending MFA response is not available.");
                }
            }
            logger.debug("handleConfigurationUpdate: MFA: {}", mfaCode);
        }
        logger.debug("handleConfigurationUpdate: api: {} waitingForMfa: {}", api, waitingForMfa);
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
        if (pendingMfaResponse != null) {
            logger.debug("dispose, pending MFA ...");
            return;
        }
        api = null;
        waitingForMfa = false;
        pendingMfaResponse = null;
    }

    public @Nullable LynkcoAPI getLynkcoAPI() {
        return api;
    }

    private void initializeAuthentication() {
        logger.debug("initializeAuthentication");
        try {
            if (api == null) {
                return;
            }
            // First try to get a cached token
            try {
                String token = tokenManager.getCccToken();
                if (token != null) {
                    // If we get here, we have a valid token
                    logger.debug("Valid cached token found, starting normal operation");
                    updateStatus(ThingStatus.ONLINE);
                    return;
                }
            } catch (LynkcoApiException e) {
                // Token not available or expired, continue with login flow
                logger.debug("No valid cached token, starting login flow");
            }

            // Start login process
            if (api != null) {
                pendingMfaResponse = api.login();
                logger.debug("initializeAuthentication pendingMfaResponse: {}", pendingMfaResponse);
                handleMfaRequired();
            }
        } catch (LynkcoApiException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Login failed: " + e.getMessage());
        }
    }

    private void handleMfaRequired() {
        logger.debug("handleMfaRequired");
        waitingForMfa = true;
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "Please enter MFA code in configuration");
    }

    private void processMfaCode(String mfaCode, @Nullable LoginResponse mfaResponse) {
        logger.debug("processMfaCode: response: {}", mfaResponse);
        try {
            if (api != null && mfaResponse != null) {
                // Handle MFA and get tokens
                TokenResponse tokenResponse = api.handleMFACode(mfaCode, mfaResponse);
                if (tokenResponse.success) {
                    // Store tokens in TokenManager
                    tokenManager.updateTokens(tokenResponse.authToken, tokenResponse.refreshToken);
                    logger.debug("MFA verification successful");

                    // Clear MFA state
                    waitingForMfa = false;
                    pendingMfaResponse = null;

                    // Clear MFA code from configuration
                    clearMfaCode();
                    updateStatus(ThingStatus.ONLINE);

                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Invalid MFA code, please try again");
                }
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Invalid MFA code, please try again");
            }
        } catch (LynkcoApiException e) {
            logger.debug("Error verifying MFA code: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Error verifying MFA code: " + e.getMessage());
        }
    }

    private void clearMfaCode() {
        Configuration configuration = editConfiguration();
        configuration.put("mfaCode", "");
        updateConfiguration(configuration);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_STATUS.equals(channelUID.getId()) && command instanceof RefreshType) {
            return;
        }
    }
}
