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

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.net.UrlEscapers;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;

/**
 * SQL helper methods.
 */
public final class SQL {

    public static final String DIALECT_POSTGRESQL = "postgresql";
    public static final String DIALECT_H2 = "h2";

    private static final Logger log = LoggerFactory.getLogger(SQL.class);

    private SQL() {
    }

    /**
     * Translate from a generic SQL exception to a more typed exception.
     *
     * @param <T> The type of the future.
     * @param e The exception to translate.
     * @return The result, as a future. This will always be a failed future.
     */
    public static <T> Future<T> translateException(final Throwable e) {

        final var sqlError = causeOf(e, SQLException.class).orElse(null);

        if (sqlError == null) {
            return Future.failedFuture(e);
        }

        log.debug("SQL Error: {}: {}", sqlError.getSQLState(), sqlError.getMessage());
        final String code = sqlError.getSQLState();
        if (code == null) {
            return Future.failedFuture(e);
        }

        switch (code) {
            case "23000": //$FALL-THROUGH$
            case "23505":
                return Future.failedFuture(new DuplicateKeyException(e));
            default:
                return Future.failedFuture(e);
        }
    }

    /**
     * Enable auto-commit.
     *
     * @param tracer The tracer.
     * @param context The span.
     * @param connection The database connection to change.
     * @param state The auto-commit state.
     * @return A future for tracking the outcome.
     */
    public static Future<SQLConnection> setAutoCommit(final Tracer tracer, final SpanContext context, final SQLConnection connection, final boolean state) {
        final Span span = startSqlSpan(tracer, context, "set autocommit", builder -> {
            builder.withTag("db.autocommit", state);
        });
        final Promise<Void> promise = Promise.promise();
        connection.setAutoCommit(state, promise);
        return finishSpan(promise.future().map(connection), span, null);
    }

    /**
     * Perform commit operation.
     *
     * @param tracer The tracer.
     * @param context The span.
     * @param connection The database connection to work on.
     * @return A future for tracking the outcome.
     */
    public static Future<SQLConnection> commit(final Tracer tracer, final SpanContext context, final SQLConnection connection) {
        final Span span = startSqlSpan(tracer, context, "commit", null);
        final Promise<Void> promise = Promise.promise();
        connection.commit(promise);
        return finishSpan(promise.future().map(connection), span, null);
    }

    /**
     * Perform rollback operation.
     *
     * @param tracer The tracer.
     * @param context The span.
     * @param connection The database connection to work on.
     * @return A future for tracking the outcome.
     */
    public static Future<SQLConnection> rollback(final Tracer tracer, final SpanContext context, final SQLConnection connection) {
        final Span span = startSqlSpan(tracer, context, "rollback", null);
        final Promise<Void> promise = Promise.promise();
        connection.rollback(promise);
        return finishSpan(promise.future().map(connection), span, null);
    }

    /**
     * Start a new span for an SQL operation.
     *
     * @param tracer The tracer to use.
     * @param context The current span context.
     * @param operationName The name of the operation.
     * @param customizer An optional customizer.
     * @return The newly created span. The caller is required to finish the span.
     */
    public static Span startSqlSpan(final Tracer tracer, final SpanContext context, final String operationName, final Consumer<Tracer.SpanBuilder> customizer) {

        if (tracer == null || context == null) {
            return null;
        }

        final SpanBuilder builder = TracingHelper
                .buildChildSpan(tracer, context, operationName, SQL.class.getSimpleName())
                .withTag(Tags.DB_TYPE.getKey(), "sql");

        if (customizer != null) {
            customizer.accept(builder);
        }

        return builder.start();

    }

    /**
     * Finish an SQL span.
     *
     * @param <T> The type of the future.
     * @param future The future of the SQL operation.
     * @param span The span to log to.
     * @param extractor An optional way to extract more information to the span. All properties added to
     *        the provided map will be logged in case of a successful operation.
     * @return The
     */
    public static <T> Future<T> finishSpan(final Future<T> future, final Span span, final BiConsumer<T, Map<String, Object>> extractor) {
        if (span == null) {
            return future;
        }

        return future
                .onComplete(result -> {
                    if (result.succeeded()) {
                        traceSuccess(result.result(), span, extractor);
                    } else {
                        traceError(result.cause(), span);
                    }
                });
    }

    private static <T> void traceSuccess(final T result, final Span span, final BiConsumer<T, Map<String, Object>> extractor) {
        final Map<String, Object> log = new HashMap<>();
        log.put(Fields.EVENT, "success");
        if (extractor != null) {
            extractor.accept(result, log);
        }
        span.log(log);
        span.finish();
    }

    private static void traceError(final Throwable e, final Span span) {
        span.log(Map.of(
                Fields.EVENT, "error",
                Fields.ERROR_KIND, e.getClass().getName(),
                Fields.ERROR_OBJECT, e,
                Fields.MESSAGE, e.getMessage(),
                Fields.STACK, Throwables.getStackTraceAsString(e)));
        Tags.ERROR.set(span, true);
        span.finish();
    }

    /**
     * Extract the database type from the JDBC URL.
     *
     * @param url A JDBC URL.
     * @return The type of the database.
     * @throws IllegalArgumentException if the URL is not a JDBC URL, it must start with {@code jdbc:}.
     */
    public static String getDatabaseDialect(final String url) {
        final URI uri = URI.create(UrlEscapers.urlPathSegmentEscaper().escape(url));
        final String scheme = uri.getScheme();
        if (!"jdbc".equals(scheme)) {
            throw new IllegalArgumentException("URL is not a JDBC url: " + url);
        }
        final URI subUri = URI.create(UrlEscapers.urlPathSegmentEscaper().escape(uri.getSchemeSpecificPart()));
        return subUri.getScheme();
    }

    /**
     * Checks if the given string matches one of the supported database dialects.
     *
     * @param databaseDialect The dialect to be checked.
     * @return {@code true} if databaseDialect is a supported dialect.
     */
    public static boolean isSupportedDatabaseDialect(final String databaseDialect) {
        return List.of(SQL.DIALECT_H2, SQL.DIALECT_POSTGRESQL).contains(databaseDialect);
    }

    /**
     * Find a cause of provided type.
     *
     * @param <T> The cause type to look for.
     * @param e The throwable to search.
     * @param clazz The class to look for.
     * @return An {@link Optional} with the cause of type {@code <T>}, should that exist in the cause
     *         chain. Otherwise, or of the throwable provided is {@code null}, {@link Optional#empty()}
     *         will be returned.
     */
    public static <T extends Throwable> Optional<T> causeOf(final Throwable e, final Class<T> clazz) {
        if (e == null) {
            return Optional.empty();
        }
        return Throwables.getCausalChain(e)
                .stream()
                .filter(clazz::isInstance)
                .findFirst()
                .map(clazz::cast);
    }

    /**
     * Check if the throwable has a cause of the provided type.
     *
     * @param <T> The cause type to look for.
     * @param e The throwable to search.
     * @param clazz The class to look for.
     * @return {@code true} if the cause chain contains the requested exception, {@code false}
     *         otherwise. If the throwable to check is {@code null}, false will be returned as well.
     */
    public static <T extends Throwable> boolean hasCauseOf(final Throwable e, final Class<T> clazz) {
        return causeOf(e, clazz).isPresent();
    }

    /**
     * Run operation transactionally.
     * <p>
     * This function will perform the following operations:
     * <ul>
     *     <li>Open a new connection</li>
     *     <li>Turn off auto-commit mode</li>
     *     <li>Call the provided function</li>
     *     <li>If the provided function failed, perform a <em>Rollback</em> operation</li>
     *     <li>If the provided function succeeded, perform a <em>Commit</em> operation</li>
     *     <li>Close the connection</li>
     * </ul>
     *
     * @param client The client to use.
     * @param tracer The tracer to use.
     * @param function The function to execute while the transaction is open.
     * @param context The span to log to.
     * @param <T> The type of the result.
     * @return A future, tracking the outcome of the operation.
     */
    public static <T> Future<T> runTransactionally(final SQLClient client, final Tracer tracer, final SpanContext context, final BiFunction<SQLConnection, SpanContext, Future<T>> function) {

        final Span span = startSqlSpan(tracer, context, "run transactionally", builder -> {
        });

        final Promise<SQLConnection> promise = Promise.promise();
        client.getConnection(promise);

        return promise.future()

                // log open connection
                .onSuccess(x -> {
                    final Map<String, Object> log = new HashMap<>();
                    log.put(Fields.EVENT, "success");
                    log.put(Fields.MESSAGE, "connection opened");
                    span.log(log);
                })

                // disable autocommit, which is enabled by default
                .flatMap(connection -> SQL.setAutoCommit(tracer, span.context(), connection, false)

                        // run code
                        .flatMap(y -> function.apply(connection, span.context())

                                // commit or rollback ... return original result
                                .compose(
                                        v -> SQL.commit(tracer, span.context(), connection).map(v),
                                        x -> SQL.rollback(tracer, span.context(), connection).flatMap(unused -> Future.failedFuture(x))))

                        // close the connection
                        .onComplete(x -> connection.close()))

                .onComplete(x -> span.finish());

    }

}
