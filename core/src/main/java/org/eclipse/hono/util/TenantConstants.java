/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.util;

/**
 * Constants &amp; utility methods used throughout the Tenant API.
 */

public final class TenantConstants extends RequestResponseApiConstants {

    /**
     *  Messages that are sent by the Hono client for the Tenant API use this as a prefix for the messageId.
     */
    public static final String MESSAGE_ID_PREFIX = "tenant-client";

    /* message payload fields */
    public static final String FIELD_ADAPTERS                    = "adapters";
    public static final String FIELD_ADAPTERS_TYPE               = "type";
    public static final String FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED = "device-authentication-required";
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
     * The name of the property that contains the RFC 2253 formatted <em>subject DN</em> of the CA certificate
     * that has been configured for a tenant.
     */
    public static final String FIELD_PAYLOAD_SUBJECT_DN = "subject-dn";
    /**
     * The name of the property that contains the trusted certificate authority configured for a tenant.
     */
    public static final String FIELD_PAYLOAD_TRUSTED_CA = "trusted-ca";
    /**
     * The name of the Tenant API endpoint.
     */
    public static final String TENANT_ENDPOINT = "tenant";

    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_TENANT_IN = "tenant.in";

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

    private TenantConstants () {
    }
}
