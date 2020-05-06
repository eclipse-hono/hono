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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.jmeter.HonoCommanderSampler;
import org.eclipse.hono.jmeter.sampleresult.HonoCommanderSampleResult;
import org.eclipse.hono.util.MessageTap;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;

/**
 * Hono commander, which creates command applicationClientFactory, send commands to devices and receive responses from devices;
 * asynchronous API needs to be used synchronous for JMeters threading model.
 */
public class HonoCommander extends AbstractClient {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HonoCommander.class);
    private final ApplicationClientFactory applicationClientFactory;
    private final HonoCommanderSampler sampler;
    private final List<String> devicesReadyToReceiveCommands = new CopyOnWriteArrayList<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);
    private final AtomicLong sampleStart = new AtomicLong(0);
    private final String tenant;
    private final int commandTimeoutInMs;
    private final String triggerType;
    private final transient Object lock = new Object();

    /**
     * Creates a new sampler to send commands to devices and receive command responses.
     *
     * @param sampler The sampler configuration.
     */
    public HonoCommander(final HonoCommanderSampler sampler) {
        super();
        this.sampler = sampler;
        final ClientConfigProperties clientConfig = new ClientConfigProperties();
        clientConfig.setHostnameVerificationRequired(false);
        clientConfig.setHost(sampler.getHost());
        clientConfig.setPort(sampler.getPortAsInt());
        clientConfig.setName(sampler.getContainer());
        clientConfig.setUsername(sampler.getUser());
        clientConfig.setPassword(sampler.getPwd());
        clientConfig.setTrustStorePath(sampler.getTrustStorePath());
        clientConfig.setReconnectAttempts(sampler.getReconnectAttemptsAsInt());
        tenant = sampler.getTenant();
        commandTimeoutInMs = sampler.getCommandTimeoutAsInt();
        triggerType = sampler.getTriggerType();
        applicationClientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, clientConfig));
    }

    /**
     * Starts this Commander.
     *
     * @return A future indicating the outcome of the startup process.
     */
    public CompletableFuture<Void> start() {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        connect()
                .compose(x -> createMessageConsumers())
                .onComplete(connectionStatus -> {
                    if (connectionStatus.succeeded()) {
                        result.complete(null);
                    } else {
                        LOG.error("Error connecting to Hono. {}", connectionStatus.cause().getMessage());
                        vertx.close();
                        result.completeExceptionally(connectionStatus.cause());
                    }
                });
        return result;
    }

    /**
     *
     * @param result The result object representing the combined outcome of the samples.
     */
    public void sample(final HonoCommanderSampleResult result) {
        synchronized (lock) {
            LOG.debug("Chosen trigger type: {}", sampler.getTriggerType());
            if ("device".equals(sampler.getTriggerType())) {
                setSampleStartAndEndTime(result);
            } else {
                result.sampleStart();
                devicesReadyToReceiveCommands
                        .parallelStream()
                        .forEach(device -> sendCommandAndReceiveResponse(tenant, device));
                result.sampleEnd();
            }
            final int noOfErrors = errorCount.getAndSet(0);
            final int noOfSuccess = successCount.getAndSet(0);
            final int noOfSamples = noOfSuccess + noOfErrors;
            final long noOfBytesSent = bytesSent.getAndSet(0);
            final long noOfBytesReceived = bytesReceived.getAndSet(0);
            result.setSampleCount(noOfSamples);
            result.setErrorCount(noOfErrors);
            result.setSentBytes(noOfBytesSent);
            result.setBytes(noOfBytesReceived);
            result.setResponseOK();
            result.setResponseCodeOK();
            LOG.debug("Commands sent: {}, Response received: {}, Error: {}", noOfSamples,
                    noOfSuccess, noOfErrors);
        }

    }

    /**
     * Closes the connections to the Device Registration Service and the AMQP Messaging Network.
     *
     * @return A future that successfully completes once the connections are closed.
     */
    public CompletableFuture<Void> close() {
        final CompletableFuture<Void> shutdownTracker = new CompletableFuture<>();
        final Promise<Void> clientTracker = Promise.promise();
        LOG.debug("Clean resources...");
        applicationClientFactory.disconnect(clientTracker);
        clientTracker.future()
                .compose(ok -> closeVertx())
                .recover(error -> closeVertx())
                .onComplete(result -> shutdownTracker.complete(null));
        return shutdownTracker;
    }

    private Future<HonoConnection> connect() {
        return applicationClientFactory
                .connect()
                .map(client -> {
                    LOG.info("connected to Hono [{}:{}]", sampler.getHost(), sampler.getPort());
                    return client;
                });
    }

    private Future<MessageConsumer> createMessageConsumers() {
        return applicationClientFactory
                .createEventConsumer(tenant,
                        MessageTap.getConsumer(message -> handleMessage("Event", message),
                                this::handleCommandReadinessNotification),
                        closeHook -> LOG.error("remotely detached consumer link"))
                .compose(consumer -> applicationClientFactory.createTelemetryConsumer(tenant,
                        MessageTap.getConsumer(message -> handleMessage("Telemetry", message),
                                this::handleCommandReadinessNotification),
                        closeHook -> LOG.error("remotely detached consumer link")))
                .map(consumer -> {
                    LOG.info("Ready to receive command readiness notifications for tenant [{}]", tenant);
                    return consumer;
                })
                .recover(Future::failedFuture);
    }

    private void handleMessage(final String msgType, final Message msg) {
        final Data body = (Data) msg.getBody();
        LOG.debug("Type: [{}] and Message: [{}]", msgType, body != null ? body.getValue().toString() : "");
    }

    private void handleCommandReadinessNotification(final TimeUntilDisconnectNotification notification) {
        if (notification.getMillisecondsUntilExpiry() == 0) {
            LOG.trace("Device [{}:{}] not ready to receive commands.", notification.getTenantId(),
                    notification.getDeviceId());
            if ("sampler".equals(triggerType)) {
                devicesReadyToReceiveCommands.remove(notification.getDeviceId());
            }
        } else {
            if ("device".equals(triggerType)) {
                LOG.debug("Sending command to device [{}:{}]", notification.getTenantId(), notification.getDeviceId());
                setSampleStartIfNotSetYet(System.currentTimeMillis());
                sendCommandAndReceiveResponse(notification.getTenantId(), notification.getDeviceId());
            } else {
                LOG.debug("A device [{}] got registered with trigger type [{}]", notification.getDeviceId(),
                        triggerType);
                devicesReadyToReceiveCommands.add(notification.getDeviceId());
            }
        }
    }

    private void sendCommandAndReceiveResponse(final String tenantId, final String deviceId) {
        applicationClientFactory.getOrCreateCommandClient(tenantId)
                .map(this::setCommandTimeOut)
                .compose(commandClient -> commandClient
                        .sendCommand(deviceId, sampler.getCommand(), Buffer.buffer(sampler.getCommandPayload()))
                        .map(commandResponse -> {
                            final String commandResponseText = Optional.ofNullable(commandResponse.getPayload())
                                    .orElse(Buffer.buffer()).toString();
                            LOG.debug("Command response from device [{}:{}] received [{}]", tenantId,
                                    deviceId, commandResponseText);
                            synchronized (lock) {
                                successCount.incrementAndGet();
                                bytesReceived.addAndGet(sampler.getCommandPayload().getBytes().length);
                                bytesSent.addAndGet(commandResponseText.getBytes().length);
                            }
                            return commandResponse;
                        })
                        .map(x -> closeCommandClient(commandClient, tenantId, deviceId))
                        .recover(error -> {
                            if (triggerType.equals("device")
                                    || devicesReadyToReceiveCommands.contains(deviceId)) {
                                errorCount.incrementAndGet();
                            }
                            LOG.error("Error processing command [{}] to device [{}:{}]", error.getMessage(), tenantId,
                                    deviceId);
                            closeCommandClient(commandClient, tenantId, deviceId);
                            return Future.failedFuture(error);
                        }));
    }

    private CommandClient setCommandTimeOut(final CommandClient commandClient) {
        commandClient.setRequestTimeout(commandTimeoutInMs);
        return commandClient;
    }

    private Void closeCommandClient(final CommandClient commandClient, final String tenantId, final String deviceId) {
        commandClient
                .close(closeHandler -> LOG.debug("CommandClient to device [{}:{}] is closed.", tenantId, deviceId));
        return null;
    }

    private void setSampleStartAndEndTime(final SampleResult result) {
        if (sampleStart.get() == 0) {
            LOG.debug("SampleStart hasn't been set or no messages received");
            result.setStampAndTime(System.currentTimeMillis(), 0);
        } else {
            final long startTime = sampleStart.getAndSet(0);
            result.setStampAndTime(startTime, System.currentTimeMillis() - startTime);
        }
    }

    private void setSampleStartIfNotSetYet(final long timestamp) {
        sampleStart.compareAndSet(0, timestamp);
    }
}
