/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
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

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jmeter.samplers.SampleResult;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.eclipse.hono.jmeter.HonoSampler;
import org.eclipse.hono.jmeter.HonoSenderSampler;
import org.eclipse.hono.util.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;

/**
 * A wrapper around a {@code HonoClient} mapping the client's asynchronous API to the blocking
 * threading model used by JMeter.
 */
public class HonoSender extends AbstractClient {

    private static final int MAX_RECONNECT_ATTEMPTS      = 0;
    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSender.class);

    private final AtomicBoolean     running = new AtomicBoolean(false);
    private final HonoSenderSampler sampler;
    private final ConnectionFactory honoConnectionFactory;
    private final HonoClient        honoClient;
    private final ConnectionFactory registrationConnectionFactory;
    private final HonoClient        registrationHonoClient;

    private RegistrationClient registrationClient;
    private String             token;
    private MessageSender      messageSender;

    /**
     * Creates a new sender for configuration properties.
     * 
     * @param sampler The configuration properties.
     */
    public HonoSender(final HonoSenderSampler sampler) {

        super();
        this.sampler = sampler;

        // hono config
        final ClientConfigProperties honoProps = new ClientConfigProperties();
        honoProps.setHostnameVerificationRequired(false);
        honoProps.setHost(sampler.getHost());
        honoProps.setPort(Integer.parseInt(sampler.getPort()));
        honoProps.setName(sampler.getContainer());
        honoProps.setUsername(sampler.getUser());
        honoProps.setPassword(sampler.getPwd());
        honoProps.setTrustStorePath(sampler.getTrustStorePath());

        honoConnectionFactory = ConnectionFactoryBuilder.newBuilder(honoProps)
                .vertx(vertx)
                .build();
        honoClient = new HonoClientImpl(vertx, honoConnectionFactory, honoProps);

        // registry config
        final ClientConfigProperties registryProps = new ClientConfigProperties();
        registryProps.setHostnameVerificationRequired(false);
        registryProps.setHost(sampler.getRegistryHost());
        registryProps.setPort(Integer.parseInt(sampler.getRegistryPort()));
        registryProps.setName(sampler.getContainer());
        registryProps.setUsername(sampler.getRegistryUser());
        registryProps.setPassword(sampler.getRegistryPwd());
        registryProps.setTrustStorePath(sampler.getRegistryTrustStorePath());

        registrationConnectionFactory = ConnectionFactoryBuilder.newBuilder(registryProps)
                .vertx(vertx)
                .build();
        registrationHonoClient = new HonoClientImpl(vertx, registrationConnectionFactory, registryProps);
    }

    /**
     * Starts this sender.
     * 
     * @param deviceId The device to send messages for.
     * @return A future indicating the outcome of the startup process.
     */
    public CompletableFuture<Void> start(final String deviceId) {

        final CompletableFuture<Void> result = new CompletableFuture<>();

        if (running.compareAndSet(false, true)) {

            LOGGER.debug("create hono sender - tenant: {}  deviceId: {}", sampler.getTenant(), sampler.getDeviceId());

            CompositeFuture.all(
                    connect().compose(ok -> createSender()),
                    connectRegistry().compose(ok -> createRegistrationClient()).compose(ok -> createDevice(deviceId)).compose(ok -> updateAssertion(deviceId)))
                .setHandler(startup -> {
                    if (startup.succeeded()) {
                        result.complete(null);
                    } else {
                        LOGGER.info("sender initialization complete");
                        LOGGER.debug("sender active: {}/{} ({})", sampler.getEndpoint(), sampler.getTenant(),
                                Thread.currentThread().getName());
                        running.set(false);
                        result.completeExceptionally(startup.cause());
                    }
                });
        } else {
            result.completeExceptionally(new javax.jms.IllegalStateException("sender is already starting"));
        }
        return result;
    }

    private Future<HonoClient> connect() {

        return honoClient
                .connect(getClientOptions(MAX_RECONNECT_ATTEMPTS))
                .map(client -> {
                    LOGGER.info("connected to Hono Messaging [{}:{}]", sampler.getHost(), sampler.getPort());
                    return client;
                });
    }

    private Future<HonoClient> connectRegistry() {

        return registrationHonoClient
                .connect(getClientOptions(MAX_RECONNECT_ATTEMPTS))
                .map(client -> {
                    LOGGER.info("connected to Device Registry [{}:{}]", sampler.getRegistryHost(), sampler.getRegistryPort());
                    return client;
                });
    }

    private Future<RegistrationClient> createRegistrationClient() {

        return registrationHonoClient
                .getOrCreateRegistrationClient(sampler.getTenant())
                .map(client -> {
                    registrationClient = client;
                    return client;
                });
    }

    private Future<MessageSender> createSender() {

        if (sampler.getEndpoint().equals(HonoSampler.Endpoint.telemetry.toString())) {
            return honoClient
                    .getOrCreateTelemetrySender(sampler.getTenant())
                    .map(sender -> {
                        messageSender = sender;
                        return sender;
                    });
        } else {
            return honoClient
                    .getOrCreateEventSender(sampler.getTenant())
                    .map(sender -> {
                        messageSender = sender;
                        return sender;
                    });
        }
    }

    private Future<Void> createDevice(final String deviceId) {

        if (registrationClient == null) {
            return Future.failedFuture(new IllegalStateException("no registration client available"));
        } else {

            final JsonObject data = new JsonObject().put("type", "jmeter test device");
            LOGGER.info("registering device [{}]", deviceId);
            return registrationClient.register(deviceId, data).recover(t -> {
                final ServiceInvocationException e = (ServiceInvocationException) t;
                if (e.getErrorCode() == HttpURLConnection.HTTP_CONFLICT) {
                    // ignore if device already exists
                    LOGGER.info("device {} already exists", deviceId);
                    return Future.succeededFuture();
                } else {
                    return Future.failedFuture(e);
                }
            }).map(ok -> {
                LOGGER.info("registered device {}", deviceId);
                return (Void) null;
            });
        }
    }

    private Future<Void> removeDevice(final String deviceId) {

        if (registrationClient == null) {
            return Future.failedFuture(new IllegalStateException("no registration client available"));
        } else {
            return registrationClient.deregister(deviceId).map(ok -> {
                LOGGER.info("removed device: {}", deviceId);
                return (Void) null;
            });
        }
    }

    private Future<String> updateAssertion(final String deviceId) {

        if (registrationClient == null) {
            return Future.failedFuture(new IllegalStateException("no registration client available"));
        } else {
            return registrationClient.assertRegistration(deviceId).map(regInfo -> {
                token = regInfo.getString(RegistrationConstants.FIELD_ASSERTION);
                LOGGER.info("got registration assertion for device [{}]: {}", deviceId, token);
                return token;
            });
        }
    }

    /**
     * Publishes a batch of messages to Hono Messaging.
     * 
     * @param sampleResult
     * @param deviceId
     * @param waitOnCredits
     */
    public void send(final SampleResult sampleResult, final String deviceId, final boolean waitOnCredits) {

        // start sample
        sampleResult.sampleStart();

        if (messageSender == null) {
            sampleResult.setSuccessful(false);
            sampleResult.setResponseMessage("sender is not connected");
            sampleResult.setResponseCode("503");
        } else {

            final AtomicInteger messagesSent = new AtomicInteger(0);
            final AtomicLong bytesSent = new AtomicLong(0);
            final byte[] payload = sampler.getData().getBytes(StandardCharsets.UTF_8);
            final CompletableFuture<Void> tracker = new CompletableFuture<>();

            final Future<ProtonDelivery> deliveryTracker = Future.future();
            final Future<Void> creditTracker = Future.future();

            final Map<String, Long> properties = new HashMap<>();
            if (sampler.isSetSenderTime()) {
                properties.put(TIME_STAMP_VARIABLE, System.currentTimeMillis());
            }

            LOGGER.trace("sending messages for device [{}]", deviceId);

            if (waitOnCredits) {

                messageSender.send(
                        deviceId,
                        properties,
                        sampler.getData(),
                        sampler.getContentType(),
                        token,
                        replenished -> creditTracker.complete()).setHandler(deliveryTracker.completer());

            } else {

                creditTracker.complete();
                messageSender.send(
                        deviceId,
                        properties,
                        sampler.getData(),
                        sampler.getContentType(),
                        token).setHandler(deliveryTracker.completer());
            }

            CompositeFuture.all(deliveryTracker, creditTracker).setHandler(send -> {
                if (send.succeeded()) {
                    bytesSent.addAndGet(payload.length);
                    messagesSent.incrementAndGet();
                    tracker.complete(null);
                } else {
                    tracker.completeExceptionally(send.cause());
                }
            });

            try {
                tracker.get(1, TimeUnit.SECONDS);
                sampleResult.setResponseMessage(MessageFormat.format("{0}/{1}/{2}", sampler.getEndpoint(), sampler.getTenant(), deviceId));
                sampleResult.setSentBytes(bytesSent.get());
                sampleResult.setSampleCount(messagesSent.get());
                LOGGER.debug("{}: sent batch of {} messages for device [{}]", sampler.getThreadName(), messagesSent.get(), deviceId);
            } catch (InterruptedException | CancellationException | ExecutionException | TimeoutException e) {
                sampleResult.setSuccessful(false);
                sampleResult.setResponseMessage(e.getCause().getMessage());
                sampleResult.setResponseCode("500");
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        sampleResult.sampleEnd();
    }

    /**
     * Closes the connections to the Device Registration Service and Hono Messaging.
     * 
     * @param deviceId The device to unregister before closing the connections.
     * @return A future that successfully completes once the connections are closed.
     */
    public CompletableFuture<Void> close(final String deviceId) {

        final CompletableFuture<Void> shutdown = new CompletableFuture<>();

        if (running.compareAndSet(true, false)) {

            final Future<Void> honoTracker = Future.future();
            honoClient.shutdown(honoTracker.completer());

            CompositeFuture.all(
                    honoTracker.otherwiseEmpty(),
                    removeDevice(deviceId).otherwiseEmpty().compose(ok -> {
                        final Future<Void> registryTracker = Future.future();
                        registrationHonoClient.shutdown(registryTracker.completer());
                        return registryTracker.otherwiseEmpty();
                    })).compose(ok -> closeVertx()).setHandler(done -> shutdown.complete(null));
        } else {
            LOGGER.debug("sender already stopped");
            shutdown.complete(null);
        }
        return shutdown;
    }
}
