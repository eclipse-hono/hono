/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import java.util.Objects;

/**
 * A unique identifier of a resource within Hono.
 * <p>
 * Each resource identifier consists of up to three parts:
 * <ol>
 * <li>an <em>endpoint</em></li>
 * <li>a <em>tenant ID</em></li>
 * <li>an (optional) <em>device ID</em></li>
 * </ol>
 * </p>
 */
public final class ResourceIdentifier {

    private String resourceId;
    private String endpoint;
    private String tenantId;
    private String deviceId;

    private ResourceIdentifier(final String resourceId, final boolean assumeDefaultTenant) {
        String[] path = resourceId.split("\\/");
        if (assumeDefaultTenant) {
            if (path.length == 0) {
                throw new IllegalArgumentException("resource identifier must at least contain an endpoint");
            } else if (path.length > 2) {
                throw new IllegalArgumentException("resource identifer must not contain more than 2 segments");
            } else {
                setFields(path[0], Constants.DEFAULT_TENANT, path.length == 2 ? path[1] : null);
            }
        } else {
            if (path.length < 2) {
                throw new IllegalArgumentException(
                        "resource identifier must at least contain an endpoint and the tenantId");
            } else if (path.length > 3) {
                throw new IllegalArgumentException("resource identifer must not contain more than 3 segments");
            } else {
                setFields(path[0], path[1], path.length == 3 ? path[2] : null);
            }
        }
    }

    private ResourceIdentifier(final String endpoint, final String tenantId, final String deviceId) {
       setFields(endpoint, tenantId, deviceId);
    }

    private void setFields(final String endpoint, final String tenantId, final String deviceId) {
        if (deviceId != null) {
            this.resourceId = String.format("%s/%s/%s", endpoint, tenantId, deviceId);
        }
        else {
            this.resourceId = String.format("%s/%s", endpoint, tenantId);
        }
        this.endpoint = endpoint;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
    }

    /**
     * Creates a resource identifier from its string representation.
     * <p>
     * The given string is split up into segments using a forward slash as the separator. The first segment is used as
     * the endpoint, the second segment is used as the tenant ID and the third segment (if present) is used as the
     * device ID.
     * </p>
     * 
     * @param resourceId the resource identifier string to parse.
     * @return the resource identifier.
     * @throws NullPointerException if the given string is {@code null}.
     * @throws IllegalArgumentException if the given string does not represent a valid resource identifier.
     */
    public static ResourceIdentifier fromString(final String resourceId) {
        Objects.requireNonNull(resourceId);
        return new ResourceIdentifier(resourceId, false);
    }

    /**
     * Creates a resource identifier from its string representation assuming the default tenant.
     * <p>
     * The given string is split up into segments using a forward slash as the separator. The first segment is used as
     * the endpoint and the second segment (if present) is used as the device ID. The tenant ID is always set to
     * {@link Constants#DEFAULT_TENANT}.
     * </p>
     * 
     * @param resourceId the resource identifier string to parse.
     * @return the resource identifier.
     * @throws NullPointerException if the given string is {@code null}.
     * @throws IllegalArgumentException if the given string does not represent a valid resource identifier.
     */
    public static ResourceIdentifier fromStringAssumingDefaultTenant(final String resourceId) {
        Objects.requireNonNull(resourceId);
        return new ResourceIdentifier(resourceId, true);
    }

    /**
     * Creates a resource identifier from endpoint, tenantId and optionally deviceId.
     *
     * @param endpoint the endpoint of the resource.
     * @param tenantId the tenant identifier.
     * @param deviceId the device identifier, may be {@code null}.
     * @return the resource identifier.
     * @throws NullPointerException if endpoint or tenantId is {@code null}.
     */
    public static ResourceIdentifier from(final String endpoint, final String tenantId, final String deviceId) {
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(tenantId);
        return new ResourceIdentifier(endpoint, tenantId, deviceId);
    }

    /**
     * @return the endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @return the tenantId
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * @return the deviceId
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Creates a string representation of this resource identifier.
     * <p>
     * The string representation consists of the endpoint, the tenant and (optionally) the device ID all separated by a
     * forward slash.
     * </p>
     */
    @Override
    public String toString() {
        return resourceId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final ResourceIdentifier that = (ResourceIdentifier) o;

        if (endpoint != null ? !endpoint.equals(that.endpoint) : that.endpoint != null)
            return false;
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null)
            return false;
        return deviceId != null ? deviceId.equals(that.deviceId) : that.deviceId == null;
    }

    @Override
    public int hashCode() {
        int result = endpoint != null ? endpoint.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        result = 31 * result + (deviceId != null ? deviceId.hashCode() : 0);
        return result;
    }
}
