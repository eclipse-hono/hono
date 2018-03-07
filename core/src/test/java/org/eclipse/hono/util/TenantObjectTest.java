/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.util;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


/**
 * Verifies behavior of {@link TenantObject}.
 *
 */
public class TenantObjectTest {

    private static ObjectMapper mapper;

    /**
     * Initializes static fields.
     */
    @BeforeClass
    public static void init() {
        mapper = new ObjectMapper();
    }

    /**
     * Verifies that a JSON string containing custom non-scalar configuration
     * properties can be deserialized into a {@code TenantObject}.
     * 
     * @throws Exception if the JSON cannot be deserialized.
     */
    @Test
    public void testDeserializationOfComplexCustomConfigProperties() throws Exception {

        final JsonObject adapterConfig = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, "custom")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put("customConfig", new JsonObject().put("customProperty", 15));
        final JsonObject config = new JsonObject()
                .put(TenantConstants.FIELD_TENANT_ID, "my-tenant")
                .put(TenantConstants.FIELD_ENABLED, true)
                .put(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(adapterConfig));
        final String jsonString = config.encode();

        final TenantObject obj = mapper.readValue(jsonString, TenantObject.class);
        assertNotNull(obj);
    }
}
