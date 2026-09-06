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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.modbus.handler.BaseModbusThingHandler;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.PollTask;
import org.openhab.core.io.transport.modbus.exception.ModbusSlaveErrorResponseException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
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
 * The {@link SigenergyInverterHandler} polls the running information of a Sigenergy hybrid inverter
 * through the plant's Modbus TCP endpoint, using the inverter's own unit ID. Monitoring only: FC04
 * reads exclusively.
 *
 * Poll layout: one one-shot identification read (model/serial/firmware, published as thing
 * properties), two regular fast blocks (running data + ESS, and grid/phase/PV-string data) and one
 * slow ratings/energy block. The default poll interval is slower than the plant's so the shared
 * endpoint stays within its one-request-per-second budget.
 *
 * Per-string PV power is derived in this handler as string voltage times string current.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SigenergyInverterHandler extends BaseModbusThingHandler {

    static final class ReadBlock {

        final List<SigenergyInverterRegisters> registers;
        final int startAddress;
        final ModbusReadRequestBlueprint blueprint;

        ReadBlock(List<SigenergyInverterRegisters> registers, int startAddress, int length, int unitId, int maxTries) {
            this.registers = registers;
            this.startAddress = startAddress;
            this.blueprint = new ModbusReadRequestBlueprint(unitId, ModbusReadFunctionCode.READ_INPUT_REGISTERS,
                    startAddress, length, maxTries);
        }
    }

    static final long SLOW_POLL_INTERVAL_MS = 60_000;
    // offsets relative the poll interval; the bridge's timeBetweenTransactionsMillis serializes requests
    static final long BLOCK_A_OFFSET_MS = 1800;
    static final long BLOCK_B_OFFSET_MS = 6800;
    static final long BLOCK_C_OFFSET_MS = 51_300;

    private static final int IDENTIFICATION_START = 30500;
    private static final int IDENTIFICATION_LENGTH = 40;

    private static final int PV_STRINGS = 4;
    private static final int PV1_VOLTAGE_ADDRESS = 31027;

    private final Logger logger = LoggerFactory.getLogger(SigenergyInverterHandler.class);

    private final Map<ReadBlock, PollTask> pollTasks = new HashMap<>();

    public SigenergyInverterHandler(Thing thing) {
        super(thing);
    }

    private static List<SigenergyInverterRegisters> inRange(int from, int to) {
        List<SigenergyInverterRegisters> result = new ArrayList<>();
        for (SigenergyInverterRegisters register : SigenergyInverterRegisters.values()) {
            if (register.getAddress() >= from && register.getAddress() <= to) {
                result.add(register);
            }
        }
        return result;
    }

    static ReadBlock buildBlockA(int unitId, int maxTries) {
        return new ReadBlock(inRange(30578, 30609), 30578, 32, unitId, maxTries);
    }

    static ReadBlock buildBlockB(int unitId, int maxTries) {
        return new ReadBlock(inRange(31000, 31037), 31000, 38, unitId, maxTries);
    }

    static ReadBlock buildBlockC(int unitId, int maxTries) {
        return new ReadBlock(inRange(30540, 30577), 30540, 38, unitId, maxTries);
    }

    static ModbusReadRequestBlueprint buildIdentificationRequest(int unitId, int maxTries) {
        return new ModbusReadRequestBlueprint(unitId, ModbusReadFunctionCode.READ_INPUT_REGISTERS, IDENTIFICATION_START,
                IDENTIFICATION_LENGTH, maxTries);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            for (ReadBlock block : pollTasks.keySet()) {
                submitOneTimePoll(block.blueprint, result -> handleReadResult(block, result),
                        error -> handleReadError(block, error));
            }
        }
        // monitoring-only binding: all other commands are ignored
    }

    @Override
    public void modbusInitialize() {
        SigenergyInverterConfiguration config = getConfigAs(SigenergyInverterConfiguration.class);

        if (config.unitId < 1 || config.unitId > 246) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid inverter unit ID: " + config.unitId + " (must be 1-246, configured in mySigen)");
            return;
        }

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
        pollTasks.clear();

        submitOneTimePoll(buildIdentificationRequest(config.unitId, config.maxTries), this::handleIdentification,
                error -> logger.debug("Failed to read inverter identification", error.getCause()));

        ReadBlock blockA = buildBlockA(config.unitId, config.maxTries);
        ReadBlock blockB = buildBlockB(config.unitId, config.maxTries);
        ReadBlock blockC = buildBlockC(config.unitId, config.maxTries);
        registerBlock(blockA, config.pollInterval, BLOCK_A_OFFSET_MS, true);
        registerBlock(blockB, config.pollInterval, BLOCK_B_OFFSET_MS, true);
        registerBlock(blockC, SLOW_POLL_INTERVAL_MS, BLOCK_C_OFFSET_MS, false);
    }

    private void registerBlock(ReadBlock block, long interval, long offset, boolean core) {
        PollTask task = registerRegularPoll(block.blueprint, interval, offset,
                result -> handleReadResult(block, result),
                core ? error -> handleReadError(block, error) : error -> handleOptionalReadError(block, error));
        pollTasks.put(block, task);
    }

    void handleIdentification(AsyncModbusReadResult result) {
        result.getRegisters().ifPresent(registers -> {
            String model = ModbusBitUtilities.extractStringFromRegisters(registers, 0, 30, StandardCharsets.US_ASCII)
                    .trim();
            String serial = ModbusBitUtilities.extractStringFromRegisters(registers, 15, 20, StandardCharsets.US_ASCII)
                    .trim();
            String firmware = ModbusBitUtilities
                    .extractStringFromRegisters(registers, 25, 30, StandardCharsets.US_ASCII).trim();
            Map<String, String> properties = editProperties();
            if (!model.isEmpty()) {
                properties.put(Thing.PROPERTY_MODEL_ID, model);
            }
            if (!serial.isEmpty()) {
                properties.put(Thing.PROPERTY_SERIAL_NUMBER, serial);
            }
            if (!firmware.isEmpty()) {
                properties.put(Thing.PROPERTY_FIRMWARE_VERSION, firmware);
            }
            updateProperties(properties);
        });
    }

    void handleReadResult(ReadBlock block, AsyncModbusReadResult result) {
        result.getRegisters().ifPresent(registers -> {
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }

            for (SigenergyInverterRegisters register : block.registers) {
                int offset = register.getAddress() - block.startAddress;
                ModbusBitUtilities.extractStateFromRegisters(registers, offset, register.getType())
                        .ifPresent(value -> updateState(
                                new ChannelUID(thing.getUID(), register.getChannelGroup(), register.getChannelName()),
                                register.createState(value)));
            }

            if (block.startAddress == 30578) {
                updateAlarmChannel(registers);
                updateState(
                        new ChannelUID(thing.getUID(), "status", ModbusSigenergyBindingConstants.CHANNEL_LAST_UPDATE),
                        new DateTimeType());
            }
            if (block.startAddress == 31000) {
                updateStringPowers(registers);
            }
        });
    }

    private void updateAlarmChannel(ModbusRegisterArray registers) {
        // registers 30605-30609 relative block start 30578; any non-zero mask is an active alarm
        boolean active = false;
        for (int offset = 27; offset <= 31; offset++) {
            Optional<DecimalType> mask = ModbusBitUtilities.extractStateFromRegisters(registers, offset,
                    ValueType.UINT16);
            // all bits set marks a mask as not applicable on this firmware
            if (mask.isPresent() && mask.get().intValue() != 0 && mask.get().intValue() != 0xFFFF) {
                active = true;
                break;
            }
        }
        updateState(new ChannelUID(thing.getUID(), "status", "alarm-active"), OnOffType.from(active));
    }

    private void updateStringPowers(ModbusRegisterArray registers) {
        for (int string = 0; string < PV_STRINGS; string++) {
            int voltageOffset = PV1_VOLTAGE_ADDRESS - 31000 + string * 2;
            Optional<DecimalType> voltage = ModbusBitUtilities.extractStateFromRegisters(registers, voltageOffset,
                    ValueType.INT16);
            Optional<DecimalType> current = ModbusBitUtilities.extractStateFromRegisters(registers, voltageOffset + 1,
                    ValueType.INT16);
            if (voltage.isEmpty() || current.isEmpty()) {
                continue;
            }
            // P = (raw/10 V) * (raw/100 A) => raw product / 1000 W
            BigDecimal watts = voltage.get().toBigDecimal().multiply(current.get().toBigDecimal()).movePointLeft(3);
            updateState(new ChannelUID(thing.getUID(), "strings", "pv" + (string + 1) + "-power"),
                    new QuantityType<>(watts, Units.WATT));
        }
    }

    void handleReadError(ReadBlock block, AsyncModbusFailure<ModbusReadRequestBlueprint> error) {
        logger.debug("Failed to read inverter data at {}", block.startAddress, error.getCause());
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                "Failed to retrieve inverter data: " + error.getCause().getMessage());
    }

    /**
     * The slow ratings block may be missing on some firmware; disable just that block on a slave error.
     */
    void handleOptionalReadError(ReadBlock block, AsyncModbusFailure<ModbusReadRequestBlueprint> error) {
        if (error.getCause() instanceof ModbusSlaveErrorResponseException) {
            PollTask task = pollTasks.remove(block);
            if (task != null) {
                unregisterRegularPoll(task);
            }
            logger.debug("Inverter registers at {} not supported by this firmware, block disabled", block.startAddress);
        } else {
            logger.debug("Failed to read optional inverter block at {}", block.startAddress, error.getCause());
        }
    }
}
