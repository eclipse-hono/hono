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

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.jmeter.samplers.SampleResult;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.jmeter.HonoSampler;
import org.eclipse.hono.jmeter.HonoSenderSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonClientOptions;

/**
 * Sender, which connects to directly to Hono; asynchronous API needs to be used synchronous for JMeters threading model
 */
public class HonoSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSender.class);

    private ConnectionFactory  honoConnectionFactory;
    private HonoClient         honoClient;

    private ConnectionFactory  registrationConnectionFactory;
    private HonoClient         registrationHonoClient;
    private RegistrationClient registrationClient;

    private String             token;
    private MessageSender      messageSender;
    private Vertx              vertx = Vertx.vertx();
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
                .port(sampler.getPort())
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
                .port(sampler.getRegistryPort())
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

        LOGGER.debug("sender active: {}/{} ({})",sampler.getEndpoint(),sampler.getTenant(),Thread.currentThread().getName());
    }

    private void connect() throws InterruptedException {
        final CountDownLatch connectLatch = new CountDownLatch(1);
        honoClient = new HonoClientImpl(vertx, honoConnectionFactory);
        honoClient.connect(new ProtonClientOptions(), connectionHandler -> {
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
        registrationHonoClient.connect(new ProtonClientOptions(), connectionHandler -> {
            if (connectionHandler.failed()) {
                LOGGER.error("HonoClient.connect() failed", connectionHandler.cause());
            }
            connectLatch.countDown();
        });
        connectLatch.await();
    }

    private void createRegistrationClient() throws InterruptedException {
        final CountDownLatch assertionLatch = new CountDownLatch(1);
        registrationHonoClient.getOrCreateRegistrationClient(sampler.getTenant(), resultHandler -> {
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
        final CountDownLatch senderLatch = new CountDownLatch(1);
        if (honoClient == null || !honoClient.isConnected()) {
            connect();
        }
        if(sampler.getEndpoint().equals(HonoSampler.Endpoint.telemetry.toString())) {
            honoClient.getOrCreateTelemetrySender(sampler.getTenant(), sampler.getDeviceId(), resultHandler -> {
                if (resultHandler.failed()) {
                    LOGGER.error("HonoClient.getOrCreateTelemetrySender() failed", resultHandler.cause());
                } else {
                    messageSender = resultHandler.result();
                }
                senderLatch.countDown();
            });
        }
        else {
            honoClient.getOrCreateEventSender(sampler.getTenant(), sampler.getDeviceId(), resultHandler -> {
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
            LOGGER.error("messsage sender is null - try to create it lazy");
            createSender();
        }
        try {
            if (messageSender != null) {
                Map<String, Long> properties = new HashMap<>();
                if (sampler.isSetSenderTime()) {
                    properties.put("millis", System.currentTimeMillis());
                }
                // defaults
                sampleResult.setResponseMessage(MessageFormat.format("Send successful for device {0} ({1}): {2}",
                        deviceId, sampler.getTenant(), sampler.getData()));
                sampleResult.setSentBytes(sampler.getData().getBytes().length);
                // start sample
                sampleResult.sampleStart();
                if(waitOnCredits) {
                    // wait until we have credits
                    final CountDownLatch senderLatch = new CountDownLatch(1);
                    messageSender.send(deviceId, properties, sampler.getData(),
                            sampler.getContentType(), token, capacityAvailableHandler -> {
                                senderLatch.countDown();
                            });
                    senderLatch.await();
                }
                else {
                    // mark send as error when we have no credits
                    boolean messageAccepted = messageSender.send(deviceId, properties, sampler.getData(),
                            sampler.getContentType(), token);
                    if (!messageAccepted) {
                        String error = MessageFormat.format(
                                "Client has not enough capacity - credit: {0}  device: {1}  address: {2}  thread: {3}",
                                messageSender.getCredit(), deviceId, sampler.getTenant(),
                                Thread.currentThread().getName());
                        sampleResult.setResponseMessage(error);
                        sampleResult.setSuccessful(false);
                        sampleResult.setResponseCode("500");
                        LOGGER.error(error);
                    }
                }
                sampleResult.sampleEnd();
                LOGGER.debug("sended: " + deviceId);
            } else {
                String error = "sender could not be established";
                sampleResult.setResponseMessage(error);
                sampleResult.setSuccessful(false);
                LOGGER.error(error);
            }
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
                    token = assertHandler.result().getPayload().getString("assertion");
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
        removeDevice();
        registrationHonoClient.shutdown();
        honoClient.shutdown();
    }
}
