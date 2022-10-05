/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.meater.internal.handler;

import static org.openhab.binding.meater.internal.MeaterBindingConstants.*;

import java.time.ZonedDateTime;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.meater.internal.MeaterConfiguration;
import org.openhab.binding.meater.internal.dto.MeaterProbeDTO.Cook;
import org.openhab.binding.meater.internal.dto.MeaterProbeDTO.Device;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MeaterHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class MeaterHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(MeaterHandler.class);

    private MeaterConfiguration config;
    private TimeZoneProvider timeZoneProvider;

    public MeaterHandler(Thing thing, TimeZoneProvider timeZoneProvider) {
        super(thing);
        this.timeZoneProvider = timeZoneProvider;
        config = getConfigAs(MeaterConfiguration.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Command received: {}", command);
        if (CHANNEL_STATUS.equals(channelUID.getId()) || command instanceof RefreshType) {
            update();
        }
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            update();
        });
    }

    public void update() {
        Device meaterProbe = getMeaterProbe();
        if (meaterProbe != null) {
            update(meaterProbe);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.communication-error.probe-offline");
        }
    }

    private @Nullable Device getMeaterProbe() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            MeaterBridgeHandler bridgeHandler = (MeaterBridgeHandler) bridge.getHandler();
            if (bridgeHandler != null) {
                return bridgeHandler.getMeaterThings().get(config.getDeviceId());
            }
        }
        return null;
    }

    private void update(Device meaterProbe) {
        // Update all channels from the updated data
        getThing().getChannels().stream().map(Channel::getUID).filter(channelUID -> isLinked(channelUID))
                .forEach(channelUID -> {
                    State state = getValue(channelUID.getId(), meaterProbe);
                    updateState(channelUID, state);
                });
        updateStatus(ThingStatus.ONLINE);
    }

    private State getValue(String channelId, Device meaterProbe) {
        Cook cook = meaterProbe.cook;
        switch (channelId) {
            case CHANNEL_INTERNAL_TEMPERATURE:
                return new QuantityType<Temperature>(meaterProbe.temperature.internal, SIUnits.CELSIUS);
            case CHANNEL_AMBIENT_TEMPERATURE:
                return new QuantityType<Temperature>(meaterProbe.temperature.ambient, SIUnits.CELSIUS);
            case CHANNEL_COOK_TARGET_TEMPERATURE:
                if (cook != null) {
                    return new QuantityType<Temperature>(cook.temperature.target, SIUnits.CELSIUS);
                }
                break;
            case CHANNEL_COOK_PEAK_TEMPERATURE:
                if (cook != null) {
                    return new QuantityType<Temperature>(cook.temperature.peak, SIUnits.CELSIUS);
                }
                break;
            case CHANNEL_COOK_ELAPSED_TIME:
                if (cook != null) {
                    return new QuantityType<>(cook.time.elapsed, Units.SECOND);
                }
                break;
            case CHANNEL_COOK_REMAINING_TIME:
                if (cook != null) {
                    return new QuantityType<>(cook.time.remaining, Units.SECOND);
                }
                break;
            case CHANNEL_COOK_ID:
                if (cook != null) {
                    return new StringType(cook.id);
                }
                break;
            case CHANNEL_COOK_NAME:
                if (cook != null) {
                    return new StringType(cook.name);
                }
                break;
            case CHANNEL_COOK_STATE:
                if (cook != null) {
                    return new StringType(cook.state);
                }
                break;
            case CHANNEL_LAST_CONNECTION:
                ZonedDateTime zdt = meaterProbe.getLastConnection();
                if (zdt != null) {
                    return new DateTimeType(zdt).toZone(timeZoneProvider.getTimeZone());
                }
            case CHANNEL_COOK_ESTIMATED_END_TIME:
                if (cook != null) {
                    if (cook.time.remaining > -1) {
                        return new DateTimeType(
                                ZonedDateTime.now(timeZoneProvider.getTimeZone()).plusSeconds(cook.time.remaining));
                    }
                }
        }
        return UnDefType.UNDEF;
    }
}
