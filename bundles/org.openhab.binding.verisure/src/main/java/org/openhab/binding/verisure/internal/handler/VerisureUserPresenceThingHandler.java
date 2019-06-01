/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.openhab.binding.verisure.internal.model.VerisureThingJSON;
import org.openhab.binding.verisure.internal.model.VerisureUserPresencesJSON;

/**
 * Handler for the User Presence Device thing type that Verisure provides.
 *
 * @author Jan Gustafsson - Initial contribution
 *
 */
@NonNullByDefault
public class VerisureUserPresenceThingHandler extends VerisureThingHandler {

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<ThingTypeUID>();
    static {
        SUPPORTED_THING_TYPES.add(THING_TYPE_USERPRESENCE);
    }

    public VerisureUserPresenceThingHandler(Thing thing) {
        super(thing);
    }
    
    public synchronized void update(@Nullable VerisureThingJSON thing) {
        logger.debug("update on thing: {}", thing);
        updateStatus(ThingStatus.ONLINE);
        if (getThing().getThingTypeUID().equals(THING_TYPE_USERPRESENCE)) {
        	VerisureUserPresencesJSON obj = (VerisureUserPresencesJSON) thing;
            if (obj != null) {
                updateUserPresenceState(obj);
            }
        } else {
            logger.warn("Can't handle this thing typeuid: {}", getThing().getThingTypeUID());
        }
    }
    
    private void updateUserPresenceState(VerisureUserPresencesJSON userPresenceJSON) {
        ChannelUID cuid = new ChannelUID(getThing().getUID(), CHANNEL_USER_NAME);
        updateState(cuid, new StringType(userPresenceJSON.getData().getInstallation().getUserTrackings().get(0).getName()));
        cuid = new ChannelUID(getThing().getUID(), CHANNEL_USER_LOCATION_NAME);
        updateState(cuid, new StringType(userPresenceJSON.getData().getInstallation().getUserTrackings().get(0).getCurrentLocationName()));
        cuid = new ChannelUID(getThing().getUID(), CHANNEL_WEBACCOUNT);
        updateState(cuid, new StringType(userPresenceJSON.getData().getInstallation().getUserTrackings().get(0).getWebAccount()));
        updateTimeStamp(userPresenceJSON.getData().getInstallation().getUserTrackings().get(0).getCurrentLocationTimestamp());
        cuid = new ChannelUID(getThing().getUID(), CHANNEL_USER_DEVICE_NAME);
        updateState(cuid, new StringType(userPresenceJSON.getData().getInstallation().getUserTrackings().get(0).getDeviceName()));
        cuid = new ChannelUID(getThing().getUID(), CHANNEL_INSTALLATION_ID);
        BigDecimal siteId = userPresenceJSON.getSiteId();
        if (siteId != null) {
            updateState(cuid, new DecimalType(siteId.longValue()));
        }
        cuid = new ChannelUID(getThing().getUID(), CHANNEL_INSTALLATION_NAME);
        StringType instName = new StringType(userPresenceJSON.getSiteName());
        updateState(cuid, instName);
    }
}


