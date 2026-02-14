/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
     * The value indicating an <em>unlimited</em> number of bytes to be allowed for a tenant.
     */
    public static final long UNLIMITED_BYTES = -1;
    /**
     * The value indicating an <em>unlimited</em> number of connections to be allowed for a tenant.
     */
    public static final int UNLIMITED_CONNECTIONS = -1;
    /**
     * The value indicating an <em>unlimited</em> number of minutes to be allowed for a tenant.
     */
    public static final long UNLIMITED_MINUTES = -1;
    /**
     * The value indicating <em>unlimited</em> time-to-live for downstream events.
     */
    public static final long UNLIMITED_TTL = -1;

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
     * The name of the property that indicates whether a CA cert can be used to
     * automatically provision new devices. 
     */
    public static final String FIELD_AUTO_PROVISIONING_ENABLED = "auto-provisioning-enabled";

    /**
     * The name of the property that contains the configuration options to limit 
     * the device connection duration of tenants.
     */
    public static final String FIELD_CONNECTION_DURATION = "connection-duration";

    /**
     * The name of the property that contains the configuration options for the data volume.
     */
    public static final String FIELD_DATA_VOLUME = "data-volume";
    /**
     * The name of the property that contains the date on which the data volume limit came into effect.
     */
    public static final String FIELD_EFFECTIVE_SINCE = "effective-since";
    /**
     * The name of the field that contains the extension fields.
     */
    public static final String FIELD_EXT = "ext";
    /**
     * The name of the property that contains the minimum message size in bytes.
     */
    public static final String FIELD_MINIMUM_MESSAGE_SIZE = "minimum-message-size";
    /**
     * The name of the property that contains the maximum number of bytes to be allowed for a tenant.
     */
    public static final String FIELD_MAX_BYTES = "max-bytes";
    /**
     * The name of the property that contains the maximum number of connections to be allowed for a tenant.
     */
    public static final String FIELD_MAX_CONNECTIONS = "max-connections";
    /**
     * The name of the property that contains the maximum connection duration in minutes to be allowed for a tenant.
     */
    public static final String FIELD_MAX_MINUTES = "max-minutes";
    /**
     * The name of the property that contains the maximum <em>time til disconnect</em> (seconds) that protocol
     * adapters should use for a tenant.
     */
    public static final String FIELD_MAX_TTD = "max-ttd";
    /**
     * The name of the property that contains the maximum <em>time to live</em> (seconds) for
     * downstream events that protocol adapters should use for a tenant.
     */
    public static final String FIELD_MAX_TTL = "max-ttl";
    /**
     * The name of the property that contains the maximum <em>time to live</em> (seconds) for
     * downstream QoS 0 telemetry messages that protocol adapters should use for a tenant.
     */
    public static final String FIELD_MAX_TTL_TELEMETRY_QOS0 = "max-ttl-telemetry-qos0";
    /**
     * The name of the property that contains the maximum <em>time to live</em> (seconds) for
     * downstream QoS 1 telemetry messages that protocol adapters should use for a tenant.
     */
    public static final String FIELD_MAX_TTL_TELEMETRY_QOS1 = "max-ttl-telemetry-qos1";
    /**
     * The name of the property that contains the maximum <em>time to live</em> (seconds) for
     * downstream command response messages that protocol adapters should use for a tenant.
     */
    public static final String FIELD_MAX_TTL_COMMAND_RESPONSE = "max-ttl-command-response";
    /**
     * The name of the property that indicates if nonce extension should be sent in OCSP request.
     */
    public static final String FIELD_OCSP_NONCE_ENABLED = "ocsp-nonce-enabled";
    /**
     * The name of the property that contains responder certificate which is used to verify OCSP response signature.
     */
    public static final String FIELD_OCSP_RESPONDER_CERT = "ocsp-responder-cert";
    /**
     * The name of the property that contains OCSP responder uri which will be used for OCSP revocation check.
     */
    public static final String FIELD_OCSP_RESPONDER_URI = "ocsp-responder-uri";
    /**
     * The name of the property that indicates if OCSP revocation check is enabled for trusted CA.
     */
    public static final String FIELD_OCSP_REVOCATION_ENABLED = "ocsp-revocation-enabled";
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
     * The name of the property that contains the period details for which the data usage is calculated.
     */
    public static final String FIELD_PERIOD = "period";
    /**
     * The name of the property that contains the number of days for which the data usage is calculated.
     */
    public static final String FIELD_PERIOD_NO_OF_DAYS = "no-of-days";
    /**
     * The name of the property that contains the mode of the period for which the data usage
     * is calculated.
     */
    public static final String FIELD_PERIOD_MODE = "mode";

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
     * The name of the property that contains the <em>time to live</em> (seconds) for
     * downstream QoS 0 telemetry messages that protocol adapters should use for a tenant
     * if a device does not specify a TTL explicitly.
     */
    public static final String FIELD_TTL_TELEMETRY_QOS0 = "ttl-telemetry-qos0";
    /**
     * The name of the property that contains the <em>time to live</em> (seconds) for
     * downstream QoS 1 telemetry messages that protocol adapters should use for a tenant
     * if a device does not specify a TTL explicitly.
     */
    public static final String FIELD_TTL_TELEMETRY_QOS1 = "ttl-telemetry-qos1";
    /**
     * The name of the property that contains the <em>time to live</em> (seconds) for
     * downstream command response messages that protocol adapters should use for a tenant
     * if a device does not specify a TTL explicitly.
     */
    public static final String FIELD_TTL_COMMAND_RESPONSE = "ttl-command-response";

    /**
     * The name of the property that defines the messaging type to be used for a tenant.
     */
    public static final String FIELD_EXT_MESSAGING_TYPE = "messaging-type";

    /**
     * The name of the property that indicates if cache invalidation is required on update operations.
     */
    public static final String FIELD_EXT_INVALIDATE_CACHE_ON_UPDATE = "invalidate-cache-on-update";

    /**
     * The name of the Tenant API endpoint.
     */
    public static final String TENANT_ENDPOINT = "tenant";

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
                    // fall through
                }
            }
            return custom;
        }
    }

    private TenantConstants() {
        // prevent instantiation
    }
}
