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
     * Create a mock for the Vert.x context that is used for the unit test.
     *
     * @param vertx The Vert.x instance that the mock of the context is created for.
     * @return The mocked context.
     */
    public static final Context mockContext(final Vertx vertx) {

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
     * Create a mock for a Proton sender, usable for a Hono client in the unit test.
     *
     * @return The mocked sender.
     */
    public static final ProtonSender mockProtonSender() {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);

        return sender;
    }

    /**
     * Create a mock for a Proton receiver, usable for a Hono client in the unit test.
     *
     * @return The mocked receiver.
     */
    public static final ProtonReceiver mockProtonReceiver() {

        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);

        return receiver;
    }
}
