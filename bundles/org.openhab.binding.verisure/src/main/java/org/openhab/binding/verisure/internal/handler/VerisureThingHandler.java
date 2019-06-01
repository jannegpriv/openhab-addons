/**
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
package org.openhab.binding.verisure.internal.handler;

import static org.openhab.binding.verisure.internal.VerisureBindingConstants.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.BridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.verisure.internal.DeviceStatusListener;
import org.openhab.binding.verisure.internal.VerisureSession;
import org.openhab.binding.verisure.internal.VerisureThingConfiguration;
import org.openhab.binding.verisure.internal.model.VerisureThingJSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class and handler for some of the different thing types that Verisure provides.
 *
 * @author Jarle Hjortland - Initial contribution
 *
 */
@NonNullByDefault
public class VerisureThingHandler extends BaseThingHandler implements DeviceStatusListener {

    protected final Logger logger = LoggerFactory.getLogger(VerisureThingHandler.class);

    protected @Nullable VerisureSession session;

    protected @Nullable VerisureThingConfiguration config;

    public VerisureThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand, channel: {}, command: {}", channelUID, command);
        if (command instanceof RefreshType) {
            Bridge bridge = getBridge();
            if (bridge != null) {
                BridgeHandler bridgeHandler = bridge.getHandler();
                if (bridgeHandler != null) {
                    bridgeHandler.handleCommand(channelUID, command);
                }
            }
            if (session != null && config.deviceId != null) {
                VerisureThingJSON thing = session.getVerisureThing(config.deviceId.replaceAll("[^a-zA-Z0-9]+", ""));
                update(thing);
            }
        } else {
            logger.warn("Unknown command! {}", command);
        }
    }

    protected void scheduleImmediateRefresh() {
        logger.debug("scheduleImmediateRefresh on thing: {}", thing);
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() != null) {
            VerisureBridgeHandler vbh = (VerisureBridgeHandler) bridge.getHandler();
            if (vbh != null) {
                vbh.scheduleImmediateRefresh(VerisureBridgeHandler.REFRESH_DELAY_SECONDS);
            }
        }
    }

	protected void updateTimeStamp(@Nullable String lastUpdatedTimeStamp) {
		if (lastUpdatedTimeStamp != null) {
			try {
				logger.debug("Parsing date {}", lastUpdatedTimeStamp);
				ZonedDateTime zdt = ZonedDateTime.parse(lastUpdatedTimeStamp);
				ZonedDateTime zdtLocal = zdt.withZoneSameInstant(ZoneId.systemDefault());

				logger.trace("Parsing datetime successful. Using date. {}", new DateTimeType(zdtLocal));
				ChannelUID cuid = new ChannelUID(getThing().getUID(), CHANNEL_TIMESTAMP);
				updateState(cuid, new DateTimeType(zdtLocal));

			} catch (IllegalArgumentException e) {
				logger.warn("Parsing date failed: {}.", e);
			}
		} else {
			logger.debug("Timestamp is null!");
		}
	}
    
    @Override
    public void initialize() {
        logger.debug("initialize on thing: {}", thing);
        // Do not go online
        config = getConfigAs(VerisureThingConfiguration.class);
        if (config.deviceId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Verisure device is missing deviceId");
        }
        Bridge bridge = getBridge();
        if (bridge != null) {
            this.bridgeStatusChanged(bridge.getStatusInfo());
        }
    }

    @Override
    public void dispose() {
        logger.debug("dispose on thing: {}", thing);
        Bridge bridge = getBridge();
        if (bridge != null) {
            VerisureBridgeHandler vbh = (VerisureBridgeHandler) bridge.getHandler();
            if (vbh != null) {
                session = vbh.getSession();
                if (session != null) {
                    session.unregisterDeviceStatusListener(this);
                }
            }
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("bridgeStatusChanged bridgeStatusInfo: {}", bridgeStatusInfo);
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            Bridge bridge = getBridge();
            if (bridge != null) {
                VerisureBridgeHandler vbh = (VerisureBridgeHandler) bridge.getHandler();
                if (vbh != null) {
                    session = vbh.getSession();
                    if (session != null && config.deviceId != null) {
                        update(session.getVerisureThing(config.deviceId.replaceAll("[^a-zA-Z0-9]+", "")));
                        session.registerDeviceStatusListener(this);
                    }
                }
            }
        }
        super.bridgeStatusChanged(bridgeStatusInfo);
    }

    @Override
    public void onDeviceStateChanged(@Nullable VerisureThingJSON thing) {
        logger.trace("onDeviceStateChanged on thing: {}", thing);
        if (thing != null) {
            String id = thing.getDeviceId();
            id.replaceAll("[^a-zA-Z0-9]+", "");
            if (config.deviceId.replaceAll("[^a-zA-Z0-9]+", "").equalsIgnoreCase((id))) {
                update(thing);
            }
        }
    }
    
    public synchronized void update(@Nullable VerisureThingJSON thing) {
    	
    }

    @Override
    public void onDeviceRemoved(@Nullable VerisureThingJSON thing) {
        logger.trace("onDeviceRemoved on thing: {}", thing);
    }

    @Override
    public void onDeviceAdded(@Nullable VerisureThingJSON thing) {
        logger.trace("onDeviceAdded on thing: {}", thing);
    }
}
