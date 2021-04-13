/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.sltrafficinformation.internal.handler;

import static org.openhab.binding.sltrafficinformation.internal.SLTrafficInformationBindingConstants.CHANNEL_DEVIATIONS;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.sltrafficinformation.internal.SLTrafficInformationConfiguration;
import org.openhab.binding.sltrafficinformation.internal.model.SLTrafficDeviations;
import org.openhab.binding.sltrafficinformation.internal.model.SLTrafficDeviations.ResponseData;
import org.openhab.core.io.net.http.HttpUtil;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link SLTrafficDeviationHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SLTrafficDeviationHandler extends BaseThingHandler {
    private static final int REQUEST_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(20);
    private final Logger logger = LoggerFactory.getLogger(SLTrafficDeviationHandler.class);

    private @Nullable SLTrafficInformationConfiguration config;
    private int refresh = 10;
    private @Nullable ScheduledFuture<?> refreshJob;

    private static final String SL_DEVIATIONS_URL = "https://api.sl.se/api2/deviations.json";
    private static final String SL_DEVIATIONS_RAW_URL = "https://api.sl.se/api2/deviationsrawdata..json";

    private Gson gson = new GsonBuilder().create();

    public SLTrafficDeviationHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} on channel {}.", command, channelUID);
        if (command instanceof RefreshType) {
            refreshAndUpdateStatus();
        }
    }

    @Override
    public void dispose() {
        logger.debug("Handler is disposed.");
        stopAutomaticRefresh();
    }

    @Override
    public void initialize() {
        // logger.debug("Start initializing!");
        config = getConfigAs(SLTrafficInformationConfiguration.class);
        this.refresh = config.refresh;

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        // background initialization:
        scheduler.execute(() -> {
            boolean thingReachable = true;

            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
                if (refresh != 0) {
                    startAutomaticRefresh();
                }
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        });

        logger.debug("Finished initializing!");

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    private void startAutomaticRefresh() {
        logger.debug("Start automatic refresh {}", refreshJob);
        if (refreshJob == null || refreshJob.isCancelled()) {
            try {
                refreshJob = scheduler.scheduleWithFixedDelay(this::refreshAndUpdateStatus, 0, refresh,
                        TimeUnit.MINUTES);
                logger.debug("Scheduling at fixed delay refreshjob {}", refreshJob);
            } catch (IllegalArgumentException e) {
                logger.warn("Refresh time value is invalid! Please change the refresh time configuration!", e);
            } catch (RejectedExecutionException e) {
                logger.warn("Automatic refresh job cannot be started!");
            }
        }
    }

    private void stopAutomaticRefresh() {
        logger.debug("Stop automatic refresh for job {}", refreshJob);
        if (refreshJob != null && !refreshJob.isCancelled()) {
            refreshJob.cancel(true);
            refreshJob = null;
        }
    }

    private void refreshAndUpdateStatus() {
        logger.debug("SLTrafficInformationHandler - Refresh thread is up'n running! {}", refreshJob);

        String url = SL_DEVIATIONS_URL + "?key=" + config.apiKeyDeviation + "&lineNumber=" + config.lineNumbers;
        SLTrafficDeviations deviations;
        try {
            String htmlJSON = HttpUtil.executeUrl("POST", url, null, null, null, REQUEST_TIMEOUT);
            logger.debug("Result: {}", htmlJSON);
            deviations = gson.fromJson(htmlJSON, SLTrafficDeviations.class);

            if (deviations != null && deviations.getMessage() == null) {
                getThing().getChannels().stream().map(Channel::getUID).filter(channelUID -> isLinked(channelUID))
                        .forEach(channelUID -> {
                            State state = getValue(channelUID.getId(), deviations);
                            updateState(channelUID, state);
                        });
            } else {
                logger.warn("Update Deviation status failed! Message: {}", deviations.getMessage());
            }
        } catch (RuntimeException | IOException e) {
            logger.warn("API request failed, exception caught: {}", e.getMessage(), e);
        }
    }

    private void handleDeviations(ResponseData response, StringBuffer result) {
        result.append("För ");
        result.append(response.getScope());
        result.append(" gäller ");
        result.append(response.getHeader());
        result.append(". ");
    }

    public State getValue(String channelId, SLTrafficDeviations deviations) {
        switch (channelId) {
            case CHANNEL_DEVIATIONS:
                StringBuffer result = new StringBuffer();
                deviations.getResponseData().stream().forEach(r -> handleDeviations(r, result));
                return new StringType(result.toString());
        }
        return UnDefType.UNDEF;
    }
}
