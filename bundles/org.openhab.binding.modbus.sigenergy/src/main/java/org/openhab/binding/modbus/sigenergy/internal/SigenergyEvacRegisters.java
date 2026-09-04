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

import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.INT32;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.UINT16;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.UINT32;

import java.math.BigDecimal;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

/**
 * Defines the Sigen EVAC (AC charger) Modbus input registers used by this binding, according to
 * the official Sigenergy Modbus Protocol V2.9 (2026-05-13), table 5-5.
 *
 * Addresses are PDU addresses and are sent on the wire as written, without subtracting one.
 * The registers are read from the charger's own Modbus unit ID (1-246, configured in mySigen),
 * through the same TCP endpoint as the plant.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public enum SigenergyEvacRegisters {

    SYSTEM_STATE(32000, UINT16, stateFactory(), "status"),
    // raw IEC 61851-1 state code, for diagnostics next to the mapped system-state channel
    RAW_STATE(32000, UINT16, DecimalType::new, "status", "raw-state"),
    TOTAL_ENERGY(32001, UINT32, energyFactory(), "charging"),
    CHARGING_POWER(32003, INT32, kilowattFactory(), "charging"),
    RATED_POWER(32005, UINT32, kilowattFactory(), "ratings"),
    RATED_CURRENT(32007, INT32, ampereFactory(), "ratings"),
    RATED_VOLTAGE(32009, UINT16, voltFactory(), "ratings"),
    // the charger's own input breaker rating; NOT the household main fuse / DLM limit
    INPUT_BREAKER_CURRENT(32010, INT32, ampereFactory(), "ratings"),
    ALARM_MASK_1(32012, UINT16, DecimalType::new, "status"),
    ALARM_MASK_2(32013, UINT16, DecimalType::new, "status"),
    ALARM_MASK_3(32014, UINT16, DecimalType::new, "status");

    private final int address;
    private final ValueType type;
    private final Function<BigDecimal, State> stateFactory;
    private final String channelGroup;
    private final String channelName;

    SigenergyEvacRegisters(int address, ValueType type, Function<BigDecimal, State> stateFactory, String channelGroup) {
        this.address = address;
        this.type = type;
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.channelName = name().toLowerCase().replace('_', '-');
    }

    SigenergyEvacRegisters(int address, ValueType type, Function<BigDecimal, State> stateFactory, String channelGroup,
            String channelName) {
        this.address = address;
        this.type = type;
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.channelName = channelName;
    }

    private static Function<BigDecimal, State> stateFactory() {
        return value -> new StringType(SigenergyEnumMappers.evacSystemState(value.intValue()));
    }

    private static Function<BigDecimal, State> kilowattFactory() {
        // gain 1000, unit kW: raw 10200 means 10.2 kW
        return value -> new QuantityType<>(value.movePointLeft(3), MetricPrefix.KILO(Units.WATT));
    }

    private static Function<BigDecimal, State> energyFactory() {
        // gain 100, unit kWh
        return value -> new QuantityType<>(value.movePointLeft(2), Units.KILOWATT_HOUR);
    }

    private static Function<BigDecimal, State> ampereFactory() {
        // gain 100, unit A
        return value -> new QuantityType<>(value.movePointLeft(2), Units.AMPERE);
    }

    private static Function<BigDecimal, State> voltFactory() {
        // gain 10, unit V
        return value -> new QuantityType<>(value.movePointLeft(1), Units.VOLT);
    }

    public int getAddress() {
        return address;
    }

    public ValueType getType() {
        return type;
    }

    public int getRegisterCount() {
        return type.getBits() / 16;
    }

    public String getChannelGroup() {
        return channelGroup;
    }

    public String getChannelName() {
        return channelName;
    }

    public State createState(DecimalType registerValue) {
        return stateFactory.apply(registerValue.toBigDecimal());
    }
}
