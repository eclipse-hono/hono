/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Timer;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

/**
 * The endpoint representing a connected MQTT device.
 *
 * @param <T> The type of properties used.
 */
public final class MqttDeviceEndpoint<T extends MqttProtocolAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(MqttDeviceEndpoint.class);

    private static final String LOG_FIELD_TOPIC_FILTER = "filter";

    private final AbstractVertxBasedMqttProtocolAdapter<T> mqttProtocolAdapter;
    private final MqttEndpoint endpoint;
    /**
     * The authenticated identity of the device or {@code null} if the device has not been authenticated.
     */
    private final Device authenticatedDevice;
    private final OptionalInt traceSamplingPriority;
    private final Map<Subscription.Key, Pair<CommandSubscription, CommandConsumer>> commandSubscriptions = new ConcurrentHashMap<>();
    private final Map<Subscription.Key, ErrorSubscription> errorSubscriptions = new HashMap<>();
    private final PendingPubAcks pendingAcks;

    private Throwable protocolLevelException;

    /**
     * Creates a new MqttDeviceEndpoint.
     *
     * @param mqttProtocolAdapter The protocol adapter instance.
     * @param endpoint The endpoint representing the connection to the device.
     * @param authenticatedDevice The authenticated identity of the device or {@code null} if the device has not been
     *            authenticated.
     * @param traceSamplingPriority The sampling priority to be applied on the <em>OpenTracing</em> spans created in the
     *            context of this endpoint.
     * @throws NullPointerException if any of the parameters except authenticatedDevice is {@code null}.
     */
    public MqttDeviceEndpoint(final AbstractVertxBasedMqttProtocolAdapter<T> mqttProtocolAdapter,
            final MqttEndpoint endpoint, final Device authenticatedDevice, final OptionalInt traceSamplingPriority) {
        this.mqttProtocolAdapter = Objects.requireNonNull(mqttProtocolAdapter);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.authenticatedDevice = authenticatedDevice;
        this.traceSamplingPriority = Objects.requireNonNull(traceSamplingPriority);

        this.pendingAcks = new PendingPubAcks(mqttProtocolAdapter.getVertx());
        registerHandlers();
    }

    private void registerHandlers() {
        endpoint.publishHandler(this::handlePublishedMessage);
        endpoint.publishAcknowledgeHandler(this::handlePubAck);
        endpoint.subscribeHandler(this::onSubscribe);
        endpoint.unsubscribeHandler(this::onUnsubscribe);
        endpoint.closeHandler(v -> onClose());
        endpoint.exceptionHandler(this::onProtocolLevelError);
    }

    /**
     * Gets the authenticated device.
     *
     * @return The authenticated device or {@code null}.
     */
    public Device getAuthenticatedDevice() {
        return authenticatedDevice;
    }

    /**
     * To be called by the MqttEndpoint exceptionHandler. The given exception could for example be a
     * {@code io.netty.handler.codec.TooLongFrameException} if the MQTT max message size was exceeded.
     * The MQTT server will close the connection on such an error, triggering a subsequent invocation of
     * the endpoint closeHandler.
     *
     * @param throwable The exception.
     */
    private void onProtocolLevelError(final Throwable throwable) {
        if (authenticatedDevice == null) {
            LOG.debug("protocol-level exception occurred [client ID: {}]", endpoint.clientIdentifier(), throwable);
        } else {
            LOG.debug("protocol-level exception occurred [tenant-id: {}, device-id: {}, client ID: {}]",
                    authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId(),
                    endpoint.clientIdentifier(), throwable);
        }
        this.protocolLevelException = throwable;
    }

    /**
     * Invoked when a device sends an MQTT <em>PUBLISH</em> packet.
     *
     * @param message The message received from the device.
     * @throws NullPointerException if message is {@code null}.
     */
    void handlePublishedMessage(final MqttPublishMessage message) {
        Objects.requireNonNull(message);
        // try to extract a SpanContext from the property bag of the message's topic (if set)
        final SpanContext spanContext = Optional.ofNullable(message.topicName())
                .flatMap(topic -> Optional.ofNullable(PropertyBag.fromTopic(message.topicName())))
                .map(propertyBag -> TracingHelper.extractSpanContext(mqttProtocolAdapter.getTracer(),
                        propertyBag::getPropertiesIterator))
                .orElse(null);
        final Span span = newChildSpan(spanContext, "PUBLISH");
        span.setTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), message.topicName());
        span.setTag(TracingHelper.TAG_QOS.getKey(), message.qosLevel().toString());
        traceSamplingPriority.ifPresent(prio -> TracingHelper.setTraceSamplingPriority(span, prio));

        final MqttContext context = MqttContext.fromPublishPacket(message, endpoint, span, authenticatedDevice);
        context.setTimer(mqttProtocolAdapter.getMetrics().startTimer());

        final Future<Void> spanPreparationFuture = authenticatedDevice == null
                ? applyTraceSamplingPriorityForTopicTenant(context.topic(), span)
                : Future.succeededFuture();

        spanPreparationFuture
                .compose(v -> checkTopic(context))
                .compose(ok -> mqttProtocolAdapter.onPublishedMessage(context))
                .onSuccess(ok -> {
                    Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_ACCEPTED);
                    mqttProtocolAdapter.onMessageSent(context);
                    span.finish();
                })
                .onFailure(error -> handlePublishedMessageError(context, error, span));
    }

    private void handlePublishedMessageError(final MqttContext context, final Throwable error, final Span span) {
        final ErrorSubscription errorSubscription = getErrorSubscription(context);
        final Future<Void> errorSentIfNeededFuture = Optional.ofNullable(errorSubscription)
                .map(v -> publishError(errorSubscription, context, error, span.context()))
                .orElseGet(Future::succeededFuture);

        Tags.HTTP_STATUS.set(span, ServiceInvocationException.extractStatusCode(error));
        TracingHelper.logError(span, error);

        if (!(error instanceof ClientErrorException)) {
            mqttProtocolAdapter.onMessageUndeliverable(context);
        }

        errorSentIfNeededFuture
                .compose(ok -> mqttProtocolAdapter.isTerminalError(error, context.deviceId(), authenticatedDevice,
                        span.context()))
                .onComplete(ar -> {
                    final boolean isTerminalError = ar.succeeded() ? ar.result() : false;
                    final MqttContext.ErrorHandlingMode errorHandlingMode = context
                            .getErrorHandlingMode(errorSubscription != null);

                    if (errorHandlingMode == MqttContext.ErrorHandlingMode.DISCONNECT || isTerminalError) {
                        if (context.deviceEndpoint().isConnected()) {
                            span.log("closing connection to device");
                            context.deviceEndpoint().close();
                        }
                    } else if (context.isAtLeastOnce()) {
                        if (errorHandlingMode == MqttContext.ErrorHandlingMode.SKIP_ACK) {
                            span.log("skipped sending PUBACK");
                        } else if (context.deviceEndpoint().isConnected()) {
                            span.log("sending PUBACK");
                            context.acknowledge();
                        }
                    }
                    span.finish();
                });
    }

    /**
     * Applies the trace sampling priority configured for the tenant derived from the given topic on the given span.
     * <p>
     * This is for unauthenticated MQTT connections where the tenant id gets taken from the message topic.
     *
     * @param topic The topic (may be {@code null}).
     * @param span The <em>OpenTracing</em> span to apply the configuration to.
     * @return A succeeded future indicating the outcome of the operation. A failure to determine the tenant is ignored
     *         here.
     * @throws NullPointerException if span is {@code null}.
     */
    private Future<Void> applyTraceSamplingPriorityForTopicTenant(final ResourceIdentifier topic,
            final Span span) {
        Objects.requireNonNull(span);
        if (topic == null || topic.getTenantId() == null) {
            // an invalid address is ignored here
            return Future.succeededFuture();
        }
        return mqttProtocolAdapter.getTenantConfiguration(topic.getTenantId(), span.context())
                .map(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantObject.getTenantId(), null);
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, null, span);
                    return (Void) null;
                })
                .recover(t -> Future.succeededFuture());
    }

    private Future<Void> checkTopic(final MqttContext context) {
        if (context.topic() == null) {
            return Future.failedFuture(
                    new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed topic name"));
        } else {
            return Future.succeededFuture();
        }
    }

    private ErrorSubscription getErrorSubscription(final MqttContext context) {
        ErrorSubscription result = null;
        if (context.tenant() != null && context.deviceId() != null) {
            // first check for a subscription with the device id taken from the topic or the authenticated device
            // (handles case where a gateway is publishing for specific edge devices and has individual error subscriptions for them)
            result = errorSubscriptions.get(ErrorSubscription.getKey(context.tenant(), context.deviceId()));
        }
        if (result == null && context.authenticatedDevice() != null) {
            // otherwise check using the authenticated device (ie. potentially gateway) id
            result = errorSubscriptions
                    .get(ErrorSubscription.getKey(context.tenant(), context.authenticatedDevice().getDeviceId()));
        }
        return result;
    }

    /**
     * Invoked when a device sends an MQTT <em>PUBACK</em> packet.
     *
     * @param msgId The message/packet id.
     * @throws NullPointerException if msgId is {@code null}.
     */
    private void handlePubAck(final Integer msgId) {
        Objects.requireNonNull(msgId);
        pendingAcks.handlePubAck(msgId);
    }

    /**
     * Invoked when a device sends an MQTT <em>SUBSCRIBE</em> packet.
     * <p>
     * This method supports topic filters for subscribing to commands
     * and error messages as defined by Hono's
     * <a href="https://www.eclipse.org/hono/docs/user-guide/mqtt-adapter/#command-control">
     * MQTT adapter user guide</a>.
     * <p>
     * When a device subscribes to a command topic filter, this method opens a
     * command consumer for receiving commands from applications for the device and
     * sends an empty notification downstream, indicating that the device will be
     * ready to receive commands until further notice.
     *
     * @param subscribeMsg The subscribe request received from the device.
     * @throws NullPointerException if subscribeMsg is {@code null}.
     */
    void onSubscribe(final MqttSubscribeMessage subscribeMsg) {
        Objects.requireNonNull(subscribeMsg);
        final Map<Object, Future<Subscription>> uniqueSubscriptions = new HashMap<>();
        final Deque<Future<Subscription>> subscriptionOutcomes = new ArrayDeque<>(
                subscribeMsg.topicSubscriptions().size());

        final Span span = newSpan("SUBSCRIBE");

        // process in reverse order and skip equivalent subscriptions, meaning the last of such subscriptions shall be used
        // (done with respect to MQTT 3.1.1 spec section 3.8.4, processing multiple filters as if they had been submitted
        //  using multiple separate SUBSCRIBE packets)
        final Deque<MqttTopicSubscription> topicSubscriptions = new LinkedList<>(subscribeMsg.topicSubscriptions());
        topicSubscriptions.descendingIterator().forEachRemaining(mqttTopicSub -> {

            final Future<Subscription> result;
            final Subscription sub = CommandSubscription.hasCommandEndpointPrefix(mqttTopicSub.topicName())
                    ? CommandSubscription.fromTopic(mqttTopicSub, authenticatedDevice)
                    : ErrorSubscription.fromTopic(mqttTopicSub, authenticatedDevice);

            if (sub == null) {
                TracingHelper.logError(span, String.format("unsupported topic filter [%s]", mqttTopicSub.topicName()));
                LOG.debug("cannot create subscription [filter: {}, requested QoS: {}]: unsupported topic filter",
                        mqttTopicSub.topicName(), mqttTopicSub.qualityOfService());
                result = Future.failedFuture(new IllegalArgumentException("unsupported topic filter"));
            } else {
                if (uniqueSubscriptions.containsKey(sub.getKey())) {
                    final Map<String, Object> items = new HashMap<>(3);
                    items.put(Fields.EVENT, "ignoring duplicate subscription");
                    items.put(LOG_FIELD_TOPIC_FILTER, sub.getTopic());
                    items.put("requested QoS", sub.getQos());
                    span.log(items);
                    result = uniqueSubscriptions.get(sub.getKey());
                } else {
                    result = registerSubscription(sub, span);
                    uniqueSubscriptions.put(sub.getKey(), result);
                }
            }
            subscriptionOutcomes.addFirst(result); // add first to get the same order as in the SUBSCRIBE packet
        });

        // wait for all futures to complete before sending SUBACK
        CompositeFuture.join(new ArrayList<>(subscriptionOutcomes)).onComplete(v -> {

            if (endpoint.isConnected()) {
                // return a status code for each topic filter contained in the SUBSCRIBE packet
                final List<MqttQoS> grantedQosLevels = subscriptionOutcomes.stream()
                        .map(future -> future.failed() ? MqttQoS.FAILURE : future.result().getQos())
                        .collect(Collectors.toList());
                endpoint.subscribeAcknowledge(subscribeMsg.messageId(), grantedQosLevels);

                // now that we have informed the device about the outcome,
                // we can send empty notifications for succeeded (distinct) command subscriptions downstream
                subscriptionOutcomes.stream()
                        .filter(subFuture -> subFuture.succeeded() && subFuture.result() instanceof CommandSubscription)
                        .map(subFuture -> subFuture.result().getKey())
                        .distinct()
                        .forEach(cmdSubKey -> {
                            mqttProtocolAdapter.sendConnectedTtdEvent(cmdSubKey.getTenant(), cmdSubKey.getDeviceId(),
                                    authenticatedDevice, span.context());
                        });
            } else {
                TracingHelper.logError(span,
                        "skipped sending command subscription notification - endpoint not connected anymore");
                LOG.debug(
                        "skipped sending command subscription notification - endpoint not connected anymore [tenant-id: {}, device-id: {}]",
                        Optional.ofNullable(authenticatedDevice).map(Device::getTenantId).orElse(""),
                        Optional.ofNullable(authenticatedDevice).map(Device::getDeviceId).orElse(""));
            }
            span.finish();
        });
    }

    private Future<Subscription> registerSubscription(final Subscription sub, final Span span) {
        return (sub instanceof CommandSubscription)
                ? registerCommandSubscription((CommandSubscription) sub, span)
                : registerErrorSubscription((ErrorSubscription) sub, span);
    }

    private Future<Subscription> registerCommandSubscription(final CommandSubscription cmdSub, final Span span) {

        if (MqttQoS.EXACTLY_ONCE.equals(cmdSub.getQos())) {
            TracingHelper.logError(span, String.format("topic filter [%s] with unsupported QoS 2", cmdSub.getTopic()));
            return Future.failedFuture(new IllegalArgumentException("QoS 2 not supported for command subscription"));
        }
        return createCommandConsumer(cmdSub, span)
                .map(consumer -> {
                    cmdSub.logSubscribeSuccess(span);
                    LOG.debug("created subscription [tenant: {}, device: {}, filter: {}, QoS: {}]",
                            cmdSub.getTenant(), cmdSub.getDeviceId(), cmdSub.getTopic(), cmdSub.getQos());
                    final Pair<CommandSubscription, CommandConsumer> existingCmdSub = commandSubscriptions.get(
                            cmdSub.getKey());
                    if (existingCmdSub != null) {
                        span.log(String.format("subscription replaces previous subscription [QoS %s, filter %s]",
                                existingCmdSub.one().getQos(), existingCmdSub.one().getTopic()));
                        LOG.debug("previous subscription [QoS {}, filter {}] is getting replaced",
                                existingCmdSub.one().getQos(), existingCmdSub.one().getTopic());
                    }
                    commandSubscriptions.put(cmdSub.getKey(), Pair.of(cmdSub, consumer));
                    return (Subscription) cmdSub;
                }).recover(t -> {
                    cmdSub.logSubscribeFailure(span, t);
                    LOG.debug("cannot create subscription [tenant: {}, device: {}, filter: {}, requested QoS: {}]",
                            cmdSub.getTenant(), cmdSub.getDeviceId(), cmdSub.getTopic(), cmdSub.getQos(), t);
                    return Future.failedFuture(t);
                });
    }

    private Future<Subscription> registerErrorSubscription(final ErrorSubscription errorSub, final Span span) {

        if (!MqttQoS.AT_MOST_ONCE.equals(errorSub.getQos())) {
            TracingHelper.logError(span, String.format("topic filter [%s] with unsupported QoS %d", errorSub.getTopic(),
                    errorSub.getQos().value()));
            return Future.failedFuture(new IllegalArgumentException(
                    String.format("QoS %d not supported for error subscription", errorSub.getQos().value())));
        }
        return Future.succeededFuture()
                .compose(v -> {
                    // in case of a gateway having subscribed for a specific device,
                    // check the via-gateways, ensuring that the gateway may act on behalf of the device at this point in time
                    if (errorSub.isGatewaySubscriptionForSpecificDevice()) {
                        return mqttProtocolAdapter.getRegistrationAssertion(
                                authenticatedDevice.getTenantId(),
                                errorSub.getDeviceId(),
                                authenticatedDevice,
                                span.context());
                    }
                    return Future.succeededFuture();
                }).recover(t -> {
                    errorSub.logSubscribeFailure(span, t);
                    LOG.debug("cannot create subscription [tenant: {}, device: {}, filter: {}, requested QoS: {}]",
                            errorSub.getTenant(), errorSub.getDeviceId(), errorSub.getTopic(), errorSub.getQos(), t);
                    return Future.failedFuture(t);
                }).compose(v -> {
                    errorSubscriptions.put(errorSub.getKey(), errorSub);
                    errorSub.logSubscribeSuccess(span);
                    LOG.debug("created subscription [tenant: {}, device: {}, filter: {}, QoS: {}]",
                            errorSub.getTenant(), errorSub.getDeviceId(), errorSub.getTopic(), errorSub.getQos());
                    return Future.succeededFuture(errorSub);
                });
    }

    /**
     * Publishes an error message to the device.
     * <p>
     * Used for an error that occurred in the context of the given MqttContext, while processing an MQTT message
     * published by a device.
     *
     * @param subscription The device's command subscription.
     * @param context The context in which the error occurred.
     * @param error The error exception.
     * @param spanContext The span context.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters except spanContext is {@code null}.
     */
    private Future<Void> publishError(
            final ErrorSubscription subscription,
            final MqttContext context,
            final Throwable error,
            final SpanContext spanContext) {

        Objects.requireNonNull(subscription);
        Objects.requireNonNull(context);
        Objects.requireNonNull(error);

        final Span span = newChildSpan(spanContext, "publish error to device");

        final int errorCode = ServiceInvocationException.extractStatusCode(error);
        final String correlationId = Optional.ofNullable(context.correlationId()).orElse("-1");
        final String publishTopic = subscription.getErrorPublishTopic(context, errorCode);
        Tags.MESSAGE_BUS_DESTINATION.set(span, publishTopic);
        TracingHelper.TAG_QOS.set(span, subscription.getQos().name());

        final JsonObject errorJson = new JsonObject();
        errorJson.put("code", errorCode);
        errorJson.put("message", ServiceInvocationException.getErrorMessageForExternalClient(error));
        errorJson.put("timestamp", ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS)
                .format(DateTimeFormatter.ISO_INSTANT));
        errorJson.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId);

        final String targetInfo =
                authenticatedDevice != null && !authenticatedDevice.getDeviceId().equals(context.deviceId())
                        ?
                        String.format("gateway [%s], device [%s]", authenticatedDevice.getDeviceId(),
                                context.deviceId())
                        :
                        String.format("device [%s]", context.deviceId());

        return publish(publishTopic, errorJson.toBuffer(), subscription.getQos())
                .onSuccess(msgId -> {
                    LOG.debug(
                            "published error message [packet-id: {}] to {} [tenant-id: {}, MQTT client-id: {}, QoS: {}, topic: {}]",
                            msgId, targetInfo, subscription.getTenant(), endpoint.clientIdentifier(),
                            subscription.getQos(), publishTopic);
                    span.log(subscription.getQos().value() > 0 ? "published error message, packet-id: " + msgId
                            : "published error message");
                }).onFailure(thr -> {
                    LOG.debug(
                            "error publishing error message to {} [tenant-id: {}, MQTT client-id: {}, QoS: {}, topic: {}]",
                            targetInfo, subscription.getTenant(), endpoint.clientIdentifier(),
                            subscription.getQos(), publishTopic, thr);
                    TracingHelper.logError(span, "failed to publish error message", thr);
                }).map(msgId -> (Void) null)
                .onComplete(ar -> span.finish());
    }

    private Future<CommandConsumer> createCommandConsumer(final CommandSubscription subscription, final Span span) {

        final Handler<CommandContext> commandHandler = commandContext -> {

            Tags.COMPONENT.set(commandContext.getTracingSpan(), mqttProtocolAdapter.getTypeName());
            TracingHelper.TAG_CLIENT_ID.set(commandContext.getTracingSpan(), endpoint.clientIdentifier());
            final Timer.Sample timer = mqttProtocolAdapter.getMetrics().startTimer();
            final Command command = commandContext.getCommand();
            final Future<TenantObject> tenantTracker = mqttProtocolAdapter.getTenantConfiguration(
                    subscription.getTenant(), commandContext.getTracingContext());

            tenantTracker.compose(tenantObject -> {
                if (command.isValid()) {
                    return mqttProtocolAdapter.checkMessageLimit(tenantObject, command.getPayloadSize(),
                            commandContext.getTracingContext());
                } else {
                    return Future.failedFuture(
                            new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed command message"));
                }
            }).compose(success -> {
                // in case of a gateway having subscribed for a specific device,
                // check the via-gateways, ensuring that the gateway may act on behalf of the device at this point in time
                if (subscription.isGatewaySubscriptionForSpecificDevice()) {
                    return mqttProtocolAdapter.getRegistrationAssertion(
                            authenticatedDevice.getTenantId(),
                            subscription.getDeviceId(),
                            authenticatedDevice,
                            commandContext.getTracingContext());
                }
                return Future.succeededFuture();
            }).compose(success -> {
                AbstractProtocolAdapterBase.addMicrometerSample(commandContext, timer);
                onCommandReceived(tenantTracker.result(), subscription, commandContext);
                return Future.succeededFuture();
            }).onFailure(t -> {
                if (t instanceof ClientErrorException) {
                    commandContext.reject(t);
                } else {
                    commandContext.release(t);
                }
                mqttProtocolAdapter.getMetrics().reportCommand(
                        command.isOneWay() ? MetricsTags.Direction.ONE_WAY : MetricsTags.Direction.REQUEST,
                        subscription.getTenant(),
                        tenantTracker.result(),
                        MetricsTags.ProcessingOutcome.from(t),
                        command.getPayloadSize(),
                        timer);
            });
        };

        final Future<RegistrationAssertion> tokenTracker = Optional.ofNullable(authenticatedDevice)
                .map(v -> mqttProtocolAdapter.getRegistrationAssertion(
                        authenticatedDevice.getTenantId(),
                        subscription.getDeviceId(),
                        authenticatedDevice,
                        span.context()))
                .orElseGet(Future::succeededFuture);

        if (subscription.isGatewaySubscriptionForSpecificDevice()) {
            // gateway scenario
            return tokenTracker.compose(v -> mqttProtocolAdapter.getCommandConsumerFactory().createCommandConsumer(
                    subscription.getTenant(),
                    subscription.getDeviceId(),
                    subscription.getAuthenticatedDeviceId(),
                    commandHandler,
                    null,
                    span.context()));
        } else {
            return tokenTracker.compose(v -> mqttProtocolAdapter.getCommandConsumerFactory().createCommandConsumer(
                    subscription.getTenant(),
                    subscription.getDeviceId(),
                    commandHandler,
                    null,
                    span.context()));
        }
    }

    /**
     * Called for a command to be delivered to a device.
     *
     * @param tenantObject The tenant configuration object.
     * @param subscription The device's command subscription.
     * @param commandContext The command to be delivered.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    private void onCommandReceived(
            final TenantObject tenantObject,
            final CommandSubscription subscription,
            final CommandContext commandContext) {

        Objects.requireNonNull(tenantObject);
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(commandContext);

        final Command command = commandContext.getCommand();
        final String publishTopic = subscription.getCommandPublishTopic(command);
        Tags.MESSAGE_BUS_DESTINATION.set(commandContext.getTracingSpan(), publishTopic);
        TracingHelper.TAG_QOS.set(commandContext.getTracingSpan(), subscription.getQos().name());

        final String targetInfo = command.isTargetedAtGateway()
                ? String.format("gateway [%s], device [%s]", command.getGatewayId(), command.getDeviceId())
                : String.format("device [%s]", command.getDeviceId());

        mqttProtocolAdapter.getCommandPayload(commandContext)
                .map(mappedPayload -> Optional.ofNullable(mappedPayload).orElseGet(Buffer::buffer))
                .onSuccess(payload -> {
                    publish(publishTopic, payload, subscription.getQos())
                            .onSuccess(msgId -> {
                                LOG.debug(
                                        "published command [packet-id: {}] to {} [tenant-id: {}, MQTT client-id: {}, QoS: {}, topic: {}]",
                                        msgId, targetInfo, subscription.getTenant(), endpoint.clientIdentifier(),
                                        subscription.getQos(), publishTopic);
                                commandContext.getTracingSpan()
                                        .log(subscription.getQos().value() > 0
                                                ? "published command, packet-id: " + msgId
                                                : "published command");
                                afterCommandPublished(msgId, commandContext, tenantObject, subscription);
                            })
                            .onFailure(thr -> {
                                LOG.debug(
                                        "error publishing command to {} [tenant-id: {}, MQTT client-id: {}, QoS: {}, topic: {}]",
                                        targetInfo, subscription.getTenant(), endpoint.clientIdentifier(),
                                        subscription.getQos(), publishTopic, thr);
                                TracingHelper.logError(commandContext.getTracingSpan(), "failed to publish command",
                                        thr);
                                reportPublishedCommand(
                                        tenantObject,
                                        subscription,
                                        commandContext,
                                        MetricsTags.ProcessingOutcome.from(thr));
                                commandContext.release(thr);
                            });
                }).onFailure(t -> {
                    LOG.debug("error mapping command [tenant-id: {}, MQTT client-id: {}, QoS: {}]",
                            subscription.getTenant(), endpoint.clientIdentifier(), subscription.getQos(), t);
                    TracingHelper.logError(commandContext.getTracingSpan(), "failed to map command", t);
                    reportPublishedCommand(
                            tenantObject,
                            subscription,
                            commandContext,
                            MetricsTags.ProcessingOutcome.from(t));
                    commandContext.release(t);
                });
    }

    private Future<Integer> publish(final String topic, final Buffer payload, final MqttQoS qosLevel) {
        final Promise<Integer> publishSentPromise = Promise.promise();
        try {
            endpoint.publish(topic, payload, qosLevel, false, false, publishSentPromise);
        } catch (final Exception e) {
            publishSentPromise.fail(!endpoint.isConnected()
                    ?
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "connection to device already closed")
                    :
                    new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, e));
        }
        return publishSentPromise.future();
    }

    private void afterCommandPublished(
            final Integer publishedMsgId,
            final CommandContext commandContext,
            final TenantObject tenantObject,
            final CommandSubscription subscription) {

        final boolean requiresPuback = MqttQoS.AT_LEAST_ONCE.equals(subscription.getQos());

        if (requiresPuback) {
            final Handler<Integer> onAckHandler = msgId -> {
                reportPublishedCommand(tenantObject, subscription, commandContext,
                        MetricsTags.ProcessingOutcome.FORWARDED);
                LOG.debug(
                        "received PUBACK [packet-id: {}] for command [tenant-id: {}, device-id: {}, MQTT client-id: {}]",
                        msgId, subscription.getTenant(), subscription.getDeviceId(), endpoint.clientIdentifier());
                commandContext.getTracingSpan().log("received PUBACK from device");
                commandContext.accept();
            };

            final Handler<Void> onAckTimeoutHandler = v -> {
                LOG.debug(
                        "did not receive PUBACK [packet-id: {}] for command [tenant-id: {}, device-id: {}, MQTT client-id: {}]",
                        publishedMsgId, subscription.getTenant(), subscription.getDeviceId(),
                        endpoint.clientIdentifier());
                commandContext.release(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                        "did not receive PUBACK from device"));
                reportPublishedCommand(tenantObject, subscription, commandContext,
                        MetricsTags.ProcessingOutcome.UNDELIVERABLE);
            };
            pendingAcks.add(publishedMsgId, onAckHandler, onAckTimeoutHandler,
                    mqttProtocolAdapter.getConfig().getSendMessageToDeviceTimeout());
        } else {
            reportPublishedCommand(tenantObject, subscription, commandContext, MetricsTags.ProcessingOutcome.FORWARDED);
            commandContext.accept();
        }
    }

    private void reportPublishedCommand(
            final TenantObject tenantObject,
            final CommandSubscription subscription,
            final CommandContext commandContext,
            final MetricsTags.ProcessingOutcome outcome) {

        mqttProtocolAdapter.getMetrics().reportCommand(
                commandContext.getCommand().isOneWay() ? MetricsTags.Direction.ONE_WAY : MetricsTags.Direction.REQUEST,
                subscription.getTenant(),
                tenantObject,
                outcome,
                commandContext.getCommand().getPayloadSize(),
                AbstractProtocolAdapterBase.getMicrometerSample(commandContext));
    }

    /**
     * Invoked when a device sends an MQTT <em>UNSUBSCRIBE</em> packet.
     * <p>
     * This method currently only supports topic filters for unsubscribing from
     * commands as defined by Hono's
     * <a href="https://www.eclipse.org/hono/docs/user-guide/mqtt-adapter/#command-control">
     * MQTT adapter user guide</a>.
     *
     * @param unsubscribeMsg The unsubscribe request received from the device.
     * @throws NullPointerException if unsubscribeMsg is {@code null}.
     */
    private void onUnsubscribe(final MqttUnsubscribeMessage unsubscribeMsg) {
        Objects.requireNonNull(unsubscribeMsg);
        final Span span = newSpan("UNSUBSCRIBE");

        @SuppressWarnings("rawtypes")
        final List<Future> removalDoneFutures = new ArrayList<>(unsubscribeMsg.topics().size());
        unsubscribeMsg.topics().forEach(topic -> {

            final AtomicReference<Subscription> removedSubscription = new AtomicReference<>();
            if (CommandSubscription.hasCommandEndpointPrefix(topic)) {
                Optional.ofNullable(CommandSubscription.getKey(topic, authenticatedDevice))
                        .map(commandSubscriptions::remove)
                        .ifPresent(subscriptionCommandConsumerPair -> {
                            final CommandSubscription subscription = subscriptionCommandConsumerPair.one();
                            removedSubscription.set(subscription);
                            subscription.logUnsubscribe(span);
                            removalDoneFutures.add(
                                    onCommandSubscriptionRemoved(subscriptionCommandConsumerPair, span, true));
                        });
            } else if (ErrorSubscription.hasErrorEndpointPrefix(topic)) {
                Optional.ofNullable(ErrorSubscription.getKey(topic, authenticatedDevice))
                        .map(errorSubscriptions::remove)
                        .ifPresent(subscription -> {
                            removedSubscription.set(subscription);
                            subscription.logUnsubscribe(span);
                        });
            }
            if (removedSubscription.get() != null) {
                LOG.debug("removed subscription with topic [{}] for device [tenant-id: {}, device-id: {}]",
                        topic, removedSubscription.get().getTenant(), removedSubscription.get().getDeviceId());
            } else {
                TracingHelper.logError(span, String.format("no subscription found for topic filter [%s]", topic));
                LOG.debug("cannot unsubscribe - no subscription found for topic filter [{}]", topic);
            }
        });
        if (endpoint.isConnected()) {
            endpoint.unsubscribeAcknowledge(unsubscribeMsg.messageId());
        }
        CompositeFuture.join(removalDoneFutures).onComplete(r -> span.finish());
    }

    private Future<Void> onCommandSubscriptionRemoved(
            final Pair<CommandSubscription, CommandConsumer> subscriptionConsumerPair,
            final Span span,
            final boolean sendDisconnectedEvent) {

        final CommandSubscription subscription = subscriptionConsumerPair.one();
        final CommandConsumer commandConsumer = subscriptionConsumerPair.two();
        return commandConsumer.close(span.context())
                .recover(thr -> {
                    TracingHelper.logError(span, thr);
                    // ignore all but precon-failed errors
                    if (ServiceInvocationException.extractStatusCode(thr) == HttpURLConnection.HTTP_PRECON_FAILED) {
                        LOG.debug(
                                "command consumer wasn't active anymore - skip sending disconnected event [tenant: {}, device-id: {}]",
                                subscription.getTenant(), subscription.getDeviceId());
                        span.log("command consumer wasn't active anymore - skip sending disconnected event");
                        return Future.failedFuture(thr);
                    }
                    return Future.succeededFuture();
                })
                .compose(v -> {
                    if (sendDisconnectedEvent) {
                        return sendDisconnectedTtdEvent(subscription.getTenant(), subscription.getDeviceId(), span);
                    }
                    return Future.succeededFuture();
                });
    }

    private Future<Void> sendDisconnectedTtdEvent(final String tenant, final String device, final Span span) {
        final Span sendEventSpan = newChildSpan(span.context(), "send Disconnected Event");
        return mqttProtocolAdapter.sendDisconnectedTtdEvent(tenant, device, authenticatedDevice,
                        sendEventSpan.context())
                .onComplete(r -> sendEventSpan.finish()).mapEmpty();
    }

    /**
     * Actively closes the connection to the device.
     *
     * @param reason The reason for closing the connection.
     * @param sendDisconnectedEvent {@code true} if events shall be sent concerning the disconnect.
     * @throws NullPointerException if reason is {@code null}.
     */
    public void close(final String reason, final boolean sendDisconnectedEvent) {
        Objects.requireNonNull(reason);

        final Span span = newSpan("close device connection");
        endpoint.closeHandler(v -> {
        });
        onCloseInternal(span, reason, sendDisconnectedEvent)
                .onComplete(ar -> span.finish());
    }

    /**
     * To be called by the MqttEndpoint closeHandler.
     */
    void onClose() {
        final Span span;
        final String reason;
        if (protocolLevelException != null) {
            span = newSpan("close connection due to protocol error");
            TracingHelper.logError(span, protocolLevelException);
            reason = "protocol error: " + protocolLevelException.toString();
        } else if (mqttProtocolAdapter.stopCalled()) {
            span = newSpan("close device connection (server shutdown)");
            reason = "server shutdown";
        } else {
            span = newSpan("CLOSE");
            reason = null;
        }
        onCloseInternal(span, reason, true)
                .onComplete(ar -> span.finish());
    }

    private Future<Void> onCloseInternal(final Span span, final String reason, final boolean sendDisconnectedEvent) {
        mqttProtocolAdapter.onBeforeEndpointClose(this);
        mqttProtocolAdapter.onClose(endpoint);
        final CompositeFuture removalDoneFuture = removeAllCommandSubscriptions(span, sendDisconnectedEvent);
        if (sendDisconnectedEvent) {
            mqttProtocolAdapter.sendDisconnectedEvent(endpoint.clientIdentifier(), authenticatedDevice, span.context());
        }
        if (authenticatedDevice == null) {
            mqttProtocolAdapter.getMetrics().decrementUnauthenticatedConnections();
        } else {
            mqttProtocolAdapter.getMetrics().decrementConnections(authenticatedDevice.getTenantId());
        }
        final String reasonSuffix = reason != null ? ("; reason: " + reason) : "";
        if (endpoint.isConnected()) {
            final Map<String, String> logFields = new HashMap<>(2);
            logFields.put(Fields.EVENT, "closing device connection");
            Optional.ofNullable(reason).ifPresent(r -> logFields.put("reason", r));
            span.log(logFields);

            if (authenticatedDevice == null) {
                LOG.debug("closing connection to anonymous device [client ID: {}]{}", endpoint.clientIdentifier(),
                        reasonSuffix);
            } else {
                LOG.debug("closing connection to device [tenant-id: {}, device-id: {}, client ID: {}]{}",
                        authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId(),
                        endpoint.clientIdentifier(), reasonSuffix);
            }
            endpoint.close();
        } else {
            // connection already closed
            if (authenticatedDevice == null) {
                LOG.debug("connection to anonymous device closed [client ID: {}]{}", endpoint.clientIdentifier(),
                        reasonSuffix);
            } else {
                LOG.debug("connection to device closed [tenant-id: {}, device-id: {}, client ID: {}]{}",
                        authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId(),
                        endpoint.clientIdentifier(), reasonSuffix);
            }
        }
        return removalDoneFuture.mapEmpty();
    }

    private CompositeFuture removeAllCommandSubscriptions(final Span span, final boolean sendDisconnectedEvent) {
        @SuppressWarnings("rawtypes")
        final List<Future> removalFutures = new ArrayList<>(commandSubscriptions.size());
        for (final var iter = commandSubscriptions.values().iterator(); iter.hasNext(); ) {
            final Pair<CommandSubscription, CommandConsumer> pair = iter.next();
            pair.one().logUnsubscribe(span);
            removalFutures.add(onCommandSubscriptionRemoved(pair, span, sendDisconnectedEvent));
            iter.remove();
        }
        return CompositeFuture.join(removalFutures);
    }

    private Span newSpan(final String operationName) {
        final Span span = newChildSpan(null, operationName);
        traceSamplingPriority.ifPresent(prio -> TracingHelper.setTraceSamplingPriority(span, prio));
        return span;
    }

    private Span newChildSpan(final SpanContext spanContext, final String operationName) {
        final Span span = TracingHelper.buildChildSpan(mqttProtocolAdapter.getTracer(), spanContext, operationName,
                        mqttProtocolAdapter.getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(TracingHelper.TAG_CLIENT_ID.getKey(), endpoint.clientIdentifier())
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .start();

        if (authenticatedDevice != null) {
            TracingHelper.setDeviceTags(span, authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
        }
        return span;
    }
}
