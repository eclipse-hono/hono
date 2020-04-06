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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hono.deviceregistry.service.credentials.CredentialKey;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.sql.SQLClient;

/**
 * A data store for devices and credentials, based on a table data model.
 */
public class TableAdapterStore extends AbstractDeviceAdapterStore {

    private static final Logger log = LoggerFactory.getLogger(TableAdapterStore.class);

    private final Statement findCredentialsStatement;

    /**
     * Create a new instance.
     *
     * @param client The SQL client ot use.
     * @param tracer The tracer to use.
     * @param cfg The SQL statement configuration.
     */
    public TableAdapterStore(final SQLClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg);
        cfg.dump(log);

        this.findCredentialsStatement = cfg
                .getRequiredStatment("findCredentials")
                .validateParameters(
                        "tenant_id",
                        "type",
                        "auth_id");

    }

    @Override
    public Future<Optional<CredentialsReadResult>> findCredentials(final CredentialKey key, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "find credentials", getClass().getSimpleName())
                .withTag("auth_id", key.getAuthId())
                .withTag("type", key.getType())
                .withTag("tenant_instance_id", key.getTenantId())
                .start();

        final var expanded = this.findCredentialsStatement.expand(params -> {
            params.put("tenant_id", key.getTenantId());
            params.put("type", key.getType());
            params.put("auth_id", key.getAuthId());
        });

        log.debug("findCredentials - statement: {}", expanded);
        return expanded.trace(this.tracer, span).query(this.client)
                .<Optional<CredentialsReadResult>>flatMap(r -> {
                    final var entries = r.getRows(true);
                    span.log(Map.of(
                            "event", "read result",
                            "rows", entries.size()));

                    final Set<String> deviceIds = entries.stream()
                            .map(o -> o.getString("device_id"))
                            .filter(o -> o != null)
                            .collect(Collectors.toSet());

                    final int num = deviceIds.size();
                    if (num <= 0) {
                        return Future.succeededFuture(Optional.empty());
                    } else if (num > 1) {
                        TracingHelper.logError(span, "Found multiple entries for a single device");
                        return Future.failedFuture(new IllegalStateException("Found multiple entries for a single device"));
                    }

                    // we know now that we have exactly one entry
                    final String deviceId = deviceIds.iterator().next();

                    final List<CommonCredential> credentials = entries.stream()
                            .map(o -> o.getString("data"))
                            .map(s -> Json.decodeValue(s, CommonCredential.class))
                            .collect(Collectors.toList());

                    return Future.succeededFuture(Optional.of(new CredentialsReadResult(deviceId, credentials, Optional.empty())));
                })

                .onComplete(x -> span.finish());

    }

}
