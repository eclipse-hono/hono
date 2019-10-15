/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.hono.client.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Represents the device specific command consumer used in protocol adapters.
 * <p>
 * Delegates method invocations to the supplied command consumer that wraps the actual Proton receiver.
 */
public final class DeviceSpecificCommandConsumer implements MessageConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceSpecificCommandConsumer.class);

    private final Supplier<DestinationCommandConsumer> delegateSupplier;
    private final String deviceId;

    /**
     * Creates a new DeviceSpecificCommandConsumer.
     *
     * @param delegateSupplier Supplies the command consumer that wraps the actual Proton receiver.
     * @param deviceId The device identifier.
     * @throws NullPointerException If delegateSupplier or deviceId is {@code null}.
     */
    public DeviceSpecificCommandConsumer(
            final Supplier<DestinationCommandConsumer> delegateSupplier, final String deviceId) {
        this.delegateSupplier = Objects.requireNonNull(delegateSupplier);
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    /**
     * Gets the current consumer delegate.
     * <p>
     * {@code null} may be returned here if {@link #close(Handler)} has already been invoked on this
     * consumer (and the delegate consumer got closed as a consequence) or if the delegate consumer
     * was remotely closed and no re-creation of the consumer (e.g. as part of a liveness check) has
     * happened (yet).
     *
     * @return The consumer instance or {@code null}.
     */
    private DestinationCommandConsumer getDelegate() {
        return delegateSupplier.get();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calls the appropriate close handler and closes the outer consumer instance if that contains
     * no further handlers.
     */
    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        final DestinationCommandConsumer delegate = getDelegate();
        if (delegate != null) {
            delegate.removeHandlerAndCloseConsumerIfEmpty(deviceId, closeHandler);
        } else {
            LOG.debug("cannot delegate close() invocation; actual consumer not available [consumer device-id {}]",
                    deviceId);
            if (closeHandler != null) {
                closeHandler.handle(Future.failedFuture("actual consumer not available"));
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calls the corresponding method on the outer consumer instance.
     */
    @Override
    public void flow(final int credits) throws IllegalStateException {
        final DestinationCommandConsumer delegate = getDelegate();
        if (delegate != null) {
            delegate.flow(credits);
        } else {
            LOG.debug("cannot delegate flow() invocation; actual consumer not available [consumer device-id {}]",
                    deviceId);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return The remaining credit of the outer consumer instance.
     */
    @Override
    public int getRemainingCredit() {
        final DestinationCommandConsumer delegate = getDelegate();
        if (delegate == null) {
            LOG.debug("cannot delegate getRemainingCredit() invocation; actual consumer not available [consumer device-id {}]",
                    deviceId);
            return 0;
        }
        return delegate.getRemainingCredit();
    }
}
