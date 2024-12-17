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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link LynkcoHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.lynkco", service = ThingHandlerFactory.class)
public class LynkcoHandlerFactory extends BaseThingHandlerFactory {

    private final Logger logger = LoggerFactory.getLogger(LynkcoHandlerFactory.class);

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_VEHICLE, THING_TYPE_BRIDGE);
    private final Gson gson;
    private HttpClient httpClient;
    private static final boolean DEBUG = false;

    @Activate
    public LynkcoHandlerFactory(@Reference HttpClientFactory httpClientFactory) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.gson = new Gson();
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_VEHICLE.equals(thingTypeUID)) {
            return new LynkcoVehicleHandler(thing);
        } else if (THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            return new LynkcoBridgeHandler((Bridge) thing, httpClient, gson);
        }
        return null;
    }

    @Reference
    protected void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        logger.debug("setHttpClientFactory this: {}", this);

        // Create an SSL Context Factory with SSL verification disabled
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();

        // Disable all SSL verification
        sslContextFactory.setTrustAll(true);
        sslContextFactory.setValidateCerts(false);
        sslContextFactory.setValidatePeerCerts(false);
        sslContextFactory.setEndpointIdentificationAlgorithm(null);

        // Create new HttpClient with SSL disabled
        this.httpClient = new HttpClient(sslContextFactory);

        // Only add proxy configuration if in DEBUG mode
        if (DEBUG) {
            try {
                ProxyConfiguration proxyConfig = httpClient.getProxyConfiguration();
                HttpProxy proxy = new HttpProxy("127.0.0.1", 8090);
                proxyConfig.getProxies().add(proxy);
            } catch (Exception e) {
                logger.error("Failed to configure proxy: {}", e.getMessage(), e);
            }
        }

        try {
            this.httpClient.start();
            logger.debug("HTTP client configured with SSL verification disabled");
        } catch (Exception e) {
            logger.error("Failed to start HTTP client: {}", e.getMessage(), e);
        }
    }
}
