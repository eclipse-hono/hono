/**
 * Copyright (c) 2018 Red Hat Inc and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial creation
 */

package org.eclipse.hono.client;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ServerErrorExceptionTest {

    @Test
    public void testCodeAndMessage() {
        ServerErrorException t = new ServerErrorException(500, "Foo Bar");

        Assertions.assertThat(t)
                .hasMessage("Foo Bar");

        Assertions.assertThat(t.getErrorCode())
                .isEqualTo(500);
    }

    @Test
    public void testCodeAndNoMessage() {
        ServerErrorException t = new ServerErrorException(500);

        Assertions.assertThat(t)
                .hasMessage("Error Code: 500");

        Assertions.assertThat(t.getErrorCode())
                .isEqualTo(500);
    }

    @Test
    public void testCodeCauseAndNoMessage() {
        ServerErrorException t = new ServerErrorException(500, new RuntimeException("Bar Foo"));

        Assertions.assertThat(t)
                .hasMessage("Error Code: 500")
                .hasCauseInstanceOf(RuntimeException.class);

        Assertions.assertThat(t.getErrorCode())
                .isEqualTo(500);
    }

    @Test
    public void testCodeCauseAndMessage() {
        ServerErrorException t = new ServerErrorException(500, "Foo Bar", new RuntimeException("Bar Foo"));

        Assertions.assertThat(t)
                .hasMessage("Foo Bar")
                .hasCauseInstanceOf(RuntimeException.class);

        Assertions.assertThat(t.getErrorCode())
                .isEqualTo(500);
    }
}
