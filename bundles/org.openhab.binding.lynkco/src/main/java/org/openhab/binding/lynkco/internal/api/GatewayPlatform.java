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
package org.openhab.binding.lynkco.internal.api;

import static org.openhab.binding.lynkco.internal.LynkcoBindingConstants.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.lynkco.internal.dto.GatewayDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Evs.ChargerStatusData.ChargerConnectionStatus;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.Evs.ChargerStatusData.ChargerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

/**
 * The {@link GatewayPlatform} implements {@link VehiclePlatform} for the modern signed mobile-app
 * gateway used by Lynk&Co 01 (2025), 02 and 08.
 *
 * Every authenticated request is signed: a per-request {@code X-NONCE} (UUID) is combined with the
 * request path (relative to the signature base) and the {@code snowflakeId} claim from the access
 * token to form {@code X-SIGNATURE = SHA-256(snowflakeId + nonce + path)} (hex). The OAuth access
 * token is used directly as the bearer token (no {@code cccToken}), and the device session must be
 * validated via {@code iamservice/validate-session} before data/command calls.
 *
 * The separate gateway state endpoints (vehicle_data, vehicle_metadata, location_state,
 * charge_state, climate_state, doors_windows_state, fuel_state) are fetched and mapped into the
 * shared {@link LynkcoDTO} so the vehicle handler and its channels are reused across platforms.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class GatewayPlatform implements VehiclePlatform {

    // Base URLs whose prefix is stripped when computing the signature path (the server signs the
    // path relative to these bases). Other URLs (e.g. iamservice) are signed with their full path.
    private static final String[] SIGNATURE_BASES = { GATEWAY_LOVE_BASE + "/", GATEWAY_COMMAND_BASE + "/" };

    private final Logger logger = LoggerFactory.getLogger(GatewayPlatform.class);
    private final Gson gson;
    private final HttpClient httpClient;
    private final LynkcoTokenManager tokenManager;

    // The access token for which the device session was last validated; re-validated on token change.
    private @Nullable String registeredToken;

    public GatewayPlatform(Gson gson, HttpClient httpClient, LynkcoTokenManager tokenManager) {
        this.gson = gson;
        this.httpClient = httpClient;
        this.tokenManager = tokenManager;
    }

    @Override
    public LynkcoDTO fetchVehicleData(String vin) throws LynkcoApiException {
        ensureDeviceRegistered();
        String base = GATEWAY_LOVE_BASE + "/vehicle/" + vin;
        GatewayDTO.VehicleData summary = getDto(base + "/vehicle_data", GatewayDTO.VehicleData.class);
        GatewayDTO.Metadata metadata = getDto(base + "/vehicle_metadata", GatewayDTO.Metadata.class);
        GatewayDTO.LocationState location = getDto(base + "/location_state", GatewayDTO.LocationState.class);
        GatewayDTO.ChargeState charge = getDto(base + "/charge_state", GatewayDTO.ChargeState.class);
        GatewayDTO.ClimateState climate = getDto(base + "/climate_state", GatewayDTO.ClimateState.class);
        GatewayDTO.DoorsWindowsState doors = getDto(base + "/doors_windows_state", GatewayDTO.DoorsWindowsState.class);
        GatewayDTO.FuelStateResponse fuel = getDto(base + "/fuel_state", GatewayDTO.FuelStateResponse.class);

        if (summary == null && charge == null && doors == null && climate == null) {
            throw new LynkcoApiException("No data received from gateway", LynkcoApiException.ErrorType.API_ERROR);
        }
        return mapToLynkcoDTO(summary, metadata, location, charge, climate, doors, fuel);
    }

    private <T> @Nullable T getDto(String url, Class<T> clazz) {
        try {
            return gson.fromJson(getJson(url), clazz);
        } catch (LynkcoApiException e) {
            logger.debug("Gateway fetch {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Map the gateway state endpoints into the shared {@link LynkcoDTO} so the vehicle handler and
     * its existing channels can be reused. Gateway values (e.g. door "CLOSED") are translated into
     * the enum strings the handler expects (e.g. "DOOR_OPEN_STATUS_CLOSED").
     */
    private LynkcoDTO mapToLynkcoDTO(GatewayDTO.@Nullable VehicleData summary, GatewayDTO.@Nullable Metadata metadata,
            GatewayDTO.@Nullable LocationState location, GatewayDTO.@Nullable ChargeState charge,
            GatewayDTO.@Nullable ClimateState climate, GatewayDTO.@Nullable DoorsWindowsState doors,
            GatewayDTO.@Nullable FuelStateResponse fuel) {
        LynkcoDTO dto = new LynkcoDTO();
        String now = Instant.now().toString();

        // Default every timestamp the handler may read to a valid value; setting the charger
        // updatedAt also drives the ONLINE status check in the handler.
        dto.shadow.evs.chargerStatusData.updatedAt = now;
        dto.shadow.vls.doorLocksUpdatedAt = now;
        dto.shadow.vls.windowStatusDriverUpdatedAt = now;
        dto.shadow.vms.bulbStatus.updatedAt = now;
        dto.shadow.vrs.airbagStatus.updatedAt = now;
        dto.shadow.vrs.seatBeltStatus.updatedAt = now;
        dto.shadow.vrs.vehicleTyresStatus.updatedAt = now;
        dto.shadow.bvs.engineStatusUpdatedAt = now;
        dto.record.odometer.vehicleUpdatedAt = now;
        dto.record.fuel.vehicleUpdatedAt = now;
        dto.record.battery.vehicleUpdatedAt = now;
        dto.record.electricStatus.vehicleUpdatedAt = now;
        dto.record.climate.vehicleUpdatedAt = now;
        dto.record.trip.vehicleUpdatedAt = now;
        dto.record.speed.vehicleUpdatedAt = now;
        dto.record.position.vehicleUpdatedAt = now;
        dto.shadow.vls.alarmStatusData = "ALARM_NOT_ACTIVATED";
        dto.record.fuel.averageConsumptionLatestDrivingCycle = UNDEFINED;

        if (summary != null) {
            if (summary.centralLock != null) {
                dto.shadow.vls.doorLocksStatus = "LOCKED".equals(summary.centralLock.status)
                        ? "DOOR_LOCKS_STATUS_LOCKED"
                        : "DOOR_LOCKS_STATUS_UNLOCKED";
            }
            if (summary.climateControl != null) {
                dto.shadow.bvs.engineStatus = summary.climateControl.engineStatus;
            }
        }

        if (doors != null) {
            dto.shadow.vls.doorLocksUpdatedAt = orNow(doors.updatedAt, now);
            dto.shadow.vls.windowStatusDriverUpdatedAt = orNow(doors.updatedAt, now);
            dto.shadow.vls.doorOpenStatusDriver = contact(doors.doorFrontLeftStatus, "DOOR_OPEN_STATUS_CLOSED",
                    "DOOR_OPEN_STATUS_OPEN");
            dto.shadow.vls.doorOpenStatusPassenger = contact(doors.doorFrontRightStatus, "DOOR_OPEN_STATUS_CLOSED",
                    "DOOR_OPEN_STATUS_OPEN");
            dto.shadow.vls.doorOpenStatusDriverRear = contact(doors.doorRearLeftStatus, "DOOR_OPEN_STATUS_CLOSED",
                    "DOOR_OPEN_STATUS_OPEN");
            dto.shadow.vls.doorOpenStatusPassengerRear = contact(doors.doorRearRightStatus, "DOOR_OPEN_STATUS_CLOSED",
                    "DOOR_OPEN_STATUS_OPEN");
            dto.shadow.vls.engineHoodStatus = contact(doors.hoodStatus, "ENGINE_HOOD_STATUS_CLOSED",
                    "ENGINE_HOOD_STATUS_OPEN");
            dto.shadow.vls.trunkOpenStatus = contact(doors.trunkStatus, "TRUNK_OPEN_STATUS_CLOSED",
                    "TRUNK_OPEN_STATUS_OPEN");
            dto.shadow.vls.tankFlapStatus = contact(doors.tankFlapStatus, "TANK_FLAP_CLOSED", "TANK_FLAP_OPEN");
            dto.shadow.vls.windowStatusDriver = contact(doors.windowFrontLeftStatus, "WINDOW_CLOSED", "WINDOW_OPEN");
            dto.shadow.vls.windowStatusPassenger = contact(doors.windowFrontRightStatus, "WINDOW_CLOSED",
                    "WINDOW_OPEN");
            dto.shadow.vls.windowStatusDriverRear = contact(doors.windowRearLeftStatus, "WINDOW_CLOSED", "WINDOW_OPEN");
            dto.shadow.vls.windowStatusPassengerRear = contact(doors.windowRearRightStatus, "WINDOW_CLOSED",
                    "WINDOW_OPEN");
            dto.shadow.vls.sunroofOpenStatus = contact(doors.sunroofStatus, "SUNROOF_CLOSED", "SUNROOF_OPEN");
        }

        if (charge != null && charge.batteryState != null) {
            GatewayDTO.BatteryState bs = charge.batteryState;
            int soc = (int) Math.round(bs.stateOfCharge * 100);
            dto.record.battery.chargeLevel = soc;
            dto.record.battery.vehicleUpdatedAt = orNow(bs.updatedAt, now);
            dto.record.electricStatus.chargeLevel = soc;
            dto.record.electricStatus.distanceToEmptyOnBatteryOnly = bs.remainingRange != null
                    ? bs.remainingRange.intValue()
                    : 0;
            dto.record.electricStatus.timeToFullyCharged = bs.remainingChargingTime != null
                    ? bs.remainingChargingTime.intValue()
                    : 0;
            dto.record.electricStatus.vehicleUpdatedAt = orNow(bs.updatedAt, now);
            dto.shadow.evs.chargerStatusData.updatedAt = orNow(bs.updatedAt, now);
            boolean charging = "CHARGING".equalsIgnoreCase(bs.status);
            boolean connected = bs.status != null && bs.status.toUpperCase().contains("CONNECT")
                    && !"DISCONNECTED".equalsIgnoreCase(bs.status);
            dto.shadow.evs.chargerStatusData.chargerState = charging ? ChargerState.CHARGER_STATE_CHARGN
                    : ChargerState.CHARGER_STATE_IDLE;
            dto.shadow.evs.chargerStatusData.chargerConnectionStatus = connected
                    ? ChargerConnectionStatus.CHARGER_CONNECTION_CONNECTED_WITH_POWER
                    : ChargerConnectionStatus.CHARGER_CONNECTION_DISCONNECTED;
            if (bs.chargeLimit != null) {
                dto.record.electricStatus.chargeLimit = bs.chargeLimit.value;
            }
        }

        if (fuel != null && fuel.fuelState != null) {
            double tank = metadata != null && metadata.fuelInfo != null && metadata.fuelInfo.tankCapacity > 0
                    ? metadata.fuelInfo.tankCapacity
                    : 100.0;
            dto.record.fuel.level = fuel.fuelState.percentageOfRemainingFuel * tank;
            dto.record.fuel.distanceToEmpty = (int) Math.round(fuel.fuelState.remainingRange);
            dto.record.fuel.averageConsumption = fuel.fuelState.averageConsumption * 10; // handler divides by 10
            dto.record.fuel.vehicleUpdatedAt = orNow(fuel.fuelState.updatedAt, now);
        }
        if (metadata != null && metadata.fuelInfo != null) {
            dto.record.fuel.fuelType = metadata.fuelInfo.fuelType;
        }

        if (climate != null) {
            dto.record.climate.interiorTemp.temp = climate.interiorTemperature;
            dto.record.climate.preClimateActive = "ACTIVE".equalsIgnoreCase(climate.status);
            dto.record.climate.vehicleUpdatedAt = orNow(climate.updatedAt, now);
            if (!climate.engineStatus.isEmpty()) {
                dto.shadow.bvs.engineStatus = climate.engineStatus;
            }
            if (climate.heaters != null) {
                dto.heaters.seatDriver = heaterActive(climate.heaters.frontLeftSeat);
                dto.heaters.seatPassenger = heaterActive(climate.heaters.frontRightSeat);
                dto.heaters.seatRearLeft = heaterActive(climate.heaters.rearLeftSeat);
                dto.heaters.seatRearRight = heaterActive(climate.heaters.rearRightSeat);
                dto.heaters.steeringWheel = heaterActive(climate.heaters.steeringWheel);
            }
        }

        if (location != null && location.vehicleLocation != null && location.vehicleLocation.coordinates != null) {
            GatewayDTO.VehicleLocation vl = location.vehicleLocation;
            dto.record.position.latitude = vl.coordinates.latitude;
            dto.record.position.longitude = vl.coordinates.longitude;
            dto.record.position.canBeTrusted = "AVAILABLE".equalsIgnoreCase(vl.status);
            dto.record.position.vehicleUpdatedAt = orNow(vl.updatedAt, now);
        }

        if (metadata != null && metadata.vehicle != null) {
            dto.record.odometer.odometerKm = metadata.vehicle.odometer;
            dto.model = metadata.vehicle.model;
            dto.propulsion = metadata.vehicle.propulsionType;
        }

        return dto;
    }

    private static boolean heaterActive(GatewayDTO.@Nullable Heater heater) {
        return heater != null && "ACTIVE".equalsIgnoreCase(heater.status);
    }

    private static String contact(String gatewayStatus, String closedToken, String openToken) {
        return "CLOSED".equalsIgnoreCase(gatewayStatus) ? closedToken : openToken;
    }

    private static String orNow(@Nullable String value, String now) {
        return value == null || value.isEmpty() ? now : value;
    }

    // --- Climate / ventilation --------------------------------------------------------------

    @Override
    public void startClimate(String vin, int climateLevel, int durationInMinutes) throws LynkcoApiException {
        // The gateway uses auto_conditioning_start; climateLevel maps to the heat level.
        // TODO confirm the exact request body/params against captured app traffic.
        String body = "{\"level\":" + climateLevel + ",\"durationInMinutes\":" + durationInMinutes + "}";
        postCommand(vin, "auto_conditioning_start", body);
    }

    @Override
    public void stopClimate(String vin) throws LynkcoApiException {
        postCommand(vin, "auto_conditioning_stop", "{}");
    }

    @Override
    public void startVentilation(String vin) throws LynkcoApiException {
        postCommand(vin, "ventilate_start", "{}");
    }

    @Override
    public void stopVentilation(String vin) throws LynkcoApiException {
        postCommand(vin, "ventilate_stop", "{}");
    }

    // --- Engine: not exposed by the gateway -------------------------------------------------

    @Override
    public void startEngine(String vin, int durationInMinutes) throws LynkcoApiException {
        throw new LynkcoApiException("Engine start is not supported on the gateway platform",
                LynkcoApiException.ErrorType.UNSUPPORTED);
    }

    @Override
    public void stopEngine(String vin) throws LynkcoApiException {
        throw new LynkcoApiException("Engine stop is not supported on the gateway platform",
                LynkcoApiException.ErrorType.UNSUPPORTED);
    }

    // --- Doors ------------------------------------------------------------------------------

    @Override
    public void lockDoors(String vin) throws LynkcoApiException {
        postCommand(vin, "door_lock", "{}");
    }

    @Override
    public void unlockDoors(String vin) throws LynkcoApiException {
        postCommand(vin, "door_unlock", "{}");
    }

    // --- Horn / lights ----------------------------------------------------------------------

    @Override
    public void startFlashLights(String vin) throws LynkcoApiException {
        postCommand(vin, "flash_lights", "{}");
    }

    @Override
    public void stopFlashLights(String vin) throws LynkcoApiException {
        // The gateway flash command is fire-and-forget; there is no explicit stop.
        logger.debug("stopFlashLights is a no-op on the gateway platform");
    }

    @Override
    public void startHonk(String vin) throws LynkcoApiException {
        postCommand(vin, "honk_horn", "{}");
    }

    @Override
    public void stopHonk(String vin) throws LynkcoApiException {
        logger.debug("stopHonk is a no-op on the gateway platform");
    }

    @Override
    public void startHonkFlash(String vin) throws LynkcoApiException {
        postCommand(vin, "honk_horn", "{}");
        postCommand(vin, "flash_lights", "{}");
    }

    // --- Gateway-only features --------------------------------------------------------------

    @Override
    public void setChargeLimit(String vin, int percent) throws LynkcoApiException {
        postCommand(vin, "set_charge_limit?percent=" + percent, "{}");
    }

    @Override
    public void startHeaters(String vin, List<String> heaters) throws LynkcoApiException {
        postCommand(vin, "start_heaters", toJsonArray(heaters));
    }

    @Override
    public void stopHeaters(String vin, List<String> heaters) throws LynkcoApiException {
        postCommand(vin, "stop_heaters", toJsonArray(heaters));
    }

    @Override
    public void openSunroof(String vin) throws LynkcoApiException {
        postCommand(vin, "sun_roof_open", "{}");
    }

    @Override
    public void closeSunroof(String vin) throws LynkcoApiException {
        postCommand(vin, "sun_roof_close", "{}");
    }

    @Override
    public void unlockGlovebox(String vin) throws LynkcoApiException {
        postCommand(vin, "glovebox_unlock", "{}");
    }

    // --- HTTP / signing helpers -------------------------------------------------------------

    private String toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array.toString();
    }

    private void postCommand(String vin, String command, String body) throws LynkcoApiException {
        ensureDeviceRegistered();
        String url = GATEWAY_COMMAND_BASE + "/vehicle/" + vin + "/command/" + command;
        try {
            Request request = signedRequest(HttpMethod.POST, url, body);
            ContentResponse response = request.send();
            if (response.getStatus() == 200) {
                logger.debug("Successfully sent gateway command {}", command);
            } else {
                logger.debug("Gateway command {} failed, HTTP status: {}, response: {}", command, response.getStatus(),
                        response.getContentAsString());
                throw new LynkcoApiException("Gateway command " + command + " failed: " + response.getStatus(),
                        response.getStatus() == 401 ? LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED
                                : LynkcoApiException.ErrorType.API_ERROR);
            }
        } catch (LynkcoApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LynkcoApiException("Network error: " + e.getMessage(),
                    LynkcoApiException.ErrorType.NETWORK_ERROR);
        }
    }

    private String getJson(String url) throws LynkcoApiException {
        try {
            Request request = signedRequest(HttpMethod.GET, url, null);
            ContentResponse response = request.send();
            if (response.getStatus() == 200) {
                String json = response.getContentAsString();
                logger.trace("Gateway response from {}: {}", url, json);
                return json;
            } else if (response.getStatus() == 401) {
                throw new LynkcoApiException("Authentication error: " + response.getContentAsString(),
                        LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED);
            } else {
                throw new LynkcoApiException("API error: " + response.getContentAsString(),
                        LynkcoApiException.ErrorType.API_ERROR);
            }
        } catch (LynkcoApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LynkcoApiException("Network error: " + e.getMessage(),
                    LynkcoApiException.ErrorType.NETWORK_ERROR);
        }
    }

    /**
     * Build a signed gateway request with all required headers.
     *
     * @param method the HTTP method
     * @param url the full request URL
     * @param body the JSON request body, or null for a body-less request
     * @return the prepared {@link Request}
     */
    private Request signedRequest(HttpMethod method, String url, @Nullable String body) throws LynkcoApiException {
        String accessToken = tokenManager.getAccessToken();
        String snowflakeId = tokenManager.getSnowflakeId();
        String customerNumber = tokenManager.getCustomerNumber();
        if (accessToken == null || snowflakeId == null) {
            throw new LynkcoApiException("Missing access token or snowflakeId for gateway request",
                    LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED);
        }

        String nonce = UUID.randomUUID().toString();
        String path = signaturePath(url);
        String signature = sha256Hex(snowflakeId + nonce + path);

        // Clear cookies accumulated during the B2C login flow; otherwise Jetty attaches them as a
        // Cookie header that the gateway rejects with "HTTP 400 invalid header".
        httpClient.getCookieStore().removeAll();

        Request request = httpClient.newRequest(url).method(method).agent(GATEWAY_USER_AGENT)
                .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken).header("X-Auth-Token", accessToken)
                .header("X-DeviceId", tokenManager.getDeviceUuid())
                .header("X-CustomerNumber", customerNumber != null ? customerNumber : "")
                .header("X-CustomerId", snowflakeId).header("api-version", "1").header("X-NONCE", nonce)
                .header("X-SIGNATURE-VERSION", GATEWAY_SIGNATURE_VERSION).header("X-SIGNATURE", signature)
                .header("X-App-Name", GATEWAY_APP_NAME).header("X-App-Version", GATEWAY_APP_VERSION)
                .header("X-App-Build-Number", GATEWAY_APP_BUILD_NUMBER)
                .header("X-Device-OS-Version", GATEWAY_DEVICE_OS_VERSION).header("X-Device-Model", GATEWAY_DEVICE_MODEL)
                .header("X-Device-Language", GATEWAY_DEVICE_LANGUAGE).header(HttpHeader.ACCEPT, "application/json")
                .header(HttpHeader.CONTENT_TYPE, "application/json");
        if (body != null) {
            request.content(new StringContentProvider(body));
        }
        return request;
    }

    /**
     * Compute the path used for signing: relative to a known signature base URL when applicable
     * (e.g. {@code /vehicle/{vin}/vehicle_data}), otherwise the full URL path.
     */
    private String signaturePath(String url) {
        for (String base : SIGNATURE_BASES) {
            if (url.startsWith(base)) {
                return "/" + url.substring(base.length()).replaceFirst("^/+", "");
            }
        }
        return URI.create(url).getRawPath();
    }

    /**
     * Register/validate the device session for the current access token. The gateway rejects data
     * and command calls with HTTP 403 until the device has been validated via the IAM service. This
     * is a no-op once validated for the current token, and re-runs after a token refresh.
     */
    private void ensureDeviceRegistered() throws LynkcoApiException {
        String accessToken = tokenManager.getAccessToken();
        if (accessToken == null) {
            throw new LynkcoApiException("Missing access token for gateway request",
                    LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED);
        }
        if (accessToken.equals(registeredToken)) {
            return;
        }
        String body = "{\"deviceUuid\":\"" + tokenManager.getDeviceUuid() + "\",\"isLogin\":true}";
        try {
            Request request = signedRequest(HttpMethod.POST, GATEWAY_IAM_BASE + "/validate-session", body);
            ContentResponse response = request.send();
            if (response.getStatus() == 200) {
                registeredToken = accessToken;
                logger.debug("Gateway device session validated");
            } else {
                throw new LynkcoApiException("Device session validation failed: " + response.getStatus(),
                        response.getStatus() == 401 ? LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED
                                : LynkcoApiException.ErrorType.API_ERROR);
            }
        } catch (LynkcoApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LynkcoApiException("Network error during device validation: " + e.getMessage(),
                    LynkcoApiException.ErrorType.NETWORK_ERROR);
        }
    }

    private String sha256Hex(String input) throws LynkcoApiException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new LynkcoApiException("Failed to compute signature: " + e.getMessage(),
                    LynkcoApiException.ErrorType.UNKNOWN_ERROR);
        }
    }
}
