/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.client.telemetry.amqp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.DownstreamAmqpMessageFactory;
import org.eclipse.hono.client.amqp.SenderCachingServiceClient;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A vertx-proton based sender for telemetry messages and events.
 */
public class ProtonBasedDownstreamSender extends SenderCachingServiceClient implements TelemetrySender, EventSender {

    private final boolean deviceDefaultsEnabled;
    private final boolean jmsVendorPropsEnabled;

    /**
     * Creates a new sender for a connection.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param deviceDefaultsEnabled {@code true} if the default properties registered for devices
     *                              should be included in messages being sent.
     * @param jmsVendorPropsEnabled {@code true} if <em>Vendor Properties</em> as defined by <a
     *                              href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf">
     *                              Advanced Message Queuing Protocol (AMQP) JMS Mapping Version 1.0, Chapter 4</a> should be included
     *                              in messages being sent.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ProtonBasedDownstreamSender(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final boolean deviceDefaultsEnabled,
            final boolean jmsVendorPropsEnabled) {
        super(connection, samplerFactory);
        this.deviceDefaultsEnabled = deviceDefaultsEnabled;
        this.jmsVendorPropsEnabled = jmsVendorPropsEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendTelemetry(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final QoS qos,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(qos);

        return getOrCreateSenderLink(TelemetryConstants.TELEMETRY_ENDPOINT, tenant.getTenantId())
            .recover(thr -> Future.failedFuture(StatusCodeMapper.toServerError(thr)))
            .compose(sender -> {
                final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, tenant.getTenantId(), device.getDeviceId());
                final Message message = createMessage(tenant, device, qos, target, contentType, payload, properties);
                switch (qos) {
                case AT_MOST_ONCE:
                    return sender.send(message, newFollowingSpan(context, "forward Telemetry data"));
                default:
                    return sender.sendAndWaitForOutcome(message, newChildSpan(context, "forward Telemetry data"));
                }
            })
            .mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendEvent(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);

        return getOrCreateSenderLink(EventConstants.EVENT_ENDPOINT, tenant.getTenantId())
                .recover(thr -> Future.failedFuture(StatusCodeMapper.toServerError(thr)))
                .compose(sender -> {
                    final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, tenant.getTenantId(), device.getDeviceId());
                    final Message message = createMessage(tenant, device, QoS.AT_LEAST_ONCE, target, contentType, payload, properties);
                    message.setDurable(true);
                    sender.setErrorInfoLoggingEnabled(true); // log on INFO level since events are usually brokered and therefore errors here might indicate issues with the broker
                    return sender.sendAndWaitForOutcome(message, newChildSpan(context, "forward Event"));
                })
                .mapEmpty();
    }

    private Message createMessage(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final QoS qos,
            final ResourceIdentifier target,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties) {

        final Map<String, Object> props = Optional.ofNullable(properties).orElseGet(HashMap::new);
        props.put(MessageHelper.APP_PROPERTY_QOS, qos.ordinal());
        props.put(MessageHelper.APP_PROPERTY_DEVICE_ID, device.getDeviceId());

        return DownstreamAmqpMessageFactory.newMessage(
                target,
                contentType,
                payload,
                tenant,
                deviceDefaultsEnabled ? tenant.getDefaults().getMap() : null,
                deviceDefaultsEnabled ? device.getDefaults() : null,
                props,
                jmsVendorPropsEnabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new StringBuilder(ProtonBasedDownstreamSender.class.getName())
                .append(" via AMQP 1.0 Messaging Network")
                .toString();
    }
}
