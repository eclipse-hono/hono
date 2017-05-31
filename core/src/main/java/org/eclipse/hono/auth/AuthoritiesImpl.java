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

package org.eclipse.hono.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.jsonwebtoken.Claims;

/**
 * A map backed implementation of authorities on resources and operations.
 *
 */
public final class AuthoritiesImpl implements Authorities {

    private static final String opTemplate = "o:%s:%s";
    private static final String resTemplate = "r:%s";
    // holds mapping resources -> activities
    private final Map<String, String> authorities = new HashMap<>();

    /**
     * Creates empty authorities.
     */
    public AuthoritiesImpl() {
    }

    /**
     * Creates authorities from claims from a JWT.
     * 
     * @param claims The claims object to retrieve authorities from.
     * @return The authorities.
     * @throws NullPointerException is claims is {@code null}.
     */
    public static Authorities from(final Claims claims) {
        Objects.requireNonNull(claims);
        AuthoritiesImpl result = new AuthoritiesImpl();
        claims.forEach((key, value) -> {
            if ((key.startsWith("o:") || key.startsWith("r:")) && value instanceof String) {
                result.authorities.put(key, (String) value);
            } else {
                // unsupported claim
            }
        });
        return result;
    }

    private static String getOperationKey(final String endpoint, final String tenant, final String operation) {
        if (tenant == null) {
            return String.format(opTemplate, endpoint, operation);
        } else {
            return String.format(opTemplate, endpoint + "/" + tenant, operation);
        }
    }

    private static String getResourceKey(final String endpoint, final String tenant) {
        if (tenant == null) {
            return String.format(resTemplate, endpoint);
        } else {
            return String.format(resTemplate, endpoint + "/" + tenant);
        }
    }

    public AuthoritiesImpl addOperation(final String resource, final String operation) {
        return addOperation(resource, null, operation);
    }

    public AuthoritiesImpl addOperation(final String endpoint, final String tenant, final String operation) {
        authorities.put(getOperationKey(endpoint, tenant, operation), String.valueOf(Activity.EXECUTE.getCode()));
        return this;
    }

    public AuthoritiesImpl addResource(final String resource, final Activity... activities) {
        return addResource(resource, null, activities);
    }

    public AuthoritiesImpl addResource(final String endpoint, final String tenant, final Activity... activities) {
        StringBuilder b = new StringBuilder();
        for (Activity a : activities) {
            b.append(a.getCode());
        }
        authorities.put(getResourceKey(endpoint, tenant), b.toString());
        return this;
    }

    public AuthoritiesImpl addAll(final Authorities authoritiesToAdd) {
        authoritiesToAdd.asMap().entrySet().stream()
            .filter(entry -> entry.getValue() instanceof String)
            .forEach(entry -> authorities.put(entry.getKey(), (String) entry.getValue()));
        return this;
    }

    @Override
    public boolean isAuthorized(final String endpoint, final String tenant, final Activity intent) {

        if (tenant == null) {
            return isAuthorized(String.format(resTemplate, endpoint), intent);
        } else {
            return isAuthorized(String.format(resTemplate, endpoint + "/" + tenant), intent) ||
                    isAuthorized(String.format(resTemplate, endpoint + "/*"), intent);
        }
    }

    @Override
    public boolean isAuthorized(final String endpoint, final String tenant, final String operation) {

        if (tenant == null) {
            return isAuthorized(String.format(opTemplate, endpoint, operation), Activity.EXECUTE);
        } else {
            return isAuthorized(String.format(opTemplate, endpoint + "/" + tenant, operation), Activity.EXECUTE) ||
                    isAuthorized(String.format(opTemplate, endpoint + "/*", operation), Activity.EXECUTE);
        }
    }

    @Override
    public Map<String, Object> asMap() {
        Map<String, Object> result = new HashMap<>();
        result.putAll(authorities);
        return result;
    }

    boolean isAuthorized(final String key, final Activity intent) {
        String grantedActivities = authorities.get(key);
        if (grantedActivities == null) {
            return false;
        } else {
            return grantedActivities.contains(String.valueOf(intent.getCode()));
        }
    }
}