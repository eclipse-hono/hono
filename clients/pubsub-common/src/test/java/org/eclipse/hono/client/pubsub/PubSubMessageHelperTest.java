/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.pubsub;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the generic behavior of {@link PubSubMessageHelper}.
 */
public class PubSubMessageHelperTest {

    /**
     * Verifies that the getTopicName method returns the formatted topic.
     */
    @Test
    public void testThatGetTopicNameReturnsFormattedString() {
        final String topic = "event";
        final String prefix = "testTenant";

        final String result = PubSubMessageHelper.getTopicName(topic, prefix);
        assertThat(result).isEqualTo("testTenant.event");
    }

}
