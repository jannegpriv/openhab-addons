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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openhab.binding.lynkco.internal.LynkcoBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests gateway request signing paths.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class GatewayPlatformTest {

    private static final String VIN = "TESTVIN";

    @Test
    public void gatewayHeadersMatchCurrentOfficialAppProfile() {
        assertEquals("2.67.0", GATEWAY_APP_VERSION);
        assertEquals("8455", GATEWAY_APP_BUILD_NUMBER);
    }

    @Test
    public void signaturePathExcludesClimateQueryParameters() {
        String url = GATEWAY_COMMAND_BASE + "/vehicle/" + VIN + "/command/auto_conditioning_start?temp=22&level=2";

        assertEquals("/vehicle/TESTVIN/command/auto_conditioning_start", GatewayPlatform.signaturePath(url));
    }

    @Test
    public void signaturePathExcludesChargeLimitQueryParameter() {
        String url = GATEWAY_COMMAND_BASE + "/vehicle/" + VIN + "/command/set_charge_limit?percent=80";

        assertEquals("/vehicle/TESTVIN/command/set_charge_limit", GatewayPlatform.signaturePath(url));
    }

    @Test
    public void signaturePathKeepsNonGatewayBasePath() {
        String url = GATEWAY_IAM_BASE + "/validate-session";

        assertEquals("/mobile-app/iamservice/v1/validate-session", GatewayPlatform.signaturePath(url));
    }
}
