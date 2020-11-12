/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.command.amqp;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.impl.AbstractRequestResponseClient;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandRouterConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for Hono's Command Router API, scoped to a specific tenant.
 *
 */
class ProtonBasedTenantCommandRouterClient extends AbstractRequestResponseClient<RequestResponseResult<JsonObject>> {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedTenantCommandRouterClient.class);

    /**
     * Creates a new client for accessing the Command Router service.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     *
     * @param connection The connection to the Command Router service.
     * @param tenantId The identifier of the tenant for which the client should be created.
     * @param sampler The sampler to use.
     */
    protected ProtonBasedTenantCommandRouterClient(final HonoConnection connection, final String tenantId, final SendMessageSampler sampler) {
        super(connection, tenantId, sampler);
    }

    /**
     * Creates a new client for accessing the Command Router service.
     *
     * @param connection The connection to the Command Router service.
     * @param tenantId The identifier of the tenant for which the client should be created.
     * @param sender The AMQP link to use for sending requests to the service.
     * @param receiver The AMQP link to use for receiving responses from the service.
     * @param sampler The sampler to use.
     */
    protected ProtonBasedTenantCommandRouterClient(
            final HonoConnection connection,
            final String tenantId,
            final ProtonSender sender,
            final ProtonReceiver receiver,
            final SendMessageSampler sampler) {

        super(connection, tenantId, sender, receiver, sampler);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Command Router API endpoint.
     *
     * @param tenantId The tenant to upload data for.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static final String getTargetAddress(final String tenantId) {
        return String.format("%s/%s", CommandRouterConstants.COMMAND_ROUTER_ENDPOINT, Objects.requireNonNull(tenantId));
    }

    @Override
    protected final String getName() {
        return CommandRouterConstants.COMMAND_ROUTER_ENDPOINT;
    }

    @Override
    protected final String createMessageId() {
        return String.format("cmd-router-client-%s", UUID.randomUUID());
    }

    @Override
    protected final RequestResponseResult<JsonObject> getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        if (payload == null) {
            return new RequestResponseResult<>(status, null, null, applicationProperties);
        } else {
            try {
                // ignoring given cacheDirective param here - command router results shall not be cached
                return new RequestResponseResult<>(status, new JsonObject(payload),
                        CacheDirective.noCacheDirective(), applicationProperties);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Command Router service", e);
                return new RequestResponseResult<>(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null,
                        applicationProperties);
            }
        }
    }

    /**
     * Creates a new command router client for a tenant.
     *
     * @param con The connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param sampler The sampler to use.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static final Future<ProtonBasedTenantCommandRouterClient> create(
            final HonoConnection con,
            final String tenantId,
            final SendMessageSampler sampler,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        LOG.debug("creating new command router client for [{}]", tenantId);
        final ProtonBasedTenantCommandRouterClient client = new ProtonBasedTenantCommandRouterClient(con, tenantId, sampler);
        // no response cache being set on client here - command router results shall not be cached
        return client.createLinks(senderCloseHook, receiverCloseHook)
                .map(ok -> {
                    LOG.debug("successfully created command router client for [{}]", tenantId);
                    return client;
                }).recover(t -> {
                    LOG.debug("failed to create command router client for [{}]", tenantId, t);
                    return Future.failedFuture(t);
                });
    }

    private Map<String, Object> createDeviceIdProperties(final String deviceId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        return properties;
    }

    /**
     * Invokes the <em>Set Last Known Gateway for Device</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/command-router">Command Router API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     *
     * @param deviceId The device id.
     * @param gatewayId The gateway id (or the device id if the last message came from the device directly).
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating whether the operation succeeded or not.
     * @throws NullPointerException if device id or gateway id is {@code null}.
     */
    public Future<Void> setLastKnownGatewayForDevice(final String deviceId, final String gatewayId, final SpanContext context) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);

        // using FollowsFrom instead of ChildOf reference here as invoking methods usually don't depend and wait on the result of this method
        final Span currentSpan = newFollowingSpan(context, "set last known gateway for device");
        TracingHelper.setDeviceTags(currentSpan, getTenantId(), deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
        final Promise<RequestResponseResult<JsonObject>> resultTracker = Promise.promise();
        createAndSendRequest(
                CommandRouterConstants.CommandRouterAction.SET_LAST_KNOWN_GATEWAY.getSubject(),
                properties,
                null,
                null,
                resultTracker,
                null,
                currentSpan);
        return mapResultAndFinishSpan(
                resultTracker.future(),
                result -> {
                    switch (result.getStatus()) {
                    case HttpURLConnection.HTTP_NO_CONTENT:
                        return null;
                    default:
                        throw StatusCodeMapper.from(result);
                    }
                },
                currentSpan);
    }

    /**
     * Unregisters a command consumer for a device.
     * <p>
     * The registration entry is only deleted if its value contains the given protocol adapter instance id.
     *
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id that the entry to be removed has to contain.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the entry was successfully removed.
     *         Otherwise the future will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if device id or adapter instance id is {@code null}.
     */
    public Future<Void> unregisterCommandConsumer(final String deviceId, final String adapterInstanceId, final SpanContext context) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);

        final Span currentSpan = newChildSpan(context, "unregister command consumer");
        TracingHelper.setDeviceTags(currentSpan, getTenantId(), deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        final Promise<RequestResponseResult<JsonObject>> resultTracker = Promise.promise();
        createAndSendRequest(
                CommandRouterConstants.CommandRouterAction.UNREGISTER_COMMAND_CONSUMER.getSubject(),
                properties,
                null,
                null,
                resultTracker,
                null,
                currentSpan);
        // not using mapResultAndFinishSpan() in order to skip logging PRECON_FAILED result as error (may have been trying to remove an expired entry)
        return resultTracker.future().recover(t -> {
            Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
            TracingHelper.logError(currentSpan, t);
            currentSpan.finish();
            return Future.failedFuture(t);
        }).map(resultValue -> {
            Tags.HTTP_STATUS.set(currentSpan, resultValue.getStatus());
            if (resultValue.isError() && resultValue.getStatus() != HttpURLConnection.HTTP_PRECON_FAILED) {
                Tags.ERROR.set(currentSpan, Boolean.TRUE);
            }
            currentSpan.finish();

            switch (resultValue.getStatus()) {
            case HttpURLConnection.HTTP_NO_CONTENT:
                return null;
            default:
                throw StatusCodeMapper.from(resultValue);
            }
        });
    }

    /**
     * Registers a protocol adapter instance as the consumer of command &amp; control messages
     * for a device.
     *
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param lifespan The lifespan of the registration entry. Using a negative duration or {@code null} here is
     *                 interpreted as an unlimited lifespan. Only the number of seconds in the given duration
     *                 will be taken into account.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating whether the operation succeeded or not.
     * @throws NullPointerException if device id or adapter instance id is {@code null}.
     */
    public Future<Void> registerCommandConsumer(final String deviceId, final String adapterInstanceId,
            final Duration lifespan, final SpanContext context) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        final int lifespanSeconds = lifespan != null && lifespan.getSeconds() <= Integer.MAX_VALUE ? (int) lifespan.getSeconds() : -1;
        final Map<String, Object> properties = createDeviceIdProperties(deviceId);
        properties.put(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        properties.put(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);

        final Span currentSpan = newChildSpan(context, "register command consumer");
        TracingHelper.setDeviceTags(currentSpan, getTenantId(), deviceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_LIFESPAN, lifespanSeconds);
        final Promise<RequestResponseResult<JsonObject>> resultTracker = Promise.promise();
        createAndSendRequest(
                CommandRouterConstants.CommandRouterAction.REGISTER_COMMAND_CONSUMER.getSubject(),
                properties,
                null,
                null,
                resultTracker,
                null,
                currentSpan);
        return mapResultAndFinishSpan(resultTracker.future(), result -> {
            switch (result.getStatus()) {
                case HttpURLConnection.HTTP_NO_CONTENT:
                    return null;
                default:
                    throw StatusCodeMapper.from(result);
            }
        }, currentSpan);
    }

    // used for testing
    final void doHandleResponse(final ProtonDelivery delivery, final Message message) {
        handleResponse(delivery, message);
    }
}
