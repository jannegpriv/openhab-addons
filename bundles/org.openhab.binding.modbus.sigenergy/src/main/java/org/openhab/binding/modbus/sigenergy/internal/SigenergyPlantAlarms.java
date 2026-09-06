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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Decodes the documented plant "Merged Alarm" bits of registers 30027-30030 (appendices 2-5),
 * 30072 (appendix 11) and 30280-30281 (appendices 12-13) of the Sigenergy Modbus Protocol V2.9.
 * Identifiers are prefixed by their source (PCS/ESS/GATEWAY/DC_CHARGER/PLANT) since several
 * appendix tables reuse the same alarm names. Undocumented bits are never part of the summary
 * but are detectable separately.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public final class SigenergyPlantAlarms {

    /** Number of merged alarm masks. */
    public static final int MASK_COUNT = 7;

    /** All bits set marks a mask as not applicable on this firmware. */
    public static final int NOT_APPLICABLE = 0xFFFF;

    // Merged Alarm 1, register 30027, appendix 2 (PCS alarm code 1)
    private static final Map<Integer, String> MASK_1 = Map.ofEntries(Map.entry(0, "PCS_SOFTWARE_VERSION_MISMATCH"),
            Map.entry(1, "PCS_LOW_INSULATION_RESISTANCE"), Map.entry(2, "PCS_OVER_TEMPERATURE"),
            Map.entry(3, "PCS_EQUIPMENT_FAULT"), Map.entry(4, "PCS_SYSTEM_GROUNDING_FAULT"),
            Map.entry(5, "PCS_PV_STRING_OVERVOLTAGE"), Map.entry(6, "PCS_PV_STRING_REVERSED"),
            Map.entry(7, "PCS_PV_STRING_BACKFILLING"), Map.entry(8, "PCS_AFCI_FAULT"),
            Map.entry(9, "PCS_GRID_POWER_OUTAGE"), Map.entry(10, "PCS_GRID_OVERVOLTAGE"),
            Map.entry(11, "PCS_GRID_UNDERVOLTAGE"), Map.entry(12, "PCS_GRID_OVERFREQUENCY"),
            Map.entry(13, "PCS_GRID_UNDERFREQUENCY"), Map.entry(14, "PCS_GRID_VOLTAGE_IMBALANCE"),
            Map.entry(15, "PCS_DC_COMPONENT_OUT_OF_LIMIT"));

    // Merged Alarm 2, register 30028, appendix 3 (PCS alarm code 2); bit 6 is not defined
    private static final Map<Integer, String> MASK_2 = Map.ofEntries(Map.entry(0, "PCS_LEAK_CURRENT_OUT_OF_LIMIT"),
            Map.entry(1, "PCS_COMMUNICATION_ABNORMAL"), Map.entry(2, "PCS_SYSTEM_INTERNAL_PROTECTION"),
            Map.entry(3, "PCS_AFCI_SELF_CHECK_FAULT"), Map.entry(4, "PCS_OFF_GRID_PROTECTION"),
            Map.entry(5, "PCS_MANUAL_OPERATION_PROTECTION"), Map.entry(7, "PCS_ABNORMAL_PHASE_SEQUENCE"),
            Map.entry(8, "PCS_SHORT_CIRCUIT_TO_PE"), Map.entry(9, "PCS_SOFT_START_FAILURE"));

    // Merged Alarm 3, register 30029, appendix 4 (ESS alarm code)
    private static final Map<Integer, String> MASK_3 = Map.ofEntries(Map.entry(0, "ESS_SOFTWARE_VERSION_MISMATCH"),
            Map.entry(1, "ESS_LOW_INSULATION_RESISTANCE"), Map.entry(2, "ESS_OVER_TEMPERATURE"),
            Map.entry(3, "ESS_EQUIPMENT_FAULT"), Map.entry(4, "ESS_UNDER_TEMPERATURE"),
            Map.entry(5, "ESS_INTERNAL_PROTECTION"), Map.entry(6, "ESS_THERMAL_RUNAWAY"));

    // Merged Alarm 4, register 30030, appendix 5 (Gateway alarm code)
    private static final Map<Integer, String> MASK_4 = Map.ofEntries(Map.entry(0, "GATEWAY_SOFTWARE_VERSION_MISMATCH"),
            Map.entry(1, "GATEWAY_OVER_TEMPERATURE"), Map.entry(2, "GATEWAY_EQUIPMENT_FAULT"),
            Map.entry(3, "GATEWAY_OFFGRID_LEAKAGE_CURRENT"), Map.entry(4, "GATEWAY_N_LINE_GROUNDING_FAULT"),
            Map.entry(5, "GATEWAY_GRID_WIRING_PHASE_SEQUENCE"), Map.entry(6, "GATEWAY_INVERTER_WIRING_PHASE_SEQUENCE"),
            Map.entry(7, "GATEWAY_GRID_PHASE_LOSS"));

    // Merged Alarm 5, register 30072, appendix 11 (DC-Charger alarm code)
    private static final Map<Integer, String> MASK_5 = Map.ofEntries(
            Map.entry(0, "DC_CHARGER_SOFTWARE_VERSION_MISMATCH"), Map.entry(1, "DC_CHARGER_LOW_INSULATION_RESISTANCE"),
            Map.entry(2, "DC_CHARGER_OVER_TEMPERATURE"), Map.entry(3, "DC_CHARGER_EQUIPMENT_FAULT"),
            Map.entry(4, "DC_CHARGER_CHARGING_FAULT"), Map.entry(5, "DC_CHARGER_EQUIPMENT_PROTECTION"));

    // Merged Alarm 6, register 30280, appendix 12 (Plant alarm 1); sparse bits
    private static final Map<Integer, String> MASK_6 = Map.ofEntries(
            Map.entry(0, "PLANT_GATEWAY_COMMUNICATION_ABNORMAL"), Map.entry(1, "PLANT_METER_COMMUNICATION_ABNORMAL"),
            Map.entry(2, "PLANT_AC_POWER_SENSOR_COMMUNICATION_ABNORMAL"),
            Map.entry(6, "PLANT_GRID_FEED_LIMIT_HARD_PROTECTION"), Map.entry(8, "PLANT_GENERATOR_START_FAILURE"),
            Map.entry(10, "PLANT_CLS_FAULT"));

    // Merged Alarm 7, register 30281, appendix 13 (Plant alarm 2)
    private static final Map<Integer, String> MASK_7 = Map.ofEntries(Map.entry(0, "PLANT_OVGR_FAULT"),
            Map.entry(1, "PLANT_RPR_FAULT"));

    private static final List<Map<Integer, String>> MASKS = List.of(MASK_1, MASK_2, MASK_3, MASK_4, MASK_5, MASK_6,
            MASK_7);

    private SigenergyPlantAlarms() {
    }

    /**
     * Returns the active documented alarms in deterministic order: mask 1 to mask 7, ascending bit
     * within each mask. A negative mask value means "not read yet"; 0xFFFF means "not applicable on
     * this firmware" (live-observed for register 30281 where OVGR/RPR do not apply). Both are skipped.
     */
    public static List<String> activeAlarms(int[] masks) {
        List<String> active = new ArrayList<>();
        for (int m = 0; m < MASK_COUNT && m < masks.length; m++) {
            if (masks[m] < 0 || masks[m] == NOT_APPLICABLE) {
                continue;
            }
            for (Map.Entry<Integer, String> entry : new TreeMap<>(MASKS.get(m)).entrySet()) {
                if ((masks[m] & (1 << entry.getKey())) != 0) {
                    active.add(entry.getValue());
                }
            }
        }
        return active;
    }

    /**
     * Returns a stable summary string of the active documented alarms, or "NONE".
     */
    public static String summary(int[] masks) {
        List<String> active = activeAlarms(masks);
        return active.isEmpty() ? "NONE" : String.join(", ", active);
    }

    /**
     * Returns whether any documented alarm bit is set.
     */
    public static boolean anyActive(int[] masks) {
        return !activeAlarms(masks).isEmpty();
    }

    /**
     * Returns the bits set in the given mask value that are not documented (maskIndex 1-7).
     */
    public static int undocumentedBits(int maskIndex, int value) {
        int documented = 0;
        for (int bit : MASKS.get(maskIndex - 1).keySet()) {
            documented |= 1 << bit;
        }
        return value & ~documented & 0xFFFF;
    }
}
