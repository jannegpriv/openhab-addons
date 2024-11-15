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
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Fields;
import org.openhab.binding.lynkco.internal.LynkcoBridgeConfiguration;
import org.openhab.binding.lynkco.internal.LynkcoException;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * The {@link LynkcoAPI} class defines the Elextrolux Delta API
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class LynkcoAPI {
    private static final String LOGIN_B2C_URL = "https://login.lynkco.com/lynkcoprod.onmicrosoft.com/b2c_1a_signin_mfa/";
    private static final String CLIENT_ID = "813902c0-0579-43f3-a767-6601c2f5fdbe";
    private static final String SCOPE_BASE_URL = "https://lynkcoprod.onmicrosoft.com/mobile-app-web-api/mobile";

    private static final String CLIENT_SECRET = "8UKrsKD7jH9zvTV7rz5HeCLkit67Mmj68FvRVTlYygwJYy4dW6KF2cVLPKeWzUQUd6KJMtTifFf4NkDnjI7ZLdfnwcPtTSNtYvbP7OzEkmQD9IjhMOf5e1zeAQYtt2yN";
    private static final String X_API_KEY = "2AMqwEV5MqVhTKrRCyYfVF8gmKrd2rAmp7cUsfky";

    private static final String BASE_URL = "https://api.ocp.electrolux.one";
    private static final String TOKEN_URL = BASE_URL + "/one-account-authorization/api/v1/token";
    private static final String AUTHENTICATION_URL = BASE_URL + "/one-account-authentication/api/v1/authenticate";
    private static final String API_URL = BASE_URL + "/appliance/api/v2";
    private static final String APPLIANCES_URL = API_URL + "/appliances";

    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final int MAX_RETRIES = 3;
    private static final int REQUEST_TIMEOUT_MS = 10_000;

    private final Logger logger = LoggerFactory.getLogger(LynkcoAPI.class);
    private final Gson gson;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;
    private final LynkcoBridgeConfiguration configuration;
    private String authToken = "";
    private Instant tokenExpiry = Instant.MIN;

    public LynkcoAPI(LynkcoBridgeConfiguration configuration, Gson gson, HttpClient httpClient) {
        this.gson = gson;
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.httpClient.setUserAgentField(new HttpField(HttpHeader.USER_AGENT,
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1"));
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    }

    public boolean refresh(Map<String, LynkcoDTO> lynkcoThings) {
        try {
            String ccToken = getCCToken();
            // Get all appliances
            String json = getAppliances();
            LynkcoDTO[] dtos = gson.fromJson(json, LynkcoDTO[].class);
            if (dtos != null) {
                for (LynkcoDTO dto : dtos) {
                    String applianceId = dto.getApplianceId();
                    // Get appliance info
                    String jsonApplianceInfo = getAppliancesInfo(applianceId);
                    LynkcoDTO.ApplianceInfo applianceInfo = gson.fromJson(jsonApplianceInfo,
                            LynkcoDTO.ApplianceInfo.class);
                    if (applianceInfo != null) {
                        if ("AIR_PURIFIER".equals(applianceInfo.getDeviceType())) {
                            dto.setApplianceInfo(applianceInfo);
                            lynkcoThings.put(dto.getProperties().getReported().getDeviceId(), dto);
                        }
                    }
                }
                return true;
            }
        } catch (Exception e) {
            logger.warn("Failed to refresh! {}", e.getMessage());
        }
        return false;
    }

    public boolean workModePowerOff(String applianceId) {
        String commandJSON = "{ \"WorkMode\": \"PowerOff\" }";
        try {
            return sendCommand(commandJSON, applianceId);
        } catch (LynkcoException e) {
            logger.warn("Work mode powerOff failed {}", e.getMessage());
        }
        return false;
    }

    public boolean workModeAuto(String applianceId) {
        String commandJSON = "{ \"WorkMode\": \"Auto\" }";
        try {
            return sendCommand(commandJSON, applianceId);
        } catch (LynkcoException e) {
            logger.warn("Work mode auto failed {}", e.getMessage());
        }
        return false;
    }

    public boolean workModeManual(String applianceId) {
        String commandJSON = "{ \"WorkMode\": \"Manual\" }";
        try {
            return sendCommand(commandJSON, applianceId);
        } catch (LynkcoException e) {
            logger.warn("Work mode manual failed {}", e.getMessage());
        }
        return false;
    }

    public boolean setFanSpeedLevel(String applianceId, int fanSpeedLevel) {
        if (fanSpeedLevel < 1 && fanSpeedLevel > 10) {
            return false;
        } else {
            String commandJSON = "{ \"Fanspeed\": " + fanSpeedLevel + "}";
            try {
                return sendCommand(commandJSON, applianceId);
            } catch (LynkcoException e) {
                logger.warn("Work mode manual failed {}", e.getMessage());
            }
        }
        return false;
    }

    public boolean setIonizer(String applianceId, String ionizerStatus) {
        String commandJSON = "{ \"Ionizer\": " + ionizerStatus + "}";
        try {
            return sendCommand(commandJSON, applianceId);
        } catch (LynkcoException e) {
            logger.warn("Work mode manual failed {}", e.getMessage());
        }
        return false;
    }

    public boolean setUILight(String applianceId, String uiLightStatus) {
        String commandJSON = "{ \"UILight\": " + uiLightStatus + "}";
        try {
            return sendCommand(commandJSON, applianceId);
        } catch (LynkcoException e) {
            logger.warn("Work mode manual failed {}", e.getMessage());
        }
        return false;
    }

    public boolean setSafetyLock(String applianceId, String safetyLockStatus) {
        String commandJSON = "{ \"SafetyLock\": " + safetyLockStatus + "}";
        try {
            return sendCommand(commandJSON, applianceId);
        } catch (LynkcoException e) {
            logger.warn("Work mode manual failed {}", e.getMessage());
        }
        return false;
    }

    private Request createRequest(String uri, HttpMethod httpMethod) {
        Request request = httpClient.newRequest(uri).method(httpMethod);
        request.timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        request.header(HttpHeader.ACCEPT, JSON_CONTENT_TYPE);
        request.header(HttpHeader.CONTENT_TYPE, JSON_CONTENT_TYPE);

        logger.debug("HTTP POST Request {}.", request.toString());

        return request;
    }

    public @Nullable LoginResponse login() throws Exception {
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

        CombinedSigninResponse combinedSigninResponse = getCombinedSigninAndSignup(xMsCpimCsrfToken, xMsCpimTransValue,
                pageViewId, codeChallenge);
        if (combinedSigninResponse == null) {
            return null;
        }

        return new LoginResponse(xMsCpimTransValue, xMsCpimCsrfToken, combinedSigninResponse.pageViewId,
                combinedSigninResponse.refererUrl, codeVerifier);
    }

    private String getCCToken() {
        if (Instant.now().isAfter(this.tokenExpiry)) {
            // Login again since token is expired
            try {
                return refreshTokens();
            } catch (Exception e) {
                logger.warn("Failed to refresh! {}", e.getMessage());
            }
        }
        return "";

    }

    private String refreshTokens() {
        if (Instant.now().isAfter(this.tokenExpiry)) {
            // Login again since token is expired
            try {
                LoginResponse response = login();
            } catch (Exception e) {
                logger.warn("Failed to refresh! {}", e.getMessage());
            }
        }
        return sendDeviceLogin();
    }

    private String sendDeviceLogin() {
        return "";

    }

    private @Nullable String authorize(String codeChallenge) throws Exception {
        String baseUrl = LOGIN_B2C_URL + "oauth2/v2.0/authorize";
        String params = "response_type=code" + "&scope="
                + URLEncoder.encode(SCOPE_BASE_URL + ".read " + SCOPE_BASE_URL + ".write profile offline_access",
                        StandardCharsets.UTF_8)
                + "&code_challenge=" + codeChallenge + "&code_challenge_method=S256"
                + "&redirect_uri=msauth.com.lynkco.prod.lynkco-app://auth" + "&client_id=" + CLIENT_ID;

        URI uri = URI.create(baseUrl + "?" + params);
        Request request = httpClient.newRequest(uri).method(HttpMethod.GET).header("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        ContentResponse response = request.send();
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
                .header("Content-Type", "application/x-www-form-urlencoded").content(new FormContentProvider(fields));

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

        String queryParams = "rememberMe=false" + "&csrf_token=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8)
                + "&tx=StateProperties=" + URLEncoder.encode(txValue, StandardCharsets.UTF_8) + "&p=B2C_1A_signin_mfa"
                + "&diags=" + URLEncoder.encode(gson.toJson(diags), StandardCharsets.UTF_8);

        URI uri = URI.create(url + "?" + queryParams);

        Request request = httpClient.newRequest(uri).method(HttpMethod.GET)
                .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("sec-fetch-site", "same-origin").header("sec-fetch-dest", "document")
                .header("accept-language", "en-GB,en;q=0.9").header("sec-fetch-mode", "navigate")
                .header("user-agent",
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1")
                .header("referer", refererBaseUrl
                        + "?x-client-Ver=1.2.22&state=ABC&client_info=1&prompt=select_account&response_type=code&x-app-name=Lynk%20%26%20Co&code_challenge_method=S256&x-app-ver=2.12.0&scope=https%3A%2F%2Flynkcoprod.onmicrosoft.com%2Fmobile-app-web-api%2Fmobile.read%20https%3A%2F%2Flynkcoprod.onmicrosoft.com%2Fmobile-app-web-api%2Fmobile.write%20openid%20profile%20offline_access&x-client-SKU=MSAL.iOS&x-client-OS=17.4.1&code_challenge="
                        + codeChallenge
                        + "&x-client-CPU=64&redirect_uri=msauth.com.lynkco.prod.lynkco-app%3A%2F%2Fauth&client-request-id=0207E18F-1598-4BD7-AC0F-705414D8B0F7&client_id="
                        + CLIENT_ID + "&x-client-DM=iPhone&return-client-request-id=true&haschrome=1")
                .header("accept-encoding", "gzip, deflate, br");

        ContentResponse response = request.send();
        if (response.getStatus() == 200) {
            String newPageViewId = response.getHeaders().get("x-ms-gateway-requestid");
            if (newPageViewId != null) {
                return new CombinedSigninResponse(newPageViewId, uri.toString());
            } else {
                logger.warn("New pageViewId not found in the response headers.");
                return null;
            }
        } else {
            logger.warn("GET request for CombinedSigninAndSignup failed with status code: {}", response.getStatus());
            return null;
        }
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

    private String getFromApi(String uri) throws LynkcoException, InterruptedException {
        try {
            for (int i = 0; i < MAX_RETRIES; i++) {
                try {
                    Request request = createRequest(uri, HttpMethod.GET);
                    request.header(HttpHeader.AUTHORIZATION, "Bearer " + authToken);
                    request.header("x-api-key", X_API_KEY);

                    ContentResponse response = request.send();
                    String content = response.getContentAsString();
                    logger.trace("API response: {}", content);

                    if (response.getStatus() != HttpStatus.OK_200) {
                        logger.debug("getFromApi failed, HTTP status: {}", response.getStatus());
                        login();
                    } else {
                        return content;
                    }
                } catch (Exception e) {
                    logger.debug("Exception error in get: {}", e.getMessage());
                }
            }
            throw new LynkcoException("Failed to fetch from API!");
        } catch (Exception e) {
            throw new LynkcoException(e);
        }
    }

    private String getAppliances() throws LynkcoException {
        try {
            return getFromApi(APPLIANCES_URL);
        } catch (LynkcoException | InterruptedException e) {
            throw new LynkcoException(e);
        }
    }

    private String getAppliancesInfo(String applianceId) throws LynkcoException {
        try {
            return getFromApi(APPLIANCES_URL + "/" + applianceId + "/info");
        } catch (LynkcoException | InterruptedException e) {
            throw new LynkcoException(e);
        }
    }

    private boolean sendCommand(String commandJSON, String applianceId) throws LynkcoException {
        try {
            for (int i = 0; i < MAX_RETRIES; i++) {
                try {
                    Request request = createRequest(APPLIANCES_URL + "/" + applianceId + "/command", HttpMethod.PUT);
                    request.header(HttpHeader.AUTHORIZATION, "Bearer " + authToken);
                    request.header("x-api-key", X_API_KEY);
                    request.content(new StringContentProvider(commandJSON), JSON_CONTENT_TYPE);

                    ContentResponse response = request.send();
                    String content = response.getContentAsString();
                    logger.trace("API response: {}", content);

                    if (response.getStatus() != HttpStatus.OK_200) {
                        logger.debug("sendCommand failed, HTTP status: {}", response.getStatus());
                        login();
                    } else {
                        return true;
                    }
                } catch (Exception e) {
                    logger.warn("Exception error in get");
                }
            }
        } catch (Exception e) {
            throw new LynkcoException(e);
        }
        return false;
    }

    // Helper Classes
    private static class LoginResponse {
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
