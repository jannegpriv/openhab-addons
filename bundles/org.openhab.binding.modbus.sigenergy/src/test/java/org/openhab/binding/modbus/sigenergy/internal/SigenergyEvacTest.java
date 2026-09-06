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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.exception.ModbusSlaveErrorResponseException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.State;

/**
 * Tests for the read-only EVAC monitoring: block layout, decoding, alarm bits, state mapping,
 * failure isolation and request staggering.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
class SigenergyEvacTest {

    private static final int EVAC_UNIT_ID = 1;

    private State decode(SigenergyEvacRegisters register, int... words) {
        ModbusRegisterArray registers = new ModbusRegisterArray(words);
        Optional<DecimalType> value = ModbusBitUtilities.extractStateFromRegisters(registers, 0, register.getType());
        assertTrue(value.isPresent());
        return register.createState(value.get());
    }

    @Test
    public void testSingleBlockLayout() {
        // exactly one FC04 read: start 32000 (PDU as written, no subtraction), length 15
        ModbusReadRequestBlueprint blueprint = SigenergyEvacHandler.buildBlueprint(EVAC_UNIT_ID, 3);
        assertEquals(ModbusReadFunctionCode.READ_INPUT_REGISTERS, blueprint.getFunctionCode());
        assertEquals(32000, blueprint.getReference());
        assertEquals(15, blueprint.getDataLength());
        assertEquals(EVAC_UNIT_ID, blueprint.getUnitID());
    }

    @Test
    public void testRegisterAddressesTypesAndCounts() {
        assertEquals(32000, SigenergyEvacRegisters.SYSTEM_STATE.getAddress());
        assertEquals(ValueType.UINT16, SigenergyEvacRegisters.SYSTEM_STATE.getType());
        assertEquals(32000, SigenergyEvacRegisters.RAW_STATE.getAddress());
        assertEquals(32001, SigenergyEvacRegisters.TOTAL_ENERGY.getAddress());
        assertEquals(ValueType.UINT32, SigenergyEvacRegisters.TOTAL_ENERGY.getType());
        assertEquals(32003, SigenergyEvacRegisters.CHARGING_POWER.getAddress());
        assertEquals(ValueType.INT32, SigenergyEvacRegisters.CHARGING_POWER.getType());
        assertEquals(32005, SigenergyEvacRegisters.RATED_POWER.getAddress());
        assertEquals(32007, SigenergyEvacRegisters.RATED_CURRENT.getAddress());
        assertEquals(32009, SigenergyEvacRegisters.RATED_VOLTAGE.getAddress());
        assertEquals(ValueType.UINT16, SigenergyEvacRegisters.RATED_VOLTAGE.getType());
        assertEquals(32010, SigenergyEvacRegisters.INPUT_BREAKER_CURRENT.getAddress());
        assertEquals(32012, SigenergyEvacRegisters.ALARM_MASK_1.getAddress());
        assertEquals(32013, SigenergyEvacRegisters.ALARM_MASK_2.getAddress());
        assertEquals(32014, SigenergyEvacRegisters.ALARM_MASK_3.getAddress());
    }

    @Test
    public void testScalingAndUnits() {
        // charging power: S32 gain 1000 kW; 10200 raw -> 10.2 kW
        assertEquals(new QuantityType<>("10.20 kW"), decode(SigenergyEvacRegisters.CHARGING_POWER, 0, 10200));
        // negative S32 decodes correctly even if unexpected: -10200 = 0xFFFFD828
        assertEquals(new QuantityType<>("-10.20 kW"), decode(SigenergyEvacRegisters.CHARGING_POWER, 0xFFFF, 0xD828));
        // total energy: U32 gain 100 kWh, above 16-bit range: 70000 raw -> 700.00 kWh
        assertEquals(new QuantityType<>("700.00 kWh"), decode(SigenergyEvacRegisters.TOTAL_ENERGY, 1, 4464));
        // rated power 11000 -> 11.0 kW, rated current 1600 -> 16.0 A, voltage 2300 -> 230.0 V
        assertEquals(new QuantityType<>("11.00 kW"), decode(SigenergyEvacRegisters.RATED_POWER, 0, 11000));
        assertEquals(new QuantityType<>("16.00 A"), decode(SigenergyEvacRegisters.RATED_CURRENT, 0, 1600));
        assertEquals(new QuantityType<>("230.0 V"), decode(SigenergyEvacRegisters.RATED_VOLTAGE, 2300));
        // input breaker rating (charger property, not household main fuse): 2500 -> 25.00 A
        assertEquals(new QuantityType<>("25.00 A"), decode(SigenergyEvacRegisters.INPUT_BREAKER_CURRENT, 0, 2500));
    }

    @Test
    public void testSystemStateMapping() {
        assertEquals("SYSTEM_INIT", SigenergyEnumMappers.evacSystemState(0x00));
        assertEquals("A1_A2", SigenergyEnumMappers.evacSystemState(0x01));
        assertEquals("B1", SigenergyEnumMappers.evacSystemState(0x02));
        assertEquals("B2", SigenergyEnumMappers.evacSystemState(0x03));
        assertEquals("C1", SigenergyEnumMappers.evacSystemState(0x04));
        assertEquals("C2", SigenergyEnumMappers.evacSystemState(0x05));
        assertEquals("F", SigenergyEnumMappers.evacSystemState(0x06));
        assertEquals("E", SigenergyEnumMappers.evacSystemState(0x07));
        // unknown values stay visible, never silently mapped
        assertEquals("UNKNOWN_9", SigenergyEnumMappers.evacSystemState(9));
        assertEquals(new StringType("C2"), decode(SigenergyEvacRegisters.SYSTEM_STATE, 5));
        assertEquals(new DecimalType(5), decode(SigenergyEvacRegisters.RAW_STATE, 5));
    }

    @Test
    public void testAlarmBitsAllDocumented() {
        // every documented bit decodes to its identifier
        assertEquals(List.of("GRID_OVERVOLTAGE"), SigenergyEvacAlarms.activeAlarms(1, 0, 0));
        assertEquals(List.of("PEN_FAULT"), SigenergyEvacAlarms.activeAlarms(1 << 8, 0, 0));
        assertEquals(List.of("LEAKAGE_CURRENT_DETECTION_CIRCUIT_FAULT"), SigenergyEvacAlarms.activeAlarms(0, 1, 0));
        assertEquals(List.of("LAMP_PANEL_COMMUNICATION_FAULT"), SigenergyEvacAlarms.activeAlarms(0, 1 << 5, 0));
        assertEquals(List.of("INTERNAL_TEMPERATURE_TOO_HIGH"), SigenergyEvacAlarms.activeAlarms(0, 0, 1));
        assertEquals(List.of("METER_COMMUNICATION_FAULT"), SigenergyEvacAlarms.activeAlarms(0, 0, 1 << 2));
        // full masks: all 9 + 6 + 3 = 18 documented alarms
        assertEquals(18, SigenergyEvacAlarms.activeAlarms(0x1FF, 0x3F, 0x7).size());
    }

    @Test
    public void testAlarmCombinationsAcrossMasksDeterministicOrder() {
        // combination spanning all masks: mask1 bits before mask2 before mask3, ascending bit order
        List<String> alarms = SigenergyEvacAlarms.activeAlarms((1 << 3) | (1 << 0), 1 << 1, 1 << 2);
        assertEquals(List.of("GRID_OVERVOLTAGE", "SHORT_CIRCUIT", "RELAY_STUCK", "METER_COMMUNICATION_FAULT"), alarms);
        assertEquals("GRID_OVERVOLTAGE, SHORT_CIRCUIT, RELAY_STUCK, METER_COMMUNICATION_FAULT",
                SigenergyEvacAlarms.summary((1 << 3) | (1 << 0), 1 << 1, 1 << 2));
    }

    @Test
    public void testEmptyAndUndefinedAlarmBits() {
        assertEquals("NONE", SigenergyEvacAlarms.summary(0, 0, 0));
        // 0xFFFF marks a mask as not applicable on this firmware
        assertEquals("NONE", SigenergyEvacAlarms.summary(0xFFFF, 0, 0));
        assertFalse(SigenergyEvacAlarms.anyActive(0, 0, 0));
        // undefined bits never appear in the summary but are detectable for diagnostics
        assertEquals("NONE", SigenergyEvacAlarms.summary(1 << 15, 1 << 10, 1 << 9));
        assertFalse(SigenergyEvacAlarms.anyActive(1 << 15, 0, 0));
        assertEquals(1 << 15, SigenergyEvacAlarms.undocumentedBits(1, 1 << 15));
        assertEquals(1 << 10, SigenergyEvacAlarms.undocumentedBits(2, 1 << 10));
        assertEquals(1 << 9, SigenergyEvacAlarms.undocumentedBits(3, 1 << 9));
        assertEquals(0, SigenergyEvacAlarms.undocumentedBits(1, 0x1FF));
    }

    @Test
    public void testHandlerDecodesFullBlock() {
        TestSetup setup = new TestSetup(Map.of("unitId", EVAC_UNIT_ID));
        // state C2 charging, energy 700.00 kWh, power 10.2 kW, rated 11 kW/16 A/230 V, breaker 25 A,
        // alarm mask1 bit2 OVERLOAD
        ModbusRegisterArray registers = new ModbusRegisterArray(5, 1, 4464, 0, 10200, 0, 11000, 0, 1600, 2300, 0, 2500,
                1 << 2, 0, 0);
        setup.handler.handleReadResult(new AsyncModbusReadResult(setup.blueprint(), registers));

        verify(setup.callback).stateUpdated(eq(setup.channel("status", "system-state")), eq(new StringType("C2")));
        verify(setup.callback).stateUpdated(eq(setup.channel("charging", "charging-power")),
                eq(new QuantityType<>("10.20 kW")));
        verify(setup.callback).stateUpdated(eq(setup.channel("charging", "total-energy")),
                eq(new QuantityType<>("700.00 kWh")));
        verify(setup.callback).stateUpdated(eq(setup.channel("ratings", "input-breaker-current")),
                eq(new QuantityType<>("25.00 A")));
        verify(setup.callback).stateUpdated(eq(setup.channel("status", "alarm-active")), eq(OnOffType.ON));
        verify(setup.callback).stateUpdated(eq(setup.channel("status", "alarm-summary")),
                eq(new StringType("OVERLOAD")));
        verify(setup.callback, times(1)).stateUpdated(eq(setup.channel("status", "last-successful-update")),
                any(DateTimeType.class));
    }

    @Test
    public void testLastUpdateOnlyOnCompleteSuccessfulPoll() {
        TestSetup setup = new TestSetup(Map.of("unitId", EVAC_UNIT_ID));
        ChannelUID lastUpdate = setup.channel("status", "last-successful-update");

        // a failed read never touches the last-update channel or fabricates values
        setup.handler.handleReadError(
                new AsyncModbusFailure<>(setup.blueprint(), mock(ModbusSlaveErrorResponseException.class)));
        verify(setup.callback, never()).stateUpdated(eq(lastUpdate), any());
        verify(setup.callback, never()).stateUpdated(eq(setup.channel("charging", "charging-power")), any());

        // a successful complete poll updates it exactly once
        ModbusRegisterArray registers = new ModbusRegisterArray(1, 0, 0, 0, 0, 0, 11000, 0, 1600, 2300, 0, 2500, 0, 0,
                0);
        setup.handler.handleReadResult(new AsyncModbusReadResult(setup.blueprint(), registers));
        verify(setup.callback, times(1)).stateUpdated(eq(lastUpdate), any(DateTimeType.class));
    }

    @Test
    public void testEvacFailureIsolatedAndRecovers() {
        // an EVAC failure updates only the EVAC thing's status; a separate plant handler is untouched
        TestSetup evac = new TestSetup(Map.of("unitId", EVAC_UNIT_ID));
        TestPlant plant = new TestPlant();

        evac.handler.handleReadError(
                new AsyncModbusFailure<>(evac.blueprint(), mock(ModbusSlaveErrorResponseException.class)));
        verify(evac.callback).statusUpdated(eq(evac.thing), argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                && info.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR));
        verifyNoInteractions(plant.callback);

        // automatic recovery on the next successful read
        ModbusRegisterArray registers = new ModbusRegisterArray(1, 0, 0, 0, 0, 0, 11000, 0, 1600, 2300, 0, 2500, 0, 0,
                0);
        evac.handler.handleReadResult(new AsyncModbusReadResult(evac.blueprint(), registers));
        verify(evac.callback).statusUpdated(eq(evac.thing), argThat(info -> info.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    public void testInvalidUnitIdIsConfigurationError() {
        for (int unitId : new int[] { 0, 247, 300 }) {
            TestSetup setup = new TestSetup(Map.of("unitId", unitId));
            setup.handler.modbusInitialize();
            verify(setup.callback).statusUpdated(eq(setup.thing),
                    argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                            && info.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR));
        }
    }

    @Test
    public void testEvacPollOffsetRespectsSharedEndpointSpacing() {
        // plant fast slots at n*5000 (core) and n*5000+2500 (load), slow slots at X1300
        long offset = SigenergyEvacHandler.EVAC_POLL_OFFSET_MS;
        assertTrue(offset % 5000 >= 1000, "too close to plant core slot");
        assertTrue(Math.abs(2500 - offset % 5000) >= 1000, "too close to plant load slot");
        for (long slow : SigenergyPlantHandler.SLOW_POLL_OFFSETS_MS) {
            assertTrue(Math.abs(slow % 5000 - offset % 5000) >= 1000, "too close to slow slot " + slow);
        }
    }

    private static final class TestSetup {
        final Thing thing = mock(Thing.class);
        final ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        final ThingUID thingUid = new ThingUID("modbus", "sigenergy-evac", "evac");
        final SigenergyEvacHandler handler;

        TestSetup(Map<String, Object> configValues) {
            when(thing.getUID()).thenReturn(thingUid);
            when(thing.getStatus()).thenReturn(ThingStatus.UNKNOWN);
            when(thing.getConfiguration()).thenReturn(new Configuration(new HashMap<>(configValues)));
            handler = new SigenergyEvacHandler(thing);
            handler.setCallback(callback);
        }

        ModbusReadRequestBlueprint blueprint() {
            return SigenergyEvacHandler.buildBlueprint(EVAC_UNIT_ID, 3);
        }

        ChannelUID channel(String group, String id) {
            return new ChannelUID(thingUid, group, id);
        }
    }

    private static final class TestPlant {
        final ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
    }
}
