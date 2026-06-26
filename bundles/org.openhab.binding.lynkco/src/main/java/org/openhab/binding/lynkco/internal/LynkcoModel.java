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
package org.openhab.binding.lynkco.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link LynkcoModel} maps the model codes returned by the gateway metadata
 * endpoint to a human readable model, the backend {@link Platform} and the default
 * propulsion type.
 *
 * Known model codes (from the official app / ha_lynkco_2025):
 * <ul>
 * <li>{@code CX11_A1} - Lynk&Co 01 (pre-2025, legacy CCC platform)</li>
 * <li>{@code CX11_A3} - Lynk&Co 01 (2025, gateway platform)</li>
 * <li>{@code E335} - Lynk&Co 02 (BEV, gateway platform)</li>
 * <li>{@code DX11} - Lynk&Co 08 (PHEV, gateway platform)</li>
 * </ul>
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public enum LynkcoModel {
    LYNKCO_01_LEGACY("CX11_A1", "Lynk&Co 01", Platform.CCC, "PHEV"),
    LYNKCO_01_2025("CX11_A3", "Lynk&Co 01 (2025)", Platform.GATEWAY, "PHEV"),
    LYNKCO_02("E335", "Lynk&Co 02", Platform.GATEWAY, "BEV"),
    LYNKCO_08("DX11", "Lynk&Co 08", Platform.GATEWAY, "PHEV"),
    UNKNOWN("", "Unknown", Platform.GATEWAY, "PHEV");

    private final String code;
    private final String displayName;
    private final Platform platform;
    private final String defaultPropulsion;

    LynkcoModel(String code, String displayName, Platform platform, String defaultPropulsion) {
        this.code = code;
        this.displayName = displayName;
        this.platform = platform;
        this.defaultPropulsion = defaultPropulsion;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getDefaultPropulsion() {
        return defaultPropulsion;
    }

    /**
     * Resolve a model from a gateway model code (prefix match to tolerate trim/sub-variants).
     *
     * @param code the model code from {@code vehicle_metadata}
     * @return the matching {@link LynkcoModel}, or {@link #UNKNOWN} if not recognized
     */
    public static LynkcoModel fromCode(String code) {
        for (LynkcoModel model : values()) {
            if (model != UNKNOWN && !model.code.isEmpty() && code.startsWith(model.code)) {
                return model;
            }
        }
        return UNKNOWN;
    }
}
