/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * Represents the device specific command consumer used in protocol adapters.
 * <p>
 * Delegates an invocation of the {@link #close(SpanContext)} to the action supplied in the constructor.
 */
public final class ProtocolAdapterCommandConsumerImpl implements ProtocolAdapterCommandConsumer {

    private final Function<SpanContext, Future<Void>> onCloseAction;
    private final AtomicBoolean closeCalled = new AtomicBoolean(false);

    /**
     * Creates a new DeviceSpecificCommandConsumer.
     *
     * @param onCloseAction The action to invoke when {@link #close(SpanContext)} is called.
     * @throws NullPointerException If onCloseAction is {@code null}.
     */
    public ProtocolAdapterCommandConsumerImpl(final Function<SpanContext, Future<Void>> onCloseAction) {
        this.onCloseAction = Objects.requireNonNull(onCloseAction);
    }

    @Override
    public Future<Void> close(final SpanContext spanContext) {
        if (closeCalled.compareAndSet(false, true)) {
            return onCloseAction.apply(spanContext);
        } else {
            return Future.succeededFuture();
        }
    }

}
