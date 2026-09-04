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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbus.handler.BaseModbusThingHandler;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.PollTask;
import org.openhab.core.io.transport.modbus.exception.ModbusSlaveErrorResponseException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SigenergyPlantHandler} polls the plant-level Sigenergy Modbus input registers and updates
 * the channels. The binding is monitoring-only: it only ever issues FC04 read requests.
 *
 * Poll layout: one mandatory fast block (core running data), one optional fast block (V2.8+ load power
 * and cell temperature) and three optional slow blocks (energy counters, 60 s). Blocks are staggered so
 * that no two requests are initiated less than roughly a second apart at the default poll interval.
 * Optional blocks fail independently: a slave error response disables just that block and its channels.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SigenergyPlantHandler extends BaseModbusThingHandler {

    /**
     * One gap-free FC04 request template with the registers it covers.
     */
    @NonNullByDefault
    static final class ReadBlock {

        final List<SigenergyPlantRegisters> registers;
        final int startAddress;
        final ModbusReadRequestBlueprint blueprint;

        ReadBlock(List<SigenergyPlantRegisters> registers, int slaveId, int maxTries) {
            this.registers = registers;
            // Sigenergy documents PDU addresses: 30014 is sent as 30014, no one-based correction
            this.startAddress = registers.get(0).getAddress();
            SigenergyPlantRegisters last = registers.get(registers.size() - 1);
            int length = last.getAddress() + last.getRegisterCount() - startAddress;
            this.blueprint = new ModbusReadRequestBlueprint(slaveId, ModbusReadFunctionCode.READ_INPUT_REGISTERS,
                    startAddress, length, maxTries);
        }
    }

    static final long SLOW_POLL_INTERVAL_MS = 60_000;
    // spread the slow blocks across the minute, each landing well clear of the fast poll slots
    static final long[] SLOW_POLL_OFFSETS_MS = { 1300, 21300, 41300 };

    private static final List<SigenergyPlantRegisters> CORE_REGISTERS = List.of( //
            SigenergyPlantRegisters.EMS_MODE, SigenergyPlantRegisters.GRID_SENSOR_STATUS,
            SigenergyPlantRegisters.GRID_POWER, SigenergyPlantRegisters.ON_OFF_GRID_STATUS,
            SigenergyPlantRegisters.BATTERY_SOC, SigenergyPlantRegisters.PLANT_POWER, SigenergyPlantRegisters.PV_POWER,
            SigenergyPlantRegisters.BATTERY_POWER, SigenergyPlantRegisters.PLANT_RUNNING_STATE,
            SigenergyPlantRegisters.GRID_PHASE_A_POWER, SigenergyPlantRegisters.GRID_PHASE_B_POWER,
            SigenergyPlantRegisters.GRID_PHASE_C_POWER, SigenergyPlantRegisters.AVAILABLE_CHARGE_CAPACITY,
            SigenergyPlantRegisters.AVAILABLE_DISCHARGE_CAPACITY);

    private static final List<SigenergyPlantRegisters> LOAD_REGISTERS = List.of( //
            SigenergyPlantRegisters.GENERAL_LOAD_POWER, SigenergyPlantRegisters.TOTAL_LOAD_POWER,
            SigenergyPlantRegisters.CELL_TEMPERATURE);

    private static final List<List<SigenergyPlantRegisters>> SLOW_REGISTERS = List.of( //
            List.of(SigenergyPlantRegisters.RATED_CAPACITY, SigenergyPlantRegisters.CHARGE_CUTOFF_SOC,
                    SigenergyPlantRegisters.DISCHARGE_CUTOFF_SOC, SigenergyPlantRegisters.BATTERY_SOH,
                    SigenergyPlantRegisters.TOTAL_PV_GENERATION, SigenergyPlantRegisters.LOAD_CONSUMPTION_TODAY,
                    SigenergyPlantRegisters.TOTAL_LOAD_CONSUMPTION),
            List.of(SigenergyPlantRegisters.TOTAL_BATTERY_CHARGED_ENERGY,
                    SigenergyPlantRegisters.TOTAL_BATTERY_DISCHARGED_ENERGY,
                    SigenergyPlantRegisters.TOTAL_IMPORTED_ENERGY, SigenergyPlantRegisters.TOTAL_EXPORTED_ENERGY),
            List.of(SigenergyPlantRegisters.PV_GENERATION_TODAY, SigenergyPlantRegisters.PV_GENERATION_YESTERDAY));

    private final Logger logger = LoggerFactory.getLogger(SigenergyPlantHandler.class);

    private @Nullable ReadBlock coreBlock;
    // optional blocks that are still being polled, with their poll task for individual unregistering
    private final Map<ReadBlock, PollTask> optionalPollTasks = new ConcurrentHashMap<>();
    // true once the direct load registers 30282/30284 (protocol V2.8+) have been read successfully
    private volatile boolean loadRegistersAvailable;

    private @Nullable BigDecimal pvPowerWatts;
    private @Nullable BigDecimal gridPowerWatts;
    private @Nullable BigDecimal batteryPowerWatts;

    public SigenergyPlantHandler(Thing thing) {
        super(thing);
    }

    static ReadBlock buildCoreBlock(int slaveId, int maxTries) {
        return new ReadBlock(CORE_REGISTERS, slaveId, maxTries);
    }

    static ReadBlock buildLoadBlock(int slaveId, int maxTries) {
        return new ReadBlock(LOAD_REGISTERS, slaveId, maxTries);
    }

    static List<ReadBlock> buildSlowBlocks(int slaveId, int maxTries) {
        List<ReadBlock> blocks = new ArrayList<>();
        for (List<SigenergyPlantRegisters> registers : SLOW_REGISTERS) {
            blocks.add(new ReadBlock(registers, slaveId, maxTries));
        }
        return blocks;
    }

    /**
     * Load power derived from PV, grid and battery power: consumption = generation + import - charging.
     * Fallback for firmware without register 30284.
     */
    static BigDecimal calculateLoadPower(BigDecimal pvPower, BigDecimal gridPower, BigDecimal batteryPower) {
        return pvPower.add(gridPower).subtract(batteryPower);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            ReadBlock core = coreBlock;
            if (core != null) {
                submitOneTimePoll(core.blueprint, result -> handleCoreReadResult(core, result),
                        this::handleCoreReadError);
            }
            for (ReadBlock block : optionalPollTasks.keySet()) {
                submitOneTimePoll(block.blueprint, result -> handleOptionalReadResult(block, result),
                        error -> handleOptionalReadError(block, error));
            }
        }
        // monitoring-only binding: all other commands are ignored
    }

    @Override
    public void modbusInitialize() {
        SigenergyPlantConfiguration config = getConfigAs(SigenergyPlantConfiguration.class);

        if (config.pollInterval < ModbusSigenergyBindingConstants.MIN_POLL_INTERVAL_MS) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid poll interval: " + config.pollInterval + " ms (minimum "
                            + ModbusSigenergyBindingConstants.MIN_POLL_INTERVAL_MS
                            + " ms supported by Sigenergy devices)");
            return;
        }

        if (config.maxTries < 1) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid maximum tries when reading: " + config.maxTries);
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);
        loadRegistersAvailable = false;
        optionalPollTasks.clear();

        ReadBlock core = buildCoreBlock(getSlaveId(), config.maxTries);
        coreBlock = core;
        registerRegularPoll(core.blueprint, config.pollInterval, 0, result -> handleCoreReadResult(core, result),
                this::handleCoreReadError);

        registerOptionalPoll(buildLoadBlock(getSlaveId(), config.maxTries), config.pollInterval,
                config.pollInterval / 2);

        List<ReadBlock> slowBlocks = buildSlowBlocks(getSlaveId(), config.maxTries);
        for (int i = 0; i < slowBlocks.size(); i++) {
            registerOptionalPoll(slowBlocks.get(i), SLOW_POLL_INTERVAL_MS, SLOW_POLL_OFFSETS_MS[i]);
        }
    }

    private void registerOptionalPoll(ReadBlock block, long interval, long initialDelay) {
        PollTask task = registerRegularPoll(block.blueprint, interval, initialDelay,
                result -> handleOptionalReadResult(block, result), error -> handleOptionalReadError(block, error));
        optionalPollTasks.put(block, task);
    }

    void handleCoreReadResult(ReadBlock block, AsyncModbusReadResult result) {
        result.getRegisters().ifPresent(registers -> {
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
            decodeBlock(block, registers);
            updateState(new ChannelUID(thing.getUID(), ModbusSigenergyBindingConstants.GROUP_OVERVIEW,
                    ModbusSigenergyBindingConstants.CHANNEL_LAST_UPDATE), new DateTimeType());
        });
    }

    private static boolean isLoadBlock(ReadBlock block) {
        return block.registers.contains(SigenergyPlantRegisters.TOTAL_LOAD_POWER);
    }

    void handleOptionalReadResult(ReadBlock block, AsyncModbusReadResult result) {
        result.getRegisters().ifPresent(registers -> {
            if (isLoadBlock(block)) {
                loadRegistersAvailable = true;
            }
            decodeBlock(block, registers);
        });
    }

    private void decodeBlock(ReadBlock block, ModbusRegisterArray registers) {
        for (SigenergyPlantRegisters register : block.registers) {
            int offset = register.getAddress() - block.startAddress;
            ModbusBitUtilities.extractStateFromRegisters(registers, offset, register.getType()).ifPresent(value -> {
                updateState(channelUid(register), register.createState(value));
                cachePowerValue(register, value.toBigDecimal());
            });
        }
        updateLoadPower();
    }

    private void cachePowerValue(SigenergyPlantRegisters register, BigDecimal rawWatts) {
        switch (register) {
            case PV_POWER -> pvPowerWatts = rawWatts;
            case GRID_POWER -> gridPowerWatts = rawWatts;
            case BATTERY_POWER -> batteryPowerWatts = rawWatts;
            default -> {
            }
        }
    }

    private void updateLoadPower() {
        if (loadRegistersAvailable) {
            // the load-power channel is fed directly from register 30284
            return;
        }
        BigDecimal pv = pvPowerWatts;
        BigDecimal grid = gridPowerWatts;
        BigDecimal battery = batteryPowerWatts;
        if (pv == null || grid == null || battery == null) {
            // derived channel only updates once all three sources have been read at least once
            return;
        }
        ChannelUID channelUid = new ChannelUID(thing.getUID(), ModbusSigenergyBindingConstants.GROUP_OVERVIEW,
                ModbusSigenergyBindingConstants.CHANNEL_LOAD_POWER);
        updateState(channelUid, new QuantityType<>(calculateLoadPower(pv, grid, battery), Units.WATT));
    }

    /**
     * Optional registers do not exist on all firmware revisions. If the device rejects the request,
     * stop polling just that block and leave its channels undefined instead of taking the thing offline.
     */
    void handleOptionalReadError(ReadBlock block, AsyncModbusFailure<ModbusReadRequestBlueprint> error) {
        if (error.getCause() instanceof ModbusSlaveErrorResponseException) {
            PollTask task = optionalPollTasks.remove(block);
            if (task != null) {
                unregisterRegularPoll(task);
            }
            if (isLoadBlock(block)) {
                loadRegistersAvailable = false;
            }
            logger.debug("Registers at {} not supported by this firmware, block disabled", block.startAddress);
        } else {
            logger.debug("Failed to read optional register block at {}", block.startAddress, error.getCause());
        }
    }

    private void handleCoreReadError(AsyncModbusFailure<ModbusReadRequestBlueprint> error) {
        logger.debug("Failed to read modbus data", error.getCause());
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                "Failed to retrieve data: " + error.getCause().getMessage());
    }

    private ChannelUID channelUid(SigenergyPlantRegisters register) {
        return new ChannelUID(thing.getUID(), register.getChannelGroup(), register.getChannelName());
    }
}
