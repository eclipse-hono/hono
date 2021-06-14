/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.registry;

import org.eclipse.hono.client.ClientErrorException;

/**
 * An exception indicating that the device is either not registered or disabled.
 */
public class DeviceDisabledOrNotRegisteredException extends ClientErrorException {
    /**
     * Resource key for the error message.
     */
    public static final String MESSAGE_KEY = "CLIENT_ERROR_DEVICE_DISABLED_OR_NOT_REGISTERED";

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a tenant and an error code.
     *
     * @param tenant The tenant that the device belongs to or {@code null} if unknown.
     * @param errorCode The code representing the erroneous outcome.
     */
    public DeviceDisabledOrNotRegisteredException(final String tenant, final int errorCode) {
        super(tenant, errorCode, getLocalizedMessage(MESSAGE_KEY));
    }
}
