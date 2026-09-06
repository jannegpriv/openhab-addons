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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.exception.ModbusSlaveErrorResponseException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;

/**
 * Tests for poll layout, staggering, derived load power, last-update semantics, configuration
 * validation and the read-only guarantee.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
class SigenergyPlantHandlerTest {

    private static final int SLAVE_ID = 247;

    /** Builds a 70-register core block array with the given values at the right offsets. */
    private static ModbusRegisterArray coreArray(int ems, int gridSensor, int gridHi, int gridLo, int onOff, int soc,
            int plantHi, int plantLo, int pvHi, int pvLo, int battHi, int battLo, int running) {
        int[] words = new int[70];
        words[0] = ems;
        words[1] = gridSensor;
        words[2] = gridHi;
        words[3] = gridLo;
        words[6] = onOff;
        words[11] = soc;
        words[28] = plantHi;
        words[29] = plantLo;
        words[32] = pvHi;
        words[33] = pvLo;
        words[34] = battHi;
        words[35] = battLo;
        words[48] = running;
        // phases at 49-54, available capacities at 61-64 left as zero
        return new ModbusRegisterArray(words);
    }

    @Test
    public void testAllBlocksUseFc04AndUnitId() {
        List<SigenergyPlantHandler.ReadBlock> all = allBlocks();
        for (SigenergyPlantHandler.ReadBlock block : all) {
            assertEquals(ModbusReadFunctionCode.READ_INPUT_REGISTERS, block.blueprint.getFunctionCode());
            assertEquals(SLAVE_ID, block.blueprint.getUnitID());
            assertTrue(block.blueprint.getDataLength() <= 125);
        }
        assertEquals(5, all.size());
    }

    private List<SigenergyPlantHandler.ReadBlock> allBlocks() {
        List<SigenergyPlantHandler.ReadBlock> slow = SigenergyPlantHandler.buildSlowBlocks(SLAVE_ID, 3);
        return List.of(SigenergyPlantHandler.buildCoreBlock(SLAVE_ID, 3),
                SigenergyPlantHandler.buildLoadBlock(SLAVE_ID, 3), slow.get(0), slow.get(1), slow.get(2));
    }

    @Test
    public void testPollBlockLayout() {
        // core: 30003..30072, sent as PDU address 30003 (not 30002)
        SigenergyPlantHandler.ReadBlock core = SigenergyPlantHandler.buildCoreBlock(SLAVE_ID, 3);
        assertEquals(30003, core.blueprint.getReference());
        assertEquals(70, core.blueprint.getDataLength());

        // SOC sits at offset 11, i.e. the request starts exactly at 30014 - 11 = 30003
        assertEquals(11, SigenergyPlantRegisters.BATTERY_SOC.getAddress() - core.startAddress);

        // optional fast block: 30280..30286 (V2.8+), incl. plant alarm masks 6-7
        SigenergyPlantHandler.ReadBlock load = SigenergyPlantHandler.buildLoadBlock(SLAVE_ID, 3);
        assertEquals(30280, load.blueprint.getReference());
        assertEquals(7, load.blueprint.getDataLength());

        // slow blocks: 30083 len 15 (U64 at 30094 ends at 30097), 30200 len 24, 30272 len 4
        List<SigenergyPlantHandler.ReadBlock> slow = SigenergyPlantHandler.buildSlowBlocks(SLAVE_ID, 3);
        assertEquals(30083, slow.get(0).blueprint.getReference());
        assertEquals(15, slow.get(0).blueprint.getDataLength());
        assertEquals(30200, slow.get(1).blueprint.getReference());
        assertEquals(24, slow.get(1).blueprint.getDataLength());
        assertEquals(30272, slow.get(2).blueprint.getReference());
        assertEquals(4, slow.get(2).blueprint.getDataLength());
    }

    @Test
    public void testSlowPollOffsetsRespectMinimumRequestSpacing() {
        // fast core polls at n*5000 ms, fast load block at n*5000+2500 ms (default poll interval)
        for (long offset : SigenergyPlantHandler.SLOW_POLL_OFFSETS_MS) {
            long gapAfterCoreSlot = offset % 5000;
            long gapToLoadSlot = Math.abs(2500 - gapAfterCoreSlot);
            assertTrue(gapAfterCoreSlot >= 1000, "slow offset " + offset + " too close to a core poll slot");
            assertTrue(gapToLoadSlot >= 1000, "slow offset " + offset + " too close to a load poll slot");
        }
        // and the slow blocks are spaced out from each other
        assertTrue(
                SigenergyPlantHandler.SLOW_POLL_OFFSETS_MS[1] - SigenergyPlantHandler.SLOW_POLL_OFFSETS_MS[0] >= 1000);
        assertTrue(
                SigenergyPlantHandler.SLOW_POLL_OFFSETS_MS[2] - SigenergyPlantHandler.SLOW_POLL_OFFSETS_MS[1] >= 1000);
    }

    @Test
    public void testCalculateLoadPowerFromBatteryDischarge() {
        // PV 0 W, grid 0 W, battery discharging 700 W -> load 700 W
        assertEquals(new BigDecimal(700),
                SigenergyPlantHandler.calculateLoadPower(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(-700)));
    }

    @Test
    public void testCalculateLoadPowerWhileCharging() {
        // PV 2500 W, grid 100 W, battery charging 400 W -> load 2200 W
        assertEquals(new BigDecimal(2200), SigenergyPlantHandler.calculateLoadPower(new BigDecimal(2500),
                new BigDecimal(100), new BigDecimal(400)));
    }

    @Test
    public void testInvalidPollIntervalIsConfigurationError() {
        TestSetup setup = new TestSetup(Map.of("pollInterval", 999, "maxTries", 3));
        setup.handler.modbusInitialize();
        setup.verifyStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
    }

    @Test
    public void testInvalidMaxTriesIsConfigurationError() {
        TestSetup setup = new TestSetup(Map.of("pollInterval", 5000, "maxTries", 0));
        setup.handler.modbusInitialize();
        setup.verifyStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
    }

    @Test
    public void testCoreDecodePublishesStatesAndDerivedLoad() {
        TestSetup setup = new TestSetup(Map.of());
        SigenergyPlantHandler.ReadBlock core = SigenergyPlantHandler.buildCoreBlock(SLAVE_ID, 3);

        // grid 100 W import, soc 32.2 %, plant 3000 W, pv 2500 W, battery charging 400 W, running
        ModbusRegisterArray registers = coreArray(1, 1, 0, 100, 0, 322, 0, 3000, 0, 2500, 0, 400, 1);
        setup.handler.handleCoreReadResult(core, new AsyncModbusReadResult(core.blueprint, registers));

        verify(setup.callback).stateUpdated(eq(setup.channel("battery", "battery-soc")),
                eq(new QuantityType<>("32.2 %")));
        verify(setup.callback).stateUpdated(eq(setup.channel("overview", "pv-power")),
                eq(new QuantityType<>("2500 W")));
        verify(setup.callback).stateUpdated(eq(setup.channel("overview", "ems-mode")), eq(new StringType("AI_MODE")));
        verify(setup.callback).stateUpdated(eq(setup.channel("overview", "plant-running-state")),
                eq(new StringType("RUNNING")));
        // derived load (no load registers seen yet): 2500 + 100 - 400 = 2200 W
        verify(setup.callback).stateUpdated(eq(setup.channel("overview", "load-power")),
                eq(new QuantityType<>("2200 W")));
    }

    @Test
    public void testLastUpdateOnlyOnSuccessfulCorePoll() {
        TestSetup setup = new TestSetup(Map.of());
        SigenergyPlantHandler.ReadBlock core = SigenergyPlantHandler.buildCoreBlock(SLAVE_ID, 3);
        SigenergyPlantHandler.ReadBlock load = SigenergyPlantHandler.buildLoadBlock(SLAVE_ID, 3);
        ChannelUID lastUpdate = setup.channel("overview", "last-successful-update");

        // optional block success must NOT touch the last-update channel
        setup.handler.handleOptionalReadResult(load,
                new AsyncModbusReadResult(load.blueprint, new ModbusRegisterArray(0, 0, 0, 100, 0, 200, 285)));
        verify(setup.callback, never()).stateUpdated(eq(lastUpdate), any());

        // optional block failure must not either
        setup.handler.handleOptionalReadError(load,
                new AsyncModbusFailure<>(load.blueprint, mock(ModbusSlaveErrorResponseException.class)));
        verify(setup.callback, never()).stateUpdated(eq(lastUpdate), any());

        // a successful core poll updates it
        ModbusRegisterArray registers = coreArray(0, 1, 0, 0, 0, 322, 0, 0, 0, 0, 0, 0, 1);
        setup.handler.handleCoreReadResult(core, new AsyncModbusReadResult(core.blueprint, registers));
        verify(setup.callback, times(1)).stateUpdated(eq(lastUpdate), any(DateTimeType.class));
    }

    @Test
    public void testSlowBlockDecodesEnergyAndU64() {
        TestSetup setup = new TestSetup(Map.of());
        SigenergyPlantHandler.ReadBlock blockC = SigenergyPlantHandler.buildSlowBlocks(SLAVE_ID, 3).get(0);

        // rated 18.00 kWh, cutoffs 100.0 %/10.0 %, soh 100.0 %, total pv 4294967297 raw,
        // load today 12.34 kWh, total load 123456 raw
        ModbusRegisterArray registers = new ModbusRegisterArray(0, 1800, 1000, 100, 1000, //
                0, 1, 0, 1, // U64 total pv generation = 4294967297
                0, 1234, // load today
                0, 0, 1, 0xE240); // U64 total load = 123456
        setup.handler.handleOptionalReadResult(blockC, new AsyncModbusReadResult(blockC.blueprint, registers));

        verify(setup.callback).stateUpdated(eq(setup.channel("battery", "rated-capacity")),
                eq(new QuantityType<>("18.00 kWh")));
        verify(setup.callback).stateUpdated(eq(setup.channel("battery", "discharge-cutoff-soc")),
                eq(new QuantityType<>("10.0 %")));
        verify(setup.callback).stateUpdated(eq(setup.channel("energy", "total-pv-generation")),
                eq(new QuantityType<>("42949672.97 kWh")));
        verify(setup.callback).stateUpdated(eq(setup.channel("energy", "load-consumption-today")),
                eq(new QuantityType<>("12.34 kWh")));
        verify(setup.callback).stateUpdated(eq(setup.channel("energy", "total-load-consumption")),
                eq(new QuantityType<>("1234.56 kWh")));
    }

    @Test
    public void testLoadRegistersFeedLoadPowerChannelWhenAvailable() {
        TestSetup setup = new TestSetup(Map.of());
        SigenergyPlantHandler.ReadBlock core = SigenergyPlantHandler.buildCoreBlock(SLAVE_ID, 3);
        SigenergyPlantHandler.ReadBlock load = SigenergyPlantHandler.buildLoadBlock(SLAVE_ID, 3);
        ChannelUID loadUid = setup.channel("overview", "load-power");

        // general load 3100 W (30282), total load 3200 W (30284), cell temp 28.5 °C (30286)
        setup.handler.handleOptionalReadResult(load,
                new AsyncModbusReadResult(load.blueprint, new ModbusRegisterArray(0, 0, 0, 3100, 0, 3200, 285)));
        verify(setup.callback).stateUpdated(eq(loadUid), eq(new QuantityType<>("3200 W")));
        verify(setup.callback).stateUpdated(eq(setup.channel("overview", "general-load-power")),
                eq(new QuantityType<>("3100 W")));
        verify(setup.callback).stateUpdated(eq(setup.channel("battery", "cell-temperature")),
                eq(new QuantityType<>("28.5 °C")));

        // subsequent core reads must not overwrite the register value with the derived one (2200 W)
        ModbusRegisterArray registers = coreArray(0, 1, 0, 100, 0, 322, 0, 3000, 0, 2500, 0, 400, 1);
        setup.handler.handleCoreReadResult(core, new AsyncModbusReadResult(core.blueprint, registers));
        verify(setup.callback, never()).stateUpdated(eq(loadUid), eq(new QuantityType<>("2200 W")));
    }

    @Test
    public void testOptionalBlockFailureDoesNotTakeThingOffline() {
        TestSetup setup = new TestSetup(Map.of());
        SigenergyPlantHandler.ReadBlock core = SigenergyPlantHandler.buildCoreBlock(SLAVE_ID, 3);
        SigenergyPlantHandler.ReadBlock load = SigenergyPlantHandler.buildLoadBlock(SLAVE_ID, 3);

        // firmware < V2.8 answers with a slave exception: no OFFLINE, fall back to derived value
        setup.handler.handleOptionalReadError(load,
                new AsyncModbusFailure<>(load.blueprint, mock(ModbusSlaveErrorResponseException.class)));
        for (SigenergyPlantHandler.ReadBlock slow : SigenergyPlantHandler.buildSlowBlocks(SLAVE_ID, 3)) {
            setup.handler.handleOptionalReadError(slow,
                    new AsyncModbusFailure<>(slow.blueprint, mock(ModbusSlaveErrorResponseException.class)));
        }
        verify(setup.callback, never()).statusUpdated(eq(setup.thing),
                argThat(info -> info.getStatus() == ThingStatus.OFFLINE));

        // core still works and the derived fallback takes over
        ModbusRegisterArray registers = coreArray(0, 1, 0, 100, 0, 322, 0, 3000, 0, 2500, 0, 400, 1);
        setup.handler.handleCoreReadResult(core, new AsyncModbusReadResult(core.blueprint, registers));
        verify(setup.callback).stateUpdated(eq(setup.channel("overview", "load-power")),
                eq(new QuantityType<>("2200 W")));
    }

    /**
     * The binding must stay read-only by construction: no source file may reference any Modbus write API.
     */
    @Test
    public void testNoWritePathExists() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            files.filter(f -> f.toString().endsWith(".java")).forEach(file -> {
                try {
                    String source = Files.readString(file);
                    assertFalse(source.contains("ModbusWrite"), "Write blueprint referenced in " + file);
                    assertFalse(source.contains("submitOneTimeWrite"), "Write submission referenced in " + file);
                    assertFalse(source.contains("WriteTask"), "Write task referenced in " + file);
                } catch (IOException e) {
                    fail(e);
                }
            });
        }
    }

    private static final class TestSetup {
        final Thing thing = mock(Thing.class);
        final ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        final ThingUID thingUid = new ThingUID("modbus", "sigenergy-plant", "plant");
        final SigenergyPlantHandler handler;

        TestSetup(Map<String, Object> configValues) {
            when(thing.getUID()).thenReturn(thingUid);
            when(thing.getStatus()).thenReturn(ThingStatus.UNKNOWN);
            when(thing.getConfiguration()).thenReturn(new Configuration(new HashMap<>(configValues)));
            handler = new SigenergyPlantHandler(thing);
            handler.setCallback(callback);
        }

        ChannelUID channel(String group, String id) {
            return new ChannelUID(thingUid, group, id);
        }

        void verifyStatus(ThingStatus status, ThingStatusDetail detail) {
            verify(callback).statusUpdated(eq(thing),
                    argThat(info -> info.getStatus() == status && info.getStatusDetail() == detail));
        }
    }
}
