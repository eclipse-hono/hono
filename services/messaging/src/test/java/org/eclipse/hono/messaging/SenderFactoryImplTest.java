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

package org.eclipse.hono.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.BiConsumer;

import org.apache.qpid.proton.amqp.transport.Target;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;


/**
 * Tests verifying behavior of {@link SenderFactoryImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class SenderFactoryImplTest {

    /**
     * Verifies that a sender created by the factory is closed when the peer
     * detaches with close = false.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testNewSenderIsClosedOnRemoteDetach(final TestContext ctx) {
        testNewSenderIsClosedOnRemoteDetachOrClose(ctx, (sender, captor) -> verify(sender).detachHandler(captor.capture()));
    }

    /**
     * Verifies that a sender created by the factory is closed when the peer
     * detaches with close = true.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testNewSenderIsClosedOnRemoteClose(final TestContext ctx) {
        testNewSenderIsClosedOnRemoteDetachOrClose(ctx, (sender, captor) -> verify(sender).closeHandler(captor.capture()));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void testNewSenderIsClosedOnRemoteDetachOrClose(
            final TestContext ctx,
            final BiConsumer<ProtonSender, ArgumentCaptor<Handler>> handlerCaptor) {

        // GIVEN a sender created by the factory
        final ResourceIdentifier address = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT,
                Constants.DEFAULT_TENANT, null);
        final Target target = mock(Target.class);
        when(target.getAddress()).thenReturn(address.getBasePath());
        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.getTarget()).thenReturn(target);
        when(sender.getRemoteTarget()).thenReturn(target);
        final ProtonConnection con = mock(ProtonConnection.class);
        final ProtonSession session = mock(ProtonSession.class);
        when(session.createSender(anyString())).thenReturn(sender);
        final Handler<String> closeHook = mock(Handler.class);
        final SenderFactoryImpl factory = new SenderFactoryImpl();
        final ArgumentCaptor<Handler> captor = ArgumentCaptor.forClass(Handler.class);

        factory.newSender(con, session, address, ProtonQoS.AT_LEAST_ONCE, drain -> {}, closeHook);
        handlerCaptor.accept(sender, captor);

        // WHEN the peer detaches from the sender
        captor.getValue().handle(Future.succeededFuture(sender));

        // THEN the sender gets closed
        verify(sender).close();
        // and the close hook is called
        verify(closeHook).handle(any());
    }
}
