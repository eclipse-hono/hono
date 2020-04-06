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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.service.base.jdbc.store.Statement.ExpandedStatement;
import org.junit.jupiter.api.Test;

/**
 * Test parsing queries.
 */
public class QueryTest {

    /**
     * Test a statement with no parameters.
     */
    @Test
    public void testNoParams() {
        final ExpandedStatement expanded = Statement.statement("select 1").expand(Collections.emptyMap());
        assertEquals("select 1", expanded.getSql());
        assertArrayEquals(new Object[] {}, expanded.getParameters());
    }

    /**
     * Test a statement with some simple named parameters.
     */
    @Test
    public void testSimpleParams() {
        final Map<String, Object> params = new HashMap<>();
        params.put("foo", 1);
        params.put("bar", "baz");

        final ExpandedStatement expanded = Statement.statement("select * from table where foo=:foo and bar=:bar").expand(params);
        assertEquals("select * from table where foo=? and bar=?", expanded.getSql());
        assertArrayEquals(new Object[] {1, "baz"}, expanded.getParameters());
    }

    /**
     * Test using the same named parameter multiple times.
     */
    @Test
    public void testDoubleParams() {
        final Map<String, Object> params = new HashMap<>();
        params.put("foo", 1);
        params.put("bar", "baz");

        final ExpandedStatement expanded = Statement.statement("select * from table where foo=:foo and foo2=:foo and bar=:bar and barfoo=:foo and 1=1").expand(params);
        assertEquals("select * from table where foo=? and foo2=? and bar=? and barfoo=? and 1=1", expanded.getSql());
        assertArrayEquals(new Object[] {1, 1, "baz", 1}, expanded.getParameters());
    }

    /**
     * Test providing additional named parameters, which are not referenced in the statement.
     */
    @Test
    public void testExtraParams() {
        final ExpandedStatement expanded = Statement.statement("select * from table where foo=:foo and bar=:bar")
                .expand(params -> {
                    params.put("foobarbaz", true); // not used
                    params.put("foo", 1);
                    params.put("bar", "baz");
                });
        assertEquals("select * from table where foo=? and bar=?", expanded.getSql());
        assertArrayEquals(new Object[] {1, "baz"}, expanded.getParameters());
    }

    /**
     * Test that a missing parameter throws an {@link IllegalArgumentException}.
     */
    @Test
    public void testMissingParam() {
        assertThrows(IllegalArgumentException.class, () -> {
            Statement.statement("select * from table where foo=:foo and bar=:bar")
                    .expand(params -> {
                        params.put("bar", "baz");
                    });
        });
    }

    /**
     * Test that a simple postgres JSON statement works.
     */
    @Test
    public void testPostgresJson1() {
        final ExpandedStatement expanded = Statement
                .statement("SELECT device_id, version, credentials FROM table WHERE tenant_id=:tenant_id AND credentials @> jsonb_build_object('type', :type, 'auth-id', :auth_id)")
                .expand(map -> {
                    map.put("tenant_id", "tenant");
                    map.put("type", "hashed-password");
                    map.put("auth_id", "auth");
                });

        assertEquals("SELECT device_id, version, credentials FROM table WHERE tenant_id=? AND credentials @> jsonb_build_object('type', ?, 'auth-id', ?)", expanded.getSql());
        assertArrayEquals(new Object[] {"tenant", "hashed-password", "auth"}, expanded.getParameters());
    }

    /**
     * Test that a postgres type case (double colon) works.
     */
    @Test
    public void testPostgresCast() {
        final ExpandedStatement expanded =
                Statement.statement("INSERT INTO %s (tenant_id, device_id, version, data) VALUES (:tenant_id, :device_id, :version, to_jsonb(:data::jsonb))", "table")
                        .expand(map -> {
                            map.put("tenant_id", "tenant");
                            map.put("device_id", "device");
                            map.put("version", "version");
                            map.put("data", "{}");
                        });

        assertEquals("INSERT INTO table (tenant_id, device_id, version, data) VALUES (?, ?, ?, to_jsonb(?::jsonb))", expanded.getSql());
        assertArrayEquals(new Object[] {"tenant", "device", "version", "{}"}, expanded.getParameters());
    }


}
