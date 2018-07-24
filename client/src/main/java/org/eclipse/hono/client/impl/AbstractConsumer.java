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

package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonReceiver;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;

/**
 * Abstract client for consuming messages from a Hono server.
 */
public abstract class AbstractConsumer extends AbstractHonoClient implements MessageConsumer {

    /**
     * Creates an abstract message consumer.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param config The configuration properties to use.
     * @param receiver The proton receiver link.
     */
    public AbstractConsumer(final Context context, final ClientConfigProperties config, final ProtonReceiver receiver) {
        super(context, config);
        this.receiver = receiver;
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
        closeLinks(closeHandler);
    }

}
