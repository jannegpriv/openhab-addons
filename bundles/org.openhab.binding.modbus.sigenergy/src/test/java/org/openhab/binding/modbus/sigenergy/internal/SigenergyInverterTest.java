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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.exception.ModbusSlaveErrorResponseException;
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
 * Tests for the hybrid inverter monitoring: block layout, decoding, derived string power and
 * failure behavior.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
class SigenergyInverterTest {

    private static final int UNIT_ID = 1;

    private State decode(SigenergyInverterRegisters register, int... words) {
        ModbusRegisterArray registers = new ModbusRegisterArray(words);
        Optional<DecimalType> value = ModbusBitUtilities.extractStateFromRegisters(registers, 0, register.getType());
        assertTrue(value.isPresent());
        return register.createState(value.get());
    }

    @Test
    public void testBlockLayout() {
        SigenergyInverterHandler.ReadBlock a = SigenergyInverterHandler.buildBlockA(UNIT_ID, 3);
        assertEquals(30578, a.blueprint.getReference());
        assertEquals(32, a.blueprint.getDataLength());

        SigenergyInverterHandler.ReadBlock b = SigenergyInverterHandler.buildBlockB(UNIT_ID, 3);
        assertEquals(31000, b.blueprint.getReference());
        assertEquals(38, b.blueprint.getDataLength());

        SigenergyInverterHandler.ReadBlock c = SigenergyInverterHandler.buildBlockC(UNIT_ID, 3);
        assertEquals(30540, c.blueprint.getReference());
        assertEquals(38, c.blueprint.getDataLength());

        ModbusReadRequestBlueprint ident = SigenergyInverterHandler.buildIdentificationRequest(UNIT_ID, 3);
        assertEquals(30500, ident.getReference());
        assertEquals(40, ident.getDataLength());

        for (ModbusReadRequestBlueprint bp : new ModbusReadRequestBlueprint[] { a.blueprint, b.blueprint, c.blueprint,
                ident }) {
            assertEquals(ModbusReadFunctionCode.READ_INPUT_REGISTERS, bp.getFunctionCode());
            assertEquals(UNIT_ID, bp.getUnitID());
            assertTrue(bp.getDataLength() <= 125);
        }
    }

    @Test
    public void testStringRegisterDecoding() {
        // PV1 voltage: S16 gain 10 -> 1234 raw = 123.4 V; current: S16 gain 100 -> 512 raw = 5.12 A
        assertEquals(new QuantityType<>("123.4 V"), decode(SigenergyInverterRegisters.PV1_VOLTAGE, 1234));
        assertEquals(new QuantityType<>("5.12 A"), decode(SigenergyInverterRegisters.PV1_CURRENT, 512));
        assertEquals(31027, SigenergyInverterRegisters.PV1_VOLTAGE.getAddress());
        assertEquals(31034, SigenergyInverterRegisters.PV4_CURRENT.getAddress());
        assertEquals(31025, SigenergyInverterRegisters.STRING_COUNT.getAddress());
    }

    @Test
    public void testCoreDecoding() {
        assertEquals(new StringType("RUNNING"), decode(SigenergyInverterRegisters.RUNNING_STATE, 1));
        assertEquals(new QuantityType<>("2500 W"), decode(SigenergyInverterRegisters.ACTIVE_POWER, 0, 2500));
        assertEquals(new QuantityType<>("-1200 W"),
                decode(SigenergyInverterRegisters.ESS_POWER, 0xFFFF, 0xFFFF - 1199));
        assertEquals(new QuantityType<>("50.02 Hz"), decode(SigenergyInverterRegisters.GRID_FREQUENCY, 5002));
        assertEquals(new QuantityType<>("3.215 V"), decode(SigenergyInverterRegisters.CELL_VOLTAGE, 3215));
        assertEquals(new QuantityType<>("-2.5 °C"), decode(SigenergyInverterRegisters.CELL_TEMPERATURE, 0xFFE7));
        // U64 accumulated energy above 32-bit range
        assertEquals(new QuantityType<>("42949672.97 kWh"),
                decode(SigenergyInverterRegisters.ACCUMULATED_CHARGE_ENERGY, 0, 1, 0, 1));
        assertEquals(new QuantityType<>("18.08 kWh"), decode(SigenergyInverterRegisters.ESS_RATED_CAPACITY, 0, 1808));
    }

    @Test
    public void testDerivedStringPowerAndAlarms() {
        TestSetup setup = new TestSetup(Map.of("unitId", UNIT_ID));
        SigenergyInverterHandler.ReadBlock blockB = SigenergyInverterHandler.buildBlockB(UNIT_ID, 3);

        // 38 registers starting at 31000; PV1 130.0V/5.00A, PV2 120.0V/2.50A, PV3 0/0, PV4 0/0
        int[] words = new int[38];
        words[31025 - 31000] = 3; // string count
        words[31027 - 31000] = 1300;
        words[31028 - 31000] = 500;
        words[31029 - 31000] = 1200;
        words[31030 - 31000] = 250;
        setup.handler.handleReadResult(blockB,
                new AsyncModbusReadResult(blockB.blueprint, new ModbusRegisterArray(words)));

        // P = V * I: 130.0 * 5.00 = 650 W, 120.0 * 2.50 = 300 W, others 0
        verify(setup.callback).stateUpdated(eq(setup.channel("strings", "pv1-power")), eq(new QuantityType<>("650 W")));
        verify(setup.callback).stateUpdated(eq(setup.channel("strings", "pv2-power")), eq(new QuantityType<>("300 W")));
        verify(setup.callback).stateUpdated(eq(setup.channel("strings", "pv3-power")), eq(new QuantityType<>("0 W")));
        verify(setup.callback).stateUpdated(eq(setup.channel("strings", "string-count")), eq(new DecimalType(3)));

        // block A with alarm mask 2 set -> alarm-active ON and last-update set
        SigenergyInverterHandler.ReadBlock blockA = SigenergyInverterHandler.buildBlockA(UNIT_ID, 3);
        int[] a = new int[32];
        a[0] = 1; // running
        a[30606 - 30578] = 1 << 4;
        setup.handler.handleReadResult(blockA, new AsyncModbusReadResult(blockA.blueprint, new ModbusRegisterArray(a)));
        verify(setup.callback).stateUpdated(eq(setup.channel("status", "alarm-active")), eq(OnOffType.ON));
        verify(setup.callback, times(1)).stateUpdated(eq(setup.channel("status", "last-successful-update")), any());
    }

    @Test
    public void testInvalidConfig() {
        for (Map<String, Object> cfg : java.util.List.<Map<String, Object>> of(Map.of("unitId", 0),
                Map.of("unitId", 247), Map.of("unitId", 1, "pollInterval", 500), Map.of("unitId", 1, "maxTries", 0))) {
            TestSetup setup = new TestSetup(cfg);
            setup.handler.modbusInitialize();
            verify(setup.callback).statusUpdated(eq(setup.thing),
                    argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                            && info.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR));
        }
    }

    @Test
    public void testCoreErrorSetsOfflineAndRecovers() {
        TestSetup setup = new TestSetup(Map.of("unitId", UNIT_ID));
        SigenergyInverterHandler.ReadBlock blockA = SigenergyInverterHandler.buildBlockA(UNIT_ID, 3);

        setup.handler.handleReadError(blockA,
                new AsyncModbusFailure<>(blockA.blueprint, mock(ModbusSlaveErrorResponseException.class)));
        verify(setup.callback).statusUpdated(eq(setup.thing), argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                && info.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR));

        setup.handler.handleReadResult(blockA,
                new AsyncModbusReadResult(blockA.blueprint, new ModbusRegisterArray(new int[32])));
        verify(setup.callback).statusUpdated(eq(setup.thing), argThat(info -> info.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    public void testIdentificationProperties() {
        TestSetup setup = new TestSetup(Map.of("unitId", UNIT_ID));
        // 40 registers: model "SigenStor EC 10.0 TP" @30500, serial "SN123" @30515, fw "V1" @30525
        int[] words = new int[40];
        def(words, 0, "SigenStor EC 10.0 TP");
        def(words, 15, "SN123");
        def(words, 25, "V1");
        setup.handler.handleIdentification(new AsyncModbusReadResult(
                SigenergyInverterHandler.buildIdentificationRequest(UNIT_ID, 3), new ModbusRegisterArray(words)));
        verify(setup.thing).setProperty(Thing.PROPERTY_MODEL_ID, "SigenStor EC 10.0 TP");
        verify(setup.thing).setProperty(Thing.PROPERTY_SERIAL_NUMBER, "SN123");
        verify(setup.thing).setProperty(Thing.PROPERTY_FIRMWARE_VERSION, "V1");
    }

    private static void def(int[] words, int offset, String text) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i < bytes.length; i++) {
            int word = offset + i / 2;
            words[word] |= (bytes[i] & 0xFF) << (i % 2 == 0 ? 8 : 0);
        }
    }

    private static final class TestSetup {
        final Thing thing = mock(Thing.class);
        final ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        final ThingUID thingUid = new ThingUID("modbus", "sigenergy-inverter", "inverter");
        final SigenergyInverterHandler handler;

        TestSetup(Map<String, Object> configValues) {
            when(thing.getUID()).thenReturn(thingUid);
            when(thing.getStatus()).thenReturn(ThingStatus.UNKNOWN);
            when(thing.getConfiguration()).thenReturn(new Configuration(new HashMap<>(configValues)));
            when(thing.getProperties()).thenReturn(new HashMap<>());
            handler = new SigenergyInverterHandler(thing);
            handler.setCallback(callback);
        }

        ChannelUID channel(String group, String id) {
            return new ChannelUID(thingUid, group, id);
        }
    }
}
