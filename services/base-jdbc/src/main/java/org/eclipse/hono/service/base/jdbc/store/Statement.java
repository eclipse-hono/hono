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

package org.eclipse.hono.service.base.jdbc.store;

import java.io.Serializable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;

/**
 * An SQL statement, which can map named parameters to positional parameters.
 */
public final class Statement {

    private static final Pattern DEFAULT_PATTERN = Pattern.compile("(?<pre>^|[^\\:]):(?<name>[a-zA-Z_]+)");

    private static final Object NOT_FOUND_MARKER = new Object();

    private final String sql;
    private final List<Map.Entry<String, Integer>> mappings;

    private Statement(final String sql, final List<Map.Entry<String, Integer>> mappings) {
        Objects.requireNonNull(sql);
        Objects.requireNonNull(mappings);

        this.sql = sql;
        this.mappings = mappings;
    }

    /**
     * Validate that all named parameters can be filled.
     *
     * @param availableParameters The parameters are available for expanding the statement.
     * @return The instance, for chained invocations.
     * @throws IllegalStateException if the statement uses a named parameters, which is not provided as
     *         "available".
     */
    public Statement validateParameters(final String... availableParameters) {
        if (availableParameters == null || availableParameters.length <= 0) {
            return this;
        }

        // sort for binary search
        Arrays.sort(availableParameters);

        final Set<String> missingKeys = new HashSet<>();
        for (final Map.Entry<String, Integer> entry : this.mappings) {
            if (Arrays.binarySearch(availableParameters, entry.getKey()) < 0) {
                missingKeys.add(entry.getKey());
            }
        }

        if (!missingKeys.isEmpty()) {
            final String[] keys = missingKeys.toArray(String[]::new);
            // sort for stable output order
            Arrays.sort(keys);

            throw new IllegalStateException(String.format(
                    "Statement uses keys which are not available - missing: %s, available: %s, statement: %s",
                    Arrays.toString(keys),
                    Arrays.toString(availableParameters),
                    this.sql));
        }

        return this;
    }

    /**
     * Expand the statement with an empty map.
     * <p>
     * This actually calls {@link #expand(Map)} with an empty map.
     *
     * @return The expanded SQL statement.
     * @throws IllegalArgumentException If a named field is present for which there is not mapped
     *         parameter.
     */
    public ExpandedStatement expand() {
        return expand(Collections.emptyMap());
    }

    /**
     * Expand the statement with the provided named parameters.
     *
     * @param mapBuilder Allows you to build a map, rather then providing one.
     * @return The expanded statement.
     * @throws IllegalArgumentException If a named field is present for which there is not mapped
     *         parameter.
     */
    public ExpandedStatement expand(final Consumer<Map<String, Object>> mapBuilder) {
        final Map<String, Object> map = new HashMap<>();
        mapBuilder.accept(map);
        map.forEach((key, value) -> {
            if (value != null && !(value instanceof Serializable)) {
                throw new RuntimeException(String.format("%s of type %s is not serializable", key, value.getClass()));
            }
        });
        return expand(map);
    }

    /**
     * Expand the statement with the provided named parameters.
     *
     * @param parameters The named parameters, may be empty, but must not be {@code null}.
     * @return The expanded statement.
     * @throws IllegalArgumentException If a named field is present for which there is not mapped
     *         parameter.
     */
    public ExpandedStatement expand(final Map<String, Object> parameters) {
        final Object[] params = new Object[this.mappings.size()];

        for (Map.Entry<String, Integer> entry : this.mappings) {
            final Object value = parameters.getOrDefault(entry.getKey(), NOT_FOUND_MARKER);
            if (value == NOT_FOUND_MARKER) { // we explicitly check here for equality of the object reference
                throw new IllegalArgumentException(String.format("Value for named parameter '%s' is missing", entry.getKey()));
            }
            params[entry.getValue()] = value;
        }

        return new ExpandedStatement(this.sql, params);
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("sql", this.sql)
                .add("mappings", this.mappings)
                .toString();
    }

    /**
     * Create a new SQL statement instance.
     * <p>
     * This will parse the SQL statement for named parameters, and record the information for expanding
     * it later on.
     *
     * @param sql The SQL statement to process. This is a formatted string according to
     *        {@link String#format(String, Object...)}.
     * @param values The values to replace in the parameter {@code sql}.
     * @return The statement, or {@code null} if the provided SQL is {@code null}.
     */
    @FormatMethod
    public static Statement statement(@FormatString final String sql, final Object... values) {
        if (sql == null) {
            return null;
        }

        final String sqlFormatted = String.format(sql, values);

        final Matcher m = DEFAULT_PATTERN.matcher(sqlFormatted);

        int idx = 0;
        final StringBuilder sb = new StringBuilder();
        final List<Map.Entry<String, Integer>> mappings = new ArrayList<>();
        while (m.find()) {
            m.appendReplacement(sb, "${pre}?");
            mappings.add(new SimpleImmutableEntry<>(m.group("name"), idx));
            idx++;
        }
        m.appendTail(sb);

        return new Statement(sb.toString(), mappings);
    }

    /**
     * An expanded statement.
     * <p>
     * This class contains the positional parameters, and their values, expanded from a named parameter
     * statement.
     */
    public static class ExpandedStatement {
        private final String sql;
        private final Object[] parameters;

        private final Tracer tracer;
        private final SpanContext spanContext;

        private ExpandedStatement(final String sql, final Object[] parameters, final Tracer tracer, final SpanContext spanContext) {
            this.sql = sql;
            this.parameters = parameters;
            this.tracer = tracer;
            this.spanContext = spanContext;
        }

        private ExpandedStatement(final String sql, final Object[] parameters) {
            this(sql, parameters, null, null);
        }

        public String getSql() {
            return this.sql;
        }

        public Object[] getParameters() {
            return this.parameters;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("sql", this.sql)
                    .add("parameters", this.parameters)
                    .toString();
        }

        /**
         * Attach a span to an expanded statement.
         *
         * @param tracer The tracer to create spans with.
         * @param spanContext The span to log to.
         * @return The new instance, containing the span.
         */
        public ExpandedStatement trace(final Tracer tracer, final SpanContext spanContext) {
            return new ExpandedStatement(this.sql, this.parameters, tracer, spanContext);
        }

        /**
         * Start a new span for this SQL statement.
         * @return The newly created span.
         */
        public Span startSqlSpan() {
            if (this.tracer == null || this.spanContext == null) {
                return null;
            }

            return SQL.startSqlSpan(this.tracer, this.spanContext, "execute SQL", builder -> {
                builder.withTag(Tags.DB_STATEMENT.getKey(), this.sql);
            });
        }

        /**
         * Execute this statement as a query.
         * @param pool The connection pool to work on.
         * @return A future tracking the query result.
         */
        public Future<ResultSet> query(final JDBCPool pool) {
            final Span sqlSpan = startSqlSpan();
            return SQL.finishSpan(pool
                    .preparedQuery(this.sql)
                    .execute(Tuple.from(getParameters()))
                    .map(this::convertRowSetToResultSet), sqlSpan, (r, log) -> {
                log.put("rows", r.getNumRows());
            });
        }

        /**
         * Execute this statement as a query.
         * @param connection The connection to work on.
         * @return A future tracking the query result.
         */
        public Future<ResultSet> query(final SqlConnection connection) {
            final Span sqlSpan = startSqlSpan();
            return SQL.finishSpan(connection
                    .preparedQuery(this.sql)
                    .execute(Tuple.from(getParameters()))
                    .map(this::convertRowSetToResultSet), sqlSpan, (r, log) -> {
                log.put("rows", r.getNumRows());
            });
        }

        /**
         * Execute this statement as a update.
         * @param pool The connection pool to work on.
         * @return A future tracking the update result.
         */
        public Future<UpdateResult> update(final JDBCPool pool) {
            final Span sqlSpan = startSqlSpan();
            return SQL.finishSpan(pool
                    .preparedQuery(this.sql)
                    .execute(Tuple.from(getParameters()))
                    .map(this::convertRowSetToUpdateResult), sqlSpan, (r, log) -> {
                log.put("rows", r);
            });
        }

        /**
         * Execute this statement as a update.
         * @param connection The connection to work on.
         * @return A future tracking the update result.
         */
        public Future<UpdateResult> update(final SqlConnection connection) {
            final Span sqlSpan = startSqlSpan();
            return SQL.finishSpan(connection
                    .preparedQuery(this.sql)
                    .execute(Tuple.from(getParameters()))
                    .map(this::convertRowSetToUpdateResult), sqlSpan, (r, log) -> {
                log.put("rows", r);
            });
        }

        private ResultSet convertRowSetToResultSet(final RowSet<Row> rows) {
            final List<JsonArray> results = new ArrayList<>();
            rows.forEach(row -> {
                final JsonArray values = new JsonArray();
                for (int index = 0; index < row.size(); ++index) {
                    values.add(row.getValue(index));
                }
                results.add(values);
            });
            return new ResultSet()
                    .setColumnNames(rows.columnsNames())
                    .setResults(results);
        }

        private UpdateResult convertRowSetToUpdateResult(final SqlResult<RowSet<Row>> sqlResult) {
            return new UpdateResult()
                    .setUpdated(sqlResult.rowCount());
        }

    }

}
