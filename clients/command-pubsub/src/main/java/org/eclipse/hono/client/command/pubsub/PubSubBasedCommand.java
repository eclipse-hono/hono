/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.command.pubsub;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessagingType;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.vertx.core.buffer.Buffer;

/**
 * A command used in a Pub/Sub based client.
 */
public final class PubSubBasedCommand implements Command {

    /**
     * If present, the command is invalid.
     */
    private final Optional<String> validationError;
    private final PubsubMessage pubsubMessage;
    private final String tenantId;
    private final String deviceId;
    private final String correlationId;
    private final String subject;
    private final String contentType;
    private final String requestId;
    private final boolean responseRequired;

    private String gatewayId;

    private PubSubBasedCommand(
            final Optional<String> validationError,
            final PubsubMessage pubsubMessage,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String subject,
            final String contentType,
            final boolean responseRequired) {

        this.validationError = validationError;
        this.pubsubMessage = pubsubMessage;
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.correlationId = correlationId;
        this.subject = subject;
        this.contentType = contentType;
        this.responseRequired = responseRequired;
        this.requestId = Commands.encodeRequestIdParameters(correlationId, MessagingType.pubsub);
    }

    /**
     * Creates a command from a Pub/Sub message, forwarded by the Command Router.
     * The message is required to contain a <em>tenant_id</em> attribute and a <em>device_id</em> attribute. If any of
     * these attributes are null or empty, an {@link IllegalArgumentException} is thrown.
     *
     * In addition, the attributes map is expected to contain
     * <ul>
     * <li>a <em>subject</em> entry</li>
     * <li>a non-empty <em>correlation-id</em> entry if the <em>response-required</em> entry has the value
     * {@code true}.</li>
     * </ul>
     * or otherwise the returned command's {@link #isValid()} method will return {@code false}.
     * <p>
     *
     * @param pubsubMessage The Pub/Sub message containing the command.
     * @return The command.
     * @throws NullPointerException If pubsubMessage is {@code null}.
     * @throws IllegalArgumentException If the pubsubMessage's attributes are empty or do not contain a tenant
     *             identifier and a target device identifier.
     */
    public static PubSubBasedCommand fromRoutedCommandMessage(final PubsubMessage pubsubMessage) {
        Objects.requireNonNull(pubsubMessage);

        final String tenantId = PubSubMessageHelper.getTenantId(pubsubMessage.getAttributesMap())
                .filter(id -> !id.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Tenant ID is not set"));
        final PubSubBasedCommand command = getCommand(pubsubMessage, tenantId);

        PubSubMessageHelper.getVia(pubsubMessage.getAttributesMap())
                .filter(id -> !id.isEmpty())
                .ifPresent(command::setGatewayId);

        return command;
    }

    /**
     * Creates a command from a Pub/Sub message.
     * The message is required to contain an attributes map.
     *
     * @param pubsubMessage The Pub/Sub message containing the command.
     * @param tenantId The tenant this Pub/Sub message belongs to.
     * @return The command.
     * @throws NullPointerException If pubsubMessage is {@code null}.
     * @throws IllegalArgumentException If the attributesMap is empty or if the attributesMap does not contain a target
     *             device identifier.
     */
    public static PubSubBasedCommand from(final PubsubMessage pubsubMessage, final String tenantId) {
        Objects.requireNonNull(pubsubMessage);
        return getCommand(pubsubMessage, tenantId);
    }

    private static PubSubBasedCommand getCommand(final PubsubMessage pubsubMessage, final String tenantId) {
        final Map<String, String> attributes = pubsubMessage.getAttributesMap();
        if (attributes.isEmpty()) {
            throw new IllegalArgumentException("attributes not set");
        }

        final String deviceId = PubSubMessageHelper.getDeviceId(attributes)
                .filter(id -> !id.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("device ID is not set"));

        final StringJoiner validationErrorJoiner = new StringJoiner(", ");
        final boolean responseRequired = PubSubMessageHelper.isResponseRequired(attributes);
        final String correlationId = PubSubMessageHelper.getCorrelationId(attributes)
                .filter(id -> !id.isEmpty())
                .orElseGet(() -> {
                    if (responseRequired) {
                        validationErrorJoiner.add("correlation-id is not set");
                    }
                    return null;
                });
        final String subject = PubSubMessageHelper.getSubject(attributes)
                .orElseGet(() -> {
                    validationErrorJoiner.add("subject not set");
                    return null;
                });
        final String contentType = PubSubMessageHelper.getContentType(attributes).orElse(null);

        return new PubSubBasedCommand(
                validationErrorJoiner.length() > 0 ? Optional.of(validationErrorJoiner.toString()) : Optional.empty(),
                pubsubMessage,
                tenantId,
                deviceId,
                correlationId,
                subject,
                contentType,
                responseRequired);
    }

    /**
     * Returns the Pub/Sub message corresponding to this command.
     *
     * @return The Pub/Sub message.
     */
    public PubsubMessage getPubsubMessage() {
        return pubsubMessage;
    }

    @Override
    public boolean isOneWay() {
        return !responseRequired;
    }

    @Override
    public boolean isValid() {
        return validationError.isEmpty();
    }

    @Override
    public String getInvalidCommandReason() {
        if (validationError.isEmpty()) {
            throw new IllegalStateException("command is valid");
        }
        return validationError.get();
    }

    @Override
    public String getTenant() {
        return tenantId;
    }

    @Override
    public String getGatewayOrDeviceId() {
        return Optional.ofNullable(gatewayId).orElse(deviceId);
    }

    @Override
    public boolean isTargetedAtGateway() {
        return gatewayId != null;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String getGatewayId() {
        return gatewayId;
    }

    @Override
    public void setGatewayId(final String gatewayId) {
        this.gatewayId = gatewayId;
    }

    @Override
    public String getName() {
        requireValid();
        return subject;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public Buffer getPayload() {
        final byte[] bytePayload = PubSubMessageHelper.getPayload(pubsubMessage);
        return Buffer.buffer(bytePayload);
    }

    @Override
    public int getPayloadSize() {
        final byte[] bytePayload = PubSubMessageHelper.getPayload(pubsubMessage);
        return Optional.ofNullable(bytePayload).map(bytes -> bytes.length).orElse(0);
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getReplyToId() {
        return null;
    }

    @Override
    public String getCorrelationId() {
        requireValid();
        return correlationId;
    }

    @Override
    public MessagingType getMessagingType() {
        return MessagingType.pubsub;
    }

    @Override
    public String toString() {
        if (isValid()) {
            if (isTargetedAtGateway()) {
                return String.format("Command [name: %s, tenant-id: %s, gateway-id: %s, device-id: %s, request-id: %s]",
                        subject, tenantId, gatewayId, deviceId, requestId);
            } else {
                return String.format("Command [name: %s, tenant-id: %s, device-id: %s, request-id: %s]",
                        subject, tenantId, deviceId, requestId);
            }
        } else {
            return String.format("Invalid Command [tenant-id: %s, device-id: %s. error: %s]", tenantId,
                    deviceId, validationError.get());
        }
    }

    @Override
    public void logToSpan(final Span span) {
        Objects.requireNonNull(span);
        if (isValid()) {
            TracingHelper.TAG_CORRELATION_ID.set(span, correlationId);
            final Map<String, Object> items = new HashMap<>(3);
            items.put(Fields.EVENT, "received command message via Pub/Sub");
            items.put("subject", subject);
            items.put("content-type", contentType);
            span.log(items);
        } else {
            TracingHelper.logError(span, "received invalid command message [" + this + "]");
        }
    }

    @Override
    public Map<String, String> getDeliveryFailureNotificationProperties() {
        return PubSubMessageHelper.getDeliveryFailureNotificationMetadata(pubsubMessage.getAttributesMap());
    }

    private void requireValid() {
        if (!isValid()) {
            throw new IllegalStateException("command is invalid");
        }
    }
}
