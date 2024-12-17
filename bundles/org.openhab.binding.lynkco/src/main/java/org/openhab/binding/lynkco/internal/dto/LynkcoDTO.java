/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link LynkcoDTO} class defines the DTO for the Lynk&Co Vehicle
 *
 * @author Jan Gustafsson - Initial contribution
 */

@NonNullByDefault
public class LynkcoDTO {
    public RecordDTO record = new RecordDTO();
    public ShadowDTO shadow = new ShadowDTO();

    public static class RecordDTO {
        public Battery battery = new Battery();
        public Climate climate = new Climate();
        public String createdAt = "";
        public ElectricStatus electricStatus = new ElectricStatus();
        public Fuel fuel = new Fuel();
        public MaintenanceStatus maintenanceStatus = new MaintenanceStatus();
        public Odometer odometer = new Odometer();
        public Position position = new Position();
        public Speed speed = new Speed();
        public Trip trip = new Trip();
        public String updatedAt = "";
        public String vin = "";
    }

    public static class ShadowDTO {
        public Bvs bvs = new Bvs();
        public Evs evs = new Evs();
        public Vcs vcs = new Vcs();
        public Vls vls = new Vls();
        public Vms vms = new Vms();
        public Vrs vrs = new Vrs();
    }

    public static class Battery {
        public String charge = "";
        public int chargeLevel;
        public int energyLevel;
        public int health;
        public int powerLevel;
        public String vehicleUpdatedAt = "";
        public int voltage;
    }

    public static class Climate {
        public Temperature exteriorTemp = new Temperature();
        public Temperature interiorTemp = new Temperature();
        public boolean preClimateActive;
        public String vehicleUpdatedAt = "";

        public static class Temperature {
            public String quality = "";
            public String unit = "";
            public double temp;
        }
    }

    public static class ElectricStatus {
        public double chargeLevel;
        public int distanceToEmptyOnBatteryOnly;
        public int timeToFullyCharged;
        public String vehicleUpdatedAt = "";
    }

    public static class Fuel {
        public double averageConsumption;
        public double averageConsumptionLatestDrivingCycle;
        public String averageConsumptionUnit = "";
        public int distanceToEmpty;
        public String fuelType = "";
        public double level;
        public int levelStatus;
        public String vehicleUpdatedAt = "";
    }

    public static class MaintenanceStatus {
        public int daysToService;
        public int distanceToService;
        public int engineCoolantTemperature;
        public int engineHoursToService;
        public String engineOilLevelStatus = "";
        public String engineOilPressureStatus = "";
        public String serviceWarningStatus = "";
        public String washerFluidLevelStatus = "";
        public String vehicleUpdatedAt = "";
    }

    public static class Odometer {
        public double odometerKm;
        public double odometerMile;
        public String vehicleUpdatedAt = "";
    }

    public static class Position {
        public int altitude;
        public boolean canBeTrusted;
        public double latitude;
        public double longitude;
        public String vehicleUpdatedAt = "";
    }

    public static class Speed {
        public int direction;
        public int speed;
        public String speedUnit = "";
        public String vehicleUpdatedAt = "";
    }

    public static class Trip {
        public double avgSpeed;
        public double avgSpeedLastDrivingCycle;
        public double tripMeter;
        public double tripMeter2;
        public String vehicleUpdatedAt = "";
    }

    public static class Bvs {
        public String engineStatus = "";
        public String engineStatusUpdatedAt = "";
        public String keyStatus = "";
        public String keyStatusUpdatedAt = "";
        public String usageMode = "";
        public String usageModeUpdatedAt = "";
    }

    public static class Evs {
        public ChargerStatusData chargerStatusData = new ChargerStatusData();
        public String powermodeStatus = "";
        public String powerModeUpdatedAt = "";

        public static class ChargerStatusData {
            public ChargerConnectionStatus chargerConnectionStatus = ChargerConnectionStatus.CHARGER_CONNECTION_UNSPECIFIED;
            public ChargerState chargerState = ChargerState.CHARGER_STATE_UNSPECIFIED;
            public String updatedAt = "";

            public enum ChargerConnectionStatus {
                CHARGER_CONNECTION_UNSPECIFIED("Unspecified"),
                CHARGER_CONNECTION_DISCONNECTED("Disconnected"),
                CHARGER_CONNECTION_CONNECTED_WITHOUT_POWER("Connected (No Power)"),
                CHARGER_CONNECTION_POWER_AVAILABLE_BUT_NOT_ACTIVATED("Power Not Activated"),
                CHARGER_CONNECTION_CONNECTED_WITH_POWER("Connected"),
                CHARGER_CONNECTION_INIT("Initializing"),
                CHARGER_CONNECTION_FAULT("Fault");

                private final String label;

                ChargerConnectionStatus(String label) {
                    this.label = label;
                }

                public String getLabel() {
                    return label;
                }

                public static ChargerConnectionStatus fromString(String text) {
                    for (ChargerConnectionStatus status : ChargerConnectionStatus.values()) {
                        if (status.name().equals(text)) {
                            return status;
                        }
                    }
                    return CHARGER_CONNECTION_UNSPECIFIED;
                }
            }

            public enum ChargerState {
                CHARGER_STATE_UNSPECIFIED("Unspecified"),
                CHARGER_STATE_IDLE("Idle"),
                CHARGER_STATE_PRE_STRT("Pre-Start"),
                CHARGER_STATE_CHARGN("Charging"),
                CHARGER_STATE_ALRM("Alarm"),
                CHARGER_STATE_SRV("Service"),
                CHARGER_STATE_DIAG("Diagnostics"),
                CHARGER_STATE_BOOT("Boot"),
                CHARGER_STATE_RSTRT("Restart");

                private final String label;

                ChargerState(String label) {
                    this.label = label;
                }

                public String getLabel() {
                    return label;
                }

                public static ChargerState fromString(String text) {
                    for (ChargerState state : ChargerState.values()) {
                        if (state.name().equals(text)) {
                            return state;
                        }
                    }
                    return CHARGER_STATE_UNSPECIFIED;
                }
            }
        }
    }

    public static class Vcs {
        public boolean preclimateActive;
        public String preclimateUpdatedAt = "";
    }

    public static class Vls {
        public String alarmStatusData = "";
        public String alarmStatusUpdatedAt = "";
        public String centralLockingStatus = "";
        public String centralLockingUpdatedAt = "";
        public String doorLockStatusDriver = "";
        public String doorLockStatusDriverRear = "";
        public String doorLockStatusDriverRearUpdatedAt = "";
        public String doorLockStatusDriverUpdatedAt = "";
        public String doorLockStatusPassenger = "";
        public String doorLockStatusPassengerRear = "";
        public String doorLockStatusPassengerRearUpdatedAt = "";
        public String doorLockStatusPassengerUpdatedAt = "";
        public String doorLocksStatus = "";
        public String doorLocksUpdatedAt = "";
        public String doorOpenStatusDriver = "";
        public String doorOpenStatusDriverRear = "";
        public String doorOpenStatusDriverRearUpdatedAt = "";
        public String doorOpenStatusDriverUpdatedAt = "";
        public String doorOpenStatusPassenger = "";
        public String doorOpenStatusPassengerRear = "";
        public String doorOpenStatusPassengerRearUpdatedAt = "";
        public String doorOpenStatusPassengerUpdatedAt = "";
        public String engineHoodStatus = "";
        public String engineHoodUpdatedAt = "";
        public String sunroofOpenStatus = "";
        public String sunroofUpdatedAt = "";
        public String tankFlapStatus = "";
        public String tankFlapUpdatedAt = "";
        public String trunkOpenStatus = "";
        public String trunkOpenUpdatedAt = "";
        public String windowStatusDriver = "";
        public String windowStatusDriverRear = "";
        public String windowStatusDriverRearUpdatedAt = "";
        public String windowStatusDriverUpdatedAt = "";
        public String windowStatusPassenger = "";
        public String windowStatusPassengerRear = "";
        public String windowStatusPassengerRearUpdatedAt = "";
        public String windowStatusPassengerUpdatedAt = "";
    }

    public static class Vms {
        public BulbStatus bulbStatus = new BulbStatus();
        public VehicleStateServiceMaintenance vehicleStateServiceMaintenance = new VehicleStateServiceMaintenance();

        public static class BulbStatus {
            public String dayRunningAny = "";
            public String fogFrontAny = "";
            public String fogRearAny = "";
            public String highBeamAny = "";
            public String highBeamLeft = "";
            public String highBeamRight = "";
            public String leftTurnAny = "";
            public String lowBeamAny = "";
            public String lowBeamLeft = "";
            public String lowBeamRight = "";
            public String multiple = "";
            public String positionAny = "";
            public String rightTurnAny = "";
            public String stopAny = "";
            public String trailerElFailure = "";
            public String trailerStopAny = "";
            public String trailerTurnAny = "";
            public String trailerTurnLeftAny = "";
            public String trailerTurnRightAny = "";
            public String updatedAt = "";
        }

        public static class VehicleStateServiceMaintenance {
            public String brakeFluidLevelStatus = "";
            public String coolantLevelStatus = "";
            public String engineOilLevelStatus = "";
            public String engineOilPressureStatus = "";
            public String serviceWarningStatus = "";
            public String serviceWarningTrigger = "";
            public String updatedAt = "";
            public String washerFluidLevelStatus = "";
        }
    }

    public static class Vrs {
        public AirbagStatus airbagStatus = new AirbagStatus();
        public String fuelLevelStatus = "";
        public String fuelLevelStatusUpdatedAt = "";
        public SeatBeltStatus seatBeltStatus = new SeatBeltStatus();
        public VehicleTyresStatus vehicleTyresStatus = new VehicleTyresStatus();

        public static class AirbagStatus {
            public String srsStatus = "";
            public String updatedAt = "";
        }

        public static class SeatBeltStatus {
            public SeatBelt driver = new SeatBelt();
            public SeatBelt driverRear = new SeatBelt();
            public SeatBelt midRear = new SeatBelt();
            public SeatBelt passenger = new SeatBelt();
            public SeatBelt passengerRear = new SeatBelt();
            public String updatedAt = "";

            public static class SeatBelt {
                public boolean fastened;
            }
        }

        public static class VehicleTyresStatus {
            public TyreStatus driverFrontTyre = new TyreStatus();
            public TyreStatus driverRearTyre = new TyreStatus();
            public TyreStatus passengerFrontTyre = new TyreStatus();
            public TyreStatus passengerRearTyre = new TyreStatus();
            public String updatedAt = "";

            public static class TyreStatus {
                public String description = "";
                public String pressure = "";
            }
        }
    }
}
