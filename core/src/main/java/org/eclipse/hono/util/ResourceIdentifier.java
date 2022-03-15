/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A unique identifier for a resource within Hono.
 * <p>
 * Each resource identifier consists of an arbitrary number of path segments.
 * The first segment always contains the name of the <em>endpoint</em> that the
 * resource belongs to.
 * <p>
 * The basic scheme is {@code <endpoint>/[tenant]/[resource-id]}. The resource-id
 * usually represents a device identifier.
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

    private final String[] resourcePath;
    private final String resource;
    private final String basePath;

    private ResourceIdentifier(final String... path) {

        Objects.requireNonNull(path);
        if (path.length == 0) {
            throw new IllegalArgumentException("path must have at least one segment");
        } else if (Strings.isNullOrEmpty(path[0])) {
            throw new IllegalArgumentException("path must not start with an empty segment");
        }

        String[] pathToUse = path;
        if (path.length == 3) {
            // resource identifier has been created using scheme endpoint/tenant/device
            // remove trailing nulls
            for (int i = path.length; i > 0; i--) {
                if (path[i - 1] != null) {
                    pathToUse = Arrays.copyOfRange(path, 0, i);
                    break;
                }
            }
        }

        for (int i = 0; i < pathToUse.length; i++) {
            final String segment = pathToUse[i];
            pathToUse[i] = Strings.isNullOrEmpty(segment) ? null : segment;
        }
        resourcePath = pathToUse;
        resource = createStringRepresentation(0);
        basePath = Optional.ofNullable(getTenantId())
                .map(tenant -> getEndpoint() + "/" + tenant)
                .orElseGet(this::getEndpoint);
    }

    private String createStringRepresentation(final int startIdx) {

        final StringBuilder b = new StringBuilder();
        for (int i = startIdx; i < resourcePath.length; i++) {
            if (resourcePath[i] != null) {
                b.append(resourcePath[i]);
            }
            if (i < resourcePath.length - 1) {
                b.append("/");
            }
        }
        return b.toString();
    }

    /**
     * Checks whether the given string is a valid resource identifier string representation.
     *
     * @param resource the resource string to parse.
     * @return {@code true} if the given string is a valid resource identifier string.
     */
    public static boolean isValid(final String resource) {
        return !Strings.isNullOrEmpty(resource) && !resource.startsWith("/");
    }

    /**
     * Creates a resource identifier from its string representation.
     * <p>
     * The given string is split up into segments using a forward slash as the separator. The first segment is used as
     * the endpoint, the second segment is used as the tenant ID and the third segment (if present) is used as the
     * resourceId part.
     * </p>
     *
     * @param resource the resource string to parse.
     * @return the resource identifier.
     * @throws NullPointerException if the given string is {@code null}.
     * @throws IllegalArgumentException if the given string is empty or contains an empty first segment.
     */
    public static ResourceIdentifier fromString(final String resource) {
        Objects.requireNonNull(resource);
        return new ResourceIdentifier(resource.split("/"));
    }

    /**
     * Creates a resource identifier for an endpoint, a tenantId and a resourceId.
     *
     * @param endpoint the endpoint of the resource.
     * @param tenantId the tenant identifier (may be {@code null}).
     * @param resourceId the resourceId part (may be {@code null}).
     * @return the resource identifier.
     * @throws NullPointerException if endpoint is {@code null}.
     * @throws IllegalArgumentException if endpoint is empty.
     */
    public static ResourceIdentifier from(final String endpoint, final String tenantId, final String resourceId) {
        Objects.requireNonNull(endpoint);
        if (endpoint.isEmpty()) {
            throw new IllegalArgumentException("endpoint must not be empty");
        }
        final String[] path;
        if (resourceId == null) {
            if (tenantId == null) {
                path = new String[] { endpoint };
            } else {
                path = new String[] { endpoint, tenantId };
            }
        } else {
            path = new String[] { endpoint, tenantId, resourceId };
        }
        return new ResourceIdentifier(path);
    }

    /**
     * Creates a resource identifier for an endpoint based on another resource identifier.
     * <p>
     * It uses all data from the original resource identifier but sets the new tenantId and resourceId.
     *
     * @param resourceIdentifier original resource identifier to copy values from.
     * @param tenantId the tenant identifier (may be {@code null}).
     * @param resourceId the resourceId part (may be {@code null}).
     * @return the resource identifier.
     * @throws NullPointerException if resourceIdentifier is {@code null}.
     */
    public static ResourceIdentifier from(
            final ResourceIdentifier resourceIdentifier,
            final String tenantId,
            final String resourceId) {

        Objects.requireNonNull(resourceIdentifier);
        String[] path = resourceIdentifier.getResourcePath();
        if (path.length < 3) {
            path = new String[3];
            path[IDX_ENDPOINT] = resourceIdentifier.getEndpoint();
        }
        path[IDX_TENANT_ID] = tenantId;
        path[IDX_RESOURCE_ID] = resourceId;
        return new ResourceIdentifier(path);
    }

    /**
     * Creates a resource identifier from path segments.
     *
     * @param path the segments of the resource path.
     * @return the resource identifier.
     * @throws NullPointerException if path is {@code null}.
     * @throws IllegalArgumentException if the path contains no segments or starts with an empty segment.
     */
    public static ResourceIdentifier fromPath(final String... path) {
        return new ResourceIdentifier(path);
    }

    /**
     * Gets this resource identifier as path segments.
     *
     * @return the segments.
     */
    public String[] toPath() {
        return Arrays.copyOf(resourcePath, resourcePath.length);
    }

    /**
     * Gets the element at a given index of the resource path.
     *
     * @param index The index (starting from zero).
     * @return The element at the index.
     * @throws ArrayIndexOutOfBoundsException if the resource path's length is shorter than the index.
     */
    public String elementAt(final int index) {
        return resourcePath[index];
    }

    /**
     * Gets the number of elements in the resource path.
     *
     * @return The resource path length.
     */
    public int length() {
        return resourcePath.length;
    }

    /**
     * Gets the endpoint part of this identifier.
     *
     * @return the endpoint (not {@code null} or empty).
     */
    public String getEndpoint() {
        return resourcePath[IDX_ENDPOINT];
    }

    /**
     * Gets the tenant part of this identifier.
     *
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
     * Gets the resourceId part of this identifier.
     * <p>
     * E.g. for a resource <em>telemetry/DEFAULT_TENANT/4711</em>, the return value will be <em>4711</em>.
     *
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
     * @return the string representation.
     */
    @Override
    public String toString() {
        return resource;
    }

    /**
     * Gets a string representation of this resource identifier's <em>endpoint</em> and <em>tenantId</em>.
     * <p>
     * E.g. for a resource <em>telemetry/DEFAULT_TENANT/4711</em>, the return value will be
     * <em>telemetry/DEFAULT_TENANT</em>.
     *
     * @return A string consisting of the properties separated by a forward slash.
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Gets a string representation of the resource identifiers' parts without the base path.
     * <p>
     * E.g. for a resource <em>command_response/myTenant/deviceId/some/path</em>, the return value will be
     * <em>deviceId/some/path</em>.
     * <p>
     * If this resource identifier doesn't contain any additional path segments after the base path, an empty string is
     * returned.
     *
     * @return A string with all parts after the base bath.
     * @see ResourceIdentifier#getBasePath()
     */
    public String getPathWithoutBase() {
        return createStringRepresentation(IDX_RESOURCE_ID);
    }

    /**
     * Checks if this resource identifier contains the event endpoint.
     *
     * @return {@code true} if this resource's endpoint is either
     *         {@value EventConstants#EVENT_ENDPOINT} or {@value EventConstants#EVENT_ENDPOINT_SHORT}.
     */
    public boolean hasEventEndpoint() {
        return EventConstants.isEventEndpoint(getEndpoint());
    }

    /**
     * Checks if this resource identifier contains the telemetry endpoint.
     *
     * @return {@code true} if this resource's endpoint is either
     *         {@value TelemetryConstants#TELEMETRY_ENDPOINT} or
     *         {@value TelemetryConstants#TELEMETRY_ENDPOINT_SHORT}.
     */
    public boolean hasTelemetryEndpoint() {
        return TelemetryConstants.isTelemetryEndpoint(getEndpoint());
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
