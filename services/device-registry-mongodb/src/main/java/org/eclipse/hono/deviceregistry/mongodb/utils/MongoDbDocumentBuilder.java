/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.mongodb.utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.credentials.CredentialsDto;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.tenant.TenantDto;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;

/**
 * Utility class for building Json documents for mongodb.
 */
public final class MongoDbDocumentBuilder {

    private static final JsonPointer FIELD_ID = JsonPointer.from("/id");
    private static final String TENANT_TRUSTED_CA_SUBJECT_PATH = String.format("%s.%s.%s",
            TenantDto.FIELD_TENANT,
            RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA,
            AuthenticationConstants.FIELD_SUBJECT_DN);
    private static final String MONGODB_OPERATOR_ELEM_MATCH = "$elemMatch";
    private static final String MONGODB_OPERATOR_OR = "$or";

    private final JsonObject document;

    private MongoDbDocumentBuilder() {
        this.document = new JsonObject();
    }

    /**
     * Creates a new builder to append document properties to.
     *
     * @return The new document builder.
     */
    public static MongoDbDocumentBuilder builder() {
        return new MongoDbDocumentBuilder();
    }

    /**
     * Sets the json object with the given tenant id.
     *
     * @param tenantId The tenant id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withTenantId(final String tenantId) {
        document.put(BaseDto.FIELD_TENANT_ID, tenantId);
        return this;
    }

    /**
     * Sets the json object with the given device id.
     *
     * @param deviceId The device id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withDeviceId(final String deviceId) {
        document.put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        return this;
    }

    /**
     * Sets the json object with the given subject DN.
     *
     * @param subjectDn The subject DN.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withCa(final String subjectDn) {
        document.put(TENANT_TRUSTED_CA_SUBJECT_PATH, new JsonObject().put("$eq", subjectDn));
        return this;
    }

    /**
     * Sets the json object with the given version if available.
     *
     * @param version The version of the document.
     * @return a reference to this for fluent use.
     * @throws NullPointerException if version is {@code null}.
     */
    public MongoDbDocumentBuilder withVersion(final Optional<String> version) {
        Objects.requireNonNull(version);
        version.ifPresent(ver -> document.put(BaseDto.FIELD_VERSION, ver));
        return this;
    }

    /**
     * Returns the json document.
     *
     * @return the json document.
     */
    public JsonObject document() {
        return document;
    }

    /**
     * Sets the json object with the given credentials type and auth id.
     *
     * @param type The credentials type.
     * @param authId The authentication identifier
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withTypeAndAuthId(final String type, final String authId) {
        withType(type);
        if (RegistryManagementConstants.SECRETS_TYPE_X509_CERT.equals(type)) {
            withGeneratedAuthId(authId);
        } else {
            withAuthId(authId);
        }
        return this;
    }

    /**
     * Sets the json object with the given credentials type.
     *
     * @param type The credentials type.
     * @return a reference to this for fluent use.
     */
    private MongoDbDocumentBuilder withType(final String type) {
        return withCredentialsPredicate(RegistryManagementConstants.FIELD_TYPE, type);
    }

    /**
     * Sets the json object with the given auth id.
     *
     * @param authId The auth id.
     * @return a reference to this for fluent use.
     */
    private MongoDbDocumentBuilder withAuthId(final String authId) {
        return withCredentialsPredicate(RegistryManagementConstants.FIELD_AUTH_ID, authId);
    }

    /**
     * Sets the json object with the given auth id.
     * <p>
     * A MongoDB query is framed to match the given auth id to
     * <ul>
     * <li>the field <em>generated-auth-id</em>.</li>
     * <li>If no match found and <em>generated-auth-id</em> is {@code null}, match with the field <em>auth-id</em>.
     * </li>
     * </ul>
     * <p>
     * Example query with auth id as "Device1-Hono-Eclipse":
     * <pre>
     * {
     *     "credentials": {
     *         "$elemMatch": {
     *             "$or": [
     *                 {
     *                     "generated-auth-id": "Device1-Hono-Eclipse"
     *                 },
     *                 {
     *                     "generated-auth-id": null,
     *                     "auth-id": "Device1-Hono-Eclipse"
     *                 }
     *             ]
     *         }
     *     }
     * }
     * </pre>
     *
     * @param authId The auth id.
     * @return a reference to this for fluent use.
     */
    private MongoDbDocumentBuilder withGeneratedAuthId(final String authId) {
        final var credentialsArraySpec = document.getJsonObject(CredentialsDto.FIELD_CREDENTIALS, new JsonObject());
        final var elementMatchSpec = credentialsArraySpec.getJsonObject(MONGODB_OPERATOR_ELEM_MATCH, new JsonObject());

        elementMatchSpec.put(MONGODB_OPERATOR_OR, new JsonArray(List.of(
                new JsonObject().put(RegistryManagementConstants.FIELD_GENERATED_AUTH_ID, authId),
                new JsonObject()
                        .put(RegistryManagementConstants.FIELD_GENERATED_AUTH_ID, null)
                        .put(RegistryManagementConstants.FIELD_AUTH_ID, authId))));
        credentialsArraySpec.put(MONGODB_OPERATOR_ELEM_MATCH, elementMatchSpec);
        document.put(CredentialsDto.FIELD_CREDENTIALS, credentialsArraySpec);

        return this;
    }

    private MongoDbDocumentBuilder withCredentialsPredicate(final String field, final String value) {
        final var credentialsArraySpec = document.getJsonObject(CredentialsDto.FIELD_CREDENTIALS, new JsonObject());
        final var elementMatchSpec = credentialsArraySpec.getJsonObject(MONGODB_OPERATOR_ELEM_MATCH, new JsonObject());
        elementMatchSpec.put(field, value);
        credentialsArraySpec.put(MONGODB_OPERATOR_ELEM_MATCH, elementMatchSpec);
        document.put(CredentialsDto.FIELD_CREDENTIALS, credentialsArraySpec);
        return this;
    }

    /**
     * Sets the json object with the given device filters.
     *
     * @param filters The device filters list.
     * @return a reference to this for fluent use.
     * @throws NullPointerException if the filters is {@code null}.
     */
    public MongoDbDocumentBuilder withDeviceFilters(final List<Filter> filters) {
        Objects.requireNonNull(filters);
        applySearchFilters(filters, MongoDbDocumentBuilder::mapDeviceField);
        return this;
    }

    /**
     * Sets the json object with the given filters for tenants search operation..
     *
     * @param filters The device filters list.
     * @return a reference to this for fluent use.
     * @throws NullPointerException if the filters is {@code null}.
     */
    public MongoDbDocumentBuilder withTenantFilters(final List<Filter> filters) {
        Objects.requireNonNull(filters);
        applySearchFilters(filters, MongoDbDocumentBuilder::mapTenantField);
        return this;
    }

    /**
     * Sets the json object with the given sorting options.
     *
     * @param sortOptions The list of soring options.
     * @return a reference to this for fluent use.
     * @throws NullPointerException if the sortOptions is {@code null}.
     */
    public MongoDbDocumentBuilder withDeviceSortOptions(final List<Sort> sortOptions) {
        Objects.requireNonNull(sortOptions);
        applySortingOptions(sortOptions, MongoDbDocumentBuilder::mapDeviceField);
        return this;
    }

    /**
     * Sets the json object with the given sorting options for tenants search operation.
     *
     * @param sortOptions The list of soring options.
     * @return a reference to this for fluent use.
     * @throws NullPointerException if the sortOptions is {@code null}.
     */
    public MongoDbDocumentBuilder withTenantSortOptions(final List<Sort> sortOptions) {
        Objects.requireNonNull(sortOptions);
        applySortingOptions(sortOptions, MongoDbDocumentBuilder::mapTenantField);
        return this;
    }

    private void applySearchFilters(final List<Filter> filters, final Function<JsonPointer, String> fieldMapper) {
        filters.forEach(filter -> {
            if (filter.getValue() instanceof String) {
                final String value = (String) filter.getValue();
                document.put(fieldMapper.apply(filter.getField()), new JsonObject().put("$regex",
                        DeviceRegistryUtils.getRegexExpressionForSearchOperation(value)));
            } else {
                document.put(fieldMapper.apply(filter.getField()), filter.getValue());
            }
        });
    }

    private void applySortingOptions(final List<Sort> sortOptions, final Function<JsonPointer, String> fieldMapper) {
        sortOptions.forEach(sortOption -> document.put(fieldMapper.apply(sortOption.getField()),
                mapSortingDirection(sortOption.getDirection())));
    }

    private static String mapDeviceField(final JsonPointer field) {
        if (FIELD_ID.equals(field)) {
            return RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID;
        } else {
            return DeviceDto.FIELD_DEVICE + field.toString().replace("/", ".");
        }
    }

    private static String mapTenantField(final JsonPointer field) {
        if (FIELD_ID.equals(field)) {
            return RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID;
        } else {
            return RegistryManagementConstants.FIELD_TENANT + field.toString().replace("/", ".");
        }
    }

    private static int mapSortingDirection(final Sort.Direction direction) {
        if (direction == Sort.Direction.asc) {
            return 1;
        } else {
            return -1;
        }
    }
}
