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

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbus.handler.BaseModbusThingHandler;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusBitUtilities;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SigenergyEvacHandler} polls the running information of a Sigen EVAC (AC charger)
 * through the plant's Modbus TCP endpoint, using the charger's own unit ID. Monitoring only:
 * a single FC04 read of registers 32000-32014 per poll, nothing else.
 *
 * The poll is staggered relative to the plant handler's poll slots so that requests to the shared
 * endpoint stay spaced out; the generic bridge's timeBetweenTransactionsMillis setting provides
 * the hard per-endpoint spacing guarantee.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SigenergyEvacHandler extends BaseModbusThingHandler {

    static final int BLOCK_START = 32000;
    static final int BLOCK_LENGTH = 15;
    // offsets within the block
    private static final int OFFSET_ALARM_1 = 12;
    private static final int OFFSET_ALARM_2 = 13;
    private static final int OFFSET_ALARM_3 = 14;
    /**
     * Fast-poll offset: plant polls at n*pollInterval (core) and n*pollInterval+pollInterval/2 (load),
     * slow plant blocks at X1300. 3800 stays >= 1.2 s away from all of them at the default 5000 ms.
     */
    static final long EVAC_POLL_OFFSET_MS = 3800;

    private final Logger logger = LoggerFactory.getLogger(SigenergyEvacHandler.class);

    private @Nullable ModbusReadRequestBlueprint blueprint;
    private int lastUndocumented1;
    private int lastUndocumented2;
    private int lastUndocumented3;

    public SigenergyEvacHandler(Thing thing) {
        super(thing);
    }

    static ModbusReadRequestBlueprint buildBlueprint(int unitId, int maxTries) {
        // PDU address 32000 sent as written, no one-based correction
        return new ModbusReadRequestBlueprint(unitId, ModbusReadFunctionCode.READ_INPUT_REGISTERS, BLOCK_START,
                BLOCK_LENGTH, maxTries);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        ModbusReadRequestBlueprint request = blueprint;
        if (command instanceof RefreshType && request != null) {
            submitOneTimePoll(request, this::handleReadResult, this::handleReadError);
        }
        // monitoring-only binding: all other commands are ignored
    }

    @Override
    public void modbusInitialize() {
        SigenergyEvacConfiguration config = getConfigAs(SigenergyEvacConfiguration.class);

        if (config.unitId < 1 || config.unitId > 246) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid charger unit ID: " + config.unitId + " (must be 1-246, configured in mySigen)");
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
        ModbusReadRequestBlueprint request = buildBlueprint(config.unitId, config.maxTries);
        blueprint = request;
        registerRegularPoll(request, config.pollInterval, EVAC_POLL_OFFSET_MS, this::handleReadResult,
                this::handleReadError);
    }

    void handleReadResult(AsyncModbusReadResult result) {
        result.getRegisters().ifPresent(registers -> {
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }

            for (SigenergyEvacRegisters register : SigenergyEvacRegisters.values()) {
                int offset = register.getAddress() - BLOCK_START;
                ModbusBitUtilities.extractStateFromRegisters(registers, offset, register.getType())
                        .ifPresent(value -> updateState(
                                new ChannelUID(thing.getUID(), register.getChannelGroup(), register.getChannelName()),
                                register.createState(value)));
            }

            updateAlarmChannels(registers);

            updateState(new ChannelUID(thing.getUID(), "status", ModbusSigenergyBindingConstants.CHANNEL_LAST_UPDATE),
                    new DateTimeType());
        });
    }

    private void updateAlarmChannels(ModbusRegisterArray registers) {
        Optional<DecimalType> m1 = ModbusBitUtilities.extractStateFromRegisters(registers, OFFSET_ALARM_1,
                ValueType.UINT16);
        Optional<DecimalType> m2 = ModbusBitUtilities.extractStateFromRegisters(registers, OFFSET_ALARM_2,
                ValueType.UINT16);
        Optional<DecimalType> m3 = ModbusBitUtilities.extractStateFromRegisters(registers, OFFSET_ALARM_3,
                ValueType.UINT16);
        if (m1.isEmpty() || m2.isEmpty() || m3.isEmpty()) {
            return;
        }
        int mask1 = m1.get().intValue();
        int mask2 = m2.get().intValue();
        int mask3 = m3.get().intValue();

        updateState(new ChannelUID(thing.getUID(), "status", "alarm-active"),
                OnOffType.from(SigenergyEvacAlarms.anyActive(mask1, mask2, mask3)));
        updateState(new ChannelUID(thing.getUID(), "status", "alarm-summary"),
                new StringType(SigenergyEvacAlarms.summary(mask1, mask2, mask3)));

        // log undocumented bits once per change, never on every poll
        int undocumented1 = SigenergyEvacAlarms.undocumentedBits(1, mask1);
        int undocumented2 = SigenergyEvacAlarms.undocumentedBits(2, mask2);
        int undocumented3 = SigenergyEvacAlarms.undocumentedBits(3, mask3);
        if (undocumented1 != lastUndocumented1 || undocumented2 != lastUndocumented2
                || undocumented3 != lastUndocumented3) {
            lastUndocumented1 = undocumented1;
            lastUndocumented2 = undocumented2;
            lastUndocumented3 = undocumented3;
            if (undocumented1 != 0 || undocumented2 != 0 || undocumented3 != 0) {
                logger.debug("Undocumented EVAC alarm bits set: mask1={}, mask2={}, mask3={}",
                        Integer.toBinaryString(mask1), Integer.toBinaryString(mask2), Integer.toBinaryString(mask3));
            }
        }
    }

    void handleReadError(AsyncModbusFailure<ModbusReadRequestBlueprint> error) {
        logger.debug("Failed to read EVAC data", error.getCause());
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                "Failed to retrieve charger data: " + error.getCause().getMessage());
    }
}
