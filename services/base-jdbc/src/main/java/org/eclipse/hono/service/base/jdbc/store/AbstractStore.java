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

package org.eclipse.hono.service.base.jdbc.store;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.base.jdbc.store.Statement.ExpandedStatement;
import org.eclipse.hono.tracing.TracingHelper;

import com.google.common.collect.ImmutableMap;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;

/**
 * An abstract JDBC based data store.
 */
public abstract class AbstractStore implements HealthCheckProvider, AutoCloseable {

    /**
     * The default statement to use for checking if a connection is live.
     */
    public static final String DEFAULT_CHECK_SQL = "SELECT 1";

    private final JDBCClient client;
    private final Tracer tracer;

    private final ExpandedStatement checkSql;

    /**
     * Create a new instance.
     *
     * @param client The client to use for accessing the DB.
     * @param tracer The tracer to use.
     * @param checkSql An optional SQL statement, which will be used to check if the connection to the
     *        database is OK. It this value is empty, the default statement {@value #DEFAULT_CHECK_SQL} will be used.
     */
    public AbstractStore(final JDBCClient client, final Tracer tracer, final Optional<Statement> checkSql) {
        this.client = Objects.requireNonNull(client);
        this.tracer = Objects.requireNonNull(tracer);
        this.checkSql = checkSql.orElseGet(() -> Statement.statement(DEFAULT_CHECK_SQL)).expand();
    }

    @Override
    public void close() throws Exception {
        this.client.close();
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        readinessHandler.register("sql", Duration.ofSeconds(10).toMillis(), p -> {
            this.checkSql
                    .query(this.client)
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            p.tryComplete(Status.OK());
                        } else {
                            p.tryFail(ar.cause());
                        }
                    });
        });
    }

    /**
     * Check of an optimistic lock outcome.
     * <p>
     * This method will take the result of a simple update and, in case of a versioned update, try to
     * figure out of the optimistic lock held.
     * <p>
     * The implementation will do a simple, post update query to the entity, in case no rows got
     * updated, and then check for the existence of the entity. So there still is a race condition
     * possible, of an object not being updated because it doesn't exist, and a new, matching entity
     * being created at the same time. Or a failed update, and someone deleting the object at the same
     * time. The state in the database would always be consistent.
     * <p>
     * So it might be that the method reports a "nothing updated" condition, although when the update
     * statement was updated, it was a broken optimistic lock condition. However, the final result still
     * is that the update failed, and the object is now deleted.
     *
     * @param result The original result, from updating the entity.
     * @param reader The reader to use for fetching the entity, by key only.
     * @param resourceVersion The optional resource version.
     * @param span The tracing span.
     * @return If the resource version is not set, it will return the result unaltered. Otherwise it
     *         will read the entity by key only. If it finds the entity, then this is considered a
     *         broken optimistic lock, and a failed future will be returned. Otherwise it is considered
     *         an "object not found" condition.
     */
    protected Future<UpdateResult> checkOptimisticLock(
            final Future<UpdateResult> result,
            final Span span,
            final Optional<String> resourceVersion,
            final Function<Span, Future<ResultSet>> reader) {

        // if we don't have a resource version ...
        if (resourceVersion.isEmpty()) {
            /// ... then there is no need to check
            return result;
        }

        return result
                .flatMap(r -> {

                    span.log(ImmutableMap.<String, Object>builder()
                            .put("event", "check update result")
                            .put("update_count", r.getUpdated())
                            .build());

                    // if we updated something ...
                    if (r.getUpdated() != 0) {
                        // ... then the optimistic lock held
                        return Future.succeededFuture(r);
                    }

                    final Span readSpan = TracingHelper.buildChildSpan(this.tracer, span.context(), "check optimistic lock", getClass().getSimpleName())
                            .withTag("resource_version", resourceVersion.get())
                            .start();

                    // we did not update anything, we need to check why ...
                    final var f = reader.apply(readSpan)
                            .flatMap(readResult -> {

                                span.log(Map.of(
                                        Fields.EVENT, "check read result",
                                        "read_count", readResult.getNumRows()));

                                // ... having read the current state, without the version ...
                                if (readResult.getNumRows() <= 0) {
                                    // ... we know that the entry simply doesn't exist
                                    return Future.succeededFuture(r);
                                } else {
                                    // ... we know that the entry does exists, just had the wrong version, and the lock broke
                                    return Future.failedFuture(new OptimisticLockingException());
                                }
                            });

                    return f.onComplete(x -> readSpan.finish());
                });

    }

}
