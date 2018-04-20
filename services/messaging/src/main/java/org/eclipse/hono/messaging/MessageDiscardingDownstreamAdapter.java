/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.messaging;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A telemetry adapter that simply logs and discards all messages.
 * <p>
 * It can be configured to pause a sender periodically for a certain amount of time in order
 * to see if the sender implements flow control correctly.
 */
@Service
@Profile("standalone")
public final class MessageDiscardingDownstreamAdapter implements DownstreamAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageDiscardingDownstreamAdapter.class);
    private static final int DEFAULT_CREDIT = 10;
    private final Vertx vertx;
    private final int pauseThreshold;
    private final long pausePeriod;
    private Map<String, LinkStatus> statusMap = new HashMap<>();
    private Consumer<Message> messageConsumer;

    /**
     * Creates a new instance.
     * <p>
     * Simply invokes {@link #MessageDiscardingDownstreamAdapter(Vertx, Consumer)}
     * with a {@code null} consumer.
     * 
     * @param vertx The Vert.x instance to run on.
     */
    public MessageDiscardingDownstreamAdapter(final Vertx vertx) {
        this(vertx, null);
    }

    /**
     * Creates a new instance.
     * <p>
     * Simply invokes {@link #MessageDiscardingDownstreamAdapter(Vertx, int, long, Consumer)}
     * with a zero pause threshold and period.
     * 
     * @param vertx The Vert.x instance to run on.
     * @param consumer a consumer that is invoked for every message received.
     */
    public MessageDiscardingDownstreamAdapter(final Vertx vertx, final Consumer<Message> consumer) {
        this(vertx, 0, 0, consumer);
    }

    /**
     * Creates a new instance.
     * 
     * @param vertx The Vert.x instance to run on.
     * @param pauseThreshold the number of messages after which the sender is paused. If set to 0 (zero) the sender will
     *                       never be paused.
     * @param pausePeriod the number of milliseconds after which the sender is resumed.
     * @param consumer a consumer that is invoked for every message received.
     */
    public MessageDiscardingDownstreamAdapter(final Vertx vertx, final int pauseThreshold, final long pausePeriod, final Consumer<Message> consumer) {
        this.vertx = vertx;
        this.pauseThreshold = pauseThreshold;
        this.pausePeriod = pausePeriod;
        this.messageConsumer = consumer;
    }

    @Override
    public void start(final Future<Void> startFuture) {
        startFuture.complete();
    }

    @Override
    public void stop(final Future<Void> stopFuture) {
        stopFuture.complete();
    }

    @Override
    public void onClientAttach(final UpstreamReceiver client, final Handler<AsyncResult<Void>> resultHandler) {
        client.replenish(DEFAULT_CREDIT);
        resultHandler.handle(Future.succeededFuture());
    }

    @Override
    public void onClientDetach(final UpstreamReceiver client) {
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    /**
     * Sets the consumer for telemetry messages received from upstream.
     * 
     * @param consumer a consumer that is invoked for every message received.
     */
    public void setMessageConsumer(final Consumer<Message> consumer) {
        this.messageConsumer = consumer;
    }

    @Override
    public void onClientDisconnect(final String connectionId) {
    }

    @Override
    public void processMessage(final UpstreamReceiver client, final ProtonDelivery delivery, final Message data) {

        LinkStatus status = statusMap.get(client.getLinkId());
        if (status == null) {
            LOG.debug("creating new link status object [{}]", client.getLinkId());
            status = new LinkStatus(client);
            statusMap.put(client.getLinkId(), status);
        }
        LOG.debug("processing telemetry data [id: {}, to: {}, content-type: {}]", data.getMessageId(), data.getAddress(),
                data.getContentType());
        if (messageConsumer != null) {
            messageConsumer.accept(data);
        }
        ProtonHelper.accepted(delivery, true);
        status.onMsgReceived();
    }

    /**
     * Information about the link status.
     */
    private class LinkStatus {

        private long msgCount;
        private UpstreamReceiver client;
        private boolean suspended;

        LinkStatus(final UpstreamReceiver client) {
            this.client = client;
        }

        public void onMsgReceived() {
            msgCount++;
            if (pauseThreshold > 0) {
                // we need to pause every n messages
                if (msgCount % pauseThreshold == 0) {
                    pause();
                }
            } else {
                client.replenish(DEFAULT_CREDIT);
            }
        }

        public void pause() {
            LOG.debug("pausing link [{}]", client.getLinkId());
            this.suspended = true;
            vertx.setTimer(pausePeriod, fired -> {
                vertx.runOnContext(run -> resume());
            });
        }

        private void resume() {
            if (suspended) {
                LOG.debug("resuming link [{}]", client.getLinkId());
                int credit = DEFAULT_CREDIT;
                if (pauseThreshold > 0) {
                    credit = pauseThreshold;
                }
                client.replenish(credit);
                this.suspended = false;
            }
        }
    }
}
