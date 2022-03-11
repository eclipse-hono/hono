/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;


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
        final Message request = ProtonHelper.message();
        request.setCorrelationId("message-id");
        final RequestResponseResult<JsonObject> response = new RequestResponseResult<>(
                HttpURLConnection.HTTP_OK,
                null,
                directive,
                null);

        // WHEN creating the AMQP message for the response
        final Message reply = RequestResponseApiConstants.getAmqpReply(
                "endpoint",
                "my-tenant",
                request,
                response);

        // THEN the message contains the corresponding cache control property
        assertThat(MessageHelper.getCacheDirective(reply)).isEqualTo(directive.toString());
    }
}
