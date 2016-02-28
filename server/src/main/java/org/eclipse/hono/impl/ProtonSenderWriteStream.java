/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.impl;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.streams.WriteStream;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A reactive stream for sending messages over an AMQP 1.0 link.
 *
 */
public class ProtonSenderWriteStream implements WriteStream<Message> {

    private static final Logger LOG        = LoggerFactory.getLogger(ProtonSenderWriteStream.class);
    private ProtonSender        sender;
    private Handler<Throwable>  exceptionHandler;
    private Handler<Void>       sentHandler;
    private AtomicInteger       count      = new AtomicInteger(1);

    public ProtonSenderWriteStream(final ProtonSender sender, final Handler<Void> sentHandler) {
        this.sender = Objects.requireNonNull(sender);
        this.sentHandler = sentHandler;
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.streams.WriteStream#exceptionHandler(io.vertx.core.Handler)
     */
    @Override
    public WriteStream<Message> exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.streams.WriteStream#write(java.lang.Object)
     */
    @Override
    public WriteStream<Message> write(Message data) {
        sendMessage(data);
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.streams.WriteStream#end()
     */
    @Override
    public void end() {
//        LOG.debug("closing outbound link");
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.streams.WriteStream#setWriteQueueMaxSize(int)
     */
    @Override
    public WriteStream<Message> setWriteQueueMaxSize(int maxSize) {
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.streams.WriteStream#writeQueueFull()
     */
    @Override
    public boolean writeQueueFull() {
        return sender.sendQueueFull();
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.streams.WriteStream#drainHandler(io.vertx.core.Handler)
     */
    @Override
    public WriteStream<Message> drainHandler(Handler<Void> handler) {
        LOG.trace("registering drain handler");
        sender.sendQueueDrainHandler(creditAvailable -> handler.handle(null));
        return this;
    }

    private void sendMessage(final Message msg) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(count.getAndIncrement());
        b.flip();
        LOG.trace("sending message [id: {}] to peer", msg.getMessageId());
        if (ProtonQoS.AT_MOST_ONCE.equals(sender.getQoS())) {
            sender.send(b.array(), msg);
            sentHandler.handle(null);
        } else {
            sender.send(b.array(), msg, res -> {
                if (Accepted.class.isInstance(res.getRemoteState())) {
                    LOG.trace("message [id: {}, remotelySettled: {}] has been accepted by peer", msg.getMessageId(),
                            res.remotelySettled());
                    res.settle();
                    if (sentHandler != null) {
                        sentHandler.handle(null);
                    }
                } else {
                    LOG.warn("message [id: {}] has not been accepted by peer: {}", msg.getMessageId(),
                            res.getRemoteState());
                }
            });
        }
    }
}
