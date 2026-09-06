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

import static org.openhab.binding.modbus.sigenergy.internal.ModbusSigenergyBindingConstants.GROUP_BATTERY;
import static org.openhab.binding.modbus.sigenergy.internal.ModbusSigenergyBindingConstants.GROUP_ENERGY;
import static org.openhab.binding.modbus.sigenergy.internal.ModbusSigenergyBindingConstants.GROUP_GRID;
import static org.openhab.binding.modbus.sigenergy.internal.ModbusSigenergyBindingConstants.GROUP_OVERVIEW;
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
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

/**
 * Defines the Sigenergy plant Modbus input registers (unit ID 247) used by this binding, according to
 * the official Sigenergy Modbus Protocol V2.9 (2026-05-13), table 5-1.
 *
 * Addresses are PDU addresses and are sent on the wire as written, without subtracting one.
 * This was live-verified: FC04 at 30014 returns the plant SOC (raw 322 = 32.2 %).
 * Multi-register values are big-endian with the high word first, hence {@link ValueType#INT32},
 * {@link ValueType#UINT32} and {@link ValueType#UINT64}.
 * Power registers are specified as gain 1000 with unit kW, so the raw register value equals watts.
 * Energy registers are gain 100 with unit kWh, temperature and SOC/SOH gain 10.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public enum SigenergyPlantRegisters {

    EMS_MODE(30003, UINT16, stringFactory(SigenergyEnumMappers::emsMode), GROUP_OVERVIEW),
    GRID_SENSOR_STATUS(30004, UINT16, stringFactory(SigenergyEnumMappers::gridSensorStatus), GROUP_GRID),
    // positive = buy from grid, negative = sell to grid
    GRID_POWER(30005, INT32, wattFactory(), GROUP_GRID),
    ON_OFF_GRID_STATUS(30009, UINT16, stringFactory(SigenergyEnumMappers::onOffGridStatus), GROUP_OVERVIEW),
    BATTERY_SOC(30014, UINT16, percentFactory(), GROUP_BATTERY),
    PLANT_POWER(30031, INT32, wattFactory(), GROUP_OVERVIEW),
    PV_POWER(30035, INT32, wattFactory(), GROUP_OVERVIEW),
    // positive = charge, negative = discharge
    BATTERY_POWER(30037, INT32, wattFactory(), GROUP_BATTERY),
    ALARM_MASK_1(30027, UINT16, DecimalType::new, GROUP_OVERVIEW),
    ALARM_MASK_2(30028, UINT16, DecimalType::new, GROUP_OVERVIEW),
    ALARM_MASK_3(30029, UINT16, DecimalType::new, GROUP_OVERVIEW),
    ALARM_MASK_4(30030, UINT16, DecimalType::new, GROUP_OVERVIEW),
    PLANT_RUNNING_STATE(30051, UINT16, stringFactory(SigenergyEnumMappers::plantRunningState), GROUP_OVERVIEW),
    // per-phase grid power, same sign convention as GRID_POWER
    GRID_PHASE_A_POWER(30052, INT32, wattFactory(), GROUP_GRID),
    GRID_PHASE_B_POWER(30054, INT32, wattFactory(), GROUP_GRID),
    GRID_PHASE_C_POWER(30056, INT32, wattFactory(), GROUP_GRID),
    AVAILABLE_CHARGE_CAPACITY(30064, UINT32, energyFactory(), GROUP_BATTERY),
    AVAILABLE_DISCHARGE_CAPACITY(30066, UINT32, energyFactory(), GROUP_BATTERY),
    ALARM_MASK_5(30072, UINT16, DecimalType::new, GROUP_OVERVIEW),

    RATED_CAPACITY(30083, UINT32, energyFactory(), GROUP_BATTERY),
    CHARGE_CUTOFF_SOC(30085, UINT16, percentFactory(), GROUP_BATTERY),
    DISCHARGE_CUTOFF_SOC(30086, UINT16, percentFactory(), GROUP_BATTERY),
    BATTERY_SOH(30087, UINT16, percentFactory(), GROUP_BATTERY),
    TOTAL_PV_GENERATION(30088, UINT64, energyFactory(), GROUP_ENERGY),
    LOAD_CONSUMPTION_TODAY(30092, UINT32, energyFactory(), GROUP_ENERGY),
    TOTAL_LOAD_CONSUMPTION(30094, UINT64, energyFactory(), GROUP_ENERGY),

    TOTAL_BATTERY_CHARGED_ENERGY(30200, UINT64, energyFactory(), GROUP_ENERGY),
    TOTAL_BATTERY_DISCHARGED_ENERGY(30204, UINT64, energyFactory(), GROUP_ENERGY),
    TOTAL_IMPORTED_ENERGY(30216, UINT64, energyFactory(), GROUP_ENERGY),
    TOTAL_EXPORTED_ENERGY(30220, UINT64, energyFactory(), GROUP_ENERGY),

    PV_GENERATION_TODAY(30272, UINT32, energyFactory(), GROUP_ENERGY),
    PV_GENERATION_YESTERDAY(30274, UINT32, energyFactory(), GROUP_ENERGY),

    // registers below exist since protocol V2.8; polled separately so older firmware can fall back
    ALARM_MASK_6(30280, UINT16, DecimalType::new, GROUP_OVERVIEW),
    ALARM_MASK_7(30281, UINT16, DecimalType::new, GROUP_OVERVIEW),
    GENERAL_LOAD_POWER(30282, INT32, wattFactory(), GROUP_OVERVIEW),
    TOTAL_LOAD_POWER(30284, INT32, wattFactory(), GROUP_OVERVIEW, ModbusSigenergyBindingConstants.CHANNEL_LOAD_POWER),
    CELL_TEMPERATURE(30286, INT16, temperatureFactory(), GROUP_BATTERY);

    private final int address;
    private final ValueType type;
    private final Function<BigDecimal, State> stateFactory;
    private final String channelGroup;
    private final String channelName;

    SigenergyPlantRegisters(int address, ValueType type, Function<BigDecimal, State> stateFactory,
            String channelGroup) {
        this.address = address;
        this.type = type;
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.channelName = name().toLowerCase().replace('_', '-');
    }

    SigenergyPlantRegisters(int address, ValueType type, Function<BigDecimal, State> stateFactory, String channelGroup,
            String channelName) {
        this.address = address;
        this.type = type;
        this.stateFactory = stateFactory;
        this.channelGroup = channelGroup;
        this.channelName = channelName;
    }

    private static Function<BigDecimal, State> wattFactory() {
        // gain 1000, unit kW: the raw value equals watts
        return value -> new QuantityType<>(value, Units.WATT);
    }

    private static Function<BigDecimal, State> energyFactory() {
        // gain 100, unit kWh: raw 1234 means 12.34 kWh
        return value -> new QuantityType<>(value.movePointLeft(2), Units.KILOWATT_HOUR);
    }

    private static Function<BigDecimal, State> percentFactory() {
        // gain 10, unit %: raw 322 means 32.2 %
        return value -> new QuantityType<>(value.movePointLeft(1), Units.PERCENT);
    }

    private static Function<BigDecimal, State> temperatureFactory() {
        // gain 10, unit degree Celsius
        return value -> new QuantityType<>(value.movePointLeft(1), SIUnits.CELSIUS);
    }

    private static Function<BigDecimal, State> stringFactory(Function<Integer, String> mapper) {
        return value -> new StringType(mapper.apply(value.intValue()));
    }

    /**
     * Returns the PDU address of this register. Sent on the wire as-is.
     *
     * @return the PDU address.
     */
    public int getAddress() {
        return address;
    }

    /**
     * Returns the {@link ValueType} used to decode this register.
     *
     * @return the value type.
     */
    public ValueType getType() {
        return type;
    }

    /**
     * Returns the number of 16-bit registers occupied by this value.
     *
     * @return the register count.
     */
    public int getRegisterCount() {
        return type.getBits() / 16;
    }

    /**
     * Returns the channel group id.
     *
     * @return the channel group id.
     */
    public String getChannelGroup() {
        return channelGroup;
    }

    /**
     * Returns the channel id.
     *
     * @return the channel id.
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * Creates the {@link State} for the given raw register value.
     *
     * @param registerValue the raw decoded value.
     * @return the state to publish.
     */
    public State createState(DecimalType registerValue) {
        return stateFactory.apply(registerValue.toBigDecimal());
    }
}
