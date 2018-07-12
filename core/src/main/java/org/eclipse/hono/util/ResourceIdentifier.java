/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A unique identifier for a resource within Hono.
 * <p>
 * Each resource identifier consists of an arbitrary number of path segments.
 * The first segment always contains the name of the <em>endpoint</em> that the
 * resource belongs to.
 * <p>
 * Within the <em>telemetry</em> and <em>registration</em> endpoints the remaining two
 * segments have the following semantics:
 * <ol>
 * <li>the <em>tenant ID</em></li>
 * <li>an (optional) <em>device ID</em></li>
 * </ol>
 * <p>
 * The basic scheme is {@code <endpoint>/[tenant]/[device-id]}
 * <p>
 * Examples:
 * <ol>
 * <li>telemetry/DEFAULT_TENANT/4711</li>
 * <li>telemetry</li>
 * <li>telemetry/</li>
 * <li>telemetry//</li>
 * <li>telemetry//4711</li>
 * <li>telemetry/DEFAULT_TENANT</li>
 * <li>telemetry/DEFAULT_TENANT/</li>
 * </ol>
 *
 */
public final class ResourceIdentifier {

    private static final int IDX_ENDPOINT = 0;
    private static final int IDX_TENANT_ID = 1;
    private static final int IDX_RESOURCE_ID = 2;
    private String[] resourcePath;
    private String resource;
    private String basePath;

    private ResourceIdentifier(final String resource, final boolean assumeDefaultTenant) {
        final String[] path = resource.split("\\/");
        final List<String> pathSegments = new ArrayList<>(Arrays.asList(path));
        if (assumeDefaultTenant) {
            pathSegments.add(1, Constants.DEFAULT_TENANT);
        }
        setResourcePath(pathSegments.toArray(new String[pathSegments.size()]));
    }

    private ResourceIdentifier(final String endpoint, final String tenantId, final String resourceId) {
        setResourcePath(new String[]{endpoint, tenantId, resourceId});
    }

    private ResourceIdentifier(final ResourceIdentifier resourceIdentifier, final String tenantId, final String resourceId) {
        String[] path = resourceIdentifier.getResourcePath();
        if (path.length < 3) {
            path = new String[3];
            path[IDX_ENDPOINT] = resourceIdentifier.getEndpoint();
        }
        path[IDX_TENANT_ID] = tenantId;
        path[IDX_RESOURCE_ID] = resourceId;
        setResourcePath(path);
    }

    private ResourceIdentifier(final String[] path) {
        setResourcePath(path);
    }

    private void setResourcePath(final String[] path) {
        final List<String> pathSegments = new ArrayList<>();
        boolean pathContainsNullSegment = false;
        for (final String segment : path) {
            if (segment == null) {
                pathContainsNullSegment = true;
            } else if (pathContainsNullSegment) {
                throw new IllegalArgumentException("path may contain trailing null segments only");
            } else {
                pathSegments.add(segment);
            }
        }
        this.resourcePath = pathSegments.toArray(new String[pathSegments.size()]);
        if (resourcePath.length > IDX_TENANT_ID && resourcePath[IDX_TENANT_ID].length() == 0) {
            resourcePath[IDX_TENANT_ID] = null;
        }
        if (resourcePath.length > IDX_RESOURCE_ID && resourcePath[IDX_RESOURCE_ID].length() == 0) {
            resourcePath[IDX_RESOURCE_ID] = null;
        }
        createStringRepresentation();
    }

    /**
     * Gets this resource identifier as path segments.
     *
     * @return the segments.
     */
    public String[] toPath() {
        return Arrays.copyOf(resourcePath, resourcePath.length);
    }

    private String createStringRepresentation(final int startIdx) {

        final StringBuilder b = new StringBuilder();
        for (int i = startIdx; i < resourcePath.length; i++) {
            b.append(resourcePath[i]);
            if (i < resourcePath.length - 1) {
                b.append("/");
            }
        }
        return b.toString();
    }

    private void createStringRepresentation() {
        resource = createStringRepresentation(0);

        final StringBuilder b = new StringBuilder(getEndpoint());
        if (getTenantId() != null) {
            b.append("/").append(getTenantId());
        }
        basePath = b.toString();
    }

    /**
     * Creates a resource identifier from its string representation.
     * <p>
     * The given string is split up into segments using a forward slash as the separator. The first segment is used as
     * the endpoint, the second segment is used as the tenant ID and the third segment (if present) is used as the
     * device ID.
     * </p>
     *
     * @param resource the resource string to parse.
     * @return the resource identifier.
     * @throws NullPointerException if the given string is {@code null}.
     * @throws IllegalArgumentException if the given string does not represent a valid resource identifier.
     */
    public static ResourceIdentifier fromString(final String resource) {
        Objects.requireNonNull(resource);
        return new ResourceIdentifier(resource, false);
    }

    /**
     * Creates a resource identifier from its string representation assuming the default tenant.
     * <p>
     * The given string is split up into segments using a forward slash as the separator. The first segment is used as
     * the endpoint and the second segment (if present) is used as the device ID. The tenant ID is always set to
     * {@link Constants#DEFAULT_TENANT}.
     * </p>
     *
     * @param resource the resource string to parse.
     * @return the resource identifier.
     * @throws NullPointerException if the given string is {@code null}.
     * @throws IllegalArgumentException if the given string does not represent a valid resource identifier.
     */
    public static ResourceIdentifier fromStringAssumingDefaultTenant(final String resource) {
        Objects.requireNonNull(resource);
        return new ResourceIdentifier(resource, true);
    }

    /**
     * Creates a resource identifier for an endpoint, a tenantId and a resourceId.
     *
     * @param endpoint the endpoint of the resource.
     * @param tenantId the tenant identifier (may be {@code null}).
     * @param resourceId the resource identifier (may be {@code null}).
     * @return the resource identifier.
     * @throws NullPointerException if endpoint is {@code null}.
     */
    public static ResourceIdentifier from(final String endpoint, final String tenantId, final String resourceId) {
        Objects.requireNonNull(endpoint);
        return new ResourceIdentifier(endpoint, tenantId, resourceId);
    }

    /**
     * Creates a resource identifier for an endpoint from an other resource identifier. It uses all data from
     * the original resource identifier but sets the new tenantId and resourceId.
     *
     * @param resourceIdentifier original resource identifier to copy values from.
     * @param tenantId the tenant identifier (may be {@code null}).
     * @param resourceId the resource identifier (may be {@code null}).
     * @return the resource identifier.
     * @throws NullPointerException if endpoint is {@code null}.
     */
    public static ResourceIdentifier from(final ResourceIdentifier resourceIdentifier, final String tenantId, final String resourceId) {
        Objects.requireNonNull(resourceIdentifier);
        return new ResourceIdentifier(resourceIdentifier, tenantId, resourceId);
    }

    /**
     * Creates a resource identifier from path segments.
     * <p>
     * The given path will be stripped of any trailing {@code null}
     * segments.
     * </p>
     *
     * @param path the segments of the resource path.
     * @return the resource identifier.
     * @throws NullPointerException if path is {@code null}.
     * @throws IllegalArgumentException if the path contains no segments or contains non-trailing
     *                                  {@code null} segments.
     */
    public static ResourceIdentifier fromPath(final String[] path) {
        Objects.requireNonNull(path);
        if (path.length == 0) {
            throw new IllegalArgumentException("path must have at least one segment");
        } else {
            return new ResourceIdentifier(path);
        }
    }

    /**
     * @return the endpoint
     */
    public String getEndpoint() {
        return resourcePath[IDX_ENDPOINT];
    }

    /**
     * @return the tenantId or {@code null} if not set.
     */
    public String getTenantId() {
        if (resourcePath.length > IDX_TENANT_ID) {
            return resourcePath[IDX_TENANT_ID];
        } else {
            return null;
        }
    }

    /**
     * @return the resourceId or {@code null} if not set.
     */
    public String getResourceId() {
        if (resourcePath.length > IDX_RESOURCE_ID) {
            return resourcePath[IDX_RESOURCE_ID];
        } else {
            return null;
        }
    }

    /**
     * Gets a copy of the full resource path of this identifier, including extended elements.
     *
     * @return The full resource path.
     */
    public String[] getResourcePath() {
        return Arrays.copyOf(resourcePath, resourcePath.length);
    }

    /**
     * Gets a string representation of this resource identifier.
     * <p>
     * The string representation consists of all path segments separated by a
     * forward slash ("/").
     * </p>
     *
     * @return the resource id.
     */
    @Override
    public String toString() {
        return resource;
    }

    /**
     * Gets a string representation of this resource identifier's
     * <em>endpoint</em> and <em>tenantId</em>.
     *
     * @return A string consisting of the properties separated by a forward slash.
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Gets a string representation of the resource identifiers' parts without the base path.
     *
     * @return A string with all parts after the base bath.
     * @see ResourceIdentifier#getBasePath()
     */
    public String getPathWithoutBase() {
        return createStringRepresentation(IDX_RESOURCE_ID);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ResourceIdentifier that = (ResourceIdentifier) o;
        return resourcePath != null ? Arrays.equals(resourcePath, that.resourcePath) : that.resourcePath == null;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(resourcePath);
    }
}
