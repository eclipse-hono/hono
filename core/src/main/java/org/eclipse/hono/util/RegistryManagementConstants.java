/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import java.util.HashSet;
import java.util.Set;

/**
 * Constants &amp; utility methods used throughout the Device Management API.
 */
public final class RegistryManagementConstants extends RequestResponseApiConstants {

    /**
     * The current version of the API.
     */
    public static final String API_VERSION = "v1";

    /**
     * The vert.x event bus event message <em>subject</em> property to use for the <em>create</em> operations.
     */
    public static final String ACTION_CREATE = "create";
    /**
     * The vert.x event bus event message <em>subject</em> property to use for the <em>get</em> operations.
     */
    public static final String ACTION_GET = "get";
    /**
     * The vert.x event bus event message <em>subject</em> property to use for the <em>delete</em> operations.
     */
    public static final String ACTION_DELETE = "delete";
    /**
     * The vert.x event bus event message <em>subject</em> property to use for the <em>update</em> operations.
     */
    public static final String ACTION_UPDATE = "update";

    /**
     * The name of the Credentials Registration HTTP API endpoint.
     */
    public static final String DEVICES_HTTP_ENDPOINT = "devices";

    /**
     * The name of the Device Registration HTTP API endpoint.
     */
    public static final String CREDENTIALS_HTTP_ENDPOINT = "credentials";

    /**
     * The name of the property that contains credentials object in event bus message.
     */
    public static final String CREDENTIALS_OBJECT = "credentials";

    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_DEVICE_MANAGEMENT_IN = "device.mgmt.in";
    /**
     * The vert.x event bus address to which inbound credentials management messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_CREDENTIALS_MANAGEMENT_IN = "credentials.mgmt.in";
    /**
     * The name of the Device Registration HTTP API endpoint.
     */
    public static final String REGISTRATION_HTTP_ENDPOINT = "devices";
    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_TENANT_IN = "tenant.in";
    /**
     * The vert.x event bus address to which inbound management messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_TENANT_MANAGEMENT_IN = "tenant.mgmt.in";
    /**
     * The name of the HTTP endpoint for the Tenant API.
     */
    public static final String TENANT_HTTP_ENDPOINT = "tenants";

    /// FIELDS
    //CREDENTIALS
    /**
     * The name of the field that contains the type of credentials.
     */
    public static final String FIELD_TYPE                        = "type";
    /**
     * The name of the field that contains the authentication identifier.
     */
    public static final String FIELD_AUTH_ID                     = "auth-id";
    /**
     * The name of the field that contains the secret(s) of the credentials.
     */
    public static final String FIELD_SECRETS                     = "secrets";
    /**
     * The name of the field that contains the extension fields.
     */
    public static final String FIELD_EXT                         = "ext";
    /**
     * The name of the field that contains the number of credentials contained in a message.
     */
    public static final String FIELD_CREDENTIALS_TOTAL           = "total";
    /**
     * The name of the field that contains the id of the entity (e.g. secret id).
     */
    public static final String FIELD_ID = "id";

    /* secrets fields */
    /**
     * The name of the field that contains the password hash.
     */
    public static final String FIELD_SECRETS_PWD_HASH            = "pwd-hash";
    /**
     * The name of the field that contains the clear text password.
     */
    public static final String FIELD_SECRETS_PWD_PLAIN           = "pwd-plain";
    /**
     * The name of the field that contains the salt for the password hash.
     */
    public static final String FIELD_SECRETS_SALT                = "salt";
    /**
     * The name of the field that contains the name of the hash function used for a hashed password.
     */
    public static final String FIELD_SECRETS_HASH_FUNCTION       = "hash-function";
    /**
     * The name of the field that contains a (pre-shared) key.
     */
    public static final String FIELD_SECRETS_KEY                 = "key";
    /**
     * The name of the field that contains the earliest point in time a secret may be used
     * for authentication.
     */
    public static final String FIELD_SECRETS_NOT_BEFORE          = "not-before";
    /**
     * The name of the field that contains the latest point in time a secret may be used
     * for authentication.
     */
    public static final String FIELD_SECRETS_NOT_AFTER           = "not-after";
    /**
     * The name of the field that contains the comment about the secret.
     */
    public static final String FIELD_SECRETS_COMMENT           = "comment";
    /**
     * The Credential service's endpoint name.
     */
    public static final String CREDENTIALS_ENDPOINT              = "credentials";

    /**
     * The type name that indicates an X.509 client certificate secret.
     */
    public static final String SECRETS_TYPE_X509_CERT            = "x509-cert";
    /**
     * The type name that indicates a hashed password secret.
     */
    public static final String SECRETS_TYPE_HASHED_PASSWORD      = "hashed-password";
    /**
     * The type name that indicates a pre-shared key secret.
     */
    public static final String SECRETS_TYPE_PRESHARED_KEY        = "psk";
    /**
     * The name of the field that contains the pattern to use for matching authentication identifiers.
     */
    public static final String SPECIFIER_WILDCARD                = "*";

    /**
     * The name of the BCrypt hash function.
     */
    public static final String HASH_FUNCTION_BCRYPT              = "bcrypt";
    /**
     * The name of the SHA-256 hash function.
     */
    public static final String HASH_FUNCTION_SHA256              = "sha-256";
    /**
     * The name of the SHA-512 hash function.
     */
    public static final String HASH_FUNCTION_SHA512              = "sha-512";
    /**
     * The name of the default hash function to use for hashed passwords if not set explicitly.
     */
    public static final String DEFAULT_HASH_FUNCTION             = HASH_FUNCTION_SHA256;

    //TENANTS
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
     * The name of the JSON property containing the device identifier.
     */
    public static final String FIELD_DEVICE = "device";
    /**
     * The name of the JSON array containing device registration information for a tenant.
     */
    public static final String FIELD_DEVICES = "devices";
    /**
     * The name of the property that contains the maximum <em>time til disconnect</em> that protocol
     * adapters should use for a tenant.
     */
    public static final String FIELD_MAX_TTD = "max-ttd";
    /**
     * The name of the property that contains the minimum message size in bytes.
     */
    public static final String FIELD_MINIMUM_MESSAGE_SIZE = "minimum-message-size";
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
     * The name of the property that contains the configuration options for the resource limits.
     */
    public static final String FIELD_RESOURCE_LIMITS = "resource-limits";
    /**
     * The name of the JSON property containing the tenant ID.
     */
    public static final String FIELD_TENANT = "tenant";
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
     * The name of the JSON property containing the last modification date and time.
     */
    public static final String FIELD_UPDATED_ON = "updatedOn";

    /**
     * The name of the JSON property containing the version of the tenant or device or credentials information.
     */
    public static final String FIELD_VERSION = "version";

    private static final Set<String> ACTIONS = new HashSet<>(
            Arrays.asList(ACTION_CREATE, ACTION_GET, ACTION_DELETE, ACTION_UPDATE));

    private RegistryManagementConstants() {
        // prevent instantiation
    }

    /**
     * Checks if a given string represents a valid action.
     *
     * @param action The string to check.
     * @return {@code true} if the given string is a supported action.
     */
    public static boolean isValidAction(final String action) {
        if (action == null) {
            return false;
        } else {
            return ACTIONS.contains(action);
        }
    }
}
