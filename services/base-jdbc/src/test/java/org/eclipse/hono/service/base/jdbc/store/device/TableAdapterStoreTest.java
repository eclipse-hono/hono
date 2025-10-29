/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.management.device.Device;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.StringTag;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

/**
 * Tests the handling of device data in the database.
 */
class TableAdapterStoreTest {

    private final Span span = mock(Span.class);
    private final SpanContext spanContext = mock(SpanContext.class);
    private final io.vertx.sqlclient.SqlConnection sqlConnection = mock(io.vertx.sqlclient.SqlConnection.class);
    private final RowSet<Row> rowSet = mock(RowSet.class);
    private final Row row = mock(Row.class);
    private final Tracer.SpanBuilder spanBuilder = mock(Tracer.SpanBuilder.class);
    private final Tracer tracer = mock(Tracer.class);
    private final JDBCPool client = mock(JDBCPool.class);

    private TableAdapterStore store;
    private final String TENANT_ID = "test-tenant";
    private final String DEVICE_ID = "device-1";
    private final String VERSION = "v1";

    /**
     * Creates a test device with default values.
     */
    private Device createTestDevice() {
        return new Device()
                .setEnabled(true)
                .setVia(Collections.singletonList("group1"));
    }

    /**
     * Mocks a single row of device data.
     *
     * @param deviceJson The JSON string of the device data (can be null)
     * @param version The version string
     */
    private void mockSingleRow(final String deviceJson, final String version) {
        // Setup row data
        when(row.size()).thenReturn(2);
        when(row.getValue(0)).thenReturn(deviceJson);
        when(row.getValue(1)).thenReturn(version);

        // Setup row set
        when(rowSet.columnsNames()).thenReturn(List.of("data", "version"));
        when(rowSet.size()).thenReturn(1);

        // Setup iterator
        final var iterator = mock(RowIterator.class);
        when(rowSet.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true).thenReturn(false);
        when(iterator.next()).thenReturn(row);

        // Mock forEach
        doAnswer(invocation -> {
            final Consumer<Row> consumer = invocation.getArgument(0);
            consumer.accept(row);
            return null;
        }).when(rowSet).forEach(any());

        // Mock prepared query
        final var preparedQueryMock = mock(io.vertx.sqlclient.PreparedQuery.class);
        when(preparedQueryMock.execute(any(Tuple.class))).thenReturn(Future.succeededFuture(rowSet));
        when(sqlConnection.preparedQuery(anyString())).thenReturn(preparedQueryMock);

        // Mock direct query
        doAnswer(invocation -> {
            final io.vertx.core.Handler<io.vertx.core.AsyncResult<RowSet<Row>>> handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(rowSet));
            return null;
        }).when(sqlConnection).query(anyString());
    }

    @BeforeEach
    void setUp() {
        // Setup tracing mocks
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.addReference(anyString(), any())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), anyString())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), any(Number.class))).thenReturn(spanBuilder);
        when(spanBuilder.withTag(any(StringTag.class), anyString())).thenReturn(spanBuilder);
        when(spanBuilder.ignoreActiveSpan()).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(span);
        when(span.context()).thenReturn(spanContext);

        // Setup JDBC client mocks
        when(client.getConnection()).thenReturn(Future.succeededFuture(sqlConnection));
        when(sqlConnection.close()).thenReturn(Future.succeededFuture());
        final var statementConfig = StatementConfiguration.empty().overrideWith(getStatementsAsInputStream(), true);

        store = new TableAdapterStore(client, tracer, statementConfig, null);
    }

    private static @NotNull ByteArrayInputStream getStatementsAsInputStream() {
        final Map<String, String> statements = new HashMap<>();
        statements.put("readRegistration",
                "SELECT * FROM devices WHERE tenant_id = :tenant_id AND device_id = :device_id");
        statements.put("findCredentials",
                "SELECT * FROM credentials WHERE tenant_id = :tenant_id AND type = :type AND auth_id = :auth_id");
        statements.put("resolveGroups",
                "SELECT * FROM device_groups WHERE tenant_id = :tenant_id AND group_id = ANY(:group_ids)");

        final Yaml yaml = new Yaml();
        final String yamlString = yaml.dump(statements);
        return new ByteArrayInputStream(yamlString.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testReadDeviceSuccess() {
        // Given
        final var device = createTestDevice();
        final var deviceJson = JsonObject.mapFrom(device).encode();
        mockSingleRow(deviceJson, VERSION);

        // When
        final var result = store.readDevice(DeviceKey.from(TENANT_ID, DEVICE_ID), spanContext).result();

        // Then
        assertTrue(result.isPresent());
        final var deviceResult = result.get();
        assertTrue(deviceResult.getDevice().isEnabled());
        assertEquals(VERSION, deviceResult.getResourceVersion().orElse(""));
    }

    @Test
    void testReadDeviceSuccess_dataNull() {
        // Given
        mockSingleRow(null, VERSION);

        // When
        final var result = store.readDevice(DeviceKey.from(TENANT_ID, DEVICE_ID), spanContext).result();

        // Then
        assertTrue(result.isPresent());
        final var deviceResult = result.get();
        assertTrue(deviceResult.getDevice().isEnabled());
        assertEquals(VERSION, deviceResult.getResourceVersion().orElse(""));
    }

    @Test
    void testReadDeviceNotFound() {
        // Given
        final var key = DeviceKey.from(TENANT_ID, "non-existent-device");

        // Mock empty result set
        when(rowSet.iterator()).thenReturn(mock(RowIterator.class));
        when(rowSet.size()).thenReturn(0);
        doAnswer(invocation -> null).when(rowSet).forEach(any());

        // Mock queries
        final var preparedQueryMock = mock(io.vertx.sqlclient.PreparedQuery.class);
        when(preparedQueryMock.execute(any(Tuple.class))).thenReturn(Future.succeededFuture(rowSet));
        when(sqlConnection.preparedQuery(anyString())).thenReturn(preparedQueryMock);

        // When
        final var result = store.readDevice(key, spanContext).result();

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testReadDeviceMultipleEntries() {
        // Given
        final var device = createTestDevice();
        final var deviceJson = JsonObject.mapFrom(device).encode();

        // Mock multiple rows
        when(row.size()).thenReturn(2);
        when(row.getValue(0)).thenReturn(deviceJson);
        when(row.getValue(1)).thenReturn(VERSION);
        when(rowSet.columnsNames()).thenReturn(List.of("data", "version"));
        when(rowSet.size()).thenReturn(2);

        // Setup iterator for multiple rows
        final var iterator = mock(RowIterator.class);
        when(rowSet.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true, true, false);
        when(iterator.next()).thenReturn(row);

        // Mock forEach for multiple rows
        doAnswer(invocation -> {
            final Consumer<Row> consumer = invocation.getArgument(0);
            consumer.accept(row);
            consumer.accept(row);
            return null;
        }).when(rowSet).forEach(any());

        // Mock queries
        final var preparedQueryMock = mock(io.vertx.sqlclient.PreparedQuery.class);
        when(preparedQueryMock.execute(any(Tuple.class))).thenReturn(Future.succeededFuture(rowSet));
        when(sqlConnection.preparedQuery(anyString())).thenReturn(preparedQueryMock);

        // When/Then
        final var future = store.readDevice(DeviceKey.from(TENANT_ID, "duplicate-device"), spanContext);
        assertTrue(future.failed());
        assertEquals("Found multiple entries for a single device", future.cause().getMessage());
    }
}
