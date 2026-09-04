/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Maps Sigenergy enum register values to stable, language-neutral canonical tokens. The tokens match
 * the state options declared in the channel types, where the English and Swedish display labels live.
 * Unknown values are preserved as "Unknown (n)".
 *
 * Value tables follow Sigenergy Modbus Protocol V2.9, registers 30003, 30004, 30009 and 30051.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public final class SigenergyEnumMappers {

    private static final Map<Integer, String> EMS_MODE = Map.of( //
            0, "MAX_SELF_CONSUMPTION", //
            1, "AI_MODE", //
            2, "TOU", //
            5, "FULL_FEED_IN_TO_GRID", //
            7, "REMOTE_EMS", //
            9, "CUSTOM");

    private static final Map<Integer, String> GRID_SENSOR_STATUS = Map.of( //
            0, "NOT_CONNECTED", //
            1, "CONNECTED");

    private static final Map<Integer, String> ON_OFF_GRID_STATUS = Map.of( //
            0, "ON_GRID", //
            1, "OFF_GRID_AUTO", //
            2, "OFF_GRID_MANUAL");

    private static final Map<Integer, String> PLANT_RUNNING_STATE = Map.of( //
            0, "STANDBY", //
            1, "RUNNING", //
            2, "FAULT", //
            3, "SHUTDOWN", //
            7, "ENVIRONMENTAL_ABNORMALITY");

    // AC-charger system states per Appendix 7 (IEC 61851-1 definitions)
    private static final Map<Integer, String> EVAC_SYSTEM_STATE = Map.of( //
            0x00, "SYSTEM_INIT", //
            0x01, "A1_A2", //
            0x02, "B1", //
            0x03, "B2", //
            0x04, "C1", //
            0x05, "C2", //
            0x06, "F", //
            0x07, "E");

    private SigenergyEnumMappers() {
    }

    /**
     * Maps the EVAC system state (register 32000). Unknown values are preserved as "UNKNOWN_&lt;raw&gt;".
     */
    public static String evacSystemState(int value) {
        String text = EVAC_SYSTEM_STATE.get(value);
        return text != null ? text : "UNKNOWN_" + value;
    }

    public static String emsMode(int value) {
        return mapOrUnknown(EMS_MODE, value);
    }

    public static String gridSensorStatus(int value) {
        return mapOrUnknown(GRID_SENSOR_STATUS, value);
    }

    public static String onOffGridStatus(int value) {
        return mapOrUnknown(ON_OFF_GRID_STATUS, value);
    }

    public static String plantRunningState(int value) {
        return mapOrUnknown(PLANT_RUNNING_STATE, value);
    }

    private static String mapOrUnknown(Map<Integer, String> mapping, int value) {
        String text = mapping.get(value);
        return text != null ? text : "Unknown (" + value + ")";
    }
}
