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

import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.INT16;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.INT32;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.UINT16;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.UINT32;
import static org.openhab.core.io.transport.modbus.ModbusConstants.ValueType.UINT64;

import java.math.BigDecimal;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.transport.modbus.ModbusConstants.ValueType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

/**
 * Defines the hybrid inverter Modbus input registers (table 5-3 of the Sigenergy Modbus Protocol V2.9)
 * used by this binding. Addresses are PDU addresses and are sent on the wire as written.
 * The registers are read from the inverter's own Modbus unit ID (1-246, configured in mySigen).
 *
 * Per-string PV power is not a register; the handler derives it as string voltage times string current.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public enum SigenergyInverterRegisters {

    // block A: 30578-30609 (running data, ESS, alarms)
    RUNNING_STATE(30578, UINT16, stringFactory(), "status"),
    ACTIVE_POWER(30587, INT32, wattFactory(), "power"),
    AVAILABLE_CHARGE_ENERGY(30595, UINT32, energyFactory(), "battery"),
    AVAILABLE_DISCHARGE_ENERGY(30597, UINT32, energyFactory(), "battery"),
    // positive = charge, negative = discharge
    ESS_POWER(30599, INT32, wattFactory(), "battery"),
    ESS_SOC(30601, UINT16, percentFactory(), "battery"),
    ESS_SOH(30602, UINT16, percentFactory(), "battery"),
    CELL_TEMPERATURE(30603, INT16, temperatureFactory(), "battery"),
    CELL_VOLTAGE(30604, UINT16, milliFactory(Units.VOLT), "battery"),
    ALARM_MASK_1(30605, UINT16, DecimalType::new, "status"),
    ALARM_MASK_2(30606, UINT16, DecimalType::new, "status"),
    ALARM_MASK_3(30607, UINT16, DecimalType::new, "status"),
    ALARM_MASK_4(30608, UINT16, DecimalType::new, "status"),
    ALARM_MASK_5(30609, UINT16, DecimalType::new, "status"),

    // block B: 31000-31037 (grid, phases, PV strings)
    GRID_FREQUENCY(31002, UINT16, centiFactory(Units.HERTZ), "power"),
    INTERNAL_TEMPERATURE(31003, INT16, temperatureFactory(), "power"),
    PHASE_A_VOLTAGE(31011, UINT32, centiFactory(Units.VOLT), "phases"),
    PHASE_B_VOLTAGE(31013, UINT32, centiFactory(Units.VOLT), "phases"),
    PHASE_C_VOLTAGE(31015, UINT32, centiFactory(Units.VOLT), "phases"),
    PHASE_A_CURRENT(31017, INT32, centiFactory(Units.AMPERE), "phases"),
    PHASE_B_CURRENT(31019, INT32, centiFactory(Units.AMPERE), "phases"),
    PHASE_C_CURRENT(31021, INT32, centiFactory(Units.AMPERE), "phases"),
    POWER_FACTOR(31023, INT16, milliDecimalFactory(), "power"),
    STRING_COUNT(31025, UINT16, DecimalType::new, "strings"),
    MPPT_COUNT(31026, UINT16, DecimalType::new, "strings"),
    PV1_VOLTAGE(31027, INT16, deciFactory(Units.VOLT), "strings"),
    PV1_CURRENT(31028, INT16, centiFactory(Units.AMPERE), "strings"),
    PV2_VOLTAGE(31029, INT16, deciFactory(Units.VOLT), "strings"),
    PV2_CURRENT(31030, INT16, centiFactory(Units.AMPERE), "strings"),
    PV3_VOLTAGE(31031, INT16, deciFactory(Units.VOLT), "strings"),
    PV3_CURRENT(31032, INT16, centiFactory(Units.AMPERE), "strings"),
    PV4_VOLTAGE(31033, INT16, deciFactory(Units.VOLT), "strings"),
    PV4_CURRENT(31034, INT16, centiFactory(Units.AMPERE), "strings"),
    PV_POWER(31035, INT32, wattFactory(), "power"),
    INSULATION_RESISTANCE(31037, UINT16, milliFactory(MetricPrefix.MEGA(Units.OHM)), "strings"),

    // block C (slow): 30540-30577 (ratings and ESS energy counters)
    RATED_ACTIVE_POWER(30540, UINT32, wattFactory(), "power"),
    ESS_RATED_CAPACITY(30548, UINT32, energyFactory(), "battery"),
    DAILY_CHARGE_ENERGY(30566, UINT32, energyFactory(), "energy"),
    ACCUMULATED_CHARGE_ENERGY(30568, UINT64, energyFactory(), "energy"),
    DAILY_DISCHARGE_ENERGY(30572, UINT32, energyFactory(), "energy"),
    ACCUMULATED_DISCHARGE_ENERGY(30574, UINT64, energyFactory(), "energy");

    private final int address;
    private final ValueType type;
    private final Function<BigDecimal, State> stateFactory;
    private final String channelGroup;
    private final String channelName;

    SigenergyInverterRegisters(int address, ValueType type, Function<BigDecimal, State> stateFactory,
            String channelGroup) {
        this.address = address;
        this.type = type;
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.channelName = name().toLowerCase().replace('_', '-');
    }

    private static Function<BigDecimal, State> stringFactory() {
        // register 30578 uses the same Appendix 1 running states as the plant register 30051
        return value -> new StringType(SigenergyEnumMappers.plantRunningState(value.intValue()));
    }

    private static Function<BigDecimal, State> wattFactory() {
        // gain 1000, unit kW: the raw value equals watts
        return value -> new QuantityType<>(value, Units.WATT);
    }

    private static Function<BigDecimal, State> energyFactory() {
        // gain 100, unit kWh
        return value -> new QuantityType<>(value.movePointLeft(2), Units.KILOWATT_HOUR);
    }

    private static Function<BigDecimal, State> percentFactory() {
        // gain 10, unit %
        return value -> new QuantityType<>(value.movePointLeft(1), Units.PERCENT);
    }

    private static Function<BigDecimal, State> temperatureFactory() {
        // gain 10, unit degree Celsius
        return value -> new QuantityType<>(value.movePointLeft(1), SIUnits.CELSIUS);
    }

    private static Function<BigDecimal, State> deciFactory(javax.measure.Unit<?> unit) {
        return value -> new QuantityType<>(value.movePointLeft(1), unit);
    }

    private static Function<BigDecimal, State> centiFactory(javax.measure.Unit<?> unit) {
        return value -> new QuantityType<>(value.movePointLeft(2), unit);
    }

    private static Function<BigDecimal, State> milliFactory(javax.measure.Unit<?> unit) {
        return value -> new QuantityType<>(value.movePointLeft(3), unit);
    }

    private static Function<BigDecimal, State> milliDecimalFactory() {
        return value -> new DecimalType(value.movePointLeft(3));
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
