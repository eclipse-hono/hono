/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.credentials;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A Data Transfer Object for credentials information.
 * <p>
 * This is basically a wrapper around a list of {@link CommonCredential} objects, adding a resource version
 * and time stamps for initial creation and last update.
 */
@RegisterForReflection(ignoreNested = false)
public final class CredentialsDto extends BaseDto<List<CommonCredential>> {

    /**
     * The name of the JSON property containing the credentials.
     */
    public static final String FIELD_CREDENTIALS = "credentials";

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID)
    private String deviceId;

    private boolean requiresMerging;

    /**
     * Creates a DTO for persisting credentials.
     * <p>
     * This method also makes sure that the identifiers of the credentials'
     * secrets are unique within their credentials object.
     * <p>
     * These properties are supposed to be asserted before persisting
     *
     * @param tenantId The identifier of the tenant that the device belongs to.
     * @param deviceId The identifier of the device that the credentials belong to.
     * @param credentials The list of credentials to store.
     * @param version The credentials' (initial) resource version.
     *
     * @return The DTO.
     *
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws ClientErrorException if any of the credentials checks fail.
     */
    public static CredentialsDto forCreation(
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> credentials,
            final String version) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(credentials);
        Objects.requireNonNull(version);

        final CredentialsDto credentialsDto = BaseDto.forCreation(CredentialsDto::new, credentials, version);
        credentialsDto.setTenantId(tenantId);
        credentialsDto.setDeviceId(deviceId);

        return credentialsDto;
    }

    /**
     * Creates a DTO for credentials read from the persistent store.
     *
     * @param tenantId The identifier of the tenant that the device belongs to.
     * @param deviceId The identifier of the device that the credentials belong to.
     * @param credentials The list of credentials from the store.
     * @param created The point in time when the credentials were created initially in the store (may be {@code null}).
     * @param updated The point in time when the credentials were updated most recently in the store (may be {@code null}).
     * @param version The resource version of the credentials in the store.
     *
     * @return The DTO.
     * @throws NullPointerException if tenant ID, device ID or credentials are {@code null}.
     */
    public static CredentialsDto forRead(
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> credentials,
            final Instant created,
            final Instant updated,
            final String version) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final CredentialsDto credentialsDto = BaseDto.forRead(
                CredentialsDto::new,
                credentials,
                created,
                updated,
                version);
        credentialsDto.setTenantId(tenantId);
        credentialsDto.setDeviceId(deviceId);

        return credentialsDto;
    }

    /**
     * Creates a DTO for updating credentials.
     * <p>
     * This method also makes sure that the identifiers of the credentials'
     * secrets are unique within their credentials object.
     *
     * @param tenantId The identifier of the tenant that the device belongs to.
     * @param deviceId The identifier of the device that the credentials belong to.
     * @param credentials The list of credentials to store.
     * @param version The new resource version to use for the object in the store.
     *
     * @return The DTO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws ClientErrorException if any of the credentials checks fail.
     */
    public static CredentialsDto forUpdate(
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> credentials,
            final String version) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(credentials);
        Objects.requireNonNull(version);

        final CredentialsDto credentialsDto = BaseDto.forUpdate(CredentialsDto::new, credentials, version);
        credentialsDto.setTenantId(tenantId);
        credentialsDto.setDeviceId(deviceId);

        return credentialsDto;
    }

    @JsonIgnore
    private void assertSecretIdUniqueness(final List<? extends CommonCredential> credentials) {

        credentials.stream()
            .map(CommonCredential::getSecrets)
            .forEach(secrets -> {
                final Set<String> secretIds = new HashSet<>();
                final AtomicInteger count = new AtomicInteger(0);
                secrets.stream()
                    .forEach(secret -> {
                        if (secret.getId() != null) {
                            requiresMerging = true;
                            secretIds.add(secret.getId());
                            count.incrementAndGet();
                        }
                    });
                if (secretIds.size() < count.get()) {
                    throw new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "secret IDs must be unique within each credentials object");
                }
            });
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
    private void setDeviceId(final String deviceId) {
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    /**
     * Gets the list of credentials.
     *
     * @return The list of credentials.
     */
    public List<CommonCredential> getCredentials() {
        return getData();
    }

    @Override
    protected void setData(final List<CommonCredential> data) {
        // validate the given credentials, secrets and generate secret ids if not available.
        Optional.ofNullable(data).ifPresent(this::assertSecretIdUniqueness);
        super.setData(data);
    }

    @Override
    @JsonProperty(FIELD_CREDENTIALS)
    public List<CommonCredential> getData() {
        return super.getData();
    }

    /**
     * Checks if the secrets contained in this DTO need to be merged with
     * existing credentials.
     * <p>
     * Otherwise the existing credentials can simply be replaced with the data contained
     * in this DTO.
     *
     * @return {@code true} if any of the secrets has an ID property and thus refers to
     *         an existing secret.
     */
    @JsonIgnore
    public boolean requiresMerging() {
        return requiresMerging;
    }

    /**
     * Assigns a unique ID to each of the credentials' secrets that do not have one already.
     */
    public void createMissingSecretIds() {
        Optional.ofNullable(getData())
            .ifPresent(creds -> creds.stream().forEach(CommonCredential::createMissingSecretIds));
    }

    /**
     * Merges the secrets of the given credential DTO with those of this one.
     *
     * @param otherCredentialsDto The DTO to be merged into this one.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if the given credentials DTO is {@code null}.
     * @throws IllegalArgumentException if the given credentials DTO does not contain any credentials.
     */
    @JsonIgnore
    public CredentialsDto merge(final CredentialsDto otherCredentialsDto) {

        Objects.requireNonNull(otherCredentialsDto);

        final var credentialsToMerge = otherCredentialsDto.getCredentials();
        if (credentialsToMerge == null) {
            throw new IllegalArgumentException("no credentials on record for device");
        }
        return merge(credentialsToMerge);
    }

    /**
     * Merges the secrets of the given credentials with those of this DTO.
     *
     * @param credentialsToMerge The credentials to be merged into this DTO.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if credentials are {@code null}.
     * @throws IllegalArgumentException if credentials is empty.
     */
    @JsonIgnore
    public CredentialsDto merge(final List<CommonCredential> credentialsToMerge) {
        Objects.requireNonNull(credentialsToMerge);

        if (credentialsToMerge.isEmpty()) {
            throw new IllegalArgumentException("no credentials on record for device");
        } else {
            this.getData().forEach(credential -> findCredentialByIdAndType(
                    credential.getAuthId(),
                    credential.getType(),
                    credentialsToMerge).ifPresent(credential::merge));

            setUpdatedOn(Instant.now());
        }
        return this;
    }

    @JsonIgnore
    private static Optional<CommonCredential> findCredentialByIdAndType(
            final String authId,
            final String authType,
            final List<CommonCredential> credentials) {

        return credentials.stream()
                .filter(credential -> authId.equals(credential.getAuthId()) && authType.equals(credential.getType()))
                .findFirst();
    }
}
