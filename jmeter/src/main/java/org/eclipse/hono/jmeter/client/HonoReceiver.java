/**
 * Copyright (c) 2016,2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.jmeter.client;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.concurrent.CountDownLatch;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.jmeter.HonoReceiverSampler;
import org.eclipse.hono.jmeter.HonoSampler;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

/**
 * Receiver, which connects to the AMQP network; asynchronous API needs to be used synchronous for JMeters threading
 * model
 */
public class HonoReceiver {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(HonoReceiver.class);

    private ConnectionFactory   amqpNetworkConnectionFactory;
    private HonoClient          amqpNetworkClient;
    private Vertx               vertx = Vertx.vertx();

    private long                sampleStart;
    private int                 messageCount;
    private long                messageSize;
    private HonoReceiverSampler sampler;

    private final transient Object lock = new Object();

    public HonoReceiver(final HonoReceiverSampler sampler) throws InterruptedException {
        this.sampler = sampler;

        // amqp network config
        amqpNetworkConnectionFactory = ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                .disableHostnameVerification()
                .host(sampler.getHost())
                .name(sampler.getContainer())
                .user(sampler.getUser())
                .password(sampler.getPwd())
                .port(Integer.parseInt(sampler.getPort()))
                .trustStorePath(sampler.getTrustStorePath())
                .vertx(vertx)
                .build();

        connect();
        createConsumer();

        LOGGER.debug("receiver active: {}/{} ({})", sampler.getEndpoint(), sampler.getTenant(),
                Thread.currentThread().getName());
    }

    private void connect() throws InterruptedException {
        final CountDownLatch receiverConnectLatch = new CountDownLatch(1);
        amqpNetworkClient = new HonoClientImpl(vertx, amqpNetworkConnectionFactory);
        amqpNetworkClient.connect(new ProtonClientOptions(), connectionHandler -> {
            if (connectionHandler.failed()) {
                LOGGER.error("HonoClient.connect() failed", connectionHandler.cause());
            }
            receiverConnectLatch.countDown();
        });
        receiverConnectLatch.await();
    }

    private void createConsumer() throws InterruptedException {
        final CountDownLatch receiverLatch = new CountDownLatch(1);
        if (amqpNetworkClient == null || !amqpNetworkClient.isConnected()) {
            connect();
        }
        if (sampler.getEndpoint().equals(HonoSampler.Endpoint.telemetry.toString())) {
            amqpNetworkClient.createTelemetryConsumer(sampler.getTenant(), Integer.parseInt(sampler.getPrefetch()), this::messageReceived, creationHandler -> {
                if (creationHandler.failed()) {
                    LOGGER.error("HonoClient.createTelemetryConsumer() failed", creationHandler.cause());
                }
                receiverLatch.countDown();
            });
        } else {
            amqpNetworkClient.createEventConsumer(sampler.getTenant(), Integer.parseInt(sampler.getPrefetch()), this::messageReceived, creationHandler -> {
                if (creationHandler.failed()) {
                    LOGGER.error("HonoClient.createEventConsumer() failed", creationHandler.cause());
                }
                receiverLatch.countDown();
            });
        }
        receiverLatch.await();
    }

    public void sample(final SampleResult result, final boolean isUseSenderTime) {
        synchronized (lock) {
            long elapsed = System.currentTimeMillis() - sampleStart;
            result.setResponseCodeOK();
            result.setSuccessful(true);
            result.setSampleCount(messageCount);
            result.setResponseMessage(MessageFormat.format("count: {0}, bytes received: {1}, period: {2}", messageCount, messageSize, elapsed));
            result.setBytes(messageSize);
            if (messageCount > 0) {
                result.setStampAndTime(sampleStart, elapsed);
            } else {
                result.setStampAndTime(sampleStart, 0);
            }
            LOGGER.info("{}: received batch of {} messages in {} milliseconds", sampler.getThreadName(), messageCount, elapsed);
            messageSize = 0;
            messageCount = 0;
            sampleStart = System.currentTimeMillis();
        }
    }

    private byte[] getValue(final Section body) {
        if (body instanceof Data) {
            return ((Data) body).getValue().getArray();
        } else if (body instanceof AmqpValue) {
            return ((AmqpValue) body).getValue().toString().getBytes(StandardCharsets.UTF_8);
        } else {
            return new byte[0];
        }
    }

    private void messageReceived(final Message message) {
        try {
            synchronized (lock) {
                final int bodyLength = getValue(message.getBody()).length;
                if (sampleStart == 0) {
                    final Long time = (Long) message.getApplicationProperties().getValue().get("millis");
                    if (time != null) {
                        sampleStart = time;
                    } else {
                        sampleStart = System.currentTimeMillis();
                    }
                }
                messageSize += bodyLength;
                messageCount++;
            }
        } catch (final Throwable t) {
            LOGGER.error("unknown exception in messageReceived()", t);
        }
    }

    public void close() {
        try {
            amqpNetworkClient.shutdown();
            vertx.close();
        } catch (final Throwable t) {
            LOGGER.error("unknown exception in closing of receiver", t);
        }
    }
}
