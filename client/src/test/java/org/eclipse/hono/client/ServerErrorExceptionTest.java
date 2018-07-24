/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import org.assertj.core.api.Assertions;
import org.junit.Test;

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

        Assertions.assertThat(t)
                .hasMessage("Foo Bar");

        Assertions.assertThat(t.getErrorCode())
                .isEqualTo(500);
    }

    /**
     * Verifies that an exception created for a status code only
     * contains a default message.
     */
    @Test
    public void testCodeAndNoMessage() {
        final ServerErrorException t = new ServerErrorException(500);

        Assertions.assertThat(t)
                .hasMessage("Error Code: 500");

        Assertions.assertThat(t.getErrorCode())
                .isEqualTo(500);
    }

    /**
     * Verifies that an exception created for a status code and a
     * cause contains a default message.
     */
    @Test
    public void testCodeCauseAndNoMessage() {
        final ServerErrorException t = new ServerErrorException(500, new RuntimeException("Bar Foo"));

        Assertions.assertThat(t)
                .hasMessage("Error Code: 500")
                .hasCauseInstanceOf(RuntimeException.class);

        Assertions.assertThat(t.getErrorCode())
                .isEqualTo(500);
    }

    /**
     * Verifies that an exception created for a status code, a message and a
     * cause contains all given values..
     */
    @Test
    public void testCodeCauseAndMessage() {
        final ServerErrorException t = new ServerErrorException(500, "Foo Bar", new RuntimeException("Bar Foo"));

        Assertions.assertThat(t)
                .hasMessage("Foo Bar")
                .hasCauseInstanceOf(RuntimeException.class);

        Assertions.assertThat(t.getErrorCode())
                .isEqualTo(500);
    }
}
