/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

/**
 * Constants &amp; utility methods used throughout the Tenant API.
 */

public final class TenantConstants extends RequestResponseApiConstants {

    /**
     * The default number of seconds that a protocol adapter should wait for
     * an upstream command.
     */
    public static final int DEFAULT_MAX_TTD = 60; // seconds

    /**
     * The default message size is set to 0, which implies no minimum size is defined.
     */
    public static final int DEFAULT_MINIMUM_MESSAGE_SIZE = 0;
    /**
     *  Messages that are sent by the Hono client for the Tenant API use this as a prefix for the messageId.
     */
    public static final String MESSAGE_ID_PREFIX = "tenant-client";

    /**
     * The name of the property that contains configuration options for specific
     * protocol adapters.
     */
    public static final String FIELD_ADAPTERS = "adapters";
    /**
     * The name of the property that contains the type name of a protocol adapter.
     */
    public static final String FIELD_ADAPTERS_TYPE = "type";
    /**
     * The name of the property that indicates whether a protocol adapter requires
     * all devices to authenticate.
     */
    public static final String FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED = "device-authentication-required";
    /**
     * The name of the property that contains the maximum <em>time til disconnect</em> that protocol
     * adapters should use for a tenant.
     */
    public static final String FIELD_MAX_TTD = "max-ttd";
    /**
     * The name of the property that contains the algorithm used for a public key.
     */
    public static final String FIELD_PAYLOAD_KEY_ALGORITHM = "algorithm";
    /**
     * The name of the property that contains the Base64 encoded (binary) DER encoding of
     * the trusted certificate configured for a tenant.
     */
    public static final String FIELD_PAYLOAD_CERT = "cert";
    /**
     * The name of the property that contains the Base64 encoded DER encoding of the public key of the
     * trusted certificate authority configured for a tenant.
     */
    public static final String FIELD_PAYLOAD_PUBLIC_KEY = "public-key";
    /**
     * The name of the property that contains the trusted certificate authority configured for a tenant.
     */
    public static final String FIELD_PAYLOAD_TRUSTED_CA = "trusted-ca";

    /**
     * The name of the property that contains the minimum message size in bytes.
     */
    public static final String FIELD_MINIMUM_MESSAGE_SIZE = "minimum-message-size";

    /**
     * The name of the Tenant API endpoint.
     */
    public static final String TENANT_ENDPOINT = "tenant";

    /**
     * The name of the property that contains the configuration options for the resource limits.
     */
    public static final String FIELD_RESOURCE_LIMITS = "resource-limits";

    /**
     * The name of the property that defines tenant-specific tracing options.
     */
    public static final String FIELD_TRACING = "tracing";
    /**
     * The name of the property that defines in how far spans created when processing
     * messages for a tenant shall be recorded (sampled) by the tracing system.
     * The property contains a {@link TracingSamplingMode} value.
     */
    public static final String FIELD_TRACING_SAMPLING_MODE = "sampling-mode";
    /**
     * The name of the property that defines in how far spans created when processing
     * messages for a tenant and a particular auth-id shall be recorded (sampled)
     * by the tracing system.
     * The property contains a JsonObject with fields having a auth-id as name and
     * a {@link TracingSamplingMode} value.
     */
    public static final String FIELD_TRACING_SAMPLING_MODE_PER_AUTH_ID = "sampling-mode-per-auth-id";

    /**
     * Request actions that belong to the Tenant API.
     */
    public enum TenantAction {
        /**
         * The <em>add Tenant</em> operation.
         */
        add,
        /**
         * The <em>get Tenant</em> operation.
         */
        get,
        /**
         * The <em>update Tenant</em> operation.
         */
        update,
        /**
         * The <em>remove Tenant</em> operation.
         */
        remove,
        /**
         * The <em>custom</em> operation.
         */
        custom;

        /**
         * Construct a TenantAction from a subject.
         *
         * @param subject The subject from which the TenantAction needs to be constructed.
         * @return TenantAction The TenantAction as enum, or {@link TenantAction#custom} otherwise.
         */
        public static TenantAction from(final String subject) {
            if (subject != null) {
                try {
                    return TenantAction.valueOf(subject);
                } catch (final IllegalArgumentException e) {
                }
            }
            return custom;
        }
    }

    private TenantConstants() {
        // prevent instantiation
    }
}
