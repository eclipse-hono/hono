/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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
 * Constants &amp; utility methods used throughout the Device Management API.
 */
public final class RegistryManagementConstants extends RequestResponseApiConstants {

    /**
     * The current version of the API.
     */
    public static final String API_VERSION = "v1";

    /**
     * The name of the Credentials Registration HTTP API endpoint.
     */
    public static final String DEVICES_HTTP_ENDPOINT = "devices";

    /**
     * The name of the Device Registration HTTP API endpoint.
     */
    public static final String CREDENTIALS_HTTP_ENDPOINT = "credentials";

    /**
     * The name of the HTTP endpoint for the Tenant API.
     */
    public static final String TENANT_HTTP_ENDPOINT = "tenants";

    // FIELD DEFINITIONS

    // DEVICES

    /**
     * The name of the field that contains the identifiers of those gateways that may act on behalf of the device.
     */
    public static final String FIELD_VIA = RegistrationConstants.FIELD_VIA;

    /**
     * The name of the field that contains the identifiers of groups of gateways that may act on behalf of the device.
     */
    public static final String FIELD_VIA_GROUPS = "viaGroups";

    /**
     * The name of the field that contains the status data for the device.
     * The status object contains the creation date, the last edit date and the last user.
     */
    public static final String FIELD_STATUS = "status";

    /**
     * The name of the field that contains the creation date of the device.
     */
    public static final String FIELD_STATUS_CREATION_DATE = "created";

    /**
     * The name of the field that contains the last update date of the device.
     */
    public static final String FIELD_STATUS_LAST_UPDATE = "updated";

    /**
     * The name of the field that contains the last user that edited the device.
     */
    public static final String FIELD_STATUS_LAST_USER = "last-user";

    /**
     * The name of the field that contains the name of a service that can be used to transform downstream messages
     * uploaded by the device before they are forwarded to downstream consumers.
     */
    public static final String FIELD_DOWNSTREAM_MESSAGE_MAPPER = "downstream-message-mapper";

    /**
     * The name of the field that contains the name of a service that can be used to transform upstream commands
     * to be sent to the device.
     */
    public static final String FIELD_UPSTREAM_MESSAGE_MAPPER = "upstream-message-mapper";

    /**
     * The name of the field that contains the names of the gateway groups that the (gateway) device is a member of.
     */
    public static final String FIELD_MEMBER_OF = "memberOf";

    /**
     * The name of the field that contains the JSON pointer corresponding to the field used for filtering devices.
     */
    public static final String FIELD_FILTER_FIELD = "field";

    /**
     * The name of the query parameter that contains the filter JSON object for search devices operation.
     */
    public static final String PARAM_FILTER_JSON = "filterJson";

    /**
     * The name of the field that contains the operator used for filtering devices.
     */
    public static final String FIELD_FILTER_OPERATOR = "op";

    /**
     * The name of the field that contains the value used for filtering devices.
     */
    public static final String FIELD_FILTER_VALUE = "value";

    /**
     * The name of the query parameter that contains the page offset for search devices operation.
     */
    public static final String PARAM_PAGE_OFFSET = "pageOffset";

    /**
     * The name of the query parameter that contains the page size for search devices operation.
     */
    public static final String PARAM_PAGE_SIZE = "pageSize";

    /**
     * The name of the field that contains sort direction used by search devices operation to sort the result set.
     */
    public static final String FIELD_SORT_DIRECTION = "direction";

    /**
     * The name of the query parameter that contains the sort JSON object used by search devices operation to sort the
     * result set.
     */
    public static final String PARAM_SORT_JSON = "sortJson";

    /**
     * The name of the field that contains the total number of objects in the result set of the search devices
     * operation.
     */
    public static final String FIELD_RESULT_SET_SIZE = "total";

    /**
     * The name of the field that contains the result of the search devices operation.
     */
    public static final String FIELD_RESULT_SET_PAGE = "result";

    // CREDENTIALS

    /**
     * The name of the field that contains a comment for the credentials.
     */
    public static final String FIELD_COMMENT                     = "comment";
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
     * The name of the property that defines the messaging type to be used for a tenant.
     */
    public static final String FIELD_EXT_MESSAGING_TYPE = "messaging-type";

    /**
     * The name of the field that contains the id of the entity (e.g. secret id).
     */
    public static final String FIELD_ID = "id";

    // SECRETS

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
     * The name of the BCrypt hash function.
     */
    public static final String HASH_FUNCTION_BCRYPT              = "bcrypt";
    /**
     * The name of the SHA-256 hash function.
     */
    public static final String HASH_FUNCTION_SHA256              = "sha-256";

    // TENANTS

    /**
     * The default message size is set to 0, which implies no minimum size is defined.
     */
    public static final int DEFAULT_MINIMUM_MESSAGE_SIZE = 0;
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
     * The name of the property that contains the configuration options to limit 
     * the device connection duration of tenants.
     */
    public static final String FIELD_CONNECTION_DURATION = "connection-duration";
    /**
     * The name of the property that contains the configuration options for a tenant's data volume limits.
     */
    public static final String FIELD_DATA_VOLUME = "data-volume";
    /**
     * The name of the JSON array containing device registration information for a tenant.
     */
    public static final String FIELD_DEVICES = "devices";
    /**
     * The name of the property that contains the date on which the data volume limit came into effect.
     */
    public static final String FIELD_EFFECTIVE_SINCE = "effective-since";
    /**
     * The name of the property that contains the maximum number of bytes to be allowed for a tenant.
     */
    public static final String FIELD_MAX_BYTES = "max-bytes";
    /**
     * The name of the property that contains the maximum number of connections to be allowed for a tenant.
     */
    public static final String FIELD_MAX_CONNECTIONS = "max-connections";
    /**
     * The name of the property that contains the maximum number of credentials allowed per device of a tenant.
     */
    public static final String FIELD_MAX_CREDENTIALS_PER_DEVICE = "max-credentials-per-device";
    /**
     * The name of the property that contains the maximum number of devices to be allowed for a tenant.
     */
    public static final String FIELD_MAX_DEVICES = "max-devices";
    /**
     * The name of the property that contains the maximum connection duration in minutes to be allowed for a tenant.
     */
    public static final String FIELD_MAX_MINUTES = "max-minutes";
    /**
     * The name of the property that contains the maximum <em>time to live</em> (seconds) for
     * downstream events that protocol adapters should use for a tenant.
     */
    public static final String FIELD_MAX_TTL = "max-ttl";
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
     * The name of the property that contains the period details for which the data usage is calculated.
     */
    public static final String FIELD_PERIOD = "period";
    /**
     * The name of the property that contains the mode of the period for which the data usage
     * is calculated.
     */
    public static final String FIELD_PERIOD_MODE = "mode";
    /**
     * The name of the property that contains the number of days for which the data usage is calculated.
     */
    public static final String FIELD_PERIOD_NO_OF_DAYS = "no-of-days";
    /**
     * The name of the property that contains the configuration options for the registration limits.
     */
    public static final String FIELD_REGISTRATION_LIMITS = "registration-limits";
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
     * The default regular expression to validate tenant IDs supplied when creating tenants are legal.
     */
    public static final String DEFAULT_TENANT_ID_PATTERN = "^[a-zA-Z0-9-_\\.]+$";

    /**
     * The default regular expression to validate device IDs supplied when creating devices are legal.
     */
    public static final String DEFAULT_DEVICE_ID_PATTERN = "^[a-zA-Z0-9-_\\.:=]+$";

    /**
     * The name of the field that contains a boolean indicating if an entity was auto-provisioned.
     */
    public static final String FIELD_AUTO_PROVISIONED   = "auto-provisioned";

    /**
     * The name of the property that indicates whether a CA cert can be used to
     * automatically provision new devices. 
     */
    public static final String FIELD_AUTO_PROVISIONING_ENABLED = "auto-provisioning-enabled";

    /**
     * The name of the property that indicates whether a unregistered device that authenticates with
     * a client certificate should be auto-provisioned as a gateway. 
     */
    public static final String FIELD_AUTO_PROVISION_AS_GATEWAY = "auto-provision-as-gateway";

    /**
     * The name of the property that defines the device identifier template for the devices/gateways
     * being auto-provisioned.
     */
    public static final String FIELD_AUTO_PROVISIONING_DEVICE_ID_TEMPLATE = "auto-provisioning-device-id-template";
    /**
     * The name of the place holder for subject DN in the device-id template used during auto-provisioning.
     */
    public static final String PLACEHOLDER_SUBJECT_DN = "{{subject-dn}}";
    /**
     * The name of the place holder for subject DN's Common Name in the device-id template used during auto-provisioning.
     */
    public static final String PLACEHOLDER_SUBJECT_CN = "{{subject-cn}}";
    /**
     * The name of the field that contains a boolean indicating if a notification for an auto-provisioned device was sent.
     */
    public static final String FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT = "auto-provisioning-notification-sent";

    /**
     * The name of the property that contains the authorities of a device.
     */
    public static final String FIELD_AUTHORITIES = "authorities";

    /**
     * The name of the authority which authorizes a gateway to perform auto-provisioning.
     */
    public static final String AUTHORITY_AUTO_PROVISIONING_ENABLED = "auto-provisioning-enabled";

    /**
     * The name of the property that contains command-endpoint of a device.
     */
    public static final String COMMAND_ENDPOINT = "command-endpoint";

    private RegistryManagementConstants() {
        // prevent instantiation
    }
}
