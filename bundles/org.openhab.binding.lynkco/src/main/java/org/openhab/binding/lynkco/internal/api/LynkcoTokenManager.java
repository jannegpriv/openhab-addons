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

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link LynkcoTokenManager} class handles tokens for the Lynk & Co API
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class LynkcoTokenManager {
    private final Logger logger = LoggerFactory.getLogger(LynkcoTokenManager.class);
    private final Thing thing;
    private final HttpClient httpClient;
    private final ReentrantLock cccTokenLock = new ReentrantLock();

    // Property keys for storing tokens
    private static final String PROPERTY_CCC_TOKEN = "cccToken";
    private static final String PROPERTY_ACCESS_TOKEN = "accessToken";
    private static final String PROPERTY_REFRESH_TOKEN = "refreshToken";
    private static final String PROPERTY_USER_ID = "userId";

    private static final String TOKEN_URL = "https://login.lynkco.com/dc6c7c0c-5ba7-414a-a7d1-d62ca1f73d13/b2c_1a_signin_mfa/oauth2/v2.0/token";

    // Cache fields
    private @Nullable String cachedCccToken;
    private @Nullable String cachedAccessToken;
    private @Nullable String cachedRefreshToken;
    private @Nullable String cachedUserId;
    private @Nullable Long cachedTokenExpiration;

    public LynkcoTokenManager(Thing thing, HttpClient httpClient) {
        this.thing = thing;
        this.httpClient = httpClient;
        loadTokensFromProperties();
    }

    public void updateTokens(String authToken, String refreshToken) throws LynkcoApiException {
        try {
            cccTokenLock.lock();

            // First update the access and refresh tokens
            updateToken(PROPERTY_ACCESS_TOKEN, authToken);
            updateToken(PROPERTY_REFRESH_TOKEN, refreshToken);

            // Then get a new CCC token using the auth token
            String cccToken = sendDeviceLogin(authToken);
            if (cccToken != null) {
                updateToken(PROPERTY_CCC_TOKEN, cccToken);
                loadTokensFromProperties();
            } else {
                clearTokens();
                throw new LynkcoApiException("Failed to obtain CCC token after updating tokens",
                        LynkcoApiException.ErrorType.AUTHENTICATION_FAILED);
            }
        } finally {
            cccTokenLock.unlock();
        }
    }

    private void loadTokensFromProperties() {
        cachedCccToken = thing.getProperties().get(PROPERTY_CCC_TOKEN);
        cachedAccessToken = thing.getProperties().get(PROPERTY_ACCESS_TOKEN);
        cachedRefreshToken = thing.getProperties().get(PROPERTY_REFRESH_TOKEN);
        cachedUserId = thing.getProperties().get(PROPERTY_USER_ID);

        if (cachedCccToken != null) {
            try {
                String payload = decodeJwtToken(cachedCccToken);
                JsonObject jsonPayload = JsonParser.parseString(payload).getAsJsonObject();
                cachedTokenExpiration = jsonPayload.get("exp").getAsLong();
            } catch (Exception e) {
                logger.debug("Could not decode cached token expiration: {}", e.getMessage());
                cachedTokenExpiration = null;
            }
        }
    }

    private String decodeJwtToken(@Nullable String token) {
        if (token != null) {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }

            String payload = parts[1];
            while (payload.length() % 4 != 0) {
                payload += "=";
            }

            byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
            return new String(decodedBytes);
        } else {
            logger.debug("token is null");
            return "";
        }
    }

    private boolean isTokenExpired(@Nullable String token) {
        if (token != null && cachedTokenExpiration != null) {
            Long cachedTokenExpiration2 = cachedTokenExpiration;
            return Instant.now().getEpochSecond() > cachedTokenExpiration2;
        }
        return true;
    }

    public @Nullable String getCccToken() throws LynkcoApiException {
        try {
            cccTokenLock.lock();

            if (cachedCccToken != null && !isTokenExpired(cachedCccToken)) {
                return cachedCccToken;
            }

            return refreshTokens();
        } finally {
            cccTokenLock.unlock();
        }
    }

    private String refreshTokens() throws LynkcoApiException {
        String refreshToken = cachedRefreshToken != null ? cachedRefreshToken
                : thing.getProperties().get(PROPERTY_REFRESH_TOKEN);

        if (refreshToken == null) {
            logger.error("Refresh token is null, re-authenticate");
            throw new LynkcoApiException("Token has expired, please re-authenticate",
                    LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED);
        }

        try {
            // Prepare headers
            Request request = httpClient.newRequest(TOKEN_URL).method(HttpMethod.POST)
                    .agent("LynkCo/3016 CFNetwork/1492.0.1 Darwin/23.3.0").header(HttpHeader.ACCEPT, "application/json")
                    .header(HttpHeader.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .header(HttpHeader.ACCEPT_ENCODING, "gzip, deflate, br").param("refresh_token", refreshToken)
                    .param("grant_type", "refresh_token");

            ContentResponse response = request.send();

            if (response.getStatus() == 200) {
                JsonObject tokens = JsonParser.parseString(response.getContentAsString()).getAsJsonObject();

                String newRefreshToken = tokens.has("refresh_token") ? tokens.get("refresh_token").getAsString() : null;
                if (newRefreshToken != null) {
                    logger.debug("Refreshed refresh token");
                    updateToken(PROPERTY_REFRESH_TOKEN, newRefreshToken);
                } else {
                    logger.error("New refresh token is null");
                    throw new LynkcoApiException("Token has expired, please re-authenticate",
                            LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED);
                }

                String accessToken = tokens.has("access_token") ? tokens.get("access_token").getAsString() : null;
                if (accessToken != null) {
                    updateToken(PROPERTY_ACCESS_TOKEN, accessToken);

                    String cccToken = sendDeviceLogin(accessToken);
                    if (cccToken != null) {
                        logger.debug("Refreshed CCC token");
                        updateToken(PROPERTY_CCC_TOKEN, cccToken);
                        return cccToken;
                    } else {
                        logger.error("New CCC token is null, please re-authenticate");
                        throw new LynkcoApiException("Token has expired, please re-authenticate",
                                LynkcoApiException.ErrorType.AUTHENTICATION_REQUIRED);
                    }
                } else {
                    logger.error("Access token is null");
                    throw new LynkcoApiException("Failed to obtain access token",
                            LynkcoApiException.ErrorType.AUTHENTICATION_FAILED);
                }
            } else {
                logger.error("Failed to get new refresh token");
                throw new LynkcoApiException("Failed to refresh tokens: " + response.getStatus(),
                        LynkcoApiException.ErrorType.NETWORK_ERROR);
            }
        } catch (Exception e) {
            clearTokens();
            throw new LynkcoApiException("Error refreshing tokens: " + e.getMessage(),
                    LynkcoApiException.ErrorType.NETWORK_ERROR);
        }
    }

    private @Nullable String sendDeviceLogin(String accessToken) throws LynkcoApiException {
        try {
            JsonObject data = new JsonObject();
            data.addProperty("deviceUuid", UUID.randomUUID().toString());
            data.addProperty("isLogin", true);

            Request request = httpClient
                    .newRequest("https://iam-service-prod.westeurope.cloudapp.azure.com/validate-session")
                    .method(HttpMethod.POST).header("user-agent", "LynkCo/3016 CFNetwork/1492.0.1 Darwin/23.3.0")
                    .header("accept", "application/json").header("content-type", "application/json")
                    .header("Accept-Encoding", "gzip, deflate, br").header("Connection", "keep-alive")
                    .header("X-Auth-Token", accessToken).header("api-version", "1")
                    .content(new StringContentProvider(data.toString()));

            ContentResponse response = request.send();

            if (response.getStatus() == 200) {
                JsonObject responseJson = JsonParser.parseString(response.getContentAsString()).getAsJsonObject();
                return responseJson.get("cccToken").getAsString();
            } else {
                logger.error("Failed to send device login, status: {}, response: {}", response.getStatus(),
                        response.getContentAsString());
                return null;
            }
        } catch (Exception e) {
            throw new LynkcoApiException("Error in device login: " + e.getMessage(),
                    LynkcoApiException.ErrorType.NETWORK_ERROR);
        }
    }

    public @Nullable String getUserId(String cccToken, String vin) throws LynkcoApiException {
        if (cachedUserId != null) {
            return cachedUserId;
        }

        try {
            Request request = httpClient.newRequest(
                    "https://delegated-driver-tls.aion.connectedcar.cloud/delegated-driver/api/delegateddriver/v1/vehicle/"
                            + vin + "/drivers")
                    .method(HttpMethod.GET).header("accept", "application/json")
                    .header("content-type", "application/json").header("Authorization", "Bearer " + cccToken);

            ContentResponse response = request.send();

            if (response.getStatus() == 200) {
                JsonObject responseJson = JsonParser.parseString(response.getContentAsString()).getAsJsonObject();
                if (responseJson.has("drivers") && responseJson.get("drivers").getAsJsonArray().size() > 0) {
                    String userId = responseJson.get("drivers").getAsJsonArray().get(0).getAsJsonObject().get("userId")
                            .getAsString();
                    updateToken(PROPERTY_USER_ID, userId);
                    return userId;
                } else {
                    logger.error("No drivers found in response");
                }
            } else {
                logger.error("Failed to get user id, status: {}, response: {}", response.getStatus(),
                        response.getContentAsString());
            }
        } catch (Exception e) {
            throw new LynkcoApiException("Error getting user ID: " + e.getMessage(),
                    LynkcoApiException.ErrorType.NETWORK_ERROR);
        }
        return null;
    }

    private void updateToken(String key, String value) {
        // Update both cache and properties
        switch (key) {
            case PROPERTY_CCC_TOKEN:
                cachedCccToken = value;
                break;
            case PROPERTY_ACCESS_TOKEN:
                cachedAccessToken = value;
                break;
            case PROPERTY_REFRESH_TOKEN:
                cachedRefreshToken = value;
                break;
            case PROPERTY_USER_ID:
                cachedUserId = value;
                break;
        }
        thing.setProperty(key, value);
    }

    private void clearTokens() {
        // Clear properties
        Map<String, String> properties = new HashMap<>(thing.getProperties());
        properties.remove(PROPERTY_CCC_TOKEN);
        properties.remove(PROPERTY_ACCESS_TOKEN);
        properties.remove(PROPERTY_REFRESH_TOKEN);
        // Don't clear user ID as it doesn't typically change
        thing.setProperties(properties);

        // Clear cache
        cachedCccToken = null;
        cachedAccessToken = null;
        cachedRefreshToken = null;
        cachedTokenExpiration = null;
    }
}
