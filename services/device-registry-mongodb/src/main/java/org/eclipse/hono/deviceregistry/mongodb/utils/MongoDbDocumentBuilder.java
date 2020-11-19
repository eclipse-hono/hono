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

package org.eclipse.hono.deviceregistry.mongodb.utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.device.Filter;
import org.eclipse.hono.service.management.device.Sort;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;

/**
 * Utility class for building Json documents for mongodb.
 */
public final class MongoDbDocumentBuilder {

    public static final String SET_OPERATOR = "$set";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonPointer FIELD_ID = JsonPointer.from("/id");
    private static final String FIELD_CREDENTIALS_AUTH_ID_KEY = String.format("%s.%s",
            MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS, RegistryManagementConstants.FIELD_AUTH_ID);
    private static final String FIELD_CREDENTIALS_TYPE_KEY = String.format("%s.%s",
            MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS, RegistryManagementConstants.FIELD_TYPE);
    private static final String TENANT_TRUSTED_CA_SUBJECT_PATH = String.format("%s.%s.%s",
            RegistryManagementConstants.FIELD_TENANT,
            RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA,
            AuthenticationConstants.FIELD_SUBJECT_DN);
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
     * Creates a MongoDb update document for the update of the device DTO.
     *
     * @param deviceDto The device DTO for which an update should be generated.
     *
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder forUpdateOf(final DeviceDto deviceDto) {
        forUpdateOf((BaseDto) deviceDto);

        final JsonObject updateDocument = document.getJsonObject(SET_OPERATOR);

        updateDocument.put(MongoDbDeviceRegistryUtils.FIELD_AUTO_PROVISIONED, deviceDto.getDeviceStatus().isAutoProvisioned());
        updateDocument.put(MongoDbDeviceRegistryUtils.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT, deviceDto.getDeviceStatus().isAutoProvisioningNotificationSent());

        return this;
    }

    /**
     * Creates a MongoDb update document for the update of the given DTO.
     *
     * @param baseDto The DTO for which an update should be generated.
     *
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder forUpdateOf(final BaseDto<?> baseDto) {
        final JsonObject updates = new JsonObject();

        if (baseDto.getData() != null) {
            final JavaType baseDtoJavaType = OBJECT_MAPPER.getTypeFactory().constructType(baseDto.getClass());
            final BeanDescription beanDescription = OBJECT_MAPPER.getSerializationConfig().introspect(baseDtoJavaType);
            final AnnotatedMethod getDataMethod = beanDescription.findMethod("getData", null);
            final JsonProperty jsonProperty = getDataMethod.getAnnotation(JsonProperty.class);
            updates.put(jsonProperty.value(), JsonObject.mapFrom(baseDto.getData()));
        }

        if (baseDto.getCreationTime() != null) {
            updates.put(MongoDbDeviceRegistryUtils.FIELD_CREATED, baseDto.getCreationTime());
        }

        if (baseDto.getUpdatedOn() != null) {
            updates.put(MongoDbDeviceRegistryUtils.FIELD_UPDATED_ON, baseDto.getUpdatedOn());
        }

        if (baseDto.getVersion() != null) {
            updates.put(MongoDbDeviceRegistryUtils.FIELD_VERSION, baseDto.getVersion());
        }

        document.put(SET_OPERATOR, updates);
        return this;
    }

    /**
     * Sets the json object with the given tenant id.
     *
     * @param tenantId The tenant id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withTenantId(final String tenantId) {
        document.put(RegistrationConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
        return this;
    }

    /**
     * Sets the json object with the given device id.
     *
     * @param deviceId The device id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withDeviceId(final String deviceId) {
        document.put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
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
        version.ifPresent(ver -> document.put(MongoDbDeviceRegistryUtils.FIELD_VERSION, ver));
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
     * Sets the json object with the given credentials type.
     *
     * @param type The credentials type.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withType(final String type) {
        document.put(FIELD_CREDENTIALS_TYPE_KEY, type);
        return this;
    }

    /**
     * Sets the json object with the given auth id.
     *
     * @param authId The auth id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withAuthId(final String authId) {
        document.put(FIELD_CREDENTIALS_AUTH_ID_KEY, authId);
        return this;
    }


    /**
     * Sets the json object with the given device filters.
     *
     * @param filters The device filters list.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withDeviceFilters(final List<Filter> filters) {

        filters.forEach(filter -> {
            if (filter.getValue() instanceof String) {
                final String value = (String) filter.getValue();
                document.put(mapDeviceField(filter.getField()),
                        new JsonObject().put("$regex",
                                DeviceRegistryUtils.getRegexExpressionForSearchOperation(value)));
            } else {
                document.put(mapDeviceField(filter.getField()), filter.getValue());
            }
        });
        return this;
    }

    /**
     * Sets the json object with the given sorting options.
     *
     * @param sortOptions The list of soring options.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withDeviceSortOptions(final List<Sort> sortOptions) {

        sortOptions.forEach(sortOption -> document.put(mapDeviceField(sortOption.getField()),
                mapSortingDirection(sortOption.getDirection())));

        return this;
    }

    private static String mapDeviceField(final JsonPointer field) {
        if (FIELD_ID.equals(field)) {
            return RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID;
        } else {
            return MongoDbDeviceRegistryUtils.FIELD_DEVICE + field.toString().replace("/", ".");
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
