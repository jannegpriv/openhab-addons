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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

    private int refreshTimeInSeconds = 300;

    private final Gson gson;
    private final HttpClient httpClient;
    private final Map<String, LynkcoDTO> lynkcoThings = new ConcurrentHashMap<>();

    private @Nullable LynkcoAPI api;
    private @Nullable LynkcoTokenManager tokenManager;
    private @Nullable ScheduledFuture<?> refreshJob;
    private boolean waitingForMfa = false;
    private @Nullable LoginResponse pendingMfaResponse;

    public LynkcoBridgeHandler(Bridge bridge, HttpClient httpClient, Gson gson) {
        super(bridge);
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public void initialize() {
        LynkcoBridgeConfiguration config = getConfigAs(LynkcoBridgeConfiguration.class);

        this.api = new LynkcoAPI(config, gson, httpClient);
        this.tokenManager = new LynkcoTokenManager(getThing(), httpClient);
        refreshTimeInSeconds = config.refresh;

        if (config.email == null || config.password == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Configuration of email and password are mandatory");
        } else if (refreshTimeInSeconds < 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Refresh time cannot be negative!");
        } else {
            updateStatus(ThingStatus.UNKNOWN);
            this.tokenManager = new LynkcoTokenManager(getThing(), httpClient);
            scheduler.execute(this::initializeAuthentication);
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        // First, let the framework handle the update
        // super.handleConfigurationUpdate(configurationParameters);

        // Then check if this is an MFA code update
        if (configurationParameters.containsKey("mfa") && waitingForMfa) {
            String mfaCode = (String) configurationParameters.get("mfa");

            // Only process if we have a valid MFA code
            if (mfaCode != null && !mfaCode.isEmpty() && api != null && tokenManager != null) {
                if (pendingMfaResponse != null) {
                    processMfaCode(mfaCode, pendingMfaResponse);
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Pending MFA response is not available.");
                }
            }
        }
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
        stopAutomaticRefresh();
    }

    public @Nullable LynkcoAPI getLynkcoAPI() {
        return api;
    }

    private void initializeAuthentication() {
        try {
            if (api == null || tokenManager == null) {
                return;
            }

            // First try to get a cached token
            try {
                if (tokenManager != null) {
                    String token = tokenManager.getCccToken();
                    if (token != null) {
                        // If we get here, we have a valid token
                        logger.debug("Valid cached token found, starting normal operation");
                        startAutomaticRefresh();
                        return;
                    }
                } else {
                    return;
                }
            } catch (LynkcoApiException e) {
                // Token not available or expired, continue with login flow
                logger.debug("No valid cached token, starting login flow");
            }

            // Start login process
            if (api != null) {
                pendingMfaResponse = api.login();
                handleMfaRequired();
            }
        } catch (LynkcoApiException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Login failed: " + e.getMessage());
        }
    }

    private void handleMfaRequired() {
        waitingForMfa = true;

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "Please enter MFA code in configuration");
    }

    private void processMfaCode(String mfaCode, @Nullable LoginResponse mfaResponse) {
        try {
            if (api != null && mfaResponse != null) {
                // Handle MFA and get tokens
                TokenResponse tokenResponse = api.handleMFACode(mfaCode, mfaResponse);

                if (tokenManager != null && tokenResponse.success) {
                    // Store tokens in TokenManager
                    tokenManager.updateTokens(tokenResponse.authToken, tokenResponse.refreshToken);

                    logger.debug("MFA verification successful");

                    // Clear MFA state
                    waitingForMfa = false;
                    pendingMfaResponse = null;

                    // Clear MFA code from configuration
                    clearMfaCode();

                    // Start normal operation
                    startAutomaticRefresh();

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

    @SuppressWarnings("null")
    private void refreshAndUpdateStatus() {
        try {
            // Get token (this will refresh if needed)
            if (tokenManager != null && api != null) {
                String cccToken = tokenManager.getCccToken();
                if (cccToken == null) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Could not obtain valid token");
                    return;
                }

                updateStatus(ThingStatus.ONLINE);

                if (api.refresh(lynkcoThings, cccToken)) {
                    getThing().getThings().stream().forEach(thing -> {
                        LynkcoVehicleHandler handler = (LynkcoVehicleHandler) thing.getHandler();
                        if (handler != null) {
                            handler.update();
                        }
                    });
                    updateStatus(ThingStatus.ONLINE);
                    return;
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                }
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Could not obtain valid token");
                return;
            }
        } catch (LynkcoApiException e) {
            switch (e.getErrorType()) {
                case AUTHENTICATION_REQUIRED:
                    // Token is invalid/expired and refresh failed, need to re-authenticate
                    logger.debug("Authentication required, restarting login flow");
                    scheduler.execute(this::initializeAuthentication);
                    break;
                case NETWORK_ERROR:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Network error: " + e.getMessage());
                    break;
                default:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Error refreshing data: " + e.getMessage());
            }
        }
    }

    private void startAutomaticRefresh() {
        ScheduledFuture<?> refreshJob = this.refreshJob;
        if (refreshJob == null || refreshJob.isCancelled()) {
            this.refreshJob = scheduler.scheduleWithFixedDelay(this::refreshAndUpdateStatus, 0, refreshTimeInSeconds,
                    TimeUnit.SECONDS);
        }
    }

    private void stopAutomaticRefresh() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
            this.refreshJob = null;
        }
        waitingForMfa = false;
        pendingMfaResponse = null;
        tokenManager = null;
        api = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_STATUS.equals(channelUID.getId()) && command instanceof RefreshType) {
            scheduler.schedule(this::refreshAndUpdateStatus, 1, TimeUnit.SECONDS);
        }
    }
}
