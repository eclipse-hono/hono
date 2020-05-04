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

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.RegistrationConstants;

import io.vertx.core.json.JsonObject;

/**
 * Utility class for building Json documents for mongodb.
 */
public final class MongoDbDocumentBuilder {

    private final JsonObject document;

    private MongoDbDocumentBuilder() {
        this.document = new JsonObject();
    }

    /**
     * Creates a new builder for a given tenant.
     * 
     * @param tenant The tenant to add to the document (may be empty).
     * @return The new document builder.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static MongoDbDocumentBuilder forTenantId(final String tenant) {
        Objects.requireNonNull(tenant);
        final MongoDbDocumentBuilder builder = new MongoDbDocumentBuilder();
        return builder.withTenantId(tenant);
    }

    /**
     * Creates a new builder for a given version.
     * 
     * @param version The version to add to the document (may be empty).
     * @return The new document builder.
     * @throws NullPointerException if version is {@code null}.
     */
    public static MongoDbDocumentBuilder forVersion(final Optional<String> version) {
        Objects.requireNonNull(version);
        final MongoDbDocumentBuilder builder = new MongoDbDocumentBuilder();
        version.ifPresent(v -> builder.withVersion(v));
        return builder;
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
     * Sets the json object with the given version.
     *
     * @param version The version of the document.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withVersion(final String version) {
        document.put(MongoDbDeviceRegistryUtils.FIELD_VERSION, version);
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
}
