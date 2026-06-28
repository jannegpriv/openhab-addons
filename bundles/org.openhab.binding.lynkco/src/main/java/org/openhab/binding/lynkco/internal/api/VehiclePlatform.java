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

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.lynkco.internal.dto.LynkcoDTO;

/**
 * The {@link VehiclePlatform} abstracts the two Lynk&Co backend platforms (legacy CCC and the
 * modern signed mobile-app gateway) behind a common data and command interface, so that the
 * vehicle handler does not need to know which platform a given vehicle uses.
 *
 * Commands that only exist on the modern gateway have default implementations that throw an
 * {@link LynkcoApiException} with {@link LynkcoApiException.ErrorType#UNSUPPORTED}; platforms that
 * support them override the relevant methods.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public interface VehiclePlatform {

    /**
     * Fetch the full vehicle data (status + records) for the given VIN.
     *
     * @param vin the vehicle identification number
     * @return a populated {@link LynkcoDTO}
     */
    LynkcoDTO fetchVehicleData(String vin) throws LynkcoApiException;

    /**
     * List the vehicles available on the account for discovery.
     *
     * @return a map of VIN to model code; empty if the platform does not support listing
     */
    default Map<String, String> listVehicles() throws LynkcoApiException {
        return Map.of();
    }

    // --- Climate / engine -------------------------------------------------------------------

    void startClimate(String vin, int climateLevel, int durationInMinutes) throws LynkcoApiException;

    void stopClimate(String vin) throws LynkcoApiException;

    void startEngine(String vin, int durationInMinutes) throws LynkcoApiException;

    void stopEngine(String vin) throws LynkcoApiException;

    // --- Doors ------------------------------------------------------------------------------

    void lockDoors(String vin) throws LynkcoApiException;

    void unlockDoors(String vin) throws LynkcoApiException;

    // --- Horn / lights ----------------------------------------------------------------------

    void startFlashLights(String vin) throws LynkcoApiException;

    void stopFlashLights(String vin) throws LynkcoApiException;

    void startHonk(String vin) throws LynkcoApiException;

    void stopHonk(String vin) throws LynkcoApiException;

    void startHonkFlash(String vin) throws LynkcoApiException;

    // --- Gateway-only features (default: unsupported) ---------------------------------------

    default void setChargeLimit(String vin, int percent) throws LynkcoApiException {
        throw unsupported("setChargeLimit");
    }

    default void startHeaters(String vin, List<String> heaters) throws LynkcoApiException {
        throw unsupported("startHeaters");
    }

    default void stopHeaters(String vin, List<String> heaters) throws LynkcoApiException {
        throw unsupported("stopHeaters");
    }

    default void startVentilation(String vin) throws LynkcoApiException {
        throw unsupported("startVentilation");
    }

    default void stopVentilation(String vin) throws LynkcoApiException {
        throw unsupported("stopVentilation");
    }

    default void openSunroof(String vin) throws LynkcoApiException {
        throw unsupported("openSunroof");
    }

    default void closeSunroof(String vin) throws LynkcoApiException {
        throw unsupported("closeSunroof");
    }

    default void unlockGlovebox(String vin) throws LynkcoApiException {
        throw unsupported("unlockGlovebox");
    }

    private static LynkcoApiException unsupported(String operation) {
        return new LynkcoApiException(operation + " is not supported on this vehicle platform",
                LynkcoApiException.ErrorType.UNSUPPORTED);
    }
}
