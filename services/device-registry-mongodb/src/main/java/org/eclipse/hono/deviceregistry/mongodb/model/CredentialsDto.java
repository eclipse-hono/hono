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
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A DTO (Data Transfer Object) class to store credentials information in mongodb.
 */
public final class CredentialsDto extends BaseDto {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, required = true)
    private String deviceId;

    @JsonProperty(value = MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS, required = true)
    private List<CommonCredential> credentials;
    private boolean requiresMerging;

    /**
     * Default constructor for serialisation/deserialization.
     */
    public CredentialsDto() {
    }

    /**
     * Creates a new instance for a list of credentials.
     * <p>
     * This constructor also makes sure that
     * <ul>
     * <li>none of the credentials have the same type and authentication identifier and</li>
     * <li>that each secret has a unique identifier within its credentials object.</li>
     * </ul>
     * <p>
     * These properties are supposed to be asserted before persisting this DTO.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param credentials The list of credentials.
     * @param version The version of the credentials to be sent as request header.
     * @throws NullPointerException if any of the parameters except credentials is {@code null}
     * @throws ClientErrorException if any of the checks fail.
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
                .ifPresent(creds -> {
                    assertTypeAndAuthId(creds);
                    assertSecretIds(creds);
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
     * Checks if the secrets contained in this DTO need to be merged when updating
     * existing credentials.
     * <p>
     * Otherwise the existing credentials can simply be replaced with the data contained
     * in this DTO.
     *
     * @return {@code true} if the secrets need to be merged.
     */
    @JsonIgnore
    public boolean requiresMerging() {
        return requiresMerging;
    }

    /**
     * Merges the secrets of the given credential DTO with that of the current one.
     *
     * @param credentialsDto The credential DTO to be merged.
     * @return  a reference to this for fluent use.
     * @throws NullPointerException if the given credential DTO is {@code null}.
     */
    @JsonIgnore
    public CredentialsDto merge(final CredentialsDto credentialsDto) {
        Objects.requireNonNull(credentialsDto);

        Optional.ofNullable(credentialsDto.getCredentials())
                .ifPresent(credentialsToMerge -> this.credentials
                        .forEach(credential -> findCredentialByIdAndType(credential.getAuthId(), credential.getType(),
                                credentialsToMerge)
                                        .ifPresent(credential::merge)));

        return this;
    }

    @JsonIgnore
    private Optional<CommonCredential> findCredentialByIdAndType(final String authId, final String authType,
            final List<CommonCredential> credentials) {
        return credentials.stream()
                .filter(credential -> authId.equals(credential.getAuthId()) && authType.equals(credential.getType()))
                .findFirst();
    }

    @JsonIgnore
    private <T extends CommonSecret> T generateSecretId(final T secret) {
        if (secret.getId() == null) {
            secret.setId(DeviceRegistryUtils.getUniqueIdentifier());
        } else {
            this.requiresMerging = true;
        }
        return secret;
    }

    @JsonIgnore
    private void assertTypeAndAuthId(final List<? extends CommonCredential> credentials) {

        final long uniqueAuthIdAndTypeCount = credentials.stream()
            .map(credential -> String.format("%s::%s", credential.getType(), credential.getAuthId()))
            .distinct()
            .count();

        if (credentials.size() > uniqueAuthIdAndTypeCount) {
            throw new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "credentials must have unique (type, auth-id)");
        }
    }

    @JsonIgnore
    private void assertSecretIds(final List<? extends CommonCredential> credentials) {

        credentials.stream()
            .map(CommonCredential::getSecrets)
            .forEach(secrets -> {
                final long uniqueIdsCount = secrets.stream()
                        .map(this::generateSecretId)
                        .map(CommonSecret::getId)
                        .distinct()
                        .count();
                if (secrets.size() > uniqueIdsCount) {
                    throw new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "secret IDs must be unique within each credentials object");
                }
            });
    }
}
