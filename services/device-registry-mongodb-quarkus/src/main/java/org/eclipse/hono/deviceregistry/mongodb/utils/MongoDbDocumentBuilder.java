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
    private static final String TENANT_ALIAS_PATH = String.format(
            "%s.%s",
            TenantDto.FIELD_TENANT,
            RegistryManagementConstants.FIELD_ALIAS);
    private static final String TENANT_TRUST_ANCHOR_GROUP_PATH = String.format(
            "%s.%s",
            TenantDto.FIELD_TENANT,
            RegistryManagementConstants.FIELD_TRUST_ANCHOR_GROUP);
    private static final String TENANT_TRUSTED_CA_PATH = String.format(
            "%s.%s",
            TenantDto.FIELD_TENANT,
            RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA);
    private static final String TENANT_TRUSTED_CA_SUBJECT_PATH = String.format(
            "%s.%s",
            TENANT_TRUSTED_CA_PATH,
            AuthenticationConstants.FIELD_SUBJECT_DN);
    private static final String MONGODB_OPERATOR_AND = "$and";
    private static final String MONGODB_OPERATOR_ELEM_MATCH = "$elemMatch";
    private static final String MONGODB_OPERATOR_EXISTS = "$exists";
    private static final String MONGODB_OPERATOR_IN = "$in";
    private static final String MONGODB_OPERATOR_NOT_EQUALS = "$ne";
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
     * Gets the document containing the filter criteria that have been added.
     *
     * @return The document.
     */
    public JsonObject document() {
        return document;
    }

    /**
     * Adds filter criteria that matches documents containing a given tenant ID.
     *
     * @param tenantId The tenant id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withTenantId(final String tenantId) {
        document.put(BaseDto.FIELD_TENANT_ID, tenantId);
        return this;
    }

    /**
     * Adds filter criteria that matches documents containing a given tenant ID.
     * <p>
     * A tenant will match if its {@value BaseDto#FIELD_TENANT_ID} or its
     * {@value RegistryManagementConstants#FIELD_ALIAS} property matches the given identifier.
     *
     * @param tenantId The tenant id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withTenantIdOrAlias(final String tenantId) {
        document.put(
                MONGODB_OPERATOR_OR,
                new JsonArray()
                        .add(new JsonObject().put(BaseDto.FIELD_TENANT_ID, tenantId))
                        .add(new JsonObject().put(
                                MONGODB_OPERATOR_AND,
                                new JsonArray()
                                    .add(new JsonObject().put(
                                            TENANT_ALIAS_PATH,
                                            new JsonObject().put(MONGODB_OPERATOR_EXISTS, true)))
                                    .add(new JsonObject().put(TENANT_ALIAS_PATH, tenantId)))));
        return this;
    }

    /**
     * Adds filter criteria that matches documents not containing a given tenant ID.
     *
     * @param tenantId The tenant ID.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withOtherTenantId(final String tenantId) {
        document.put(BaseDto.FIELD_TENANT_ID, new JsonObject().put(MONGODB_OPERATOR_NOT_EQUALS, tenantId));
        return this;
    }

    /**
     * Adds filter criteria that matches documents containing a given device ID.
     *
     * @param deviceId The device id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withDeviceId(final String deviceId) {
        document.put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        return this;
    }

    /**
     * Adds filter criteria that matches documents containing a trust anchor with a given subject DN.
     *
     * @param subjectDn The subject DN.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withCa(final String subjectDn) {
        document.put(TENANT_TRUSTED_CA_SUBJECT_PATH, new JsonObject().put("$eq", subjectDn));
        return this;
    }

    /**
     * Adds filter criteria that matches documents containing a trust anchor with any of a given set of subject DNs.
     * Sets the json object with the given subject DNs.
     *
     * @param subjectDns The list of subject DNs.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withAnyCa(final List<String> subjectDns) {
        document.put(
                TENANT_TRUSTED_CA_PATH,
                new JsonObject().put(
                        MONGODB_OPERATOR_ELEM_MATCH,
                        new JsonObject().put(
                                RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                new JsonObject().put(MONGODB_OPERATOR_IN, new JsonArray(subjectDns)))));
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
     * Sets the json object with the given credentials type.
     *
     * @param type The credentials type.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withType(final String type) {
        return withCredentialsPredicate(RegistryManagementConstants.FIELD_TYPE, type);
    }

    /**
     * Sets the json object with the given auth id.
     *
     * @param authId The auth id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withAuthId(final String authId) {
        return withCredentialsPredicate(RegistryManagementConstants.FIELD_AUTH_ID, authId);
    }

    /**
     * Sets the json object with the given trust anchor group.
     *
     * @param trustAnchorGroup The trust anchor group name.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withTrustAnchorGroup(final String trustAnchorGroup) {
        document.put(
                MONGODB_OPERATOR_OR,
                new JsonArray()
                        .add(new JsonObject().put(
                                TENANT_TRUST_ANCHOR_GROUP_PATH,
                                new JsonObject().put(MONGODB_OPERATOR_EXISTS, false)))
                        .add(new JsonObject().put(
                                TENANT_TRUST_ANCHOR_GROUP_PATH,
                                new JsonObject().put(MONGODB_OPERATOR_NOT_EQUALS, trustAnchorGroup))));
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
     * Sets the json object with the given filters for tenants search operation.
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

    private MongoDbDocumentBuilder withCredentialsPredicate(final String field, final String value) {
        final var credentialsArraySpec = document.getJsonObject(CredentialsDto.FIELD_CREDENTIALS, new JsonObject());
        final var elementMatchSpec = credentialsArraySpec.getJsonObject(MONGODB_OPERATOR_ELEM_MATCH, new JsonObject());
        elementMatchSpec.put(field, value);
        credentialsArraySpec.put(MONGODB_OPERATOR_ELEM_MATCH, elementMatchSpec);
        document.put(CredentialsDto.FIELD_CREDENTIALS, credentialsArraySpec);
        return this;
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
        if (direction == Sort.Direction.ASC) {
            return 1;
        } else {
            return -1;
        }
    }
}
