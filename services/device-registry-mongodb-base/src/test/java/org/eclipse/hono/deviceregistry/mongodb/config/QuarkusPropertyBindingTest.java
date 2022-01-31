/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.deviceregistry.mongodb.config;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;


/**
 * Tests verifying the binding of application properties to configuration classes.
 *
 */
@QuarkusTest
class QuarkusPropertyBindingTest {

    @Inject
    MongoDbBasedHttpServiceConfigOptions httpEndpointOptions;

    @Test
    void testHttpEndpointOptionsBinding() {
        assertThat(httpEndpointOptions).isNotNull();
        final var props = new MongoDbBasedHttpServiceConfigProperties(httpEndpointOptions);
        assertThat(props.getAuth().getCollectionName()).isEqualTo("user-accounts");
    }

}
