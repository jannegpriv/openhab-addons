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
package org.openhab.binding.lynkco.internal.dto;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * DTOs for the modern Lynk&Co mobile-app gateway used by the 01 (2025), 02 and 08. Each nested
 * class mirrors the JSON of one gateway state endpoint; only the fields the binding consumes are
 * declared (gson ignores the rest). Fields are nullable as endpoints may omit sections.
 *
 * @author Jan Gustafsson - Initial contribution
 */
public class GatewayDTO {

    /** {@code /list/vehicles} */
    public static class VehicleList {
        public @Nullable List<VehicleListEntry> listOfVehicles;
    }

    public static class VehicleListEntry {
        public @Nullable VehicleListVehicle vehicle;
        public String role = "";
    }

    public static class VehicleListVehicle {
        public String vin = "";
        public String model = "";
    }

    /** {@code /vehicle/{vin}/vehicle_data} */
    public static class VehicleData {
        public @Nullable StatusValue centralLock;
        public @Nullable ClimateControlSummary climateControl;
        public @Nullable StatusValue gloveBox;
        public boolean driveModeEnabled;
    }

    public static class StatusValue {
        public String status = "";
    }

    public static class ClimateControlSummary {
        public String status = "";
        public String engineStatus = "";
    }

    /** {@code /vehicle/{vin}/vehicle_metadata} */
    public static class Metadata {
        public @Nullable VehicleMeta vehicle;
        public @Nullable FuelInfo fuelInfo;
    }

    public static class VehicleMeta {
        public String model = "";
        public String propulsionType = "";
        public long odometer;
    }

    public static class FuelInfo {
        public String fuelType = "";
        public double tankCapacity;
    }

    /** {@code /vehicle/{vin}/location_state} */
    public static class LocationState {
        public @Nullable VehicleLocation vehicleLocation;
    }

    public static class VehicleLocation {
        public String updatedAt = "";
        public String status = "";
        public @Nullable Coordinates coordinates;
    }

    public static class Coordinates {
        public double latitude;
        public double longitude;
    }

    /** {@code /vehicle/{vin}/charge_state} */
    public static class ChargeState {
        public @Nullable BatteryState batteryState;
    }

    public static class BatteryState {
        public double stateOfCharge;
        public @Nullable Double remainingRange;
        public @Nullable Double remainingChargingTime;
        public String updatedAt = "";
        public String status = "";
        public @Nullable ChargeLimit chargeLimit;
    }

    public static class ChargeLimit {
        public int value;
        public int min;
        public int max;
        public int suggestedLimit;
    }

    /** {@code /vehicle/{vin}/climate_state} */
    public static class ClimateState {
        public double interiorTemperature;
        public double targetTemperature;
        public String status = "";
        public String updatedAt = "";
        public String engineStatus = "";
        public @Nullable Heaters heaters;
    }

    public static class Heaters {
        public @Nullable Heater steeringWheel;
        public @Nullable Heater frontLeftSeat;
        public @Nullable Heater frontRightSeat;
        public @Nullable Heater rearLeftSeat;
        public @Nullable Heater rearRightSeat;
    }

    public static class Heater {
        public String status = "";
    }

    /** {@code /vehicle/{vin}/doors_windows_state} */
    public static class DoorsWindowsState {
        public String doorFrontLeftStatus = "";
        public String doorFrontRightStatus = "";
        public String doorRearLeftStatus = "";
        public String doorRearRightStatus = "";
        public String windowFrontLeftStatus = "";
        public String windowFrontRightStatus = "";
        public String windowRearLeftStatus = "";
        public String windowRearRightStatus = "";
        public String sunroofStatus = "";
        public String hoodStatus = "";
        public String trunkStatus = "";
        public String tankFlapStatus = "";
        public String updatedAt = "";
    }

    /** {@code /vehicle/{vin}/fuel_state} */
    public static class FuelStateResponse {
        public @Nullable FuelStateInner fuelState;
    }

    public static class FuelStateInner {
        public double percentageOfRemainingFuel;
        public double remainingRange;
        public double averageConsumption;
        public String updatedAt = "";
    }
}
