/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.jmeter.HonoSampler;
import org.eclipse.hono.jmeter.HonoSenderSampler;
import org.eclipse.hono.util.JwtHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A wrapper around a {@code HonoClient} mapping the client's asynchronous API to the blocking
 * threading model used by JMeter.
 */
public class HonoSender extends AbstractClient {

    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSender.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final HonoSenderSampler sampler;
    private final HonoClient honoClient;
    private final byte[] payload;
    private final Context ctx;

    private HonoClient registrationHonoClient;
    private String assertion;
    private Instant assertionExpiration;

    /**
     * Creates a new sender for configuration properties.
     *
     * @param sampler The configuration properties.
     */
    public HonoSender(final HonoSenderSampler sampler) {

        super();
        this.ctx = vertx.getOrCreateContext();
        this.sampler = sampler;
        this.payload = sampler.getData().getBytes(StandardCharsets.UTF_8);

        // hono config
        final ClientConfigProperties honoProps = new ClientConfigProperties();
        honoProps.setHostnameVerificationRequired(false);
        honoProps.setHost(sampler.getHost());
        honoProps.setPort(sampler.getPortAsInt());
        honoProps.setName(sampler.getContainer());
        honoProps.setUsername(sampler.getUser());
        honoProps.setPassword(sampler.getPwd());
        honoProps.setTrustStorePath(sampler.getTrustStorePath());
        honoProps.setReconnectAttempts(MAX_RECONNECT_ATTEMPTS);
        honoClient = new HonoClientImpl(vertx, honoProps);

        final String registryHost = sampler.getRegistryHost();
        final String staticAssertion = sampler.getRegistrationAssertion();
        if ((registryHost != null && registryHost.length() > 0) &&
                (staticAssertion == null || staticAssertion.length() == 0)) {
            // only connect to Device Registration service if no static assertion token
            // has been configured
            final ClientConfigProperties registryProps = new ClientConfigProperties();
            registryProps.setHostnameVerificationRequired(false);
            registryProps.setHost(registryHost);
            registryProps.setPort(sampler.getRegistryPortAsInt());
            registryProps.setName(sampler.getContainer());
            registryProps.setUsername(sampler.getRegistryUser());
            registryProps.setPassword(sampler.getRegistryPwd());
            registryProps.setTrustStorePath(sampler.getRegistryTrustStorePath());
            registryProps.setReconnectAttempts(MAX_RECONNECT_ATTEMPTS);
            registrationHonoClient = new HonoClientImpl(vertx, registryProps);
        } else {
            LOGGER.info("Registration Service host is not set, will use static token from Registration Assertion config option");
        }
    }

    /**
     * Starts this sender.
     * <p>
     * As part of the startup the sender connects to Hono Messaging and the
     * Device Registration service.
     *
     * @return A future indicating the outcome of the startup process.
     */
    public CompletableFuture<Void> start() {

        final CompletableFuture<Void> result = new CompletableFuture<>();
        final String tenant = sampler.getTenant();
        if (running.compareAndSet(false, true)) {

            ctx.runOnContext(start -> {
                LOGGER.debug("create hono sender - tenant: {}", sampler.getTenant());

                CompositeFuture.all(connectToHonoMessaging(), connectToRegistrationService())
                    .setHandler(startup -> {
                        if (startup.succeeded()) {
                            LOGGER.info("sender initialization complete");
                            LOGGER.debug("sender active: {}/{} ({})", sampler.getEndpoint(), tenant,
                                    Thread.currentThread().getName());
                            result.complete(null);
                        } else {
                            running.set(false);
                            result.completeExceptionally(startup.cause());
                        }
                    });
            });
        } else {
            result.completeExceptionally(new IllegalStateException("sender is already starting"));
        }
        return result;
    }

    private Future<HonoClient> connectToHonoMessaging() {

        return honoClient
                .connect(getClientOptions())
                .map(client -> {
                    LOGGER.info("connected to Hono Messaging [{}:{}]", sampler.getHost(), sampler.getPort());
                    return client;
                });
    }

    private Future<HonoClient> connectToRegistrationService() {

        if (registrationHonoClient == null) {
            LOGGER.info("no client for Registration Service configured");
            return Future.succeededFuture(null);
        } else {
            return registrationHonoClient
                    .connect(getClientOptions())
                    .map(client -> {
                        LOGGER.info("connected to Device Registration service [{}:{}]", sampler.getRegistryHost(), sampler.getRegistryPort());
                        return client;
                    });
        }
    }

    private Future<RegistrationClient> getRegistrationClient(final String tenant) {

        return registrationHonoClient.getOrCreateRegistrationClient(tenant);
    }

    private Future<MessageSender> getSender(final String endpoint, final String tenant) {

        if (endpoint.equals(HonoSampler.Endpoint.telemetry.toString())) {
            LOGGER.trace("getting telemetry sender for tenant [{}]", tenant);
            return honoClient.getOrCreateTelemetrySender(tenant);
        } else {
            LOGGER.trace("getting event sender for tenant [{}]", tenant);
            return honoClient.getOrCreateEventSender(tenant);
        }
    }

    private Future<String> getRegistrationAssertion(final String tenant, final String deviceId) {

        final String registrationAssertion = sampler.getRegistrationAssertion();
        if (registrationAssertion != null && registrationAssertion.length() > 0) {
            return Future.succeededFuture(registrationAssertion);
        } else if (assertion != null && Instant.now().isBefore(assertionExpiration)) {
            return Future.succeededFuture(assertion);
        } else {
            return getRegistrationClient(tenant)
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
     * Publishes multiple messages to Hono.
     *
     * @param sampleResult The result object representing the combined outcome of the samples.
     * @param messageCount The number of messages to send
     * @param deviceId The identifier if the device to send a message for.
     * @param waitForDeliveryResult A flag indicating whether to wait for the result of the send operation.
     */
    public void send(final SampleResult sampleResult, final int messageCount, final String deviceId,
                     final boolean waitForDeliveryResult) {
        final long sampleStart = System.currentTimeMillis();
        long addedSendDurations = 0;
        boolean isSuccessful = true;
        String firstResponseErrorMessage = "";
        String firstResponseErrorCode = "";
        long sentBytes = 0;
        int errorCount = 0;
        for (int i = 0; i < messageCount; i++) {
            final SampleResult subResult = new SampleResult();
            subResult.setDataType(SampleResult.TEXT);
            subResult.setResponseOK();
            subResult.setResponseCodeOK();
            subResult.setSampleLabel(sampleResult.getSampleLabel());
            // send the message
            send(subResult, deviceId, waitForDeliveryResult);
            // can't call sampleResult.addSubResult(subResult) here - this would prevent a later invocation of sampleResult.setStampAndTime()
            sampleResult.addRawSubResult(subResult);

            if (!subResult.isSuccessful()) {
                isSuccessful = false;
                errorCount++;
                if (firstResponseErrorMessage.isEmpty()) {
                    firstResponseErrorMessage = subResult.getResponseMessage();
                    firstResponseErrorCode = subResult.getResponseCode();
                }
            }
            sentBytes += subResult.getSentBytes();
            addedSendDurations += subResult.getTime();
        }
        sampleResult.setSuccessful(isSuccessful);
        final String responseMessage = MessageFormat.format("BatchResult {0}/{1}/{2}", sampler.getEndpoint(), sampler.getTenant(), deviceId);
        if (isSuccessful) {
            sampleResult.setResponseMessage(responseMessage);
        } else {
            sampleResult.setResponseMessage(responseMessage + ": " + errorCount + " errors - first: " + firstResponseErrorMessage);
            sampleResult.setResponseCode(firstResponseErrorCode);
        }
        sampleResult.setSentBytes(sentBytes);
        sampleResult.setSampleCount(messageCount);
        sampleResult.setErrorCount(errorCount); // NOTE: This method does nothing in JMeter 3.3/4.0
        final long averageElapsedTimePerMessage = addedSendDurations / messageCount;
        sampleResult.setStampAndTime(sampleStart, averageElapsedTimePerMessage);
    }

    /**
     * Publishes a message to Hono.
     *
     * @param sampleResult The result object representing the outcome of the sample.
     * @param deviceId The identifier if the device to send a message for.
     * @param waitForDeliveryResult A flag indicating whether to wait for the result of the send operation.
     */
    public void send(final SampleResult sampleResult, final String deviceId, final boolean waitForDeliveryResult) {

        final String endpoint = sampler.getEndpoint();
        final String tenant = sampler.getTenant();

        final Future<MessageSender> senderFuture = getSender(endpoint, tenant);
        final Future<String> regAssertionFuture = senderFuture.compose(ok -> getRegistrationAssertion(tenant, deviceId));
        final CompletableFuture<SampleResult> tracker = new CompletableFuture<>();
        final Future<ProtonDelivery> deliveryTracker = Future.future();
        deliveryTracker.setHandler(s -> {
            if (s.succeeded()) {
                sampleResult.setResponseMessage(MessageFormat.format("{0}/{1}/{2}", endpoint, tenant, deviceId));
                sampleResult.setSentBytes(payload.length);
                sampleResult.setSampleCount(1);
                tracker.complete(sampleResult);
            } else {
                tracker.completeExceptionally(s.cause());
            }
        });

        // start sample
        sampleResult.sampleStart();
        regAssertionFuture.map(token -> {

            final Message msg = ProtonHelper.message();
            msg.setAddress(senderFuture.result().getEndpoint() + "/" + tenant);
            MessageHelper.setPayload(msg, sampler.getContentType(), Buffer.buffer(sampler.getData()));
            MessageHelper.addDeviceId(msg, deviceId);
            MessageHelper.addRegistrationAssertion(msg, token);
            if (sampler.isSetSenderTime()) {
                MessageHelper.addProperty(msg, TIME_STAMP_VARIABLE, System.currentTimeMillis());
            }

            LOGGER.trace("sending message for device [{}]; credit: {}", deviceId, senderFuture.result().getCredit());

            final Handler<Void> sendHandler = s -> {
                if (waitForDeliveryResult) {
                    senderFuture.result().sendAndWaitForOutcome(msg).setHandler(deliveryTracker);
                } else {
                    senderFuture.result().send(msg).setHandler(ar -> {
                        if (ar.succeeded()) {
                            LOGGER.debug("{}: got delivery result for message sent for device [{}]: remoteState={}, localState={}",
                                    sampler.getThreadName(), deviceId, ar.result().getRemoteState(),
                                    ar.result().getLocalState());
                        } else {
                            LOGGER.warn("{}: error getting delivery result for message sent for device [{}]", sampler.getThreadName(), deviceId, ar.cause());
                        }
                    });
                    deliveryTracker.complete();
                }
            };

            ctx.runOnContext(send -> {
                if (senderFuture.result().getCredit() > 0) {
                    sendHandler.handle(null);
                } else {
                    senderFuture.result().sendQueueDrainHandler(sendHandler);
                }
            });

            return null;

        }).otherwise(t -> {
            tracker.completeExceptionally(t);
            return null;
        });

        try {
            tracker.get(sampler.getSendTimeoutOrDefaultAsInt(), TimeUnit.MILLISECONDS);
            LOGGER.debug("{}: sent message for device [{}]", sampler.getThreadName(), deviceId);
        } catch (InterruptedException | CancellationException | ExecutionException | TimeoutException e) {
            sampleResult.setSuccessful(false);
            if (e.getCause() instanceof ServiceInvocationException) {
                final ServiceInvocationException sie = (ServiceInvocationException) e.getCause();
                sampleResult.setResponseMessage(sie.getMessage());
                sampleResult.setResponseCode(String.valueOf(sie.getErrorCode()));
            } else {
                String uncompletedFutureHint = "";
                if (e instanceof TimeoutException) {
                    uncompletedFutureHint = !senderFuture.isComplete() ? " - timeout waiting for sender link"
                            : !regAssertionFuture.isComplete() ? "- timeout waiting for registration assertion"
                                : !deliveryTracker.isComplete() ? " - timeout waiting for message delivery"
                                    : "";
                }
                sampleResult.setResponseMessage((e.getCause() != null ? e.getCause().getMessage() : e.getClass().getSimpleName()) + uncompletedFutureHint);
                sampleResult.setResponseCode(String.valueOf(HttpURLConnection.HTTP_INTERNAL_ERROR));
            }
            LOGGER.debug("{}: error sending message for device [{}]: {}", sampler.getThreadName(), deviceId, sampleResult.getResponseMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        sampleResult.sampleEnd();
    }

    /**
     * Closes the connections to the Device Registration Service and Hono Messaging.
     *
     * @return A future that successfully completes once the connections are closed.
     */
    public CompletableFuture<Void> close() {

        final CompletableFuture<Void> shutdown = new CompletableFuture<>();

        if (running.compareAndSet(true, false)) {

            final Future<Void> honoTracker = Future.future();
            if (honoClient != null) {
                honoClient.shutdown(honoTracker.completer());
            } else {
                honoTracker.complete();
            }
            final Future<Void> registrationServiceTracker = Future.future();
            if (registrationHonoClient != null) {
                registrationHonoClient.shutdown(registrationServiceTracker.completer());
            } else {
                registrationServiceTracker.complete();
            }

            CompositeFuture.all(
                    honoTracker.otherwiseEmpty(),
                    registrationServiceTracker.otherwiseEmpty()
                    ).compose(ok -> closeVertx()).setHandler(done -> shutdown.complete(null));
        } else {
            LOGGER.debug("sender already stopped");
            shutdown.complete(null);
        }
        return shutdown;
    }
}
