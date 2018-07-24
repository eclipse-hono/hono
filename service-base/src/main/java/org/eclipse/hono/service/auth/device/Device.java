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

package org.eclipse.hono.service.auth.device;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.util.CredentialsConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.RequiredTypeException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;


/**
 * An authenticated client of a protocol adapter representing a device.
 * <p>
 * The device's identity and authorities are contained in a Java Web Token.
 */
public class Device implements User {

    private final JsonObject principal;
    private final Set<Object> authorities = new HashSet<>();

    /**
     * Creates a new device for a token.
     * <p>
     * The token is expected to contain the device identifier in the <em>sub</em> claim and
     * the tenant identifier in the <em>ten</em> claim.
     * 
     * @param token The token asserting the device's identity.
     * @throws NullPointerException if the token does not contain a tenant and device identifier.
     */
    public Device(final Jws<Claims> token) {
        this(Objects.requireNonNull(token).getBody().get("ten", String.class), token.getBody().getSubject());
        try {
            final Set<?> aut = token.getBody().get("aut", Set.class);
            if (aut != null) {
                authorities.addAll(aut);
            }
        } catch (final RequiredTypeException e) {
            // token contains no authorities claim
        }
    }

    /**
     * Creates a new device for a tenant and device identifier.
     * 
     * @param tenantId The tenant.
     * @param deviceId The device identifier.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    public Device(final String tenantId, final String deviceId) {
        super();
        this.principal = getPrincipal(tenantId, deviceId);
    }

    private JsonObject getPrincipal(final String tenantId, final String deviceId) {
        return new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, Objects.requireNonNull(tenantId))
                .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, Objects.requireNonNull(deviceId));
    }

    /**
     * Checks if this device has a particular authority.
     * <p>
     * In order for the check to succeed, the JWT must
     * <ul>
     * <li>not be expired</li>
     * <li>contain the given authorities in its <em>aut</em> claim</li>
     * </ul>
     * 
     * @param authority The authority to check for.
     * @param resultHandler The handler to notify about the outcome of the check.
     */
    @Override
    public User isAuthorized(final String authority, final Handler<AsyncResult<Boolean>> resultHandler) {
        for (final Object item : authorities) {
            if (authority.equals(item)) {
                resultHandler.handle(Future.succeededFuture(Boolean.TRUE));
                return this;
            }
        }
        resultHandler.handle(Future.succeededFuture(Boolean.FALSE));
        return this;
    }

    @Override
    public JsonObject principal() {
        return principal;
    }

    @Override
    public void setAuthProvider(final AuthProvider authProvider) {
        // NOOP, JWT is self contained
    }

    /**
     * This method does nothing.
     */
    @Override
    public User clearCache() {
        return this;
    }

    /**
     * Gets the identifier of the tenant this device belongs to.
     * 
     * @return The identifier.
     */
    public String getTenantId() {
        return principal.getString(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID);
    }

    /**
     * Gets this device's identifier.
     * 
     * @return The identifier.
     */
    public String getDeviceId() {
        return principal.getString(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);
    }

    @Override
    public String toString() {
        return String.format("device [%s: %s, %s: %s]",
                CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID,
                getDeviceId(),
                CredentialsConstants.FIELD_PAYLOAD_TENANT_ID,
                getTenantId());
    }

    /**
     * Gets the device id in an address structure.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @return tenantId and deviceId as an address.
     */
    public static String asAddress(final String tenantId, final String deviceId) {
        return String.format("%s/%s", tenantId, deviceId);
    }

    /**
     * Gets the device id in an address structure.
     * @param device The device.
     * @return tenantId and deviceId as an address.
     */
    public static String asAddress(final Device device) {
        return String.format("%s/%s", device.getTenantId(), device.getDeviceId());
    }
}
