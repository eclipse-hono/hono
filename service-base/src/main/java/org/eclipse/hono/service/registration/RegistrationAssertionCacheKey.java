/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.registration;

/**
 * A utility class for creating cache keys
 */
public final class RegistrationAssertionCacheKey {

    /**
     * The token representing the asserted status.
     */
    private final String token;

    /**
     * The tenant that the device is expected to belong to.
     */
    private final String tenantId;

    /**
     * The device that is expected to be the subject of the assertion.
     */
    private final String deviceId;

    /**
     * Key for validation cache (which is used to improve registration assertion validations) consisting of token,
     * tenantId and deviceId
     * @param token token, which is part of the cache key
     * @param tenantId tenantId, which is part of the cache key
     * @param deviceId deviceId, which is part of the cache key
     */
    RegistrationAssertionCacheKey(final String token, final String tenantId, final String deviceId) {
        this.token = token;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
    }

    /**
     * Getter method for token
     *
     * @return token
     */
    public String getToken() {
        return token;
    }

    /**
     * Getter method for tenantId
     *
     * @return tenantId
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Getter method for deviceId
     *
     * @return deviceId
     */
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RegistrationAssertionCacheKey that = (RegistrationAssertionCacheKey) o;

        return token.equals(that.token) && tenantId.equals(that.tenantId) && deviceId.equals(that.deviceId);
    }

    @Override
    public int hashCode() {
        int result = token.hashCode();
        result = 31 * result + tenantId.hashCode();
        result = 31 * result + deviceId.hashCode();
        return result;
    }
}
