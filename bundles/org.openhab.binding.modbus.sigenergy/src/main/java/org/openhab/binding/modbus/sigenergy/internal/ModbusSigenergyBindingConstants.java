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
package org.openhab.binding.modbus.sigenergy.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.modbus.ModbusBindingConstants;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link ModbusSigenergyBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class ModbusSigenergyBindingConstants {

    /**
     * ThingType-ID for the Sigenergy plant.
     */
    public static final ThingTypeUID THING_TYPE_PLANT = new ThingTypeUID(ModbusBindingConstants.BINDING_ID,
            "sigenergy-plant");

    /**
     * ThingType-ID for the Sigen EVAC (AC charger).
     */
    public static final ThingTypeUID THING_TYPE_EVAC = new ThingTypeUID(ModbusBindingConstants.BINDING_ID,
            "sigenergy-evac");

    /**
     * Channel group ids.
     */
    public static final String GROUP_OVERVIEW = "overview";
    public static final String GROUP_GRID = "grid";
    public static final String GROUP_BATTERY = "battery";
    public static final String GROUP_ENERGY = "energy";

    /**
     * Channel id of the total load power channel (register 30284, derived fallback on old firmware).
     */
    public static final String CHANNEL_LOAD_POWER = "load-power";

    /**
     * Channel id of the last-successful-update timestamp channel.
     */
    public static final String CHANNEL_LAST_UPDATE = "last-successful-update";

    /**
     * Minimum allowed poll interval in milliseconds.
     */
    public static final int MIN_POLL_INTERVAL_MS = 1000;
}
