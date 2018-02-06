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

package org.eclipse.hono.jmeter.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.jms.IllegalStateException;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.jmeter.HonoReceiverSampler;
import org.eclipse.hono.jmeter.HonoSampler;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Future;

/**
 * Receiver, which connects to the AMQP network; asynchronous API needs to be used synchronous for JMeters threading
 * model
 */
public class HonoReceiver extends AbstractClient {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(HonoReceiver.class);

    private final ConnectionFactory   amqpNetworkConnectionFactory;
    private final HonoClient          amqpNetworkClient;
    private final HonoReceiverSampler sampler;

    private final transient Object lock = new Object();

    private long sampleStart;
    private int messageCount;
    private long totalSampleDeliveryTime;
    private long messageSize;

    /**
     * Creates a new receiver.
     * 
     * @param sampler The sampler configuration.
     */
    public HonoReceiver(final HonoReceiverSampler sampler) {

        super();
        if (sampler.isUseSenderTime() && sampler.getSenderTimeVariableName() == null) {
            throw new IllegalArgumentException("SenderTime VariableName must be set when using SenderTime flag");
        }
        this.sampler = sampler;

        final ClientConfigProperties clientConfig = new ClientConfigProperties();
        clientConfig.setHostnameVerificationRequired(false);
        clientConfig.setHost(sampler.getHost());
        clientConfig.setPort(Integer.parseInt(sampler.getPort()));
        clientConfig.setName(sampler.getContainer());
        clientConfig.setUsername(sampler.getUser());
        clientConfig.setPassword(sampler.getPwd());
        clientConfig.setTrustStorePath(sampler.getTrustStorePath());
        clientConfig.setInitialCredits(Integer.parseInt(sampler.getPrefetch()));

        // amqp network config
        amqpNetworkConnectionFactory = ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder(clientConfig)
                .vertx(vertx)
                .build();
        amqpNetworkClient = new HonoClientImpl(vertx, amqpNetworkConnectionFactory, clientConfig);
    }

    /**
     * Starts this receiver.
     * 
     * @return A future indicating the outcome of the startup process.
     */
    public CompletableFuture<Void> start() {

        final CompletableFuture<Void> result = new CompletableFuture<>();

        connect().compose(client -> createConsumer()).setHandler(attempt -> {
            if (attempt.succeeded()) {
                LOGGER.debug("receiver active: {}/{} ({})", sampler.getEndpoint(), sampler.getTenant(),
                        Thread.currentThread().getName());
                result.complete(null);
            } else {
                result.completeExceptionally(attempt.cause());
            }
        });
        return result;
    }

    private Future<HonoClient> connect() {

        return amqpNetworkClient
                .connect(getClientOptions(Integer.parseInt(sampler.getReconnectAttempts())))
                .map(client -> {
                    LOGGER.info("connected to AMQP Messaging Network [{}:{}]", sampler.getHost(), sampler.getPort());
                    return client;
                });
    }

    private Future<MessageConsumer> createConsumer() {

        if (amqpNetworkClient == null) {
            return Future.failedFuture(new IllegalStateException("not connected to Hono"));
        } else if (sampler.getEndpoint().equals(HonoSampler.Endpoint.telemetry.toString())) {
            return amqpNetworkClient
                    .createTelemetryConsumer(sampler.getTenant(), this::messageReceived, closeHook -> {
                        LOGGER.error("consumer {} was closed", sampler.getEndpoint());
                    })
                    .map(consumer -> {
                        LOGGER.info("created {} consumer", sampler.getEndpoint());
                        return consumer;
                    });
        } else {
            return amqpNetworkClient
                    .createEventConsumer(sampler.getTenant(), this::messageReceived, closeHook -> {
                        LOGGER.error("consumer {} was closed", sampler.getEndpoint());
                    })
                    .map(consumer -> {
                        LOGGER.info("created {} consumer", sampler.getEndpoint());
                        return consumer;
                    });
        }
    }

    public void sample(final SampleResult result) {
        synchronized (lock) {
            long elapsed = 0;
            result.setResponseCodeOK();
            result.setSuccessful(true);
            result.setSampleCount(messageCount);
            result.setBytes(messageSize);
            result.setResponseMessage(
                    MessageFormat.format("count: {0}, bytes received: {1}, period: {2}", messageCount,
                            messageSize, elapsed));
            if (sampler.isUseSenderTime() && messageCount > 0) {
                elapsed = totalSampleDeliveryTime / messageCount;
                result.setStampAndTime(sampleStart, elapsed);
            } else if (sampleStart != 0 && messageCount > 0) { // sampling is started only when a message with a
                                                               // timestamp is received.
                elapsed = System.currentTimeMillis() - sampleStart;
                result.setStampAndTime(sampleStart, elapsed);
                result.setIdleTime(elapsed);
            } else {
                noMessagesReceived(result);
            }
            LOGGER.info("{}: received batch of {} messages in {} milliseconds", sampler.getThreadName(), messageCount,
                    elapsed);
            // reset all counters
            totalSampleDeliveryTime = 0;
            messageSize = 0;
            messageCount = 0;
            sampleStart = 0;
        }
    }

    private void noMessagesReceived(final SampleResult result) {
        LOGGER.warn("No messages were received");
        result.setIdleTime(0);
    }

    private void verifySenderTimeAndSetSamplingTime(final Long senderTime, long sampleReceivedTime) {
        if (senderTime != null) {
            if (sampleStart == 0) { // set sample start only once when the first message is received.
                sampleStart = senderTime;
            }
            long sampleDeliveryTime = sampleReceivedTime - senderTime;
            LOGGER.debug("Message delivered in : {}", sampleDeliveryTime);
            totalSampleDeliveryTime += sampleDeliveryTime;
        } else {
            throw new IllegalArgumentException("No Timestamp variable found in message");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJSONValue(final Section message) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(getValue(message),
                    HashMap.class);
            if (result == null) {
                result = Collections.emptyMap();
            }
        } catch (IOException e) {
            LOGGER.warn("Could not parse the received message", e);
            result = Collections.emptyMap();
        }
        return result;
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
        synchronized (lock) {
            messageCount++;
            LOGGER.trace("Received message. count : {}", messageCount);
            byte[] messageBody = getValue(message.getBody());
            messageSize += messageBody.length;

            if (sampler.isUseSenderTime()) {
                long sampleReceivedTime = System.currentTimeMillis();
                LOGGER.debug("Message received time : {}", sampleReceivedTime);
                if (sampler.isSenderTimeInPayload()) {
                    final Long time = (Long) getJSONValue(message.getBody())
                            .get(sampler.getSenderTimeVariableName());
                    verifySenderTimeAndSetSamplingTime(time, sampleReceivedTime);
                } else {
                    final Long time = (Long) message.getApplicationProperties().getValue().get("timeStamp");
                    verifySenderTimeAndSetSamplingTime(time, sampleReceivedTime);
                }
            } else {
                if (sampleStart == 0) {
                    sampleStart = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * Closes the connection to the AMQP Messaging Network.
     * 
     * @return A future that successfully completes once the connection is closed.
     */
    public CompletableFuture<Void> close() {

        final CompletableFuture<Void> result = new CompletableFuture<>();
        final Future<Void> clientTracker = Future.future();
        amqpNetworkClient.shutdown(clientTracker.completer());
        clientTracker.otherwiseEmpty().compose(ok -> closeVertx()).setHandler(attempt -> result.complete(null));
        return result;
    }
}
