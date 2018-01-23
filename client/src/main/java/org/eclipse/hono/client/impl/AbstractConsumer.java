/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Abstract client for consuming messages from a Hono server.
 */
abstract class AbstractConsumer extends AbstractHonoClient implements MessageConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractConsumer.class);

    AbstractConsumer(final Context context, final ClientConfigProperties config, final ProtonReceiver receiver) {
        super(context, config);
        this.receiver = receiver;
    }

    @Override
    public void flow(final int credits) {
        receiver.flow(credits);
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        closeLinks(closeHandler);
    }

    static Future<ProtonReceiver> createConsumer(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String pathSeparator,
            final String address,
            final ProtonQoS qos,
            final BiConsumer<ProtonDelivery, Message> consumer) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(pathSeparator);
        Objects.requireNonNull(address);
        Objects.requireNonNull(qos);

        final Future<ProtonReceiver> result = Future.future();
        final String targetAddress = String.format(address, pathSeparator, tenantId);

        context.runOnContext(open -> {
            final ProtonReceiver receiver = con.createReceiver(targetAddress);
            receiver.setAutoAccept(true);
            receiver.setPrefetch(clientConfig.getInitialCredits());
            receiver.setQoS(qos);
            receiver.handler((delivery, message) -> {
                if (consumer != null) {
                    consumer.accept(delivery, message);
                }
                if (LOG.isTraceEnabled()) {
                    int remainingCredits = receiver.getCredit() - receiver.getQueued();
                    LOG.trace("handling message [remotely settled: {}, queued messages: {}, remaining credit: {}]",
                            delivery.remotelySettled(), receiver.getQueued(), remainingCredits);
                }
            });
            receiver.openHandler(receiverOpen -> {
                if (receiverOpen.succeeded()) {
                    LOG.debug("receiver [source: {}, qos: {}] open", receiver.getRemoteSource(), receiver.getRemoteQoS());
                    if (qos.equals(ProtonQoS.AT_LEAST_ONCE) && !qos.equals(receiver.getRemoteQoS())) {
                        LOG.info("remote container uses other QoS than requested [requested: {}, in use: {}]",
                                qos, receiver.getRemoteQoS());
                    }
                    result.complete(receiver);
                } else {
                    result.fail(receiverOpen.cause());
                }
            });
            receiver.open();
        });
        return result;
    }
}
