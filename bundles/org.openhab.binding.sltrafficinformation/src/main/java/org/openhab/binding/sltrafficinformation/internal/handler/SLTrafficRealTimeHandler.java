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

import static org.openhab.binding.sltrafficinformation.internal.SLTrafficInformationBindingConstants.*;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.measure.quantity.Time;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.sltrafficinformation.internal.SLTrafficInformationConfiguration;
import org.openhab.binding.sltrafficinformation.internal.model.SLTrafficRealTime;
import org.openhab.binding.sltrafficinformation.internal.model.SLTrafficRealTime.Deviation;
import org.openhab.binding.sltrafficinformation.internal.model.SLTrafficRealTime.TrafficType;
import org.openhab.binding.sltrafficinformation.internal.util.Either;
import org.openhab.core.io.net.http.HttpUtil;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
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
 * The {@link SLTrafficRealTimeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SLTrafficRealTimeHandler extends BaseThingHandler {
    private static final int REQUEST_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(20);
    private final Logger logger = LoggerFactory.getLogger(SLTrafficRealTimeHandler.class);

    private @Nullable SLTrafficInformationConfiguration config;
    private int refresh = 10;
    private @Nullable ScheduledFuture<?> refreshJob;

    private static final String SL_REAL_TIME_INFO_URL = "https://api.sl.se/api2/realtimedeparturesV4.json";
    private static final long SECONDS_PER_MINUTE = TimeUnit.MINUTES.toSeconds(1);
    private static final long SECONDS_PER_HOUR = TimeUnit.HOURS.toSeconds(1);

    private Gson gson = new GsonBuilder().create();
    private @Nullable Thing thing;

    public SLTrafficRealTimeHandler(Thing thing) {
        super(thing);
        this.thing = thing;
        logger.debug("SLTrafficRealTimeHandler created for thing {}.", thing);
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
        logger.debug("Handler for thing {} disposed.", thing);
        stopAutomaticRefresh();
    }

    @Override
    public void initialize() {
        // logger.debug("Start initializing!");
        config = getConfigAs(SLTrafficInformationConfiguration.class);
        refresh = config.refresh;

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        scheduler.execute(() -> {
            boolean thingReachable = true; // <background task with long running initialization here>
            // when done do:
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

    private @Nullable SLTrafficRealTime sendAPIQuery(String url) {
        try {
            String htmlJSON = HttpUtil.executeUrl("POST", url, null, null, null, REQUEST_TIMEOUT);
            logger.debug("sendAPIQuery result: {}", htmlJSON);
            return gson.fromJson(htmlJSON, SLTrafficRealTime.class);
        } catch (RuntimeException | IOException e) {
            logger.warn("API request failed, exception caught: {}", e.getMessage(), e);
        }
        return null;
    }

    private void refreshAndUpdateStatus() {
        logger.debug("SLTrafficInformationHandler - Refresh thread is up'n running! {}", refreshJob);

        String url = SL_REAL_TIME_INFO_URL + "?key=" + config.apiKeyRealTime + "&siteid=" + config.siteId
                + "&timewindow=" + config.timeWindow;
        SLTrafficRealTime realTimeInfo = sendAPIQuery(url);
        if (realTimeInfo != null && realTimeInfo.getMessage() == null) {
            getThing().getChannels().stream().map(Channel::getUID).filter(channelUID -> isLinked(channelUID))
                    .forEach(channelUID -> {
                        State state = getValue(channelUID.getId(), realTimeInfo);
                        updateState(channelUID, state);
                    });
        } else {
            if (realTimeInfo != null) {
                logger.warn("Update real time status failed! Message: {}", realTimeInfo.getMessage());
            }
            logger.debug("Let's try once more");
            SLTrafficRealTime realTimeInfo2 = sendAPIQuery(url);
            if (realTimeInfo2 != null && realTimeInfo2.getMessage() == null) {
                getThing().getChannels().stream().map(Channel::getUID).filter(channelUID -> isLinked(channelUID))
                        .forEach(channelUID -> {
                            State state = getValue(channelUID.getId(), realTimeInfo2);
                            updateState(channelUID, state);
                        });
            } else {
                if (realTimeInfo2 != null) {
                    logger.warn("Update real time status failed again! Message: {}", realTimeInfo2.getMessage());
                }
                logger.debug("Let's wait to next refresh period");
            }
        }
    }

    private TrafficType mapHandleTraffic(TrafficType traffic) {
        if (traffic != null) {
            logger.debug("bus: {}", traffic.getDestination());
        } else {
            logger.debug("traffic is null");
        }
        return traffic;
    }

    private Boolean filterHandleTraffic(Either e) {
        if (e.isLeft()) {
            logger.debug("Exception caught: {}", e.getLeft());
            return false;
        } else {
            Optional<TrafficType> o = e.getRight();
            TrafficType traffic = o.get();
            String journeyDirection = config.journeyDirection;
            String destinations = config.destinations;
            String lineNumbers = config.lineNumbers;

            if (journeyDirection != null && (journeyDirection.equals("1") || journeyDirection.equals("2"))) {
                if (lineNumbers != null) {
                    return lineNumbers.toLowerCase().contains(traffic.getLineNumber().toLowerCase())
                            && journeyDirection.equals(traffic.getJourneyDirection());
                } else {
                    return journeyDirection.equals(traffic.getJourneyDirection());
                }
            } else if (destinations != null) {
                if (lineNumbers != null && journeyDirection != null) {
                    return lineNumbers.toLowerCase().contains(traffic.getLineNumber().toLowerCase())
                            && journeyDirection.equals(traffic.getJourneyDirection());
                } else {
                    return destinations.toLowerCase().contains(traffic.getDestination().toLowerCase());
                }
            } else {
                logger.warn("Neither direction {} nor destination {} is configured!", journeyDirection, destinations);
                return false;
            }
        }
    }

    private void forHandleTraffic(Either e, StringBuffer result) {
        if (e.isLeft()) {
            logger.debug("Exception caught: {}", e.getLeft());
        } else {
            Optional<TrafficType> o = e.getRight();
            TrafficType traffic = o.get();
            logger.debug("b: {}", traffic);
            result.append(traffic.getGroupOfLine() != null ? traffic.getGroupOfLine() : "Buss");
            result.append(" ");
            result.append(traffic.getLineNumber());
            result.append(" till ");
            result.append(traffic.getDestination());
            if (traffic.getDisplayTime().contains(":")) {
                result.append(" går klockan ");
            } else if (traffic.getDisplayTime().contains("Nu")) {
                result.append(" går ");
            } else {
                result.append(" går om ");
            }
            result.append(traffic.getDisplayTime());
            result.append(". ");
            List<Deviation> deviations = traffic.getDeviations();
            if (deviations != null) {
                deviations.stream().forEach(deviation -> {
                    result.append(deviation.getText() + " ");
                });
            }

        }
    }

    private void forHandleDepartures(Either e, StringBuffer result) {
        if (e.isLeft()) {
            logger.debug("Exception caught: {}", e.getLeft());
        } else {
            Optional<TrafficType> o = e.getRight();
            TrafficType traffic = o.get();
            logger.debug("b: {}", traffic);
            result.append(traffic.getDisplayTime());
            result.append(" ");
        }
    }

    private void getDepartures(SLTrafficRealTime realTime, StringBuffer result) {
        // Handle buses
        realTime.getResponseData().getBuses().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                .filter(this::filterHandleTraffic).forEach(eitherBus -> forHandleDepartures(eitherBus, result));

        // Handle trains
        realTime.getResponseData().getTrains().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                .filter(this::filterHandleTraffic).forEach(eitherTrain -> forHandleDepartures(eitherTrain, result));

        // Handle trams
        realTime.getResponseData().getTrams().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                .filter(this::filterHandleTraffic).forEach(eitherTram -> forHandleDepartures(eitherTram, result));

        // Handle metros
        realTime.getResponseData().getMetros().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                .filter(this::filterHandleTraffic).forEach(eitherMetro -> forHandleDepartures(eitherMetro, result));
    }

    private void forHandleDeparturesDeviations(Either e, List<TrafficType> trafficList) {
        if (e.isLeft()) {
            logger.debug("Exception caught: {}", e.getLeft());
        } else {
            Optional<TrafficType> o = e.getRight();
            TrafficType traffic = o.get();
            trafficList.add(traffic);
        }
    }

    private List<TrafficType> getDeparturesDeviations(SLTrafficRealTime realTime) {
        List<TrafficType> traffic = new ArrayList<TrafficType>();
        // Handle buses
        realTime.getResponseData().getBuses().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                .filter(this::filterHandleTraffic)
                .forEach(eitherBus -> forHandleDeparturesDeviations(eitherBus, traffic));

        // Handle trains
        realTime.getResponseData().getTrains().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                .filter(this::filterHandleTraffic)
                .forEach(eitherTrain -> forHandleDeparturesDeviations(eitherTrain, traffic));

        // Handle trams
        realTime.getResponseData().getTrams().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                .filter(this::filterHandleTraffic)
                .forEach(eitherTram -> forHandleDeparturesDeviations(eitherTram, traffic));

        // Handle metros
        realTime.getResponseData().getMetros().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                .filter(this::filterHandleTraffic)
                .forEach(eitherMetro -> forHandleDeparturesDeviations(eitherMetro, traffic));
        return traffic;
    }

    private String convertToMinutes(String time) {
        if (time.contains(":")) {
            LocalTime timeForDeparture = LocalTime.parse(time);
            LocalTime now = LocalTime.now();
            long seconds = 0;

            if (timeForDeparture.getHour() < now.getHour()) {
                Duration duration1 = Duration.between(now, LocalTime.parse("23:59"));
                Duration duration2 = Duration.between(LocalTime.parse("00:00"), timeForDeparture);
                seconds = duration1.getSeconds() + duration2.getSeconds() + 60;

            } else {
                Duration duration = Duration.between(now, timeForDeparture);
                seconds = duration.getSeconds();
            }

            long minutes = (seconds
                    % (TimeUnit.DAYS.toHours(1) * TimeUnit.HOURS.toSeconds(1) * TimeUnit.HOURS.toSeconds(1)))
                    / TimeUnit.HOURS.toMinutes(1);
            return String.valueOf(minutes);
        } else if (time.equals("Nu")) {
            return "0";
        }
        return time;
    }

    private boolean filter(String s) {
        boolean containsMin = s.equals("min");
        boolean result = !containsMin;
        return result;
    }

    public State getValue(String channelId, SLTrafficRealTime realTime) {
        StringBuffer result = new StringBuffer();
        int loop = 0;
        switch (channelId) {
            case CHANNEL_REAL_TIME_INFO:
                // Handle buses
                realTime.getResponseData().getBuses().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                        .filter(this::filterHandleTraffic).forEach(eitherBus -> forHandleTraffic(eitherBus, result));

                // Handle trains
                realTime.getResponseData().getTrains().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                        .filter(this::filterHandleTraffic)
                        .forEach(eitherTrain -> forHandleTraffic(eitherTrain, result));

                // Handle trams
                realTime.getResponseData().getTrams().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                        .filter(this::filterHandleTraffic).forEach(eitherTram -> forHandleTraffic(eitherTram, result));

                // Handle metros
                realTime.getResponseData().getMetros().stream().map(Either.liftWithValue(this::mapHandleTraffic))
                        .filter(this::filterHandleTraffic)
                        .forEach(eitherMetro -> forHandleTraffic(eitherMetro, result));

                return new StringType(result.toString());
            case CHANNEL_NEXT_DEPARTURE:
            case CHANNEL_SECOND_DEPARTURE:
            case CHANNEL_THIRD_DEPARTURE:
                if (channelId.equals(CHANNEL_SECOND_DEPARTURE)) {
                    loop = 1;
                } else if (channelId.equals(CHANNEL_THIRD_DEPARTURE)) {
                    loop = 2;
                }
                getDepartures(realTime, result);
                String departureList = result.toString();
                if (!departureList.equals("")) {
                    String[] departures = departureList.split(" ");
                    List<Integer> filteredDepartures = Stream.of(departures).map(this::convertToMinutes)
                            .filter(this::filter).mapToInt(Integer::parseInt).mapToObj(i -> i)
                            .collect(Collectors.toList());
                    for (Integer integer : filteredDepartures) {
                        if (integer.intValue() > config.offset) {
                            if (loop == 0) {
                                return new QuantityType<Time>(integer.intValue(), Units.MINUTE);
                            }
                            loop--;
                        }
                    }
                }
                break;
            case CHANNEL_NEXT_DEPARTURE_DEVIATIONS:
            case CHANNEL_SECOND_DEPARTURE_DEVIATIONS:
            case CHANNEL_THIRD_DEPARTURE_DEVIATIONS:
                if (channelId.equals(CHANNEL_SECOND_DEPARTURE_DEVIATIONS)) {
                    loop = 1;
                } else if (channelId.equals(CHANNEL_THIRD_DEPARTURE_DEVIATIONS)) {
                    loop = 2;
                }
                List<TrafficType> traffic = getDeparturesDeviations(realTime);
                if (traffic.size() != 0) {
                    for (TrafficType t : traffic) {
                        logger.debug("Traffic: {}", t);
                        String displayTime = t.getDisplayTime();
                        int timeInMin = 0;
                        if (displayTime.equals("Nu")) {
                            timeInMin = 0;
                        } else if (displayTime.contains("min")) {
                            displayTime = displayTime.replace(" min", "");
                            timeInMin = Integer.valueOf(displayTime);
                        } else if (displayTime.contains(":")) {
                            displayTime = convertToMinutes(displayTime);
                            timeInMin = Integer.valueOf(displayTime);
                        }
                        if (timeInMin > config.offset) {
                            if (loop == 0) {
                                if (t.getDeviations() != null) {
                                    StringBuffer deviations = new StringBuffer();
                                    t.getDeviations().stream().forEach(deviation -> {
                                        deviations.append(deviation.getText() + " ");
                                    });
                                    return new StringType(deviations.toString());
                                } else {
                                    return new StringType("Inga avvikelser.");
                                }
                            }
                            loop--;
                        }
                    }
                }
                break;
        }
        return UnDefType.UNDEF;
    }
}
