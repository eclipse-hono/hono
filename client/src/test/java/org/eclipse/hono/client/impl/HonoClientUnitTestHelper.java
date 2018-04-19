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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Helper class that provides mocks for objects that are needed by unit tests using a Hono client.
 *
 */
public final class HonoClientUnitTestHelper {

    private HonoClientUnitTestHelper() {}

    /**
     * Creates a mocked vert.x Context which immediately invokes any handler that is passed to its runOnContext method.
     *
     * @param vertx The vert.x instance that the mock of the context is created for.
     * @return The mocked context.
     */
    @SuppressWarnings("unchecked")
    public static Context mockContext(final Vertx vertx) {

        final Context context = mock(Context.class);

        when(context.owner()).thenReturn(vertx);
        doAnswer(invocation -> {
            Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(any(Handler.class));
        return context;
    }

    /**
     * Creates a mocked Proton sender which always returns {@code true} when its isOpen method is called.
     *
     * @return The mocked sender.
     */
    public static ProtonSender mockProtonSender() {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);

        return sender;
    }

    /**
     * Creates a mocked Proton receiver which always returns {@code true} when its isOpen method is called.
     *
     * @return The mocked receiver.
     */
    public static ProtonReceiver mockProtonReceiver() {

        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);

        return receiver;
    }
}
