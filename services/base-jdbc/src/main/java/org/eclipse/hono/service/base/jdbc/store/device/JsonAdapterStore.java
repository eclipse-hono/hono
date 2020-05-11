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

import org.eclipse.hono.deviceregistry.service.credentials.CredentialKey;
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
import io.opentracing.log.Fields;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;

/**
 * A data store for devices and credentials, based on a JSON data model.
 */
public class JsonAdapterStore extends AbstractDeviceAdapterStore {

    public static final String DEFAULT_TABLE_NAME_JSON = "devices";

    private static final Logger log = LoggerFactory.getLogger(JsonAdapterStore.class);

    private final Statement findCredentialsStatement;

    private final boolean hierarchical;

    /**
     * Create a new instance.
     *
     * @param client The SQL client ot use.
     * @param tracer The tracer to use.
     * @param hierarchical If the JSON store uses a hierarchical model for a flat one.
     * @param cfg The SQL statement configuration.
     */
    public JsonAdapterStore(final SQLClient client, final Tracer tracer, final boolean hierarchical, final StatementConfiguration cfg) {
        super(client, tracer, cfg);
        cfg.dump(log);

        this.hierarchical = hierarchical;

        this.findCredentialsStatement = cfg
                .getRequiredStatement("findCredentials")
                .validateParameters(
                        "tenant_id",
                        "type",
                        "auth_id");

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
    public Future<Optional<CredentialsReadResult>> findCredentials(final CredentialKey key, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "find credentials", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("auth_id", key.getAuthId())
                .withTag("type", key.getType())
                .start();

        final var expanded = this.findCredentialsStatement.expand(params -> {
            params.put("tenant_id", key.getTenantId());
            params.put("type", key.getType());
            params.put("auth_id", key.getAuthId());
        });

        log.debug("findCredentials - statement: {}", expanded);
        return expanded

                .trace(this.tracer, span)
                .query(this.client)

                .<Optional<CredentialsReadResult>>flatMap(r -> {
                    final var entries = r.getRows(true);
                    span.log(Map.of(
                            Fields.EVENT, "read result",
                            "rows", entries.size()));
                    switch (entries.size()) {
                        case 0:
                            return Future.succeededFuture(Optional.empty());
                        case 1:
                            final var entry = entries.get(0);
                            final var deviceId = entry.getString("device_id");
                            final var credentials = decodeCredentials(entry.getString("credentials"));
                            final var version = Optional.ofNullable(entry.getString("version"));
                            return Future.succeededFuture(Optional.of(new CredentialsReadResult(deviceId, credentials, version)));
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
