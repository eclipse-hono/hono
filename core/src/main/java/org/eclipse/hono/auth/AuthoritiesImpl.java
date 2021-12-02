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

package org.eclipse.hono.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;

/**
 * A map backed implementation of authorities on resources and operations.
 *
 */
public final class AuthoritiesImpl implements Authorities {

    /**
     * The prefix used to indicate an authority on a resource.
     */
    public static final String PREFIX_RESOURCE = "r:";
    /**
     * The prefix used to indicate an authority on an operation.
     */
    public static final String PREFIX_OPERATION = "o:";

    private static final Logger LOG = LoggerFactory.getLogger(AuthoritiesImpl.class);
    private static final String TEMPLATE_OP = PREFIX_OPERATION + "%s:%s";
    private static final String TEMPLATE_RESOURCE = PREFIX_RESOURCE + "%s";
    // holds mapping resources -> activities
    private final Map<String, String> authorities = new HashMap<>();

    /**
     * Creates authorities from claims from a JWT.
     *
     * @param claims The claims object to retrieve authorities from.
     * @return The authorities.
     * @throws NullPointerException if claims is {@code null}.
     */
    public static Authorities from(final Claims claims) {
        Objects.requireNonNull(claims);
        final AuthoritiesImpl result = new AuthoritiesImpl();
        claims.forEach((key, value) -> {
            if ((key.startsWith(PREFIX_OPERATION) || key.startsWith(PREFIX_RESOURCE)) && value instanceof String) {
                LOG.trace("adding claim [key: {}, value: {}]", key, value);
                result.authorities.put(key, (String) value);
            } else {
                LOG.trace("ignoring unsupported claim [key: {}]", key);
            }
        });
        return result;
    }

    private static String getOperationKey(final String endpoint, final String tenant, final String operation) {
        if (tenant == null) {
            return String.format(TEMPLATE_OP, endpoint, operation);
        } else {
            return String.format(TEMPLATE_OP, endpoint + "/" + tenant, operation);
        }
    }

    private static String getResourceKey(final String endpoint, final String tenant) {
        if (tenant == null) {
            return String.format(TEMPLATE_RESOURCE, endpoint);
        } else {
            return String.format(TEMPLATE_RESOURCE, endpoint + "/" + tenant);
        }
    }

    /**
     * Adds an authority to execute an operation.
     *
     * @param resource The resource the operation belongs to.
     * @param operation The operation.
     * @return This instance for command chaining.
     */
    public AuthoritiesImpl addOperation(final String resource, final String operation) {
        return addOperation(resource, null, operation);
    }

    /**
     * Adds an authority to execute an operation.
     *
     * @param endpoint The endpoint segment of the resource the operation belongs to.
     * @param tenant The tenant segment of the resource the operation belongs to.
     * @param operation The operation.
     * @return This instance for command chaining.
     */
    public AuthoritiesImpl addOperation(final String endpoint, final String tenant, final String operation) {
        authorities.put(getOperationKey(endpoint, tenant, operation), String.valueOf(Activity.EXECUTE.getCode()));
        return this;
    }

    /**
     * Adds an authority to perform one or more activities on a resource.
     *
     * @param resource The resource.
     * @param activities The activities.
     * @return This instance for command chaining.
     */
    public AuthoritiesImpl addResource(final String resource, final Activity... activities) {
        return addResource(resource, null, activities);
    }

    /**
     * Adds an authority to perform one or more activities on a resource.
     *
     * @param endpoint The endpoint segment of the resource.
     * @param tenant The tenant segment of the resource.
     * @param activities The activities.
     * @return This instance for command chaining.
     */
    public AuthoritiesImpl addResource(final String endpoint, final String tenant, final Activity... activities) {
        final StringBuilder b = new StringBuilder();
        for (final Activity a : activities) {
            b.append(a.getCode());
        }
        authorities.put(getResourceKey(endpoint, tenant), b.toString());
        return this;
    }

    /**
     * Adds all authorities contained in another object to this instance.
     *
     * @param authoritiesToAdd The object containing the authorities to add.
     * @return This instance for command chaining.
     */
    public AuthoritiesImpl addAll(final Authorities authoritiesToAdd) {
        authoritiesToAdd.asMap().entrySet().stream()
            .filter(entry -> entry.getValue() instanceof String)
            .forEach(entry -> {
                final String value = (String) entry.getValue();
                LOG.trace("adding authority [key: {}, activities: {}]", entry.getKey(), value);
                authorities.put(entry.getKey(), value);
            });
        return this;
    }

    @Override
    public boolean isAuthorized(final ResourceIdentifier resource, final Activity intent) {

        boolean allowed = false;
        if (resource.getResourceId() != null) {
            allowed = isAuthorized(String.format(TEMPLATE_RESOURCE, resource.toString()), intent);
        }
        if (!allowed && resource.getTenantId() != null) {
            allowed = isAuthorized(String.format(TEMPLATE_RESOURCE, resource.getEndpoint() + "/" + resource.getTenantId()), intent) ||
                    isAuthorized(String.format(TEMPLATE_RESOURCE, resource.getEndpoint() + "/*"), intent);
        }
        if (!allowed) {
            allowed = isAuthorized(String.format(TEMPLATE_RESOURCE, resource.getEndpoint()), intent) ||
                    isAuthorized(String.format(TEMPLATE_RESOURCE, "*"), intent);
        }
        return allowed;
    }

    @Override
    public boolean isAuthorized(final ResourceIdentifier resource, final String operation) {

        boolean allowed = false;
        if (resource.getResourceId() != null) {
            allowed = isAuthorized(String.format(TEMPLATE_OP, resource.toString(), operation), Activity.EXECUTE) ||
                    isAuthorized(String.format(TEMPLATE_OP, resource.toString(), "*"), Activity.EXECUTE);
        }
        if (!allowed && resource.getTenantId() != null) {
            allowed = isAuthorized(String.format(TEMPLATE_OP, resource.getEndpoint() + "/" + resource.getTenantId(), operation), Activity.EXECUTE) ||
                    isAuthorized(String.format(TEMPLATE_OP, resource.getEndpoint() + "/" + resource.getTenantId(), "*"), Activity.EXECUTE) ||
                    isAuthorized(String.format(TEMPLATE_OP, resource.getEndpoint() + "/*", operation), Activity.EXECUTE) ||
                    isAuthorized(String.format(TEMPLATE_OP, resource.getEndpoint() + "/*", "*"), Activity.EXECUTE);
        }
        if (!allowed) {
            allowed = isAuthorized(String.format(TEMPLATE_OP, resource.getEndpoint(), operation), Activity.EXECUTE) ||
                    isAuthorized(String.format(TEMPLATE_OP, resource.getEndpoint(), "*"), Activity.EXECUTE) ||
                    isAuthorized(String.format(TEMPLATE_OP, "*", operation), Activity.EXECUTE) ||
                    isAuthorized(String.format(TEMPLATE_OP, "*", "*"), Activity.EXECUTE);
        }
        return allowed;
    }

    @Override
    public Map<String, Object> asMap() {
        final Map<String, Object> result = new HashMap<>(authorities);
        return result;
    }

    boolean isAuthorized(final String key, final Activity intent) {
        boolean result = false;
        final String grantedActivities = authorities.get(key);
        if (grantedActivities == null) {
            LOG.trace("no claim for key [{}]", key);
        } else {
            result = grantedActivities.contains(String.valueOf(intent.getCode())) ||
                    grantedActivities.equals("*");
            LOG.trace("found claim [key: {}, activities: {}] {}matching intent [{}]", key, grantedActivities, result ? "" : "not ", intent.name());
        }
        return result;
    }
}
