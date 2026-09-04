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

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;

/**
 * Tests for register decoding and state conversion.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
class SigenergyPlantRegistersTest {

    private State decode(SigenergyPlantRegisters register, int... words) {
        ModbusRegisterArray registers = new ModbusRegisterArray(words);
        Optional<DecimalType> value = ModbusBitUtilities.extractStateFromRegisters(registers, 0, register.getType());
        assertTrue(value.isPresent());
        return register.createState(value.get());
    }

    @Test
    public void testSocConversion() {
        // live-verified: raw 322 at address 30014 was 32.2 % in MySigen
        State state = decode(SigenergyPlantRegisters.BATTERY_SOC, 322);
        assertEquals(new QuantityType<>("32.2 %"), state);
    }

    @Test
    public void testS32PositiveConversion() {
        // 700 as big-endian S32, high word first
        State state = decode(SigenergyPlantRegisters.GRID_POWER, 0, 700);
        assertEquals(new QuantityType<>("700 W"), state);
    }

    @Test
    public void testS32NegativeConversion() {
        // -700 = 0xFFFFFD44
        State state = decode(SigenergyPlantRegisters.GRID_POWER, 0xFFFF, 0xFD44);
        assertEquals(new QuantityType<>("-700 W"), state);
    }

    @Test
    public void testPhasePowerSigns() {
        assertEquals(new QuantityType<>("1500 W"), decode(SigenergyPlantRegisters.GRID_PHASE_A_POWER, 0, 1500));
        assertEquals(new QuantityType<>("-1500 W"), decode(SigenergyPlantRegisters.GRID_PHASE_B_POWER, 0xFFFF, 0xFA24));
        assertEquals(new QuantityType<>("0 W"), decode(SigenergyPlantRegisters.GRID_PHASE_C_POWER, 0, 0));
    }

    @Test
    public void testBatterySignSemanticsPreserved() {
        // negative = discharge, positive = charge; the raw sign must pass through unchanged
        assertEquals(new QuantityType<>("-700 W"), decode(SigenergyPlantRegisters.BATTERY_POWER, 0xFFFF, 0xFD44));
        assertEquals(new QuantityType<>("700 W"), decode(SigenergyPlantRegisters.BATTERY_POWER, 0, 700));
    }

    @Test
    public void testS16TemperatureConversion() {
        // gain 10, degree Celsius; S16 two's complement
        assertEquals(new QuantityType<>("28.5 °C"), decode(SigenergyPlantRegisters.CELL_TEMPERATURE, 285));
        // -10 raw = 0xFFF6 -> -1.0 °C
        assertEquals(new QuantityType<>("-1.0 °C"), decode(SigenergyPlantRegisters.CELL_TEMPERATURE, 0xFFF6));
    }

    @Test
    public void testU32EnergyConversion() {
        // gain 100 kWh: raw 1234 -> 12.34 kWh
        assertEquals(new QuantityType<>("12.34 kWh"), decode(SigenergyPlantRegisters.PV_GENERATION_TODAY, 0, 1234));
        // U32 above the S16 range
        assertEquals(new QuantityType<>("655360.00 kWh"),
                decode(SigenergyPlantRegisters.LOAD_CONSUMPTION_TODAY, 1000, 0));
    }

    @Test
    public void testU64EnergyConversionAbove32BitRange() {
        // 0x0000000100000001 = 4294967297 raw -> 42949672.97 kWh; must not truncate to 32 bits
        assertEquals(new QuantityType<>("42949672.97 kWh"),
                decode(SigenergyPlantRegisters.TOTAL_PV_GENERATION, 0, 1, 0, 1));
        // and a plain in-range value: 123456 raw -> 1234.56 kWh
        assertEquals(new QuantityType<>("1234.56 kWh"),
                decode(SigenergyPlantRegisters.TOTAL_IMPORTED_ENERGY, 0, 0, 1, 0xE240));
    }

    @Test
    public void testPercentConversions() {
        assertEquals(new QuantityType<>("10.0 %"), decode(SigenergyPlantRegisters.DISCHARGE_CUTOFF_SOC, 100));
        assertEquals(new QuantityType<>("100.0 %"), decode(SigenergyPlantRegisters.CHARGE_CUTOFF_SOC, 1000));
        assertEquals(new QuantityType<>("99.5 %"), decode(SigenergyPlantRegisters.BATTERY_SOH, 995));
    }

    @Test
    public void testEnumMappings() {
        // canonical tokens per Sigenergy Modbus Protocol V2.9, registers 30003/30004/30009/30051
        assertEquals("MAX_SELF_CONSUMPTION", SigenergyEnumMappers.emsMode(0));
        assertEquals("AI_MODE", SigenergyEnumMappers.emsMode(1));
        assertEquals("TOU", SigenergyEnumMappers.emsMode(2));
        assertEquals("FULL_FEED_IN_TO_GRID", SigenergyEnumMappers.emsMode(5));
        assertEquals("REMOTE_EMS", SigenergyEnumMappers.emsMode(7));
        assertEquals("CUSTOM", SigenergyEnumMappers.emsMode(9));
        assertEquals("Unknown (42)", SigenergyEnumMappers.emsMode(42));

        assertEquals("NOT_CONNECTED", SigenergyEnumMappers.gridSensorStatus(0));
        assertEquals("CONNECTED", SigenergyEnumMappers.gridSensorStatus(1));
        assertEquals("Unknown (3)", SigenergyEnumMappers.gridSensorStatus(3));

        assertEquals("ON_GRID", SigenergyEnumMappers.onOffGridStatus(0));
        assertEquals("OFF_GRID_AUTO", SigenergyEnumMappers.onOffGridStatus(1));
        assertEquals("OFF_GRID_MANUAL", SigenergyEnumMappers.onOffGridStatus(2));
        assertEquals("Unknown (7)", SigenergyEnumMappers.onOffGridStatus(7));

        assertEquals("STANDBY", SigenergyEnumMappers.plantRunningState(0));
        assertEquals("RUNNING", SigenergyEnumMappers.plantRunningState(1));
        assertEquals("FAULT", SigenergyEnumMappers.plantRunningState(2));
        assertEquals("SHUTDOWN", SigenergyEnumMappers.plantRunningState(3));
        assertEquals("ENVIRONMENTAL_ABNORMALITY", SigenergyEnumMappers.plantRunningState(7));
        assertEquals("Unknown (4)", SigenergyEnumMappers.plantRunningState(4));
    }

    @Test
    public void testEnumRegisterCreatesStringState() {
        assertEquals(new StringType("AI_MODE"), decode(SigenergyPlantRegisters.EMS_MODE, 1));
        assertEquals(new StringType("Unknown (42)"), decode(SigenergyPlantRegisters.EMS_MODE, 42));
        assertEquals(new StringType("RUNNING"), decode(SigenergyPlantRegisters.PLANT_RUNNING_STATE, 1));
    }

    @Test
    public void testPduAddressesTypesAndCounts() {
        // Sigenergy addresses are PDU addresses; no one-based correction may ever be applied
        assertRegister(SigenergyPlantRegisters.EMS_MODE, 30003, ValueType.UINT16, 1);
        assertRegister(SigenergyPlantRegisters.GRID_SENSOR_STATUS, 30004, ValueType.UINT16, 1);
        assertRegister(SigenergyPlantRegisters.GRID_POWER, 30005, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.ON_OFF_GRID_STATUS, 30009, ValueType.UINT16, 1);
        assertRegister(SigenergyPlantRegisters.BATTERY_SOC, 30014, ValueType.UINT16, 1);
        assertRegister(SigenergyPlantRegisters.PLANT_POWER, 30031, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.PV_POWER, 30035, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.BATTERY_POWER, 30037, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.PLANT_RUNNING_STATE, 30051, ValueType.UINT16, 1);
        assertRegister(SigenergyPlantRegisters.GRID_PHASE_A_POWER, 30052, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.GRID_PHASE_B_POWER, 30054, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.GRID_PHASE_C_POWER, 30056, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.AVAILABLE_CHARGE_CAPACITY, 30064, ValueType.UINT32, 2);
        assertRegister(SigenergyPlantRegisters.AVAILABLE_DISCHARGE_CAPACITY, 30066, ValueType.UINT32, 2);
        assertRegister(SigenergyPlantRegisters.RATED_CAPACITY, 30083, ValueType.UINT32, 2);
        assertRegister(SigenergyPlantRegisters.CHARGE_CUTOFF_SOC, 30085, ValueType.UINT16, 1);
        assertRegister(SigenergyPlantRegisters.DISCHARGE_CUTOFF_SOC, 30086, ValueType.UINT16, 1);
        assertRegister(SigenergyPlantRegisters.BATTERY_SOH, 30087, ValueType.UINT16, 1);
        assertRegister(SigenergyPlantRegisters.TOTAL_PV_GENERATION, 30088, ValueType.UINT64, 4);
        assertRegister(SigenergyPlantRegisters.LOAD_CONSUMPTION_TODAY, 30092, ValueType.UINT32, 2);
        assertRegister(SigenergyPlantRegisters.TOTAL_LOAD_CONSUMPTION, 30094, ValueType.UINT64, 4);
        assertRegister(SigenergyPlantRegisters.TOTAL_BATTERY_CHARGED_ENERGY, 30200, ValueType.UINT64, 4);
        assertRegister(SigenergyPlantRegisters.TOTAL_BATTERY_DISCHARGED_ENERGY, 30204, ValueType.UINT64, 4);
        assertRegister(SigenergyPlantRegisters.TOTAL_IMPORTED_ENERGY, 30216, ValueType.UINT64, 4);
        assertRegister(SigenergyPlantRegisters.TOTAL_EXPORTED_ENERGY, 30220, ValueType.UINT64, 4);
        assertRegister(SigenergyPlantRegisters.PV_GENERATION_TODAY, 30272, ValueType.UINT32, 2);
        assertRegister(SigenergyPlantRegisters.PV_GENERATION_YESTERDAY, 30274, ValueType.UINT32, 2);
        assertRegister(SigenergyPlantRegisters.GENERAL_LOAD_POWER, 30282, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.TOTAL_LOAD_POWER, 30284, ValueType.INT32, 2);
        assertRegister(SigenergyPlantRegisters.CELL_TEMPERATURE, 30286, ValueType.INT16, 1);
    }

    private void assertRegister(SigenergyPlantRegisters register, int address, ValueType type, int count) {
        assertEquals(address, register.getAddress(), register.name());
        assertEquals(type, register.getType(), register.name());
        assertEquals(count, register.getRegisterCount(), register.name());
    }

    @Test
    public void testTotalLoadPowerMapsToLoadPowerChannel() {
        assertEquals("load-power", SigenergyPlantRegisters.TOTAL_LOAD_POWER.getChannelName());
        assertEquals("general-load-power", SigenergyPlantRegisters.GENERAL_LOAD_POWER.getChannelName());
        assertEquals("overview", SigenergyPlantRegisters.TOTAL_LOAD_POWER.getChannelGroup());
        assertEquals("energy", SigenergyPlantRegisters.TOTAL_PV_GENERATION.getChannelGroup());
        assertEquals("battery", SigenergyPlantRegisters.CELL_TEMPERATURE.getChannelGroup());
    }
}
