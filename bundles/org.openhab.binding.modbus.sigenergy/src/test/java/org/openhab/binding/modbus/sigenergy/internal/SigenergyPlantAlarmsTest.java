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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for the merged plant alarm decoding (registers 30027-30030, 30072, 30280-30281,
 * appendices 2-5 and 11-13).
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
class SigenergyPlantAlarmsTest {

    private static int[] masks(int m1, int m2, int m3, int m4, int m5, int m6, int m7) {
        return new int[] { m1, m2, m3, m4, m5, m6, m7 };
    }

    @Test
    public void testDocumentedBitsPerMask() {
        assertEquals(List.of("PCS_SOFTWARE_VERSION_MISMATCH"),
                SigenergyPlantAlarms.activeAlarms(masks(1, 0, 0, 0, 0, 0, 0)));
        assertEquals(List.of("PCS_DC_COMPONENT_OUT_OF_LIMIT"),
                SigenergyPlantAlarms.activeAlarms(masks(1 << 15, 0, 0, 0, 0, 0, 0)));
        assertEquals(List.of("PCS_SOFT_START_FAILURE"),
                SigenergyPlantAlarms.activeAlarms(masks(0, 1 << 9, 0, 0, 0, 0, 0)));
        assertEquals(List.of("ESS_THERMAL_RUNAWAY"),
                SigenergyPlantAlarms.activeAlarms(masks(0, 0, 1 << 6, 0, 0, 0, 0)));
        assertEquals(List.of("GATEWAY_GRID_PHASE_LOSS"),
                SigenergyPlantAlarms.activeAlarms(masks(0, 0, 0, 1 << 7, 0, 0, 0)));
        assertEquals(List.of("DC_CHARGER_CHARGING_FAULT"),
                SigenergyPlantAlarms.activeAlarms(masks(0, 0, 0, 0, 1 << 4, 0, 0)));
        // sparse bits of appendix 12
        assertEquals(List.of("PLANT_GRID_FEED_LIMIT_HARD_PROTECTION"),
                SigenergyPlantAlarms.activeAlarms(masks(0, 0, 0, 0, 0, 1 << 6, 0)));
        assertEquals(List.of("PLANT_CLS_FAULT"), SigenergyPlantAlarms.activeAlarms(masks(0, 0, 0, 0, 0, 1 << 10, 0)));
        assertEquals(List.of("PLANT_RPR_FAULT"), SigenergyPlantAlarms.activeAlarms(masks(0, 0, 0, 0, 0, 0, 1 << 1)));
        // full documented mask counts: 15 + 9 + 7 + 8 + 6 + 6 + 2 = 53 alarms
        // (mask 1 uses 0x7FFF, not 0xFFFF, since all-bits-set is the not-applicable sentinel)
        assertEquals(53, SigenergyPlantAlarms.activeAlarms(masks(0x7FFF, 0x3BF, 0x7F, 0xFF, 0x3F, 0x547, 0x3)).size());
    }

    @Test
    public void testUndocumentedBitsExcluded() {
        // appendix 3 has no bit 6; appendix 12 gaps at bits 3-5, 7, 9
        assertEquals("NONE", SigenergyPlantAlarms.summary(masks(0, 1 << 6, 0, 0, 0, (1 << 3) | (1 << 9), 1 << 5)));
        assertFalse(SigenergyPlantAlarms.anyActive(masks(0, 1 << 6, 0, 0, 0, 0, 0)));
        assertEquals(1 << 6, SigenergyPlantAlarms.undocumentedBits(2, 1 << 6));
        assertEquals((1 << 3) | (1 << 9), SigenergyPlantAlarms.undocumentedBits(6, (1 << 3) | (1 << 9)));
        assertEquals(0, SigenergyPlantAlarms.undocumentedBits(1, 0xFFFF));
    }

    @Test
    public void testDeterministicOrderAcrossMasks() {
        List<String> alarms = SigenergyPlantAlarms.activeAlarms(masks(1 << 10, 0, 1, 0, 0, 1 << 1, 1));
        assertEquals(List.of("PCS_GRID_OVERVOLTAGE", "ESS_SOFTWARE_VERSION_MISMATCH",
                "PLANT_METER_COMMUNICATION_ABNORMAL", "PLANT_OVGR_FAULT"), alarms);
        assertEquals("PCS_GRID_OVERVOLTAGE, ESS_SOFTWARE_VERSION_MISMATCH, PLANT_METER_COMMUNICATION_ABNORMAL, "
                + "PLANT_OVGR_FAULT", SigenergyPlantAlarms.summary(masks(1 << 10, 0, 1, 0, 0, 1 << 1, 1)));
    }

    @Test
    public void testUnreadMasksSkipped() {
        // -1 = not read yet: must not affect the result and never fabricate alarms
        assertEquals("NONE", SigenergyPlantAlarms.summary(masks(-1, -1, -1, -1, -1, -1, -1)));
        assertEquals(List.of("ESS_EQUIPMENT_FAULT"),
                SigenergyPlantAlarms.activeAlarms(masks(-1, -1, 1 << 3, -1, -1, -1, -1)));
    }

    @Test
    public void testNotApplicableMaskSkipped() {
        // 0xFFFF = not applicable on this firmware (live-observed on register 30281): never an alarm
        assertEquals("NONE", SigenergyPlantAlarms.summary(masks(0, 0, 0, 0, 0, 0, 0xFFFF)));
        assertFalse(SigenergyPlantAlarms.anyActive(masks(0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF)));
        // a real alarm elsewhere still reports
        assertEquals(java.util.List.of("ESS_THERMAL_RUNAWAY"),
                SigenergyPlantAlarms.activeAlarms(masks(0, 0, 1 << 6, 0, 0, 0, 0xFFFF)));
    }

    @Test
    public void testEmptyMasks() {
        assertEquals("NONE", SigenergyPlantAlarms.summary(masks(0, 0, 0, 0, 0, 0, 0)));
        assertFalse(SigenergyPlantAlarms.anyActive(masks(0, 0, 0, 0, 0, 0, 0)));
    }
}
