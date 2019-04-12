/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonReceiver;

/**
 * Abstract client for consuming messages from a Hono server.
 */
public abstract class AbstractConsumer extends AbstractHonoClient implements MessageConsumer {

    private Handler<String> localCloseHandler;

    /**
     * Creates an abstract message consumer.
     *
     * @param connection The connection to use.
     * @param receiver The proton receiver link.
     */
    public AbstractConsumer(final HonoConnection connection, final ProtonReceiver receiver) {

        super(connection);
        this.receiver = receiver;
    }

    /**
     * Sets a handler which will be invoked after this consumer has been
     * locally closed.
     * 
     * @param handler The handler.
     */
    protected void setLocalCloseHandler(final Handler<String> handler) {
        this.localCloseHandler = handler;
    }

    @Override
    public int getRemainingCredit() {
        return receiver.getCredit() - receiver.getQueued();
    }

    @Override
    public void flow(final int credits) throws IllegalStateException {
        receiver.flow(credits);
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {

        closeLinks(ok -> {
            if (localCloseHandler != null) {
                localCloseHandler.handle(receiver.getSource().getAddress());
            }
            if (closeHandler != null) {
                closeHandler.handle(Future.succeededFuture());
            }
        });
    }
}
