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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.RecordDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.ShadowDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The {@link CccPlatform} implements {@link VehiclePlatform} for the legacy
 * "connectedcar.cloud" (CCC) backend used by pre-2025 Lynk&Co 01 vehicles. It authenticates with
 * the {@code cccToken} and uses the direct vehicle-data and remote-vehicle-control (RVC) endpoints.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class CccPlatform implements VehiclePlatform {

    // Direct CCC vehicle-data API
    private static final String VEHICLE_BASE_URL = "https://vehicle-data-tls.aion.connectedcar.cloud/api/v1/vds/vehicles/";
    // Direct remote vehicle control (RVC) endpoint for commands
    private static final String VEHICLE_CONTROL_URL = "https://remote-vehicle-control-tls.aion.connectedcar.cloud/api/v1/rvc/vehicles/%s/remotecontrol";

    private final Logger logger = LoggerFactory.getLogger(CccPlatform.class);
    private final Gson gson;
    private final HttpClient httpClient;
    private final LynkcoTokenManager tokenManager;

    public CccPlatform(Gson gson, HttpClient httpClient, LynkcoTokenManager tokenManager) {
        this.gson = gson;
        this.httpClient = httpClient;
        this.tokenManager = tokenManager;
    }

    @Override
    public LynkcoDTO fetchVehicleData(String vin) throws LynkcoApiException {
        LynkcoDTO vehicleData = new LynkcoDTO();
        vehicleData.shadow = fetchData(String.format("%s%s/data/shadow", VEHICLE_BASE_URL, vin), ShadowDTO.class);
        vehicleData.record = fetchData(String.format("%s%s/data/record", VEHICLE_BASE_URL, vin), RecordDTO.class);
        return vehicleData;
    }

    @Override
    public void startClimate(String vin, int climateLevel, int durationInMinutes) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/climate", vin);

        JsonObject data = new JsonObject();
        data.addProperty("climateLevel", climateLevel);
        data.addProperty("command", "START");
        JsonArray dayOfWeek = new JsonArray();
        dayOfWeek.add("ONCE");
        data.add("dayofweek", dayOfWeek);
        data.addProperty("durationInSeconds", durationInMinutes * 60);
        data.addProperty("scheduledTime", 10);
        JsonArray heatItems = new JsonArray();
        heatItems.add("ALL");
        data.add("heatItems", heatItems);
        data.addProperty("startTimeOfDay", "00:00");
        data.addProperty("timerId", "1");
        JsonArray ventilationItems = new JsonArray();
        ventilationItems.add("ALL");
        data.add("ventilationItems", ventilationItems);

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start climate command");
        }
    }

    @Override
    public void stopClimate(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/climate", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "STOP");
        JsonArray dayOfWeek = new JsonArray();
        dayOfWeek.add("ONCE");
        data.add("dayofweek", dayOfWeek);
        data.addProperty("startTimeOfDay", "00:00");
        data.addProperty("durationInSeconds", 1);
        data.addProperty("timerId", "1");
        JsonArray ventilationItems = new JsonArray();
        ventilationItems.add("ALL");
        data.add("ventilationItems", ventilationItems);
        data.addProperty("scheduledTime", 10);

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent stop climate command");
        }
    }

    @Override
    public void startEngine(String vin, int durationInMinutes) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/engine", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "START");
        data.addProperty("durationInSeconds", durationInMinutes * 60);

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start engine command");
        }
    }

    @Override
    public void stopEngine(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/engine", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "STOP");
        data.addProperty("durationInSeconds", 1800);

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent stop engine command");
        }
    }

    @Override
    public void lockDoors(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/doorlock", vin);

        JsonObject data = new JsonObject();
        JsonArray doorItems = new JsonArray();
        doorItems.add("ALL_DOORS");
        data.add("doorItems", doorItems);

        JsonArray targetItems = new JsonArray();
        targetItems.add("TRUNK");
        targetItems.add("HOOD");
        targetItems.add("TANK_FLAG");
        targetItems.add("BACK_CHARGE_LID");
        targetItems.add("FRONT_CHARGE_LID");
        data.add("targetItems", targetItems);

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent lock doors command");
        }
    }

    @Override
    public void unlockDoors(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/doorunlock", vin);

        JsonObject data = new JsonObject();
        JsonArray doorItems = new JsonArray();
        doorItems.add("ALL_DOORS");
        data.add("doorItems", doorItems);

        JsonArray targetItems = new JsonArray();
        targetItems.add("TRUNK");
        targetItems.add("FRONT_CHARGE_LID");
        data.add("targetItems", targetItems);

        data.addProperty("durationInSeconds", 15);
        data.addProperty("timeStart", 0);

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent unlock doors command");
        }
    }

    @Override
    public void startFlashLights(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "START");
        data.addProperty("control", "FLASH");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start flash lights command");
        }
    }

    @Override
    public void startHonk(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "START");
        data.addProperty("control", "HONK");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start honk command");
        }
    }

    @Override
    public void startHonkFlash(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "START");
        data.addProperty("control", "HONK_FLASH");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start honk and flash command");
        }
    }

    @Override
    public void stopFlashLights(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "STOP");
        data.addProperty("control", "FLASH");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent stop flash lights command");
        }
    }

    @Override
    public void stopHonk(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "STOP");
        data.addProperty("control", "HONK");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent stop honk command");
        }
    }

    private boolean makeHttpRequest(String endpoint, JsonObject data, String vin) throws LynkcoApiException {
        if (endpoint.isEmpty()) {
            throw new IllegalArgumentException("Endpoint must not be null or empty.");
        }

        String userId = "";
        String token = tokenManager.getCccToken();
        if (token != null) {
            userId = tokenManager.getUserId(token, vin);
        } else {
            logger.debug("token is null!");
            return false;
        }

        try {
            Request request = httpClient.newRequest(endpoint).method(HttpMethod.POST)
                    .header(HttpHeader.USER_AGENT, "LynkCo/3016 CFNetwork/1492.0.1 Darwin/23.3.0")
                    .header(HttpHeader.ACCEPT, "application/json")
                    .header(HttpHeader.ACCEPT_ENCODING, "gzip, deflate, br").header(HttpHeader.CONNECTION, "keep-alive")
                    .header("userId", userId).header("X-B3-TraceId", "2d3c260f81d6c8e9548d1ddd3db2d482")
                    .header(HttpHeader.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeader.CONTENT_TYPE, "application/json")
                    .content(new StringContentProvider(data.toString()));
            ContentResponse response = request.send();

            if (response.getStatus() == 200) {
                logger.debug("Command executed successfully");
                return true;
            } else {
                logger.debug("Failed to execute command, HTTP status: {}, response: {}", response.getStatus(),
                        response.getContentAsString());
                return false;
            }
        } catch (Exception e) {
            throw new LynkcoApiException("Network error: " + e.getMessage(),
                    LynkcoApiException.ErrorType.NETWORK_ERROR);
        }
    }

    private <T> T fetchData(String endpoint, Class<T> dtoClass) throws LynkcoApiException {
        if (endpoint.isEmpty()) {
            throw new IllegalArgumentException("Endpoint must not be null or empty.");
        }

        String token = tokenManager.getCccToken();
        logger.debug("fetchData CCC token: {}", token);
        try {
            Request request = httpClient.newRequest(endpoint).method(HttpMethod.GET)
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
            ContentResponse response = request.send();

            if (response.getStatus() == 200) {
                String jsonResponse = response.getContentAsString();
                logger.trace("Response: {}", jsonResponse);
                return gson.fromJson(jsonResponse, dtoClass);
            } else if (response.getStatus() == 401) {
                throw new LynkcoApiException("Authentication error: " + response.getContentAsString(),
                        LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED);
            } else {
                throw new LynkcoApiException("API error: " + response.getContentAsString(),
                        LynkcoApiException.ErrorType.API_ERROR);
            }
        } catch (Exception e) {
            throw new LynkcoApiException("Network error: " + e.getMessage(),
                    LynkcoApiException.ErrorType.NETWORK_ERROR);
        }
    }
}
