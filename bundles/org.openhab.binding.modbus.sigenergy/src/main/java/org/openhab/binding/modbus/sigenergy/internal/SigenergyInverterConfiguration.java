/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.modbus.sigenergy.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link SigenergyInverterConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SigenergyInverterConfiguration {

    /**
     * The inverter's own Modbus unit ID (1-246), configured in the mySigen app. Distinct from the
     * plant unit ID 247 on the parent bridge.
     */
    public int unitId = 1;
    /**
     * Default is slower than the plant poll to keep the shared endpoint within its request budget.
     */
    public int pollInterval = 10000;
    public int maxTries = 3;
}
