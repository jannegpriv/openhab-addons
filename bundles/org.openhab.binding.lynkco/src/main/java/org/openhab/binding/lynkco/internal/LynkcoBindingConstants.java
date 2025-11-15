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
package org.openhab.binding.lynkco.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link LynkcoBindingConstants} is responsible for defining String constants for channel
 * names.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class LynkcoBindingConstants {
    public static final String BINDING_ID = "lynkco";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_VEHICLE = new ThingTypeUID(BINDING_ID, "vehicle");
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "api");

    // List of Channel groups
    public static final String GROUP_DOORS = "doors";
    public static final String GROUP_WINDOWS = "windows";
    public static final String GROUP_ODOMETER = "odometer";
    public static final String GROUP_FUEL = "fuel";
    public static final String GROUP_POSITION = "position";
    public static final String GROUP_BATTERY = "battery";
    public static final String GROUP_CHARGING = "charging";
    public static final String GROUP_CLIMATE = "climate";
    public static final String GROUP_MAINTENANCE = "maintenance";
    public static final String GROUP_BULBS = "bulbs";
    public static final String GROUP_SAFETY = "safety";
    public static final String GROUP_TYRES = "tyres";
    public static final String GROUP_CLIMATE_CONTROL = "climate-control";
    public static final String GROUP_ENGINE_CONTROL = "engine-control";
    public static final String GROUP_DOORS_CONTROL = "doors-control";
    public static final String GROUP_LIGHTS_CONTROL = "lights-control";
    public static final String GROUP_HORN_CONTROL = "horn-control";
    public static final String GROUP_TRIP = "trip";
    public static final String GROUP_VEHICLE_STATUS = "vehicle-status";
    public static final String GROUP_SPEED = "speed";

    // Speed channels
    public static final String CHANNEL_SPEED = "speed";
    public static final String CHANNEL_DIRECTION = "direction";

    // Trip channels
    public static final String CHANNEL_AVG_SPEED = "avg-speed";
    public static final String CHANNEL_LAST_TRIP_SPEED = "last-trip-speed";
    public static final String CHANNEL_TRIP_METER = "trip-meter";
    public static final String CHANNEL_TRIP_METER_2 = "trip-meter-2";

    // Vehicle status channels
    public static final String CHANNEL_ENGINE_STATUS = "engine-status";
    public static final String CHANNEL_KEY_STATUS = "key-status";
    public static final String CHANNEL_USAGE_MODE = "usage-mode";

    // Door channels
    public static final String DOOR_DRIVER = "door-driver";
    public static final String DOOR_PASSENGER = "door-passenger";
    public static final String DOOR_REAR_LEFT = "door-rear-left";
    public static final String DOOR_REAR_RIGHT = "door-rear-right";
    public static final String HOOD = "hood";
    public static final String TRUNK = "trunk";
    public static final String LOCKS_STATUS = "locks-status";
    public static final String TANK_FLAP = "tank-flap";
    public static final String ALARM_STATUS = "alarm-status";

    // Window channels
    public static final String WINDOW_DRIVER = "window-driver";
    public static final String WINDOW_PASSENGER = "window-passenger";
    public static final String WINDOW_REAR_LEFT = "window-rear-left";
    public static final String WINDOW_REAR_RIGHT = "window-rear-right";
    public static final String SUNROOF = "sunroof";

    // Odometer channels
    public static final String ODOMETER_KM = "odometer-km";
    public static final String TRIP_METER_1 = "trip-meter-1";
    public static final String TRIP_METER_2 = "trip-meter-2";

    // Fuel channels
    public static final String FUEL_LEVEL = "level";
    public static final String FUEL_LEVEL_STATUS = "level-status";
    public static final String FUEL_TYPE = "type";
    public static final String FUEL_RANGE = "range";
    public static final String FUEL_CONSUMPTION = "consumption";
    public static final String FUEL_CONSUMPTION_LAST = "consumption-last-trip";

    // Position channels
    public static final String LOCATION = "location";
    public static final String LOCATION_TRUSTED = "location-trusted";
    public static final String LOCATION_UPDATED = "updated-at";

    // Battery channels
    public static final String BATTERY_CHARGE_LEVEL = "charge-level";
    public static final String BATTERY_TIME_TO_CHARGE = "time-to-charge";
    public static final String BATTERY_RANGE = "range-electric";
    public static final String BATTERY_CHARGE = "charge";
    public static final String BATTERY_HEALTH = "health";
    public static final String BATTERY_VOLTAGE = "voltage";
    public static final String BATTERY_ENERGY = "energy-level";
    public static final String BATTERY_POWER = "power-level";

    public static final String CHARGING_LEVEL = "charging-level";
    public static final String CHARGING_RANGE = "range";
    public static final String CHARGING_TIME = "time-to-full";
    public static final String CHARGER_STATE = "charger-state";
    public static final String CHARGER_CONNECTION_STATUS = "charger-connection-status";
    public static final String POWER_MODE = "power-mode";

    // Climate channels
    public static final String TEMPERATURE_EXTERIOR = "temp-exterior";
    public static final String TEMPERATURE_INTERIOR = "temp-interior";
    public static final String PRECLIMATE_ACTIVE = "preclimate-active";

    // Common channels
    public static final String CHANNEL_TIMESTAMP = "last-update";

    // Maintenance channels
    public static final String BRAKE_FLUID = "brake-fluid";
    public static final String COOLANT = "coolant";
    public static final String ENGINE_OIL_LEVEL = "engine-oil-level";
    public static final String ENGINE_OIL_PRESSURE = "engine-oil-pressure";
    public static final String SERVICE_WARNING = "service-warning";
    public static final String WASHER_FLUID = "washer-fluid";

    // Bulb channels
    public static final String DAYTIME_RUNNING = "daytime-running";
    public static final String FOG_FRONT = "fog-front";
    public static final String FOG_REAR = "fog-rear";
    public static final String HIGH_BEAM = "high-beam";
    public static final String HIGH_BEAM_LEFT = "high-beam-left";
    public static final String HIGH_BEAM_RIGHT = "high-beam-right";
    public static final String TURN_LEFT = "turn-left";
    public static final String LOW_BEAM = "low-beam";
    public static final String LOW_BEAM_LEFT = "low-beam-left";
    public static final String LOW_BEAM_RIGHT = "low-beam-right";
    public static final String POSITION_LIGHTS = "position";
    public static final String TURN_RIGHT = "turn-right";
    public static final String STOP_LIGHTS = "stop";

    // Safety channels
    public static final String AIRBAG_UPDATED = "airbag-updated";
    public static final String AIRBAG_STATUS = "airbag-status";
    public static final String SEATBELT_UPDATED = "seatbelt-updated";
    public static final String SEATBELT_DRIVER = "seatbelt-driver";
    public static final String SEATBELT_PASSENGER = "seatbelt-passenger";
    public static final String SEATBELT_REAR_LEFT = "seatbelt-rear-left";
    public static final String SEATBELT_REAR_MIDDLE = "seatbelt-rear-middle";
    public static final String SEATBELT_REAR_RIGHT = "seatbelt-rear-right";

    // Tyre channels
    public static final String TYRE_UPDATED = "tyre-updated";
    public static final String TYRE_FRONT_LEFT = "front-left";
    public static final String TYRE_FRONT_RIGHT = "front-right";
    public static final String TYRE_REAR_LEFT = "rear-left";
    public static final String TYRE_REAR_RIGHT = "rear-right";

    // Climate Control Channels
    public static final String CHANNEL_PRECLIMATE = "preclimate";

    // Engine Control Channels
    public static final String CHANNEL_ENGINE_START = "start";

    // Door Control Channels
    public static final String CHANNEL_DOOR_LOCK = "lock";

    // Light Control Channels
    public static final String CHANNEL_LIGHT_FLASH = "flash";

    // Horn Control Channels
    public static final String CHANNEL_HORN = "honk";
    public static final String CHANNEL_HONK_FLASH = "honkflash";

    // Misc
    public static final String CHANNEL_STATUS = "status";

    // Default value for undefined integers
    public static final int UNDEFINED = -1;

    // List of all supported Thing Types
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_BRIDGE, THING_TYPE_VEHICLE);
}
