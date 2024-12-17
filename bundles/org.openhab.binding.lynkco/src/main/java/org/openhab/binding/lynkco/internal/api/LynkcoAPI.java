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
package org.openhab.binding.lynkco.internal.api;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.Fields;
import org.openhab.binding.lynkco.internal.LynkcoBridgeConfiguration;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.RecordDTO;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO.ShadowDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link LynkcoAPI} class defines the Lynk 6 Co API
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class LynkcoAPI {
    private static final String LOGIN_B2C_URL = "https://login.lynkco.com/lynkcoprod.onmicrosoft.com/b2c_1a_signin_mfa/";
    private static final String CLIENT_ID = "813902c0-0579-43f3-a767-6601c2f5fdbe";
    private static final String SCOPE_BASE_URL = "https://lynkcoprod.onmicrosoft.com/mobile-app-web-api/mobile";
    private static final String VEHICLE_BASE_URL = "https://vehicle-data-tls.aion.connectedcar.cloud/api/v1/vds/vehicles/";
    private static final String VEHICLE_CONTROL_URL = "https://remote-vehicle-control-tls.aion.connectedcar.cloud/api/v1/rvc/vehicles/%s/remotecontrol";
    private static final String REDIRECT_URI = "msauth.com.lynkco.prod.lynkco-app://auth";
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1";

    private final Logger logger = LoggerFactory.getLogger(LynkcoAPI.class);
    private final Gson gson;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;
    private final LynkcoBridgeConfiguration configuration;
    private final LynkcoTokenManager tokenManager;

    public LynkcoAPI(LynkcoBridgeConfiguration configuration, Gson gson, HttpClient httpClient,
            LynkcoTokenManager tokenManager) {
        this.gson = gson;
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.tokenManager = tokenManager;
    }

    public @Nullable LoginResponse login() throws LynkcoApiException {
        try {
            String username = configuration.email;
            String password = configuration.password;
            if (username == null || password == null) {
                logger.warn("Username or password is null!");
                return null;
            }
            String[] codeVerifierChallenge = PkceUtil.generatePkcePair();
            String codeVerifier = codeVerifierChallenge[0];
            String codeChallenge = codeVerifierChallenge[1];

            String pageViewId = authorize(codeChallenge);
            if (pageViewId == null) {
                logger.warn("Authorization failed, page_view_id missing.");
                return null;
            }

            String xMsCpimTransValue = getCookieValue("x-ms-cpim-trans");
            String xMsCpimCsrfToken = getCookieValue("x-ms-cpim-csrf");
            if (xMsCpimTransValue == null || xMsCpimCsrfToken == null) {
                logger.warn("Authorization failed, missing cookies");
                return null;
            }

            if (!postLogin(username, password, xMsCpimTransValue, xMsCpimCsrfToken)) {
                logger.warn("Login failed. Exiting...");
                return null;
            }

            CombinedSigninResponse combinedSigninResponse = getCombinedSigninAndSignup(xMsCpimCsrfToken,
                    xMsCpimTransValue, pageViewId, codeChallenge);
            if (combinedSigninResponse == null) {
                logger.warn("combinedSigninResponse is null!");
                return null;
            }

            return new LoginResponse(xMsCpimTransValue, xMsCpimCsrfToken, combinedSigninResponse.pageViewId,
                    combinedSigninResponse.refererUrl, codeVerifier);
        } catch (Exception e) {
            logger.warn("login caught exception: {}", e.toString());
            String message = e.getMessage();
            if (message == null) {
                message = "No exception mesage";
            }
            throw new LynkcoApiException(message, LynkcoApiException.ErrorType.AUTHENTICATION_FAILED);
        }
    }

    public TokenResponse handleMFACode(String verificationCode, LoginResponse mfaContext) throws LynkcoApiException {
        try {
            logger.debug("handleMFACode: mfaContext: {}", mfaContext.toString());
            // Step 1: Post verification
            boolean verificationSuccess = postVerification(verificationCode, mfaContext.xMsCpimTransValue,
                    mfaContext.xMsCpimCsrfToken);

            if (!verificationSuccess) {
                logger.error("Verification failed. Exiting...");
                return TokenResponse.failure("MFA verification failed");
            }
            logger.debug("Verification successful.");

            // Step 2: Get redirect code
            String code = getRedirect(mfaContext);

            // Step 3: Get tokens
            return getTokens(code, mfaContext.codeVerifier);

        } catch (Exception e) {
            throw new LynkcoApiException("Error in MFA verification: " + e.getMessage(),
                    LynkcoApiException.ErrorType.AUTHENTICATION_FAILED);
        }
    }

    public void fetchVehicleShadowData(LynkcoDTO vehicleData, String vin) throws LynkcoApiException {
        String endpoint = String.format("%s%s/data/shadow", VEHICLE_BASE_URL, vin);
        vehicleData.shadow = fetchData(endpoint, ShadowDTO.class);
    }

    public void fetchVehicleRecordData(LynkcoDTO vehicleData, String vin) throws LynkcoApiException {
        String endpoint = String.format("%s%s/data/record", VEHICLE_BASE_URL, vin);
        vehicleData.record = fetchData(endpoint, RecordDTO.class);
    }

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

    public void startEngine(String vin, int durationInMinutes) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/engine", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "START");
        data.addProperty("durationInSeconds", durationInMinutes * 60);

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start engine command");
        }
    }

    public void stopEngine(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/engine", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "STOP");
        data.addProperty("durationInSeconds", 1800);

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent stop engine command");
        }
    }

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

    public void startFlashLights(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "START");
        data.addProperty("control", "FLASH");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start flash lights command");
        }
    }

    public void startHonk(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "START");
        data.addProperty("control", "HONK");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start honk command");
        }
    }

    public void startHonkFlash(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "START");
        data.addProperty("control", "HONK_FLASH");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent start honk and flash command");
        }
    }

    public void stopFlashLights(String vin) throws LynkcoApiException {
        String endpoint = String.format(VEHICLE_CONTROL_URL + "/honkflash", vin);

        JsonObject data = new JsonObject();
        data.addProperty("command", "STOP");
        data.addProperty("control", "FLASH");

        if (makeHttpRequest(endpoint, data, vin)) {
            logger.debug("Successfully sent stop flash lights command");
        }
    }

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

    /**
     * Response class for token-related operations like MFA verification
     */
    public static class TokenResponse {
        public final boolean success;
        public String authToken = "";
        public String refreshToken = "";
        public String errorMessage = "";

        public static TokenResponse success(String authToken, String refreshToken) {
            return new TokenResponse(true, authToken, refreshToken, "");
        }

        public static TokenResponse failure(String errorMessage) {
            return new TokenResponse(false, "", "", errorMessage);
        }

        private TokenResponse(boolean success, String authToken, String refreshToken, String errorMessage) {
            this.success = success;
            this.authToken = authToken;
            this.refreshToken = refreshToken;
            this.errorMessage = errorMessage;
        }
    }

    private @Nullable String authorize(String codeChallenge) throws Exception {
        String baseUrl = LOGIN_B2C_URL + "oauth2/v2.0/authorize";

        // Create the request and set query parameters using param()
        Request request = httpClient.newRequest(baseUrl).method(HttpMethod.GET).param("response_type", "code")
                .param("scope", SCOPE_BASE_URL + ".read " + SCOPE_BASE_URL + ".write profile offline_access")
                .param("code_challenge", codeChallenge).param("code_challenge_method", "S256")
                .param("redirect_uri", REDIRECT_URI).param("client_id", CLIENT_ID)
                .header(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        ContentResponse response = request.send();

        // Handle the response
        if (response.getStatus() == 200) {
            return response.getHeaders().get("x-ms-gateway-requestid");
        } else {
            logger.warn("GET request for authorization failed with status code: {}", response.getStatus());
            return null;
        }
    }

    private boolean postLogin(String username, String password, String xMsCpimTransValue, String xMsCpimCsrfToken)
            throws Exception {
        String txValue = "StateProperties=" + xMsCpimTransValue;
        String encodedTxValue = URLEncoder.encode(txValue, StandardCharsets.UTF_8);
        String queryParams = "p=B2C_1A_signin_mfa&tx=" + encodedTxValue;

        Fields fields = new Fields();
        fields.add("request_type", "RESPONSE");
        fields.add("signInName", username);
        fields.add("password", password);

        String baseUrl = LOGIN_B2C_URL + "SelfAsserted";
        URI uri = URI.create(baseUrl + "?" + queryParams);

        Request request = httpClient.newRequest(uri).method(HttpMethod.POST).header("x-csrf-token", xMsCpimCsrfToken)
                .header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .content(new FormContentProvider(fields));
        ContentResponse response = request.send();

        if (response.getStatus() == 200) {
            logger.debug("POST request for login successful.");
            return true;
        } else {
            logger.warn("POST request for login failed with status code: {}", response.getStatus());
            return false;
        }
    }

    private @Nullable CombinedSigninResponse getCombinedSigninAndSignup(String csrfToken, String txValue,
            String pageViewId, String codeChallenge)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        String url = LOGIN_B2C_URL + "api/CombinedSigninAndSignup/confirmed";
        String refererBaseUrl = LOGIN_B2C_URL + "v2.0/authorize";

        JsonObject diags = new JsonObject();
        diags.addProperty("pageViewId", pageViewId);
        diags.addProperty("pageId", "CombinedSigninAndSignup");
        diags.add("trace", new JsonObject());

        Request request = httpClient.newRequest(url).method(HttpMethod.GET).agent(USER_AGENT)
                .header(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("sec-fetch-site", "same-origin").header("sec-fetch-dest", "document")
                .header(HttpHeader.ACCEPT_LANGUAGE, "en-GB,en;q=0.9").header("sec-fetch-mode", "navigate")
                .header(HttpHeader.REFERER, refererBaseUrl
                        + "?x-client-Ver=1.2.22&state=ABC&client_info=1&prompt=select_account&response_type=code&x-app-name=Lynk%20%26%20Co&code_challenge_method=S256&x-app-ver=2.12.0&scope=https%3A%2F%2Flynkcoprod.onmicrosoft.com%2Fmobile-app-web-api%2Fmobile.read%20https%3A%2F%2Flynkcoprod.onmicrosoft.com%2Fmobile-app-web-api%2Fmobile.write%20openid%20profile%20offline_access&x-client-SKU=MSAL.iOS&x-client-OS=17.4.1&code_challenge="
                        + codeChallenge
                        + "&x-client-CPU=64&redirect_uri=msauth.com.lynkco.prod.lynkco-app%3A%2F%2Fauth&client-request-id=0207E18F-1598-4BD7-AC0F-705414D8B0F7&client_id="
                        + CLIENT_ID + "&x-client-DM=iPhone&return-client-request-id=true&haschrome=1")
                .param("rememberMe", "false").param("csrf_token", csrfToken).param("tx", "StateProperties=" + txValue)
                .param("p", "B2C_1A_signin_mfa").param("diags", gson.toJson(diags));
        ContentResponse response = request.send();

        if (response.getStatus() == 200) {
            // Extract the new pageViewId from the response headers
            String newPageViewId = response.getHeaders().get("x-ms-gateway-requestid");
            if (newPageViewId != null) {
                return new CombinedSigninResponse(newPageViewId, request.getURI().toString());
            } else {
                logger.warn("New pageViewId not found in the response headers.");
                return null;
            }
        } else {
            logger.warn("GET request for CombinedSigninAndSignup failed with status code: {}", response.getStatus());
            return null;
        }
    }

    public boolean postVerification(String verificationCode, String xMsCpimTransValue, String xMsCpimCsrfToken)
            throws Exception {
        String url = LOGIN_B2C_URL + "SelfAsserted";

        Request request = httpClient.POST(url).param("p", "B2C_1A_signin_mfa")
                .param("tx", "StateProperties=" + xMsCpimTransValue) // No need for manual encoding
                .header("x-csrf-token", xMsCpimCsrfToken)
                .header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");

        // Add form fields as content
        Fields fields = new Fields();
        fields.add("verificationCode", verificationCode);
        fields.add("request_type", "RESPONSE");
        request.content(new FormContentProvider(fields));
        ContentResponse response = request.send();

        if (response.getStatus() == 200) {
            return true;
        } else {
            throw new LynkcoApiException("POST verification failed with status code: " + response.getStatus(),
                    LynkcoApiException.ErrorType.MFA_INVALID);
        }
    }

    private String getRedirect(LoginResponse loginResponse) throws Exception {
        String url = LOGIN_B2C_URL + "api/SelfAsserted/confirmed";

        JsonObject diagsJson = new JsonObject();
        diagsJson.addProperty("pageViewId", loginResponse.pageViewId);
        diagsJson.addProperty("pageId", "SelfAsserted");
        diagsJson.addProperty("trace", "[]");

        String csrfToken = getCookieValue("x-ms-cpim-csrf");
        if (csrfToken == null) {
            logger.error("CSRF token cookie not found");
            throw new LynkcoApiException("CSRF token cookie not found", LynkcoApiException.ErrorType.MFA_INVALID);
        }

        // Temp increase Request Header Size
        int defaultRequestBufferSize = httpClient.getRequestBufferSize();
        logger.debug("Default header request bufer size: {}", defaultRequestBufferSize);
        httpClient.setRequestBufferSize(16384);

        Request request = httpClient.newRequest(url).method(HttpMethod.GET).agent(USER_AGENT).followRedirects(false)
                .header(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header(HttpHeader.ACCEPT_ENCODING, "gzip, deflate, br").header("sec-fetch-site", "same-origin")
                .header("sec-fetch-dest", "document").header("sec-fetch-mode", "navigate")
                .header(HttpHeader.ACCEPT_LANGUAGE, "en-GB,en;q=0.9")
                .header(HttpHeader.REFERER, loginResponse.refererUrl).param("csrf_token", csrfToken)
                .param("tx", "StateProperties=" + loginResponse.xMsCpimTransValue).param("p", "B2C_1A_signin_mfa")
                .param("diags", diagsJson.toString());

        // Debug logging
        logger.debug("Using CSRF token from cookie: {}", csrfToken);
        logger.debug("Request URI: {}", request.getURI());
        logger.debug("Request headers:");
        request.getHeaders().forEach(field -> logger.debug("{}: {}", field.getName(), field.getValue()));

        ContentResponse response = request.send();
        httpClient.setRequestBufferSize(defaultRequestBufferSize);

        logger.debug("Response status: {}", response.getStatus());
        logger.debug("Response headers:");
        response.getHeaders().forEach(field -> logger.debug("{}: {}", field.getName(), field.getValue()));

        if (response.getStatus() == 301 || response.getStatus() == 302) {
            String location = response.getHeaders().get("Location");
            if (location != null) {
                // Extract the 'code' parameter from the redirect URL
                return getQueryParam(location, "code");
            }
        } else if (response.getStatus() == 200) {
            // Inspect the response body for any additional clues
            String responseBody = response.getContentAsString();
            if (responseBody.contains("code=")) {
                return getQueryParam(responseBody, "code");
            }
            throw new LynkcoApiException("Unexpected response content. Could not find redirect code.",
                    LynkcoApiException.ErrorType.MFA_INVALID);
        } else {
            logger.debug("GET redirect request failed with status code: {}", response.getStatus());
        }
        throw new LynkcoApiException("Failed to get redirect code. Status code: " + response.getStatus(),
                LynkcoApiException.ErrorType.MFA_INVALID);
    }

    public TokenResponse getTokens(String code, String codeVerifier) throws Exception {
        String url = LOGIN_B2C_URL + "oauth2/v2.0/token";
        Fields fields = new Fields();
        fields.add("client_info", "1");
        fields.add("scope", SCOPE_BASE_URL + ".read " + SCOPE_BASE_URL + ".write openid profile offline_access");
        fields.add("code", code);
        fields.add("grant_type", "authorization_code");
        fields.add("code_verifier", codeVerifier);
        fields.add("redirect_uri", REDIRECT_URI);
        fields.add("client_id", CLIENT_ID);

        Request request = httpClient.POST(url).content(new FormContentProvider(fields))
                .agent("LynkCo/3047 CFNetwork/1494.0.7 Darwin/23.4.0").header(HttpHeader.ACCEPT, "application/json")
                .header(HttpHeader.ACCEPT_ENCODING, "gzip, deflate, br").header("x-ms-pkeyauth+", "1.0")
                .header("x-client-last-telemetry", "4|0|||").header("x-client-ver", "1.2.22")
                .header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded");
        ContentResponse response = request.send();

        if (response.getStatus() == 200) {
            String jsonResponse = response.getContentAsString();
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            String accessToken = jsonObject.get("access_token").getAsString();
            String refreshToken = jsonObject.get("refresh_token").getAsString();
            if (accessToken != null && refreshToken != null) {
                return TokenResponse.success(accessToken, refreshToken);
            }
        } else {
            throw new LynkcoApiException("Failed to obtain tokens. Status code: " + response.getStatus(),
                    LynkcoApiException.ErrorType.MFA_INVALID);
        }
        logger.debug("Failed to obtain tokens. Status code: {}", response.getStatus());
        return TokenResponse.failure("Failed to obtain tokens");
    }

    private @Nullable String getCookieValue(String name) {
        List<HttpCookie> cookies = httpClient.getCookieStore().getCookies();
        for (HttpCookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String getQueryParam(String url, String paramName) {
        String[] parts = url.split("\\?");
        if (parts.length > 1) {
            String[] params = parts[1].split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                    return keyValue[1];
                }
            }
        }
        logger.debug("Could not find query parameter {} in URL {}", paramName, url);
        return "";
    }

    // Helper Classes
    public static class LoginResponse {
        public final String xMsCpimTransValue;
        public final String xMsCpimCsrfToken;
        public final String pageViewId;
        public final String refererUrl;
        public final String codeVerifier;

        public LoginResponse(String xMsCpimTransValue, String xMsCpimCsrfToken, String pageViewId, String refererUrl,
                String codeVerifier) {
            this.xMsCpimTransValue = xMsCpimTransValue;
            this.xMsCpimCsrfToken = xMsCpimCsrfToken;
            this.pageViewId = pageViewId;
            this.refererUrl = refererUrl;
            this.codeVerifier = codeVerifier;
        }

        @Override
        public String toString() {
            return "LoginResponse{" + "xMsCpimTransValue='" + xMsCpimTransValue + '\'' + ", xMsCpimCsrfToken='"
                    + xMsCpimCsrfToken + '\'' + ", pageViewId='" + pageViewId + '\'' + ", refererUrl='" + refererUrl
                    + '\'' + ", codeVerifier='" + codeVerifier + '\'' + '}';
        }
    }

    private static class CombinedSigninResponse {
        public final String pageViewId;
        public final String refererUrl;

        public CombinedSigninResponse(String pageViewId, String refererUrl) {
            this.pageViewId = pageViewId;
            this.refererUrl = refererUrl;
        }
    }

    private static class PkceUtil {

        public static String[] generatePkcePair() throws NoSuchAlgorithmException {
            String codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);
            return new String[] { codeVerifier, codeChallenge };
        }

        private static String generateCodeVerifier() {
            SecureRandom secureRandom = new SecureRandom();
            byte[] codeVerifierBytes = new byte[32];
            secureRandom.nextBytes(codeVerifierBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);
        }

        private static String generateCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        }
    }
}
