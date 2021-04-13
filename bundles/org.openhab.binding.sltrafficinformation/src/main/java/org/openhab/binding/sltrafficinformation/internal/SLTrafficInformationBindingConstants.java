/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.sltrafficinformation.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link SLTrafficInformationBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SLTrafficInformationBindingConstants {

    private static final String BINDING_ID = "sltrafficinformation";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_DEVIATIONS = new ThingTypeUID(BINDING_ID, "deviations");
    public static final ThingTypeUID THING_TYPE_REAL_TIME_INFO = new ThingTypeUID(BINDING_ID, "realTimeInformation");

    // List of all Channel ids
    public static final String CHANNEL_DEVIATIONS = "deviations";
    public static final String CHANNEL_REAL_TIME_INFO = "realTimeInformation";
    public static final String CHANNEL_NEXT_DEPARTURE = "nextDeparture";
    public static final String CHANNEL_SECOND_DEPARTURE = "secondDeparture";
    public static final String CHANNEL_THIRD_DEPARTURE = "thirdDeparture";
    public static final String CHANNEL_NEXT_DEPARTURE_DEVIATIONS = "nextDepartureDeviations";
    public static final String CHANNEL_SECOND_DEPARTURE_DEVIATIONS = "secondDepartureDeviations";
    public static final String CHANNEL_THIRD_DEPARTURE_DEVIATIONS = "thirdDepartureDeviations";
}
