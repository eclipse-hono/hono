/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link KafkaAdminClientConfigProperties}.
 */
public class KafkaAdminClientConfigPropertiesTest {

    private static final String PROPERTY_FILE_CLIENT_ID = "target/test-classes/clientid.properties";

    /**
     * Verifies that the client ID set with {@link KafkaAdminClientConfigProperties#setDefaultClientIdPrefix(String)} is applied when it
     * is NOT present in the configuration.
     */
    @Test
    public void testThatClientIdIsApplied() {
        final String clientId = "the-client";

        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setDefaultClientIdPrefix(clientId);

        final Map<String, String> adminClientConfig = config.getAdminClientConfig("adminClientName");
        assertThat(adminClientConfig.get("client.id")).startsWith(clientId + "-adminClientName-");
    }

    /**
     * Verifies that the client ID set with {@link KafkaAdminClientConfigProperties#setDefaultClientIdPrefix(String)} is NOT applied
     * when it is present in the configuration.
     */
    @Test
    public void testThatClientIdIsNotAppliedIfAlreadyPresent() {

        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setPropertyFiles(List.of(PROPERTY_FILE_CLIENT_ID));
        config.setDefaultClientIdPrefix("other-client");

        final Map<String, String> adminClientConfig = config.getAdminClientConfig("adminClientName");
        assertThat(adminClientConfig.get("client.id")).startsWith("configured-id-adminClientName-");
    }
}
