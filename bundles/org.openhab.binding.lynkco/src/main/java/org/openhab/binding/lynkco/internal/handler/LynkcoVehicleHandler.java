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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lynkco.internal.LynkcoConfiguration;
import org.openhab.binding.lynkco.internal.api.LynkcoAPI;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LynkcoVehicleHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class LynkcoVehicleHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(LynkcoVehicleHandler.class);

    private LynkcoConfiguration config = new LynkcoConfiguration();

    public LynkcoVehicleHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Command received: {}", command);
        if (CHANNEL_STATUS.equals(channelUID.getId()) || command instanceof RefreshType) {
            Bridge bridge = getBridge();
            if (bridge != null) {
                BridgeHandler bridgeHandler = bridge.getHandler();
                if (bridgeHandler != null) {
                    bridgeHandler.handleCommand(channelUID, command);
                }
            }
        } else {

        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(LynkcoConfiguration.class);
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            update();
            Map<String, String> properties = refreshProperties();
            updateProperties(properties);
        });
    }

    public void update() {
        LynkcoDTO dto = getLynkcoDTO();
        if (dto != null) {
            update(dto);
        } else {
            logger.warn("LynkcoDTO is null!");
        }
    }

    private @Nullable LynkcoAPI getLynkcoAPI() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            LynkcoBridgeHandler handler = (LynkcoBridgeHandler) bridge.getHandler();
            if (handler != null) {
                return handler.getLynkcoAPI();
            }
        }
        return null;
    }

    private @Nullable LynkcoDTO getLynkcoDTO() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            LynkcoBridgeHandler bridgeHandler = (LynkcoBridgeHandler) bridge.getHandler();
            if (bridgeHandler != null) {
                return bridgeHandler.getLynkcoThings().get(config.getDeviceId());
            }
        }
        return null;
    }

    private void update(@Nullable LynkcoDTO dto) {
        if (dto != null) {
            // Update all channels from the updated data
            getThing().getChannels().stream().map(Channel::getUID).filter(channelUID -> isLinked(channelUID))
                    .forEach(channelUID -> {
                        State state = getValue(channelUID.getId(), dto);
                        logger.trace("Channel: {}, State: {}", channelUID, state);
                        updateState(channelUID, state);
                    });
            updateStatus(ThingStatus.ONLINE);
        }
    }

    private State getValue(String channelId, LynkcoDTO dto) {

        return UnDefType.UNDEF;
    }

    private Map<String, String> refreshProperties() {
        Map<String, String> properties = new HashMap<>();
        Bridge bridge = getBridge();
        if (bridge != null) {
            LynkcoBridgeHandler bridgeHandler = (LynkcoBridgeHandler) bridge.getHandler();
            if (bridgeHandler != null) {
                LynkcoDTO dto = bridgeHandler.getLynkcoThings().get(config.getDeviceId());
                if (dto != null) {
                    properties.put(Thing.PROPERTY_VENDOR, dto.getApplianceInfo().getBrand());
                    properties.put(PROPERTY_COLOUR, dto.getApplianceInfo().getColour());
                    properties.put(PROPERTY_DEVICE, dto.getApplianceInfo().getDeviceType());
                    properties.put(Thing.PROPERTY_MODEL_ID, dto.getApplianceInfo().getModel());
                    properties.put(Thing.PROPERTY_SERIAL_NUMBER, dto.getApplianceInfo().getSerialNumber());
                    properties.put(Thing.PROPERTY_FIRMWARE_VERSION, dto.getProperties().getReported().getFrmVerNIU());
                }
            }
        }
        return properties;
    }
}
