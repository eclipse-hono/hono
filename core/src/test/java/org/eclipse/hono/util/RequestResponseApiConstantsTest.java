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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.apache.qpid.proton.message.Message;
import org.junit.Test;


/**
 * Tests verifying behavior of {@link RequestResponseApiConstants}.
 *
 */
public class RequestResponseApiConstantsTest {

    /**
     * Verifies that the AMQP reply created by the helper from a JSON response
     * contains a cache control property.
     */
    @Test
    public void testGetAmqpReplyAddsCacheDirective() {

        // GIVEN a response that is not supposed to be cached by a client
        final CacheDirective directive = CacheDirective.noCacheDirective();
        final String correlationId = "message-id";
        final EventBusMessage response = EventBusMessage.forStatusCode(200)
                .setTenant("my-tenant")
                .setDeviceId("my-device")
                .setCacheDirective(directive)
                .setCorrelationId(correlationId);

        // WHEN creating the AMQP message for the response
        final Message reply = RequestResponseApiConstants.getAmqpReply("endpoint", response);

        // THEN the message contains the corresponding cache control property
        assertThat(MessageHelper.getCacheDirective(reply), is(directive.toString()));
    }
}
