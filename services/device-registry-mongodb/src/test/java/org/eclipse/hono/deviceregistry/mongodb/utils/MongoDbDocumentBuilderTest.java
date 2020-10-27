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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.service.management.BaseDto;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.JsonObject;

class MongoDbDocumentBuilderTest {

    /**
     * Verifies that the update document contains the property "data" as set in {@link BaseDto} if the concrete sublass
     * does not override the property name.
     */
    @Test
    public void updateDocumentContainsDataPropertyNameIfNotOverridden() {
        final JsonObject mongoDbDocument = MongoDbDocumentBuilder.builder()
                .forUpdateOf(TestDtoWithOverridenJsonProperty.forUpdate(TestDto::new, new JsonObject(), "foo"))
                .document();

        assertThat(mongoDbDocument.getJsonObject("$set").containsKey("data")).isTrue();
    }

    /**
     * Verifies that the update document contains the overridden property name if a concrete subclass overrides the
     * "getData" method.
     */
    @Test
    public void updateDocumentContainsOverriddenPropertyName() {
        final JsonObject mongoDbDocument = MongoDbDocumentBuilder.builder()
            .forUpdateOf(TestDtoWithOverridenJsonProperty.forUpdate(TestDtoWithOverridenJsonProperty::new, new JsonObject(), "foo"))
            .document();

        assertThat(mongoDbDocument.getJsonObject("$set").containsKey("newDataFieldName")).isTrue();
    }

    private static final class TestDto extends BaseDto<JsonObject> {
    }

    private static final class TestDtoWithOverridenJsonProperty extends BaseDto<JsonObject> {
        @Override
        @JsonProperty("newDataFieldName")
        public JsonObject getData() {
            return super.getData();
        }
    }

}
