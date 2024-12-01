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
package org.openhab.binding.lynkco.internal.handler;

import static org.openhab.binding.lynkco.internal.LynkcoBindingConstants.*;
import static org.openhab.core.library.unit.MetricPrefix.KILO;
import static org.openhab.core.library.unit.SIUnits.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lynkco.internal.LynkcoVehicleConfiguration;
import org.openhab.binding.lynkco.internal.api.LynkcoAPI;
import org.openhab.binding.lynkco.internal.api.LynkcoApiException;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Battery;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Bvs;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Climate;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.ElectricStatus;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Fuel;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Position;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.RecordDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Speed;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Trip;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Vls;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Vms.BulbStatus;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Vms.VehicleStateServiceMaintenance;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Vrs;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Vrs.VehicleTyresStatus;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LynkcoVehicleHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class LynkcoVehicleHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(LynkcoVehicleHandler.class);

    private LynkcoVehicleConfiguration config = new LynkcoVehicleConfiguration();
    private @Nullable ScheduledFuture<?> refreshJob;
    private @Nullable ScheduledFuture<?> instantUpdate;

    public LynkcoVehicleHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Command received: {} on channelID: {}", command, channelUID);

        if (CHANNEL_STATUS.equals(channelUID.getId()) || command instanceof RefreshType) {
            updateNow();
            return;
        }

        switch (channelUID.getId()) {
            case GROUP_CLIMATE_CONTROL + "#" + CHANNEL_PRECLIMATE:
                if (command instanceof OnOffType) {
                    actionClimate(command == OnOffType.ON, 2, 30);
                }
                break;

            case GROUP_ENGINE_CONTROL + "#" + CHANNEL_ENGINE_START:
                if (command instanceof OnOffType) {
                    actionEngine(command == OnOffType.ON, 15);
                }
                break;

            case GROUP_DOORS_CONTROL + "#" + CHANNEL_DOOR_LOCK:
                if (command instanceof OnOffType) {
                    actionDoors(command == OnOffType.ON);
                }
                break;

            case GROUP_LIGHTS_CONTROL + "#" + CHANNEL_LIGHT_FLASH:
                if (command instanceof OnOffType) {
                    actionHonkBlink(false, command == OnOffType.ON);
                }
                break;

            case GROUP_HORN_CONTROL + "#" + CHANNEL_HORN:
                if (command instanceof OnOffType) {
                    actionHonkBlink(command == OnOffType.ON, false);
                }
                break;

            case GROUP_HORN_CONTROL + "#" + CHANNEL_HONK_FLASH:
                if (command == OnOffType.ON) {
                    actionHonkBlink(true, true);
                }
                break;
        }
        updateNow();
    }

    @Override
    public void initialize() {
        scheduler.execute(() -> {
            Map<String, String> properties = refreshProperties();
            updateProperties(properties);
        });

        config = getConfigAs(LynkcoVehicleConfiguration.class);

        if (config.vin.isEmpty() || config.refresh < 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "VIN is missing.");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);
        startAutomaticRefresh();
    }

    @Override
    public void dispose() {
        stopAutomaticRefresh();
        updateStatus(ThingStatus.REMOVED);
    }

    public void actionClimate(boolean start, int climateLevel, int durationInMinutes) {
        LynkcoAPI api = getLynkcoAPI();
        if (api == null) {
            logger.debug("api is null");
            return;
        }
        try {
            if (start) {
                api.startClimate(config.vin, climateLevel, durationInMinutes);
            } else {
                api.stopClimate(config.vin);
            }
        } catch (LynkcoApiException e) {
            logger.warn("Failed to control climate: {}", e.getMessage());
        }
    }

    public void actionEngine(boolean start, int durationInMinutes) {
        LynkcoAPI api = getLynkcoAPI();
        if (api == null) {
            logger.debug("api is null");
            return;
        }
        try {
            if (start) {
                api.startEngine(config.vin, durationInMinutes);
            } else {
                api.stopEngine(config.vin);
            }
        } catch (LynkcoApiException e) {
            logger.warn("Failed to control engine: {}", e.getMessage());
        }
    }

    public void actionDoors(boolean lock) {
        LynkcoAPI api = getLynkcoAPI();
        if (api == null) {
            logger.debug("api is null");
            return;
        }
        try {
            if (lock) {
                api.lockDoors(config.vin);
            } else {
                api.unlockDoors(config.vin);
            }
        } catch (LynkcoApiException e) {
            logger.warn("Failed to control doors: {}", e.getMessage());
        }
    }

    public void actionHonkBlink(boolean honk, boolean blink) {
        LynkcoAPI api = getLynkcoAPI();
        if (api == null) {
            logger.debug("api is null");
            return;
        }
        try {
            if (honk && blink) {
                api.startHonkFlash(config.vin);
            } else if (honk) {
                api.startHonk(config.vin);
            } else if (blink) {
                api.startFlashLights(config.vin);
            }
        } catch (LynkcoApiException e) {
            logger.warn("Failed to control honk/blink: {}", e.getMessage());
        }
    }

    private void pollVehicleData() {
        try {
            LynkcoAPI api = getLynkcoAPI();
            if (api == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge is offline.");
                return;
            }
            LynkcoDTO vehicleData = new LynkcoDTO();
            api.fetchVehicleRecordData(vehicleData, config.vin);
            api.fetchVehicleShadowData(vehicleData, config.vin);
            update(vehicleData);
        } catch (Exception e) {
            logger.debug("Error polling data for VIN {}", config.vin, e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Error polling vehicle data.");
        }
    }

    private void startAutomaticRefresh() {
        ScheduledFuture<?> refreshJob = this.refreshJob;
        if (refreshJob == null || refreshJob.isCancelled()) {
            this.refreshJob = scheduler.scheduleWithFixedDelay(this::pollVehicleData, 0, config.refresh,
                    TimeUnit.MINUTES);
        }
    }

    private void stopAutomaticRefresh() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
            this.refreshJob = null;
        }
    }

    private synchronized void updateNow() {
        Future<?> localRef = instantUpdate;
        if (localRef == null || localRef.isDone()) {
            instantUpdate = scheduler.schedule(this::pollVehicleData, 0, TimeUnit.SECONDS);
        } else {
            logger.debug("Already waiting for scheduled refresh");
        }
    }

    private @Nullable LynkcoAPI getLynkcoAPI() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            LynkcoBridgeHandler handler = (LynkcoBridgeHandler) bridge.getHandler();
            if (handler != null) {
                return handler.getLynkcoAPI();
            }
        }
        return null;
    }

    private State getValue(String groupId, String channelId, LynkcoDTO dto) {
        switch (groupId) {
            case GROUP_DOORS:
                return getDoorsValue(channelId, dto.shadow.vls);
            case GROUP_DOORS_CONTROL:
                return getDoorsLocksValue(channelId, dto.shadow.vls);
            case GROUP_WINDOWS:
                return getWindowsValue(channelId, dto.shadow.vls);
            case GROUP_ODOMETER:
                return getOdometerValue(channelId, dto.record);
            case GROUP_FUEL:
                return getFuelValue(channelId, dto.record.fuel);
            case GROUP_POSITION:
                return getPositionValue(channelId, dto.record.position);
            case GROUP_BATTERY:
                return getBatteryStatusValue(channelId, dto.record.battery);
            case GROUP_CHARGING:
                return getChargingValue(channelId, dto.record.electricStatus);
            case GROUP_CLIMATE:
                return getClimateValue(channelId, dto.record.climate);
            case GROUP_CLIMATE_CONTROL:
                return getClimateControlValue(channelId, dto.record.climate);
            case GROUP_MAINTENANCE:
                return getMaintenanceValue(channelId, dto.shadow.vms.vehicleStateServiceMaintenance);
            case GROUP_BULBS:
                return getBulbValue(channelId, dto.shadow.vms.bulbStatus);
            case GROUP_SAFETY:
                return getSafetyValue(channelId, dto.shadow.vrs);
            case GROUP_TYRES:
                return getTyreValue(channelId, dto.shadow.vrs.vehicleTyresStatus);
            case GROUP_TRIP:
                return getTripValue(channelId, dto.record.trip);
            case GROUP_VEHICLE_STATUS:
                return getVehicleStatusValue(channelId, dto.shadow.bvs);
            case GROUP_ENGINE_CONTROL:
                return getEngineStatusValue(channelId, dto.shadow.bvs);
            case GROUP_SPEED:
                return getSpeedValue(channelId, dto.record.speed);
        }
        return UnDefType.UNDEF;
    }

    private void update(@Nullable LynkcoDTO dto) {
        if (dto != null) {
            getThing().getChannels().stream().map(Channel::getUID).filter(channelUID -> isLinked(channelUID))
                    .forEach(channelUID -> {
                        String groupId = channelUID.getGroupId();
                        String channelId = channelUID.getIdWithoutGroup();
                        if (groupId != null) {
                            State state = getValue(groupId, channelId, dto);
                            logger.trace("Channel: {}, State: {}", channelUID, state);
                            updateState(channelUID, state);
                        }
                    });

            if (!dto.shadow.evs.chargerStatusData.updatedAt.isEmpty()) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "No recent data from vehicle");
            }
        }
    }

    private State getDoorsValue(String channelId, Vls vls) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(vls.doorLocksUpdatedAt);
            case DOOR_DRIVER:
                return "DOOR_OPEN_STATUS_CLOSED".equals(vls.doorOpenStatusDriver) ? OpenClosedType.CLOSED
                        : OpenClosedType.OPEN;
            case DOOR_PASSENGER:
                return "DOOR_OPEN_STATUS_CLOSED".equals(vls.doorOpenStatusPassenger) ? OpenClosedType.CLOSED
                        : OpenClosedType.OPEN;
            case DOOR_REAR_LEFT:
                return "DOOR_OPEN_STATUS_CLOSED".equals(vls.doorOpenStatusDriverRear) ? OpenClosedType.CLOSED
                        : OpenClosedType.OPEN;
            case DOOR_REAR_RIGHT:
                return "DOOR_OPEN_STATUS_CLOSED".equals(vls.doorOpenStatusPassengerRear) ? OpenClosedType.CLOSED
                        : OpenClosedType.OPEN;
            case HOOD:
                return "ENGINE_HOOD_STATUS_CLOSED".equals(vls.engineHoodStatus) ? OpenClosedType.CLOSED
                        : OpenClosedType.OPEN;
            case TRUNK:
                return "TRUNK_OPEN_STATUS_CLOSED".equals(vls.trunkOpenStatus) ? OpenClosedType.CLOSED
                        : OpenClosedType.OPEN;
            case TANK_FLAP:
                return "TANK_FLAP_CLOSED".equals(vls.tankFlapStatus) ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
            case LOCKS_STATUS:
                return "DOOR_LOCKS_STATUS_LOCKED".equals(vls.doorLocksStatus) ? OnOffType.ON : OnOffType.OFF;
            case ALARM_STATUS:
                return "ALARM_NOT_ACTIVATED".equals(vls.alarmStatusData) ? OnOffType.OFF : OnOffType.ON;
        }
        return UnDefType.UNDEF;
    }

    private State getDoorsLocksValue(String channelId, Vls vls) {
        switch (channelId) {
            case CHANNEL_DOOR_LOCK:
                return "DOOR_LOCKS_STATUS_LOCKED".equals(vls.doorLocksStatus) ? OnOffType.ON : OnOffType.OFF;
        }
        return UnDefType.UNDEF;
    }

    private State getWindowsValue(String channelId, Vls vls) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(vls.windowStatusDriverUpdatedAt);
            case WINDOW_DRIVER:
                return "WINDOW_CLOSED".equals(vls.windowStatusDriver) ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
            case WINDOW_PASSENGER:
                return "WINDOW_CLOSED".equals(vls.windowStatusPassenger) ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
            case WINDOW_REAR_LEFT:
                return "WINDOW_CLOSED".equals(vls.windowStatusDriverRear) ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
            case WINDOW_REAR_RIGHT:
                return "WINDOW_CLOSED".equals(vls.windowStatusPassengerRear) ? OpenClosedType.CLOSED
                        : OpenClosedType.OPEN;
            case SUNROOF:
                return "SUNROOF_CLOSED".equals(vls.sunroofOpenStatus) ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
        }
        return UnDefType.UNDEF;
    }

    private State getOdometerValue(String channelId, RecordDTO record) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(record.odometer.vehicleUpdatedAt);
            case ODOMETER_KM:
                return new QuantityType<>(record.odometer.odometerKm, KILO(METRE));
            case TRIP_METER_1:
                return new QuantityType<>(record.trip.tripMeter, KILO(METRE));
            case TRIP_METER_2:
                return new QuantityType<>(record.trip.tripMeter2, KILO(METRE));
        }
        return UnDefType.UNDEF;
    }

    private State getFuelValue(String channelId, Fuel fuel) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(fuel.vehicleUpdatedAt);
            case FUEL_LEVEL:
                return new QuantityType<>(fuel.level, Units.PERCENT);
            case FUEL_LEVEL_STATUS:
                return new DecimalType(fuel.levelStatus);
            case FUEL_TYPE:
                return new StringType(fuel.fuelType);
            case FUEL_RANGE:
                return new QuantityType<>(fuel.distanceToEmpty, KILO(METRE));
            case FUEL_CONSUMPTION:
                return fuel.averageConsumption != UNDEFINED ? new DecimalType(fuel.averageConsumption / 10)
                        : UnDefType.UNDEF;
            case FUEL_CONSUMPTION_LAST:
                return fuel.averageConsumptionLatestDrivingCycle != UNDEFINED
                        ? new DecimalType(fuel.averageConsumptionLatestDrivingCycle / 10)
                        : UnDefType.UNDEF;
        }
        return UnDefType.UNDEF;
    }

    private State getPositionValue(String channelId, Position position) {
        switch (channelId) {
            case LOCATION:
                return new PointType(position.latitude + "," + position.longitude + "," + position.altitude);
            case LOCATION_TRUSTED:
                return OnOffType.from(position.canBeTrusted);
            case LOCATION_UPDATED:
                ZonedDateTime zdt = ZonedDateTime.parse(position.vehicleUpdatedAt);
                return new DateTimeType(zdt);
        }
        return UnDefType.UNDEF;
    }

    private State getBatteryStatusValue(String channelId, Battery battery) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(battery.vehicleUpdatedAt);
            case BATTERY_CHARGE:
                return new StringType(battery.charge);
            case BATTERY_CHARGE_LEVEL:
                return new QuantityType<>(battery.chargeLevel, Units.PERCENT);
            case BATTERY_ENERGY:
                return new QuantityType<>(battery.energyLevel, Units.PERCENT);
            case BATTERY_HEALTH:
                return new QuantityType<>(battery.health, Units.PERCENT);
            case BATTERY_POWER:
                return new QuantityType<>(battery.powerLevel, Units.PERCENT);
            case BATTERY_VOLTAGE:
                return new QuantityType<>(battery.voltage, Units.VOLT);
        }
        return UnDefType.UNDEF;
    }

    private State getChargingValue(String channelId, ElectricStatus electricStatus) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(electricStatus.vehicleUpdatedAt);
            case CHARGING_LEVEL:
                return new QuantityType<>(electricStatus.chargeLevel, Units.PERCENT);
            case CHARGING_RANGE:
                return new QuantityType<>(electricStatus.distanceToEmptyOnBatteryOnly, KILO(METRE));
            case CHARGING_TIME:
                return new QuantityType<>(electricStatus.timeToFullyCharged, Units.MINUTE);
        }
        return UnDefType.UNDEF;
    }

    private State getClimateValue(String channelId, Climate climate) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(climate.vehicleUpdatedAt);
            case TEMPERATURE_EXTERIOR:
                return new QuantityType<>(climate.exteriorTemp.temp, CELSIUS);
            case TEMPERATURE_INTERIOR:
                return new QuantityType<>(climate.interiorTemp.temp, CELSIUS);
            case PRECLIMATE_ACTIVE:
                return OnOffType.from(climate.preClimateActive);
        }
        return UnDefType.UNDEF;
    }

    private State getClimateControlValue(String channelId, Climate climate) {
        switch (channelId) {
            case CHANNEL_PRECLIMATE:
                return OnOffType.from(climate.preClimateActive);
        }
        return UnDefType.UNDEF;
    }

    private State getBulbValue(String channelId, BulbStatus status) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(status.updatedAt);
            case DAYTIME_RUNNING:
                return new StringType(status.dayRunningAny);
            case FOG_FRONT:
                return new StringType(status.fogFrontAny);
            case FOG_REAR:
                return new StringType(status.fogRearAny);
            case HIGH_BEAM:
                return new StringType(status.highBeamAny);
            case HIGH_BEAM_LEFT:
                return new StringType(status.highBeamLeft);
            case HIGH_BEAM_RIGHT:
                return new StringType(status.highBeamRight);
            case TURN_LEFT:
                return new StringType(status.leftTurnAny);
            case LOW_BEAM:
                return new StringType(status.lowBeamAny);
            case LOW_BEAM_LEFT:
                return new StringType(status.lowBeamLeft);
            case LOW_BEAM_RIGHT:
                return new StringType(status.lowBeamRight);
            case POSITION_LIGHTS:
                return new StringType(status.positionAny);
            case TURN_RIGHT:
                return new StringType(status.rightTurnAny);
            case STOP_LIGHTS:
                return new StringType(status.stopAny);
        }
        return UnDefType.UNDEF;
    }

    private State getSafetyValue(String channelId, Vrs status) {
        switch (channelId) {
            case AIRBAG_UPDATED:
                return new DateTimeType(status.airbagStatus.updatedAt);
            case AIRBAG_STATUS:
                return new StringType(status.airbagStatus.srsStatus);
            case SEATBELT_UPDATED:
                return new DateTimeType(status.seatBeltStatus.updatedAt);
            case SEATBELT_DRIVER:
                return OnOffType.from(status.seatBeltStatus.driver.fastened);
            case SEATBELT_PASSENGER:
                return OnOffType.from(status.seatBeltStatus.passenger.fastened);
            case SEATBELT_REAR_LEFT:
                return OnOffType.from(status.seatBeltStatus.driverRear.fastened);
            case SEATBELT_REAR_MIDDLE:
                return OnOffType.from(status.seatBeltStatus.midRear.fastened);
            case SEATBELT_REAR_RIGHT:
                return OnOffType.from(status.seatBeltStatus.passengerRear.fastened);
        }
        return UnDefType.UNDEF;
    }

    private State getTyreValue(String channelId, VehicleTyresStatus status) {
        switch (channelId) {
            case TYRE_UPDATED:
                return new DateTimeType(status.updatedAt);
            case TYRE_FRONT_LEFT:
                return new StringType(status.driverFrontTyre.pressure);
            case TYRE_FRONT_RIGHT:
                return new StringType(status.passengerFrontTyre.pressure);
            case TYRE_REAR_LEFT:
                return new StringType(status.driverRearTyre.pressure);
            case TYRE_REAR_RIGHT:
                return new StringType(status.passengerRearTyre.pressure);
        }
        return UnDefType.UNDEF;
    }

    private State getMaintenanceValue(String channelId, VehicleStateServiceMaintenance status) {
        switch (channelId) {
            case BRAKE_FLUID:
                return new StringType(status.brakeFluidLevelStatus);
            case COOLANT:
                return new StringType(status.coolantLevelStatus);
            case ENGINE_OIL_LEVEL:
                return new StringType(status.engineOilLevelStatus);
            case ENGINE_OIL_PRESSURE:
                return new StringType(status.engineOilPressureStatus);
            case SERVICE_WARNING:
                return new StringType(status.serviceWarningStatus);
            case WASHER_FLUID:
                return new StringType(status.washerFluidLevelStatus);
        }
        return UnDefType.UNDEF;
    }

    private State getTripValue(String channelId, Trip trip) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(trip.vehicleUpdatedAt);
            case CHANNEL_AVG_SPEED:
                return new QuantityType<>(trip.avgSpeed, KILOMETRE_PER_HOUR);
            case CHANNEL_LAST_TRIP_SPEED:
                return new QuantityType<>(trip.avgSpeedLastDrivingCycle, KILOMETRE_PER_HOUR);
            case CHANNEL_TRIP_METER:
                return new QuantityType<>(trip.tripMeter, KILO(METRE));
            case CHANNEL_TRIP_METER_2:
                return new QuantityType<>(trip.tripMeter2, KILO(METRE));
        }
        return UnDefType.UNDEF;
    }

    private State getVehicleStatusValue(String channelId, Bvs bvs) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(bvs.engineStatusUpdatedAt);
            case CHANNEL_ENGINE_STATUS:
                return new StringType(bvs.engineStatus);
            case CHANNEL_KEY_STATUS:
                return new StringType(bvs.keyStatus);
            case CHANNEL_USAGE_MODE:
                return new StringType(bvs.usageMode);
        }
        return UnDefType.UNDEF;
    }

    private State getEngineStatusValue(String channelId, Bvs bvs) {
        switch (channelId) {
            case CHANNEL_ENGINE_START:
                return "ENGINE_OFF".equalsIgnoreCase(bvs.engineStatus) ? OnOffType.OFF : OnOffType.ON;
        }
        return UnDefType.UNDEF;
    }

    private State getSpeedValue(String channelId, Speed speed) {
        switch (channelId) {
            case CHANNEL_TIMESTAMP:
                return new DateTimeType(speed.vehicleUpdatedAt);
            case CHANNEL_SPEED:
                return new QuantityType<>(speed.speed, KILOMETRE_PER_HOUR);
            case CHANNEL_DIRECTION:
                return new QuantityType<>(speed.direction, Units.DEGREE_ANGLE);
        }
        return UnDefType.UNDEF;
    }

    private Map<String, String> refreshProperties() {
        Map<String, String> properties = new HashMap<>();
        Bridge bridge = getBridge();
        if (bridge != null) {
            LynkcoBridgeHandler bridgeHandler = (LynkcoBridgeHandler) bridge.getHandler();
            if (bridgeHandler != null) {
                LynkcoDTO dto = bridgeHandler.getLynkcoThings().get(config.vin);
                if (dto != null) {
                    // properties.put(Thing.v, dto.getApplianceInfo().getBrand());
                }

            }
        }
        return properties;
    }
}
