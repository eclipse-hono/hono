/**
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.coap;

import java.util.Objects;

import org.eclipse.hono.auth.Device;

/**
 * Contains device and authentication related information pertaining to a CoAP request message.
 */
public final class RequestDeviceAndAuth {

    /**
     * Request message's origin device.
     */
    private final Device originDevice;
    /**
     * The authentication identifier of the request or {@code null}.
     */
    private final String authId;
    /**
     * Authenticated device or {@code null}.
     */
    private final Device authenticatedDevice;

    /**
     * Creates a new RequestDeviceAndAuth instance.
     *
     * @param originDevice The request message's origin device.
     * @param authId The authentication identifier of the request or {@code null} if the request is unauthenticated.
     * @param authenticatedDevice The authenticated device, or {@code null} if the request is unauthenticated.
     *                            A non-null value different from the originDevice means that the authenticatedDevice
     *                            represents a gateway, doing a request on behalf of the originDevice.
     * @throws NullPointerException if originDevice is {@code null}.
     */
    public RequestDeviceAndAuth(final Device originDevice, final String authId, final Device authenticatedDevice) {
        this.originDevice = Objects.requireNonNull(originDevice);
        this.authId = authId;
        this.authenticatedDevice = authenticatedDevice;
    }

    /**
     * Gets the request message's origin device.
     *
     * @return The device.
     */
    public Device getOriginDevice() {
        return originDevice;
    }

    /**
     * Gets the authentication identifier of the request.
     * <p>
     * Will be {@code null} for an unauthenticated request.
     *
     * @return The authentication identifier or {@code null}.
     */
    public String getAuthId() {
        return authId;
    }

    /**
     * Gets the device used for authenticating the request.
     * <p>
     * Will be {@code null} for an unauthenticated request.
     * <p>
     * A returned non-null value different from {@link #getOriginDevice()} represents
     * a gateway, doing a request on behalf of the originDevice.
     *
     * @return The authenticated device or {@code null}.
     */
    public Device getAuthenticatedDevice() {
        return authenticatedDevice;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[");
        sb.append(originDevice);
        if (authenticatedDevice == null) {
            sb.append(", unauthenticated");
        } else if (!authenticatedDevice.equals(originDevice)) {
            sb.append(", via gateway ").append(authenticatedDevice);
        }
        sb.append(']');
        return sb.toString();
    }
}
