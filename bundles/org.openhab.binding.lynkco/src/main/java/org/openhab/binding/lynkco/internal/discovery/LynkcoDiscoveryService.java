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
package org.openhab.binding.lynkco.internal.discovery;

import static org.openhab.binding.lynkco.internal.LynkcoBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lynkco.internal.LynkcoVehicleConfiguration;
import org.openhab.binding.lynkco.internal.handler.LynkcoBridgeHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * The {@link LynkcoDiscoveryService} searches for available
 * Electrolux Pure A9 discoverable through Electrolux Delta API.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = LynkcoDiscoveryService.class)
@NonNullByDefault
public class LynkcoDiscoveryService extends AbstractThingHandlerDiscoveryService<LynkcoBridgeHandler> {
    private static final int SEARCH_TIME = 2;

    public LynkcoDiscoveryService() {
        super(LynkcoBridgeHandler.class, SUPPORTED_THING_TYPES_UIDS, SEARCH_TIME);
    }

    @Override
    protected void startScan() {
        ThingUID bridgeUID = thingHandler.getThing().getUID();
        thingHandler.getLynkcoThings().entrySet().stream().forEach(thing -> {
            thingDiscovered(DiscoveryResultBuilder.create(new ThingUID(THING_TYPE_VEHICLE, bridgeUID, thing.getKey()))
                    .withLabel("LynkCo Vehicle").withBridge(bridgeUID)
                    .withProperty(LynkcoVehicleConfiguration.DEVICE_ID_LABEL, thing.getKey())
                    .withRepresentationProperty(LynkcoVehicleConfiguration.DEVICE_ID_LABEL).build());
        });

        stopScan();
    }
}
