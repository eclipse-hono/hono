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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;

/**
 * Argument matchers and mocks for the use with vert.x.
 */
public final class VertxMockSupport {

    private VertxMockSupport() {
    }

    /**
     * Creates a mocked vert.x Context which immediately invokes any handler that is passed to its runOnContext method.
     *
     * @param owner The vert.x instance that will be the owner of the created context.
     * @return The mocked context.
     */
    public static Context mockContext(final Vertx owner) {

        final Context context = mock(Context.class);

        when(context.owner()).thenReturn(owner);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(VertxMockSupport.anyHandler());
        return context;
    }

    /**
     * Creates a mocked vert.x Context which immediately invokes any handler that is passed to its
     * {@link ContextInternal#runOnContext(Handler)} or {@link ContextInternal#executeBlocking(Handler, TaskQueue)}
     * methods.
     *
     * @param owner The vert.x instance that will be the owner of the created context.
     * @return The mocked context.
     */
    public static ContextInternal mockContextInternal(final VertxInternal owner) {

        final ContextInternal context = mock(ContextInternal.class);

        when(context.owner()).thenReturn(owner);
        when(context.unwrap()).thenReturn(context);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(VertxMockSupport.anyHandler());
        doAnswer(invocation -> {
            final Handler<Promise<Void>> handler = invocation.getArgument(0);
            final Promise<Void> prom = Promise.promise();
            handler.handle(prom);
            return prom;
        }).when(context).executeBlocking(VertxMockSupport.anyHandler(), any(TaskQueue.class));
        return context;
    }

    /**
     * Creates a promise mock.
     *
     * @param <T> The type of the promise.
     * @return The mocked promise.
     */
    public static <T> PromiseInternal<T> promiseInternal() {
        @SuppressWarnings("unchecked")
        final PromiseInternal<T> promise = mock(PromiseInternal.class);
        return promise;
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
        final Context context = VertxMockSupport.mockContext(vertx);
        executeBlockingCodeImmediately(vertx, context);
    }

    /**
     * Ensures that blocking code is executed immediately on the given vert.x instance.
     *
     * @param vertx The mocked vert.x instance.
     * @param context The (mocked) context to return as the result of {@link Vertx#getOrCreateContext()}.
     */
    public static void executeBlockingCodeImmediately(final Vertx vertx, final Context context) {

        doAnswer(VertxMockSupport::handleExecuteBlockingInvocation)
                .when(vertx).executeBlocking(anyHandler(), anyHandler());

        when(vertx.getOrCreateContext()).thenReturn(context);

        doAnswer(VertxMockSupport::handleExecuteBlockingInvocation)
                .when(context).executeBlocking(anyHandler(), any());

        when(vertx.executeBlocking(anyHandler()))
                .thenAnswer(VertxMockSupport::handleExecuteBlockingInvocationReturningFuture);

        when(context.executeBlocking(anyHandler()))
                .thenAnswer(VertxMockSupport::handleExecuteBlockingInvocationReturningFuture);
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

    private static <T> Future<T> handleExecuteBlockingInvocationReturningFuture(final InvocationOnMock invocation) {
        final Promise<T> result = Promise.promise();
        final Handler<Promise<?>> blockingCodeHandler = invocation.getArgument(0);
        blockingCodeHandler.handle(result);
        return result.future();
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
