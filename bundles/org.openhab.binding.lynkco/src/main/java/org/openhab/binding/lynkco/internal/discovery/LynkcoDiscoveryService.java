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
package org.openhab.binding.lynkco.internal.discovery;

import static org.openhab.binding.lynkco.internal.LynkcoBindingConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lynkco.internal.LynkcoModel;
import org.openhab.binding.lynkco.internal.LynkcoVehicleConfiguration;
import org.openhab.binding.lynkco.internal.Platform;
import org.openhab.binding.lynkco.internal.api.LynkcoApiException;
import org.openhab.binding.lynkco.internal.api.VehiclePlatform;
import org.openhab.binding.lynkco.internal.handler.LynkcoBridgeHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LynkcoDiscoveryService} discovers the vehicles on the account via the gateway
 * {@code /list/vehicles} endpoint, so they appear in the Inbox with VIN and model pre-filled and
 * no VIN has to be entered manually.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = LynkcoDiscoveryService.class)
@NonNullByDefault
public class LynkcoDiscoveryService extends AbstractThingHandlerDiscoveryService<LynkcoBridgeHandler> {
    private static final int SEARCH_TIME = 10;

    private final Logger logger = LoggerFactory.getLogger(LynkcoDiscoveryService.class);

    public LynkcoDiscoveryService() {
        super(LynkcoBridgeHandler.class, SUPPORTED_THING_TYPES_UIDS, SEARCH_TIME);
    }

    @Override
    protected void startScan() {
        ThingUID bridgeUID = thingHandler.getThing().getUID();
        try {
            VehiclePlatform gateway = thingHandler.getVehiclePlatform(Platform.GATEWAY);
            Map<String, String> vehicles = gateway.listVehicles();
            logger.debug("Discovered {} Lynk&Co vehicle(s)", vehicles.size());
            for (Map.Entry<String, String> entry : vehicles.entrySet()) {
                String vin = entry.getKey();
                LynkcoModel model = LynkcoModel.fromCode(entry.getValue());
                thingDiscovered(DiscoveryResultBuilder.create(new ThingUID(THING_TYPE_VEHICLE, bridgeUID, vin))
                        .withLabel(model.getDisplayName() + " (" + vin + ")").withBridge(bridgeUID)
                        .withProperty(LynkcoVehicleConfiguration.DEVICE_ID_LABEL, vin)
                        .withProperty(PROPERTY_PLATFORM, model.getPlatform().name())
                        .withRepresentationProperty(LynkcoVehicleConfiguration.DEVICE_ID_LABEL).build());
            }
        } catch (LynkcoApiException e) {
            logger.warn("Lynk&Co vehicle discovery failed: {}", e.getMessage());
        }
        stopScan();
    }
}
