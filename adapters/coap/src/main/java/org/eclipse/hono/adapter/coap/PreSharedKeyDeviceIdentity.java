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

import org.eclipse.hono.adapter.auth.device.DeviceCredentials;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;

/**
 * Helper class to represent the device identity based on pre shared key identity.
 */
public class PreSharedKeyDeviceIdentity implements DeviceCredentials {

    private static final Logger LOG = LoggerFactory.getLogger(PreSharedKeyDeviceIdentity.class);

    /**
     * Identity that the device wants to authenticate as.
     */
    private final String authId;
    /**
     * Tenant id the device belongs to.
     */
    private final String tenantId;
    /**
     * Precalculated hash.
     */
    private final int hash;

    /**
     * Create a new instance.
     *
     * @param tenantId The tenant id that the device belongs to.
     * @param authId The identity that the device wants to authenticate as.
     */
    public PreSharedKeyDeviceIdentity(final String tenantId, final String authId) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.authId = Objects.requireNonNull(authId);
        this.hash = tenantId.hashCode() + 31 * authId.hashCode();
    }

    /**
     * Creates a new instance.
     *
     * @param identity The identity provided by the device using the pre shared key handshake.
     * @param separateRegex The regular expression to split identity for multi tenant.
     * @param span The current open tracing span or {@code null}.
     * @return The instance of the created object. Will be {@code null} if the identity is {@code null} or if the
     *         identity does not comply with the structure defined by the separateRegex.
     */
    public static final PreSharedKeyDeviceIdentity create(final String identity, final String separateRegex,
            final Span span) {

        if (identity == null) {
            LOG.trace("username must not be null");
            if (span != null) {
                span.log("PSK identity must not be null");
            }
            return null;
        }

        if (separateRegex == null) {
            return new PreSharedKeyDeviceIdentity(Constants.DEFAULT_TENANT, identity);
        } else {
            // multi tenantId -> <userId><sep-regex><tenantId> (default)
            final String[] userComponents = identity.split(separateRegex, 2);
            if (userComponents.length != 2) {
                LOG.trace("username [{}] does not comply with expected pattern [<authId>@<tenantId>]", identity);
                if (span != null) {
                    span.log("PSK identity [" + identity + "] does not comply with expected pattern [<authId>"
                            + separateRegex + "<tenantId>]");
                }
                return null;
            }
            return new PreSharedKeyDeviceIdentity(userComponents[1], userComponents[0]);
        }
    }

    /**
     * Gets the identity that the device wants to authenticate as.
     * <p>
     * This is the <em>auth ID</em> part parsed from the identity.
     *
     * @return The identity.
     */
    @Override
    public final String getAuthId() {
        return authId;
    }

    /**
     * Gets the tenant that the device claims to belong to.
     * <p>
     * This is the <em>tenant ID</em> part parsed from the identity.
     *
     * @return The tenant.
     */
    @Override
    public final String getTenantId() {
        return tenantId;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!getClass().isInstance(obj)) {
            return false;
        }
        final PreSharedKeyDeviceIdentity other = (PreSharedKeyDeviceIdentity) obj;
        if (authId == null) {
            if (other.authId != null) {
                return false;
            }
        } else if (!authId.equals(other.authId)) {
            return false;
        }
        if (tenantId == null) {
            if (other.tenantId != null) {
                return false;
            }
        } else if (!tenantId.equals(other.tenantId)) {
            return false;
        }
        return true;
    }

    @Override
    public String getType() {
        return CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY;
    }
}
