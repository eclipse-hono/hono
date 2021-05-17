/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Argument matchers and mocks for the use with vert.x.
 */
public final class VertxMockSupport {

    private VertxMockSupport() {
    }

    /**
     * Creates a mocked vert.x Context which immediately invokes any handler that is passed to its runOnContext method.
     *
     * @param vertx The vert.x instance that the mock of the context is created for.
     * @return The mocked context.
     */
    public static Context mockContext(final Vertx vertx) {

        final Context context = mock(Context.class);

        when(context.owner()).thenReturn(vertx);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(VertxMockSupport.anyHandler());
        return context;
    }

    /**
     * Ensures that timers set on the given vert.x instance run immediately.
     *
     * @param vertx The mocked vert.x instance.
     */
    public static void runTimersImmediately(final Vertx vertx) {

        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            final Handler<Long> handler = invocation.getArgument(1);
            final long timerId = 1;
            handler.handle(timerId);
            return timerId;
        });
    }

    /**
     * Ensures that blocking code is executed immediately on the given vert.x instance.
     *
     * @param vertx The mocked vert.x instance.
     */
    public static void executeBlockingCodeImmediately(final Vertx vertx) {

        doAnswer(VertxMockSupport::handleExecuteBlockingInvocation)
                .when(vertx).executeBlocking(anyHandler(), anyHandler());

        final Context context = VertxMockSupport.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);

        doAnswer(VertxMockSupport::handleExecuteBlockingInvocation)
                .when(context).executeBlocking(anyHandler(), any());
    }

    private static Void handleExecuteBlockingInvocation(final InvocationOnMock invocation) {
        final Promise<Void> result = Promise.promise();
        final Handler<Promise<?>> blockingCodeHandler = invocation.getArgument(0);
        final Handler<AsyncResult<?>> resultHandler = invocation.getArgument(1);
        blockingCodeHandler.handle(result);
        if (resultHandler != null) {
            resultHandler.handle(result.future());
        }
        return null;
    }

    /**
     * Matches any handler of given type, excluding nulls.
     *
     * @param <T> The handler type.
     * @return The value returned by {@link ArgumentMatchers#any(Class)}.
     */
    public static <T> Handler<T> anyHandler() {
        @SuppressWarnings("unchecked")
        final Handler<T> result = ArgumentMatchers.any(Handler.class);
        return result;
    }

    /**
     * Creates mock object for a handler.
     *
     * @param <T> The handler type.
     * @return The value returned by {@link Mockito#mock(Class)}.
     */
    public static <T> Handler<T> mockHandler() {
        @SuppressWarnings("unchecked")
        final Handler<T> result = Mockito.mock(Handler.class);
        return result;
    }

    /**
     * Argument captor for a handler.
     *
     * @param <T> The handler type.
     * @return The value returned by {@link ArgumentCaptor#forClass(Class)}.
     */
    public static <T> ArgumentCaptor<Handler<T>> argumentCaptorHandler() {
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Handler<T>> result = ArgumentCaptor.forClass(Handler.class);
        return result;
    }
}
