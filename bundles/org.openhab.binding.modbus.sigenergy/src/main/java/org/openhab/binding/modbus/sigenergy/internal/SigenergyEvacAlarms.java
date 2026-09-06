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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Decodes the documented EVAC alarm bits of registers 32012-32014 (Sigenergy Modbus Protocol V2.9,
 * appendices 8-10). Undocumented bits are never part of the summary but are detectable separately
 * so the handler can log them.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public final class SigenergyEvacAlarms {

    // index = bit number; documented bits only
    private static final String[] MASK_1 = { "GRID_OVERVOLTAGE", "GRID_UNDERVOLTAGE", "OVERLOAD", "SHORT_CIRCUIT",
            "CHARGING_OUTPUT_OVERCURRENT", "LEAKAGE_CURRENT_OUT_OF_LIMIT", "GROUNDING_FAULT",
            "ABNORMAL_GRID_PHASE_SEQUENCE", "PEN_FAULT" };
    private static final String[] MASK_2 = { "LEAKAGE_CURRENT_DETECTION_CIRCUIT_FAULT", "RELAY_STUCK",
            "PILOT_CIRCUIT_FAULT", "AUXILIARY_POWER_SUPPLY_MODULE_FAULT", "ELECTRIC_LOCK_FAULT",
            "LAMP_PANEL_COMMUNICATION_FAULT" };
    private static final String[] MASK_3 = { "INTERNAL_TEMPERATURE_TOO_HIGH", "CHARGING_CABLE_FAULT",
            "METER_COMMUNICATION_FAULT" };

    private static final String[][] MASKS = { MASK_1, MASK_2, MASK_3 };

    private SigenergyEvacAlarms() {
    }

    /**
     * Returns the active documented alarms in deterministic order: mask 1 bit 0 upwards, then mask 2,
     * then mask 3.
     */
    public static List<String> activeAlarms(int mask1, int mask2, int mask3) {
        int[] values = { mask1, mask2, mask3 };
        List<String> active = new ArrayList<>();
        for (int m = 0; m < MASKS.length; m++) {
            if (values[m] == 0xFFFF) {
                // all bits set marks the mask as not applicable on this firmware
                continue;
            }
            for (int bit = 0; bit < MASKS[m].length; bit++) {
                if ((values[m] & (1 << bit)) != 0) {
                    active.add(MASKS[m][bit]);
                }
            }
        }
        return active;
    }

    /**
     * Returns a stable summary string of the active documented alarms, or "NONE".
     */
    public static String summary(int mask1, int mask2, int mask3) {
        List<String> active = activeAlarms(mask1, mask2, mask3);
        return active.isEmpty() ? "NONE" : String.join(", ", active);
    }

    /**
     * Returns whether any documented alarm bit is set.
     */
    public static boolean anyActive(int mask1, int mask2, int mask3) {
        return !activeAlarms(mask1, mask2, mask3).isEmpty();
    }

    /**
     * Returns the bits set in the given mask that are not documented (maskIndex 1-3), for diagnostics.
     */
    public static int undocumentedBits(int maskIndex, int value) {
        int documented = (1 << MASKS[maskIndex - 1].length) - 1;
        return value & ~documented & 0xFFFF;
    }
}
