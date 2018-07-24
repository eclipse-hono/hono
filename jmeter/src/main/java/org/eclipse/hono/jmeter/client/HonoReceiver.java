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

package org.eclipse.hono.jmeter.client;

import java.util.concurrent.CompletableFuture;

import javax.jms.IllegalStateException;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.jmeter.HonoReceiverSampler;
import org.eclipse.hono.jmeter.HonoSampler;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * Receiver, which connects to the AMQP network; asynchronous API needs to be used synchronous for JMeters threading
 * model.
 */
public class HonoReceiver extends AbstractClient {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(HonoReceiver.class);

    private final HonoClient          amqpNetworkClient;
    private final HonoReceiverSampler sampler;

    private final transient Object lock = new Object();

    private long sampleStart;
    private int  messageCount;
    private int  errorCount;
    private long totalSampleDeliveryTime;
    private boolean senderClockNotInSync;
    private long bytesReceived;
    private MessageConsumer messageConsumer;

    /**
     * Creates a new receiver.
     * 
     * @param sampler The sampler configuration.
     */
    public HonoReceiver(final HonoReceiverSampler sampler) {

        super();
        if (sampler.isUseSenderTime() && sampler.isSenderTimeInPayload() && sampler.getSenderTimeVariableName() == null) {
            throw new IllegalArgumentException("SenderTime VariableName must be set when using SenderTime flag");
        }
        this.sampler = sampler;

        final ClientConfigProperties clientConfig = new ClientConfigProperties();
        clientConfig.setHostnameVerificationRequired(false);
        clientConfig.setHost(sampler.getHost());
        clientConfig.setPort(sampler.getPortAsInt());
        clientConfig.setName(sampler.getContainer());
        clientConfig.setUsername(sampler.getUser());
        clientConfig.setPassword(sampler.getPwd());
        clientConfig.setTrustStorePath(sampler.getTrustStorePath());
        clientConfig.setInitialCredits(Integer.parseInt(sampler.getPrefetch()));

        // amqp network config
        amqpNetworkClient = new HonoClientImpl(vertx, clientConfig);
    }

    /**
     * Starts this receiver.
     * 
     * @return A future indicating the outcome of the startup process.
     */
    public CompletableFuture<Void> start() {

        final CompletableFuture<Void> result = new CompletableFuture<>();
        final String endpoint = sampler.getEndpoint();
        final String tenant = sampler.getTenant();
        connect().compose(client -> createConsumer(endpoint, tenant)).setHandler(attempt -> {
            if (attempt.succeeded()) {
                LOGGER.debug("receiver active: {}/{} ({})", endpoint, tenant, Thread.currentThread().getName());
                messageConsumer = attempt.result();
                result.complete(null);
            } else {
                result.completeExceptionally(attempt.cause());
            }
        });
        return result;
    }

    private Future<HonoClient> connect() {

        final int reconnectAttempts = Integer.parseInt(sampler.getReconnectAttempts());
        return amqpNetworkClient
                .connect(getClientOptions(reconnectAttempts))
                .map(client -> {
                    LOGGER.info("connected to AMQP Messaging Network [{}:{}]", sampler.getHost(), sampler.getPort());
                    return client;
                });
    }

    private Future<MessageConsumer> createConsumer(final String endpoint, final String tenant) {

        if (amqpNetworkClient == null) {
            return Future.failedFuture(new IllegalStateException("not connected to Hono"));
        } else if (endpoint.equals(HonoSampler.Endpoint.telemetry.toString())) {
            return amqpNetworkClient
                    .createTelemetryConsumer(tenant, this::messageReceived, closeHook -> {
                        LOGGER.error("telemetry consumer was closed");
                    }).map(consumer -> {
                        LOGGER.info("created telemetry consumer [{}]", tenant);
                        return consumer;
                    });
        } else {
            return amqpNetworkClient
                    .createEventConsumer(tenant, this::messageReceived, closeHook -> {
                        LOGGER.error("event consumer was closed");
                    }).map(consumer -> {
                        LOGGER.info("created event consumer [{}]", tenant);
                        return consumer;
                    });
        }
    }

    /**
     * Takes a sample.
     * 
     * @param result The result to set the sampler's current statistics on.
     */
    public void sample(final SampleResult result) {
        synchronized (lock) {
            long elapsed = 0;
            result.setResponseCodeOK();
            result.setSuccessful(errorCount == 0 && !senderClockNotInSync);
            result.setSampleCount(messageCount);
            result.setErrorCount(errorCount); // NOTE: This method does nothing in JMeter 3.3/4.0
            result.setBytes(bytesReceived);
            if (messageCount > 0) {
                if (sampler.isUseSenderTime() && sampleStart != 0 && errorCount == 0) {
                    // errorCount has to be 0 here - otherwise totalSampleDeliveryTime doesn't have a correct value
                    elapsed = senderClockNotInSync ? -1 : totalSampleDeliveryTime / messageCount;
                    result.setStampAndTime(sampleStart, elapsed);
                } else if (sampleStart != 0) {
                    elapsed = System.currentTimeMillis() - sampleStart;
                    result.setStampAndTime(sampleStart, elapsed);
                    result.setIdleTime(elapsed);
                } else {
                    LOGGER.warn("sampleStart hasn't been set - setting elapsed time to 0");
                    result.setStampAndTime(System.currentTimeMillis(), 0);
                    result.setIdleTime(0);
                }
            } else {
                // empty sample
                result.setStampAndTime(System.currentTimeMillis(), 0);
                result.setIdleTime(0);
            }
            String responseMessage = "";
            if (errorCount == 0) {
                final String formatString = sampler.isUseSenderTime() ? "{}: received batch of {} messages; average delivery time: {}ms"
                        : "{}: received batch of {} messages in {}ms";
                LOGGER.info(formatString, sampler.getThreadName(), messageCount, elapsed);
            } else {
                LOGGER.info("{}: received batch of {} messages with {} errors in {}ms", sampler.getThreadName(), messageCount, errorCount, elapsed);
                responseMessage = "got " + errorCount + " invalid messages";
            }
            if (senderClockNotInSync) {
                responseMessage = (responseMessage.isEmpty() ? "" : responseMessage + "; ") + "sender clock not in sync";
                LOGGER.error("The sender time extracted from at least one of the received messages is newer than the receiver time" +
                        " - sender and receiver clocks are not in sync. Consider deactivating usage of the sender time.");
            }
            result.setResponseMessage(responseMessage);

            // reset all fields
            totalSampleDeliveryTime = 0;
            senderClockNotInSync = false;
            bytesReceived = 0;
            messageCount = 0;
            errorCount = 0;
            sampleStart = 0;
        }
    }

    private void messageReceived(final Message message) {
        synchronized (lock) {
            final long sampleReceivedTime = System.currentTimeMillis();
            messageCount++;

            if (!(message.getBody() instanceof Data)) {
                errorCount++;
                setSampleStartIfNotSetYet(sampleReceivedTime);
                LOGGER.warn("got message with non-Data body section; increasing errorCount in batch to {}; current batch size: {}",
                        errorCount, messageCount);
                return;
            }
            final byte[] messageBody = ((Data) message.getBody()).getValue().getArray();
            bytesReceived += messageBody.length;
            final Long senderTime = getSenderTime(message, messageBody);
            if (sampler.isUseSenderTime() && senderTime == null) {
                errorCount++;
                setSampleStartIfNotSetYet(sampleReceivedTime);
                LOGGER.warn("got message without sender time information; increasing errorCount in batch to {}; current batch size: {}",
                        errorCount, messageCount);
                return;
            }

            if (sampler.isUseSenderTime()) {
                setSampleStartIfNotSetYet(senderTime); // set sample start only once when the first message is received.
                final long sampleDeliveryTime = sampleReceivedTime - senderTime;
                if (sampleDeliveryTime < 0) {
                    // means that time on sender and receiver is not in sync
                    LOGGER.debug("got negative delivery time from received sender time: {}ms", sampleDeliveryTime);
                    senderClockNotInSync = true;
                }
                totalSampleDeliveryTime += sampleDeliveryTime;
                LOGGER.trace("received message; current batch size: {}; reception timestamp: {}; delivery time: {}ms; remaining credit: {}",
                        messageCount, sampleReceivedTime, sampleDeliveryTime, messageConsumer.getRemainingCredit());
            } else {
                setSampleStartIfNotSetYet(sampleReceivedTime);
                LOGGER.trace("received message; current batch size: {}; reception timestamp: {}; remaining credit: {}", 
                        messageCount, sampleReceivedTime, messageConsumer.getRemainingCredit());
            }
        }
    }

    private void setSampleStartIfNotSetYet(final long timestamp) {
        if (sampleStart == 0) {
            sampleStart = timestamp;
        }
    }

    private Long getSenderTime(final Message message, final byte[] messageBody) {
        if (!sampler.isUseSenderTime()) {
            return null;
        }
        return sampler.isSenderTimeInPayload() ? getSenderTimeFromJsonPayload(messageBody)
                : getSenderTimeFromMessageProperties(message);
    }

    private Long getSenderTimeFromJsonPayload(final byte[] payload) {
        try {
            final JsonObject jsonObject = Buffer.buffer(payload).toJsonObject();
            final Long senderTime = jsonObject.getLong(sampler.getSenderTimeVariableName(), null);
            if (senderTime == null) {
                LOGGER.warn("could not get sender time from JSON payload: entry with key '"
                        + sampler.getSenderTimeVariableName() + "' not found or empty");
            }
            return senderTime;
        } catch (final DecodeException e) {
            LOGGER.error("could not parse the received message as JSON", e);
            return null;
        }
    }

    private Long getSenderTimeFromMessageProperties(final Message message) {
        final Long senderTime = MessageHelper.getApplicationProperty(message.getApplicationProperties(), TIME_STAMP_VARIABLE,
                Long.class);
        if (senderTime == null) {
            LOGGER.warn("could not get sender time from '" + TIME_STAMP_VARIABLE + "' message application property");
        }
        return senderTime;
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
