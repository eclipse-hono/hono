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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.eclipse.hono.util.JwtHelper;
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

    private static final int    MAX_RECONNECT_ATTEMPTS = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSender.class);

    private final AtomicBoolean     running = new AtomicBoolean(false);
    private final HonoSenderSampler sampler;
    private final ConnectionFactory honoConnectionFactory;
    private final HonoClient        honoClient;
    private final ConnectionFactory registrationConnectionFactory;
    private final HonoClient        registrationHonoClient;
    private final byte[]            payload;

    private String assertion;
    private Instant assertionExpiration;

    /**
     * Creates a new sender for configuration properties.
     * 
     * @param sampler The configuration properties.
     */
    public HonoSender(final HonoSenderSampler sampler) {

        super();
        this.sampler = sampler;
        this.payload = sampler.getData().getBytes(StandardCharsets.UTF_8);

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
     * <p>
     * As part of the startup the sender connects to Hono Messaging and the
     * Device Registration service.
     * 
     * @param deviceId The device to send messages for.
     * @return A future indicating the outcome of the startup process.
     */
    public CompletableFuture<Void> start(final String deviceId) {

        final CompletableFuture<Void> result = new CompletableFuture<>();

        if (running.compareAndSet(false, true)) {

            LOGGER.debug("create hono sender - tenant: {}  deviceId: {}", sampler.getTenant(), sampler.getDeviceId());

            CompositeFuture.all(
                    connectToHonoMessaging(),
                    connectToRegistrationService().compose(ok -> createDevice(deviceId)))
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
            result.completeExceptionally(new IllegalStateException("sender is already starting"));
        }
        return result;
    }

    private Future<HonoClient> connectToHonoMessaging() {

        return honoClient
                .connect(getClientOptions(MAX_RECONNECT_ATTEMPTS))
                .map(client -> {
                    LOGGER.info("connected to Hono Messaging [{}:{}]", sampler.getHost(), sampler.getPort());
                    return client;
                });
    }

    private Future<HonoClient> connectToRegistrationService() {

        return registrationHonoClient
                .connect(getClientOptions(MAX_RECONNECT_ATTEMPTS))
                .map(client -> {
                    LOGGER.info("connected to Device Registry [{}:{}]", sampler.getRegistryHost(), sampler.getRegistryPort());
                    return client;
                });
    }

    private Future<RegistrationClient> getRegistrationClient() {

        return registrationHonoClient.getOrCreateRegistrationClient(sampler.getTenant());
    }

    private Future<MessageSender> getSender() {

        if (sampler.getEndpoint().equals(HonoSampler.Endpoint.telemetry.toString())) {
            return honoClient.getOrCreateTelemetrySender(sampler.getTenant());
        } else {
            return honoClient.getOrCreateEventSender(sampler.getTenant());
        }
    }

    private Future<Void> createDevice(final String deviceId) {

        final JsonObject data = new JsonObject().put("type", "jmeter test device");
        LOGGER.info("registering device [{}]", deviceId);
        return getRegistrationClient()
                .compose(client -> client.register(deviceId, data))
                .recover(t -> {
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

    private Future<Void> removeDevice(final String deviceId) {

        return getRegistrationClient()
                .compose(client -> client.deregister(deviceId))
                .map(ok -> {
                    LOGGER.info("removed device: {}", deviceId);
                    return (Void) null;
                });
    }

    private Future<String> getRegistrationAssertion(final String deviceId) {

        if (assertion != null && Instant.now().isBefore(assertionExpiration)) {
            return Future.succeededFuture(assertion);
        } else {
            return getRegistrationClient()
                    .compose(client -> client.assertRegistration(deviceId))
                    .map(regInfo -> {
                        assertion = regInfo.getString(RegistrationConstants.FIELD_ASSERTION);
                        assertionExpiration = JwtHelper.getExpiration(assertion).toInstant();
                        LOGGER.info("got registration assertion for device [{}], expires: {}", deviceId, assertionExpiration);
                        return assertion;
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

        final CompletableFuture<SampleResult> tracker = new CompletableFuture<>();

        // start sample
        sampleResult.sampleStart();
        final Future<MessageSender> senderFuture = getSender();
        senderFuture.compose(ok -> getRegistrationAssertion(deviceId)).map(token -> {

            final Future<ProtonDelivery> deliveryTracker = Future.future();
            final Future<Void> creditTracker = Future.future();

            final Map<String, Long> properties = new HashMap<>();
            if (sampler.isSetSenderTime()) {
                properties.put(TIME_STAMP_VARIABLE, System.currentTimeMillis());
            }

            LOGGER.trace("sending messages for device [{}]", deviceId);

            if (waitOnCredits) {

                senderFuture.result().send(
                        deviceId,
                        properties,
                        sampler.getData(),
                        sampler.getContentType(),
                        token,
                        replenished -> creditTracker.complete()).setHandler(deliveryTracker.completer());

            } else {

                creditTracker.complete();
                senderFuture.result().send(
                        deviceId,
                        properties,
                        sampler.getData(),
                        sampler.getContentType(),
                        token).setHandler(deliveryTracker.completer());
            }

            CompositeFuture.all(deliveryTracker, creditTracker).setHandler(send -> {
                if (send.succeeded()) {
                    sampleResult.setResponseMessage(MessageFormat.format("{0}/{1}/{2}", sampler.getEndpoint(), sampler.getTenant(), deviceId));
                    sampleResult.setSentBytes(payload.length);
                    sampleResult.setSampleCount(1);
                    tracker.complete(sampleResult);
                } else {
                    tracker.completeExceptionally(send.cause());
                }
            });

            return null;

        }).otherwise(t -> {
            tracker.completeExceptionally(new IllegalStateException("sender is not connected"));
            return null;
        });

        try {
            tracker.get(1, TimeUnit.SECONDS);
            LOGGER.debug("{}: sent message for device [{}]", sampler.getThreadName(), deviceId);
        } catch (InterruptedException | CancellationException | ExecutionException | TimeoutException e) {
            sampleResult.setSuccessful(false);
            sampleResult.setResponseMessage(e.getCause() != null ? e.getCause().getMessage() : e.getClass().getSimpleName());
            sampleResult.setResponseCode("500");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
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
