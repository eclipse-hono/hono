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

package org.eclipse.hono.service.base.jdbc.store.device;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;

/**
 * A data store for devices and credentials, based on a JSON data model.
 */
public class JsonManagementStore extends AbstractDeviceManagementStore {

    private static final Logger log = LoggerFactory.getLogger(JsonManagementStore.class);

    private final Statement readCredentialsStatement;

    private final Statement updateCredentialsStatement;
    private final Statement updateCredentialsVersionedStatement;

    private final boolean hierarchical;

    /**
     * Create a new instance.
     *
     * @param client The SQL client to use.
     * @param tracer The tracer to use.
     * @param hierarchical If the JSON store uses a hierarchical model for a flat one.
     * @param cfg The SQL statement configuration.
     */
    public JsonManagementStore(final SQLClient client, final Tracer tracer, final boolean hierarchical, final StatementConfiguration cfg) {
        super(client, tracer, cfg);
        cfg.dump(log);

        this.hierarchical = hierarchical;

        this.readCredentialsStatement = cfg
                .getRequiredStatment("readCredentials")
                .validateParameters(
                        "tenant_id",
                        "device_id");

        this.updateCredentialsStatement = cfg
                .getRequiredStatment("updateCredentials")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "next_version",
                        "data");

        this.updateCredentialsVersionedStatement = cfg
                .getRequiredStatment("updateCredentialsVersioned")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "next_version",
                        "data",
                        "expected_version");

    }

    @Override
    public Future<Boolean> setCredentials(final DeviceKey key, final List<CommonCredential> credentials, final Optional<String> resourceVersion,
            final SpanContext spanContext) {

        final String json = encodeCredentials(credentials);

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "set credentials", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .withTag("data", json)
                .start();

        resourceVersion.ifPresent(version -> span.setTag("version", version));

        final Statement statement = resourceVersion.isPresent() ? this.updateCredentialsVersionedStatement : this.updateCredentialsStatement;

        return updateJsonField(key, statement, json, resourceVersion, span)
                .map(result -> result.getUpdated() > 0)
                .onComplete(x -> span.finish());

    }

    private String encodeCredentials(final List<CommonCredential> credentials) {
        if (this.hierarchical) {
            return encodeCredentialsHierarchical(credentials);
        } else {
            return Json.encode(credentials.toArray(CommonCredential[]::new));
        }
    }

    static String encodeCredentialsHierarchical(final List<CommonCredential> credentials) {
        final JsonObject result = new JsonObject();

        for (CommonCredential entry : credentials) {
            final JsonObject c = JsonObject.mapFrom(entry);

            final String type = c.getString(CredentialsConstants.FIELD_TYPE);
            final String authId = c.getString(CredentialsConstants.FIELD_AUTH_ID);

            final JsonObject target = lookupEntry(result, type, authId);
            copyFields(c, target);
        }

        return result.toString();
    }

    /**
     * Get credentials object from tree: {@code type->auth_id->}, validating duplicate entries.
     *
     * @param result The result to work on.
     * @param type The type to look for.
     * @param authId The auth id to look for.
     * @return The object from the tree, never returns {@code null}, will create a new entry when
     *         necessary.
     */
    private static JsonObject lookupEntry(final JsonObject result, final String type, final String authId) {

        final JsonObject typeObject = result.getJsonObject(type, new JsonObject());
        result.put(type, typeObject);

        JsonObject authObject = typeObject.getJsonObject(authId);
        if (authObject != null) {
            throw new IllegalArgumentException(String.format("Duplicate entry for 'type'/'authId': '%s'/'%s'", type, authId));
        }
        authObject = new JsonObject();
        typeObject.put(authId, authObject);
        return authObject;

    }

    @Override
    public Future<Optional<CredentialsReadResult>> getCredentials(final DeviceKey key, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "get credentials", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .start();

        return read(this.client, key, this.readCredentialsStatement, span)
                .<Optional<CredentialsReadResult>>flatMap(r -> {
                    final var entries = r.getRows(true);
                    span.log(Map.of(
                            "event", "read result",
                            "rows", entries.size()));
                    switch (entries.size()) {
                        case 0:
                            return Future.succeededFuture(Optional.empty());
                        case 1:
                            try {
                                final var entry = entries.get(0);
                                final var deviceId = entry.getString("device_id");
                                final var credentialsString = entry.getString("credentials");
                                final var credentials = decodeCredentials(credentialsString);
                                final var version = Optional.ofNullable(entry.getString("version"));
                                log.debug("Converted - deviceId: {}, version: {}, credentials: {} -> {}", deviceId, version, credentialsString, credentials);
                                return Future.succeededFuture(Optional.of(new CredentialsReadResult(deviceId, credentials, version)));
                            } catch (Exception e) {
                                log.info("Failed to convert result", e);
                                return Future.failedFuture(e);
                            }

                        default:
                            TracingHelper.logError(span, "Found multiple entries for a single device");
                            return Future.failedFuture(new IllegalStateException("Found multiple entries for a single device"));
                    }

                })

                .onComplete(x -> span.finish());

    }

    private List<CommonCredential> decodeCredentials(final String credentials) {
        if (credentials == null || credentials.isBlank()) {
            return Collections.emptyList();
        }

        if (this.hierarchical) {
            return decodeCredentialsHierarchical(credentials);
        } else {
            return Arrays.asList(Json.decodeValue(credentials, CommonCredential[].class));
        }
    }

    static List<CommonCredential> decodeCredentialsHierarchical(final String credentials) {
        final JsonObject json = new JsonObject(credentials);

        final List<CommonCredential> result = new ArrayList<>();

        for (Map.Entry<String, Object> typeEntry : (Iterable<Map.Entry<String, Object>>) () -> json.iterator()) {
            final Object value = typeEntry.getValue();
            if (!(value instanceof JsonObject)) {
                continue;
            }
            final JsonObject jsonValue = (JsonObject) value;
            for (Map.Entry<String, Object> authEntry : (Iterable<Map.Entry<String, Object>>) () -> jsonValue.iterator()) {
                final Object credentialValue = authEntry.getValue();
                if (!(credentialValue instanceof JsonObject)) {
                    continue;
                }

                final JsonObject credentialJsonValue = (JsonObject) credentialValue;
                final JsonObject credentialEntry = new JsonObject();
                credentialEntry.put(CredentialsConstants.FIELD_TYPE, typeEntry.getKey());
                credentialEntry.put(CredentialsConstants.FIELD_AUTH_ID, authEntry.getKey());
                copyFields(credentialJsonValue, credentialEntry);
                result.add(credentialEntry.mapTo(CommonCredential.class));
            }
        }

        return result;

    }

    /**
     * Copy field from source to target object, if set.
     *
     * @param from Source to copy from.
     * @param to Target to copy to.
     * @param key The key of the field.
     */
    private static void copyField(final JsonObject from, final JsonObject to, final String key) {
        final Object value = from.getValue(key);
        if (value != null) {
            to.put(key, value);
        }
    }

    /**
     * Copy all credential fields.
     *
     * @param from Source to copy from.
     * @param to Target to copy to.
     */
    private static void copyFields(final JsonObject from, final JsonObject to) {
        copyField(from, to, CredentialsConstants.FIELD_ENABLED);
        copyField(from, to, CredentialsConstants.FIELD_SECRETS);
        copyField(from, to, "comment");
        copyField(from, to, "ext");
    }

}
