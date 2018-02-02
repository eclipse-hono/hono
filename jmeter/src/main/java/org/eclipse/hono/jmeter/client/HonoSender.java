/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jmeter.samplers.SampleResult;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.jmeter.HonoSampler;
import org.eclipse.hono.jmeter.HonoSenderSampler;
import org.eclipse.hono.util.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;

/**
 * A wrapper around a {@code HonoClient} mapping the client's asynchronous API to the blocking
 * threading model used by JMeter.
 */
public class HonoSender extends AbstractClient {

    private static final int MAX_RECONNECT_ATTEMPTS      = 0;
    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSender.class);
    private static final int MAX_MESSAGES_PER_BATCH_SEND = 300;

    private final AtomicBoolean  running = new AtomicBoolean(false);

    private ConnectionFactory  honoConnectionFactory;
    private HonoClient         honoClient;

    private ConnectionFactory  registrationConnectionFactory;
    private HonoClient         registrationHonoClient;
    private RegistrationClient registrationClient;

    private String             token;
    private MessageSender      messageSender;
    private Vertx              vertx = vertx();
    private HonoSenderSampler  sampler;

    public HonoSender(final HonoSenderSampler sampler) throws InterruptedException {
        this.sampler = sampler;

        // hono config
        honoConnectionFactory = ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                .disableHostnameVerification()
                .host(sampler.getHost())
                .name(sampler.getContainer())
                .user(sampler.getUser())
                .password(sampler.getPwd())
                .port(Integer.parseInt(sampler.getPort()))
                .trustStorePath(sampler.getTrustStorePath())
                .vertx(vertx)
                .build();

        // registry config
        registrationConnectionFactory = ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                .disableHostnameVerification()
                .host(sampler.getRegistryHost())
                .name(sampler.getContainer())
                .user(sampler.getRegistryUser())
                .password(sampler.getRegistryPwd())
                .port(Integer.parseInt(sampler.getRegistryPort()))
                .trustStorePath(sampler.getRegistryTrustStorePath())
                .vertx(vertx)
                .build();

        LOGGER.debug("create hono sender - tenant: {}  deviceId: {}",sampler.getTenant(),sampler.getDeviceId());

        connectRegistry();
        createRegistrationClient();
        createDevice();

        connect();
        updateAssertion();
        createSender();
        running.compareAndSet(false, true);
        LOGGER.debug("sender active: {}/{} ({})",sampler.getEndpoint(),sampler.getTenant(),Thread.currentThread().getName());
    }

    private void connect() throws InterruptedException {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        honoClient = new HonoClientImpl(vertx, honoConnectionFactory);
        honoClient.connect(getClientOptions(MAX_RECONNECT_ATTEMPTS)).setHandler(connectionHandler -> {
            if (connectionHandler.failed()) {
                LOGGER.error("HonoClient.connect() failed", connectionHandler.cause());
            }
            connectLatch.countDown();
        });
        connectLatch.await();
    }

    private void connectRegistry() throws InterruptedException {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        registrationHonoClient = new HonoClientImpl(vertx, registrationConnectionFactory);
        registrationHonoClient.connect(getClientOptions(MAX_RECONNECT_ATTEMPTS)).setHandler(connectionHandler -> {
            if (connectionHandler.failed()) {
                LOGGER.error("HonoClient.connect() failed", connectionHandler.cause());
            }
            connectLatch.countDown();
        });
        connectLatch.await();
    }

    private void createRegistrationClient() throws InterruptedException {
        final CountDownLatch assertionLatch = new CountDownLatch(1);
        registrationHonoClient.getOrCreateRegistrationClient(sampler.getTenant()).setHandler(resultHandler -> {
            if (resultHandler.failed()) {
                LOGGER.error("HonoClient.getOrCreateRegistrationClient() failed", resultHandler.cause());
            } else {
                registrationClient = resultHandler.result();
            }
            assertionLatch.countDown();
        });
        assertionLatch.await();
    }

    private void createSender() throws InterruptedException {

        if (honoClient == null) {
            throw new IllegalStateException("not connected to Hono");
        }

        final CountDownLatch senderLatch = new CountDownLatch(1);
        if (sampler.getEndpoint().equals(HonoSampler.Endpoint.telemetry.toString())) {
            honoClient.getOrCreateTelemetrySender(sampler.getTenant()).setHandler(resultHandler -> {
                if (resultHandler.failed()) {
                    LOGGER.error("HonoClient.getOrCreateTelemetrySender() failed", resultHandler.cause());
                } else {
                    messageSender = resultHandler.result();
                }
                senderLatch.countDown();
            });
        } else {
            honoClient.getOrCreateEventSender(sampler.getTenant()).setHandler(resultHandler -> {
                if (resultHandler.failed()) {
                    LOGGER.error("HonoClient.getOrCreateEventSender() failed", resultHandler.cause());
                } else {
                    messageSender = resultHandler.result();
                }
                senderLatch.countDown();
            });
        }
        senderLatch.await();
    }

    public void send(final SampleResult sampleResult, final String deviceId, final boolean waitOnCredits) throws InterruptedException {

        if (messageSender == null) {
            LOGGER.warn("messsage sender is null, trying to create it lazily ...");
            createSender();
        }

        try {
            if (messageSender != null) {

                final AtomicInteger messagesSent = new AtomicInteger(0);
                final AtomicLong bytesSent = new AtomicLong(0);
                final long messageLength = sampler.getData().getBytes(StandardCharsets.UTF_8).length;

                // defaults
                sampleResult.setResponseMessage(MessageFormat.format("{0}/{1}/{2}", sampler.getEndpoint(), sampler.getTenant(), deviceId));
                // sampleResult.setResponseData(sampler.getData().getBytes());
                // start sample
                sampleResult.sampleStart();

                if (waitOnCredits) {

                    final CountDownLatch batchComplete = new CountDownLatch(1);
                    final Handler<Void> runBatch = run -> {
                        LOGGER.info("starting batch send with {} credits available", messageSender.getCredit());
                        while (!messageSender.sendQueueFull() && messagesSent.get() < MAX_MESSAGES_PER_BATCH_SEND) {
                            Map<String, Object> properties = new HashMap<>();
                            if (sampler.isSetSenderTime()) {
                                properties.put(TIME_STAMP_VARIABLE, System.currentTimeMillis());
                            }
                            messageSender.send(deviceId, properties, sampler.getData(), sampler.getContentType(), token, (Handler<Void>) null);
                            bytesSent.addAndGet(messageLength);
                            messagesSent.incrementAndGet();
                            if (LOGGER.isDebugEnabled()) {
                                if (messagesSent.get() % 200 == 0) {
                                    LOGGER.debug("messages sent: {}", messagesSent.get());
                                }
                            }
                        }
                        batchComplete.countDown();
                    };

                    vertx.getOrCreateContext().runOnContext(batchSend -> {

                        if (messageSender.sendQueueFull()) {
                            LOGGER.info("waiting for credits ...");
                            messageSender.sendQueueDrainHandler(runBatch);
                        } else {
                            runBatch.handle(null);
                        }
                    });

                    batchComplete.await();

                } else {

                    Map<String, Long> properties = new HashMap<>();
                    if (sampler.isSetSenderTime()) {
                        properties.put(TIME_STAMP_VARIABLE, System.currentTimeMillis());
                    }

                    final CompletableFuture<ProtonDelivery> delivery = new CompletableFuture<>();

                    // mark send as error when we have no credits
                    messageSender.send(deviceId, properties, sampler.getData(), sampler.getContentType(), token)
                        .setHandler(sendAttempt -> {
                            if (sendAttempt.succeeded()) {
                                bytesSent.addAndGet(messageLength);
                                messagesSent.incrementAndGet();
                                delivery.complete(sendAttempt.result());
                            } else {
                                String error = MessageFormat.format(
                                        "ERROR: Client has not enough capacity - credit: {0}  device: {1}  address: {2}  thread: {3}",
                                        messageSender.getCredit(), deviceId, sampler.getTenant(),
                                        sampler.getThreadName());
                                sampleResult.setResponseMessage(error);
                                sampleResult.setSuccessful(false);
                                sampleResult.setResponseCode("500");
                                LOGGER.error(error);
                                delivery.completeExceptionally(sendAttempt.cause());
                            }
                        });
                    delivery.get();
                }

                sampleResult.setSentBytes(bytesSent.get());
                sampleResult.setSampleCount(messagesSent.get());
                LOGGER.info("{}: sent batch of {} messages for device {}", sampler.getThreadName(), messagesSent.get(), deviceId);
            } else {
                String error = "sender link could not be established";
                sampleResult.setResponseMessage(error);
                sampleResult.setSuccessful(false);
                LOGGER.error(error);
            }

            sampleResult.sampleEnd();

        } catch (Throwable t) {
            LOGGER.error("unknown exception", t);
        }
    }

    private void createDevice() throws InterruptedException {
        if (registrationClient != null) {
            CountDownLatch latch = new CountDownLatch(1);
            JsonObject data = new JsonObject("{ \"type\": \"jmeter test device\" }");
            registrationClient.register(sampler.getDeviceId(), data, assertHandler -> {
                if (assertHandler.failed()) {
                    LOGGER.error("RegistrationClient.register() failed", assertHandler.cause());
                }
                latch.countDown();
            });
            latch.await();
            LOGGER.debug("created device: {}",sampler.getDeviceId());
        } else {
            LOGGER.debug("device could not be created - registrationClient is NULL: {}",sampler.getDeviceId());
        }
    }

    private void removeDevice() throws InterruptedException {
        if (registrationClient != null) {
            CountDownLatch latch = new CountDownLatch(1);
            registrationClient.deregister(sampler.getDeviceId(), assertHandler -> {
                if (assertHandler.failed()) {
                    LOGGER.error("RegistrationClient.deregister() failed", assertHandler.cause());
                }
                latch.countDown();
            });
            latch.await();
            LOGGER.debug("removed device: {}",sampler.getDeviceId());
        } else {
            LOGGER.debug("device could not be removed - registrationClient is NULL: {}",sampler.getDeviceId());
        }
    }

    private void updateAssertion() throws InterruptedException {
        if (registrationClient != null) {
            CountDownLatch latch = new CountDownLatch(1);
            registrationClient.assertRegistration(sampler.getDeviceId(), assertHandler -> {
                if (assertHandler.failed()) {
                    LOGGER.error("RegistrationClient.assertRegistration() failed", assertHandler.cause());
                } else {
                    token = assertHandler.result().getPayload().getString(RegistrationConstants.FIELD_ASSERTION);
                }
                latch.countDown();
            });
            latch.await();
            LOGGER.debug("updated assertion: {} for device: {}",token,sampler.getDeviceId());
        } else {
            LOGGER.debug("assertion could not be updated - registrationClient is NULL: {}",sampler.getDeviceId());
        }
    }

    public void close() throws InterruptedException {

        if (running.compareAndSet(true, false)) {
            try {
                final CountDownLatch closeLatch = new CountDownLatch(1);
                messageSender.close(closeAttempt -> {
                    if (closeAttempt.succeeded()) {
                        closeLatch.countDown();
                    }
                });
                if (!closeLatch.await(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("could not close sender properly, shutting down connection ...");
                }
                removeDevice();
                registrationHonoClient.shutdown();
                honoClient.shutdown();
                vertx.close();
            } catch (final Throwable t) {
                LOGGER.error("unknown exception in closing of sender", t);
            }
        }
    }
}
