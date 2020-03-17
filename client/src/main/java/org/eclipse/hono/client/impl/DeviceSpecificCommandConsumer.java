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
import java.util.function.Supplier;

import org.eclipse.hono.client.MessageConsumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Represents the device specific command consumer used in protocol adapters.
 * <p>
 * Delegates an invocation of the {@link #close(Handler)} to the action supplied in the constructor.
 */
// TODO rename; implement a different interface instead, only providing the close(Handler) method
public final class DeviceSpecificCommandConsumer implements MessageConsumer {

    private final Supplier<Future<Void>> onCloseAction;
    private final AtomicBoolean closeCalled = new AtomicBoolean(false);

    /**
     * Creates a new DeviceSpecificCommandConsumer.
     *
     * @param onCloseAction The action to invoke when {@link #close(Handler)} is called.
     * @throws NullPointerException If onCloseAction is {@code null}.
     */
    public DeviceSpecificCommandConsumer(final Supplier<Future<Void>> onCloseAction) {
        this.onCloseAction = Objects.requireNonNull(onCloseAction);
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        if (closeCalled.compareAndSet(false, true)) {
            final Future<Void> onCloseActionFuture = onCloseAction.get();
            if (closeHandler != null) {
                onCloseActionFuture.setHandler(closeHandler);
            }
        } else if (closeHandler != null) {
            closeHandler.handle(Future.succeededFuture());
        }
    }

    @Override
    public void flow(final int credits) throws IllegalStateException {
        // no-op
    }

    @Override
    public int getRemainingCredit() {
        return -1;
    }
}
