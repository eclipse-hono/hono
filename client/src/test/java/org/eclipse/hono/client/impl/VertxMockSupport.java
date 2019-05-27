/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import io.vertx.core.Handler;

/**
 * Argument matchers for the use with vert.x.
 */
public final class VertxMockSupport {

    private VertxMockSupport() {
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
