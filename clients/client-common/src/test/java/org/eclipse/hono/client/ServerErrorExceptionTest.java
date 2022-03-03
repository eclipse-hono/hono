/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link ServerErrorException}.
 *
 */
public class ServerErrorExceptionTest {

    /**
     * Verifies creation of an exception for a status code and message.
     */
    @Test
    public void testCodeAndMessage() {
        final ServerErrorException t = new ServerErrorException(500, "Foo Bar");

        assertThat(t).hasMessageThat().isEqualTo("Foo Bar");
        assertThat(t.getErrorCode()).isEqualTo(500);
    }

    /**
     * Verifies that an exception created for a status code only
     * contains a default message.
     */
    @Test
    public void testCodeAndNoMessage() {
        final ServerErrorException t = new ServerErrorException(500);

        assertThat(t).hasMessageThat().isEqualTo("Error Code: 500");
        assertThat(t.getErrorCode()).isEqualTo(500);
    }

    /**
     * Verifies that an exception created for a status code and a
     * cause contains a default message.
     */
    @Test
    public void testCodeCauseAndNoMessage() {
        final ServerErrorException t = new ServerErrorException(500, new RuntimeException("Bar Foo"));

        assertThat(t).hasMessageThat().isEqualTo("Error Code: 500");
        assertThat(t).hasCauseThat().isInstanceOf(RuntimeException.class);

        assertThat(t.getErrorCode()).isEqualTo(500);
    }

    /**
     * Verifies that an exception created for a status code, a message and a
     * cause contains all given values.
     */
    @Test
    public void testCodeCauseAndMessage() {
        final ServerErrorException t = new ServerErrorException(500, "Foo Bar", new RuntimeException("Bar Foo"));

        assertThat(t).hasMessageThat().isEqualTo("Foo Bar");
        assertThat(t).hasCauseThat().isInstanceOf(RuntimeException.class);

        assertThat(t.getErrorCode()).isEqualTo(500);
    }

    /**
     * Verifies that an exception created with a client facing error message
     * contains the localized message string.
     */
    @Test
    public void testClientFacingMessage() {
        final ServerErrorException t = new ServerErrorException(500, "Foo Bar", new RuntimeException("Bar Foo"));
        final String messageKey = SendMessageTimeoutException.CLIENT_FACING_MESSAGE_KEY;
        t.setClientFacingMessageWithKey(messageKey);

        assertThat(t.getClientFacingMessage())
                .isEqualTo(ServiceInvocationException.getLocalizedMessage(messageKey));
        assertThat(t.getClientFacingMessage()).isNotEqualTo(messageKey);
    }
}
