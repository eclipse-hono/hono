/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.mongodb.model;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CommonSecret;
import org.eclipse.hono.service.management.credentials.GenericCredential;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A DTO (Data Transfer Object) class to store credentials information in mongodb.
 */
public class CredentialsDto extends BaseDto {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, required = true)
    private String deviceId;

    @JsonProperty(value = MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS, required = true)
    private List<CommonCredential> credentials;
    private boolean hasSecretIds;

    /**
     * Default constructor for serialisation/deserialization.
     */
    public CredentialsDto() {
        // Explicit default constructor.
    }

    /**
     * Creates a new data transfer object to store credentials information in mongodb.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param credentials The list of credentials.
     * @param version The version of the credentials to be sent as request header.
     * @throws NullPointerException if any of the parameters except credentials is {@code null}
     * @throws IllegalArgumentException if validation of the given credentials fail.
     */
    public CredentialsDto(
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> credentials,
            final String version) {

        setTenantId(tenantId);
        setDeviceId(deviceId);

        //Validate the given credentials, secrets and generate secret ids if not available.
        Optional.ofNullable(credentials)
                .ifPresent(ok -> {
                    validateForUniqueAuthIdAndType(credentials);
                    validateSecretsAndGenerateIds(credentials);
                });
        setCredentials(credentials);

        setVersion(version);
        setUpdatedOn(Instant.now());
    }

    /**
     * Gets the identifier of the tenant.
     *
     * @return The identifier of the tenant.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the identifier of the tenant.
     *
     * @param tenantId The tenant's identifier.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    public void setTenantId(final String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    /**
     * Gets the identifier of the device.
     *
     * @return The identifier of the device.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the identifier of the device.
     *
     * @param deviceId The identifier of the device.
     * @throws NullPointerException if the deviceId is {@code null}.
     */
    public void setDeviceId(final String deviceId) {
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    /**
     * Gets the list of credentials.
     *
     * @return The list of credentials.
     */
    public List<CommonCredential> getCredentials() {
        return credentials;
    }

    /**
     * Sets the list of credentials.
     *
     * @param credentials A list of credentials.
     */
    public void setCredentials(final List<CommonCredential> credentials) {
        this.credentials = credentials;
    }

    /**
     * Checks whether the credentials to be updated already have secret ids.
     *
     * @return hasSecretIds {@code true} if any credential already has a secret identifier,
     *                      otherwise {@code false}
     */
    @JsonIgnore
    public boolean hasSecretIds() {
        return hasSecretIds;
    }

    /**
     * Sets whether the credentials to be updated already have secret ids.
     *
     * @param hasSecretIds {@code true} if any credential already has a secret identifier,
     *                         otherwise {@code false}
     */
    @JsonIgnore
    public void setHasSecretIds(final boolean hasSecretIds) {
        this.hasSecretIds = hasSecretIds;
    }

    @JsonIgnore
    private String getAuthType(final CommonCredential credential) {
        if (credential instanceof GenericCredential) {
            return ((GenericCredential) credential).getType();
        } else if (credential instanceof PasswordCredential) {
            return RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD;
        } else if (credential instanceof PskCredential) {
            return RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY;
        } else if (credential instanceof X509CertificateCredential) {
            return RegistryManagementConstants.SECRETS_TYPE_X509_CERT;
        }
        return null;
    }

    @JsonIgnore
    private List<? extends CommonSecret> getSecrets(final CommonCredential credential) {
        if (credential instanceof PasswordCredential) {
            return ((PasswordCredential) credential).getSecrets();
        } else if (credential instanceof X509CertificateCredential) {
            return ((X509CertificateCredential) credential).getSecrets();
        } else if (credential instanceof PskCredential) {
            return ((PskCredential) credential).getSecrets();
        } else {
            return ((GenericCredential) credential).getSecrets();
        }
    }

    @JsonIgnore
    private <T extends CommonSecret> T generateSecretId(final T secret) {
        if (secret.getId() == null) {
            secret.setId(DeviceRegistryUtils.getUniqueIdentifier());
        } else {
            this.setHasSecretIds(true);
        }
        return secret;
    }

    @JsonIgnore
    private void validateForUniqueAuthIdAndType(final List<? extends CommonCredential> credentials) {
            final long uniqueAuthIdAndTypeCount = credentials.stream()
                    .map(credential -> credential.getAuthId() + getAuthType(credential))
                    .distinct()
                    .count();

            if (credentials.size() != uniqueAuthIdAndTypeCount) {
                throw new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "the composite key of auth-id and type must be unique");
            }
    }

    @JsonIgnore
    private void validateSecretsAndGenerateIds(final List<? extends CommonCredential> credentials) {

        credentials.stream()
                .map(this::getSecrets)
                .filter(secrets -> secrets != null && !secrets.isEmpty())
                .forEach(secrets -> {
                    final long uniqueIdsCount = secrets.stream()
                            .map(this::generateSecretId)
                            .map(CommonSecret::getId)
                            .distinct()
                            .count();
                    if (secrets.size() != uniqueIdsCount) {
                        throw new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                                "secret ids must be unique for the secrets belonging to the same auth-id and type");
                    }
                });
    }
}
