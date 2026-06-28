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
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.RecordDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.ShadowDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

/**
 * The {@link GatewayPlatform} implements {@link VehiclePlatform} for the modern signed mobile-app
 * gateway used by Lynk&Co 01 (2025), 02 and 08.
 *
 * Every authenticated request is signed: a per-request {@code X-NONCE} (UUID) is combined with the
 * request path and the {@code snowflakeId} claim from the access token to form
 * {@code X-SIGNATURE = SHA-256(snowflakeId + nonce + path)} (hex). The OAuth access token is used
 * directly as the bearer token (no {@code cccToken}).
 *
 * NOTE: The exact JSON response shapes of the separate gateway state endpoints and some command
 * payloads still need to be confirmed against captured traffic from a real vehicle (see the
 * implementation plan, "Step 0"). For now the combined {@code vehicle_data} endpoint is parsed into
 * the shared {@link LynkcoDTO}; the richer state endpoints can be layered in once verified.
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

    // Separate gateway state endpoints. Their exact JSON shapes still need to be confirmed against
    // a real vehicle; logDiagnosticEndpoints() dumps them at TRACE level to make that capture easy.
    private static final String[] DIAGNOSTIC_ENDPOINTS = { "/list/vehicles", "/vehicle/%s/vehicle_data",
            "/vehicle/%s/vehicle_metadata", "/vehicle/%s/location_state", "/vehicle/%s/charge_state",
            "/vehicle/%s/climate_state", "/vehicle/%s/doors_windows_state", "/vehicle/%s/fuel_state" };

    @Override
    public LynkcoDTO fetchVehicleData(String vin) throws LynkcoApiException {
        if (logger.isTraceEnabled()) {
            logDiagnosticEndpoints(vin);
        }
        LynkcoDTO vehicleData = new LynkcoDTO();
        String json = getJson(GATEWAY_LOVE_BASE + "/vehicle/" + vin + "/vehicle_data");
        RecordDTO record = gson.fromJson(json, RecordDTO.class);
        ShadowDTO shadow = gson.fromJson(json, ShadowDTO.class);
        if (record != null) {
            vehicleData.record = record;
        }
        if (shadow != null) {
            vehicleData.shadow = shadow;
        }
        return vehicleData;
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

    /**
     * Fetch every known gateway state endpoint and log the raw response at TRACE level. This is a
     * diagnostic aid for capturing the JSON shapes from a real vehicle (see the implementation
     * plan, "Step 0"); failures on individual endpoints are logged and ignored. Enable with
     * {@code log:set TRACE org.openhab.binding.lynkco} on the karaf console.
     */
    private void logDiagnosticEndpoints(String vin) {
        try {
            ensureDeviceRegistered();
        } catch (LynkcoApiException e) {
            logger.trace("Gateway diagnostic device validation failed: {}", e.getMessage());
        }
        for (String template : DIAGNOSTIC_ENDPOINTS) {
            String path = template.contains("%s") ? String.format(template, vin) : template;
            String url = GATEWAY_LOVE_BASE + path;
            try {
                Request request = signedRequest(HttpMethod.GET, url, null);
                ContentResponse response = request.send();
                logger.trace("Gateway diagnostic GET {} -> HTTP {}: {}", url, response.getStatus(),
                        response.getContentAsString());
            } catch (Exception e) {
                logger.trace("Gateway diagnostic GET {} failed: {}", url, e.getMessage());
            }
        }
    }

    private String getJson(String url) throws LynkcoApiException {
        ensureDeviceRegistered();
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
