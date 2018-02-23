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

package org.eclipse.hono.client.impl;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Support for client tests that mocks the classes that clients usually need to access for unit tests.
 *
 */
public abstract class AbstractClientUnitTestSupport {
    protected ProtonSender sender;
    protected ProtonReceiver receiver;
    protected Context context;
    protected Vertx vertx;

    /**
     * Initialize the mocks for the Vertx and Proton classes that are of common interest to all client unit tests.
     * <p>
     * This method is intended to be called in the setup method of JUnit (that is annotated with {@link org.junit.Before}).
     */
    protected final void createMocks() {

        vertx = mock(Vertx.class);

        context = mock(Context.class);

        when(context.owner()).thenReturn(vertx);
        doAnswer(invocation -> {
            Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(any(Handler.class));

        sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);
    }

}
