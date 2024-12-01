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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link LynkcoApiException} class handles exceptions for the Lynk&Co API
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class LynkcoApiException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum ErrorType {
        NETWORK_ERROR,
        AUTHENTICATION_FAILED,
        AUTHENTICATION_REQUIRED,
        MFA_REQUIRED,
        MFA_INVALID,
        MFA_EXPIRED,
        TOKEN_EXPIRED,
        REFRESH_TOKEN_EXPIRED,
        API_ERROR,
        UNKNOWN_ERROR
    }

    private final ErrorType errorType;

    public LynkcoApiException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
