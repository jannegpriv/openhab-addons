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
 * The {@link Platform} enumerates the two Lynk&Co backend platforms the binding supports.
 *
 * <ul>
 * <li>{@link #CCC} - the legacy "connectedcar.cloud" platform used by pre-2025 Lynk&Co 01.</li>
 * <li>{@link #GATEWAY} - the modern signed mobile-app gateway used by Lynk&Co 01 (2025), 02 and 08.</li>
 * </ul>
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public enum Platform {
    CCC,
    GATEWAY;

    public static Platform fromString(String value) {
        for (Platform platform : values()) {
            if (platform.name().equalsIgnoreCase(value)) {
                return platform;
            }
        }
        return GATEWAY;
    }
}
