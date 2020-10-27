/**
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
 */


package org.eclipse.hono.adapter.client.telemetry.amqp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.amqp.SenderCachingServiceClient;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.impl.EventSenderImpl;
import org.eclipse.hono.client.impl.TelemetrySenderImpl;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.AddressHelper;
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

    /**
     * Creates a new sender for a connection.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ProtonBasedDownstreamSender(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {
        super(connection, samplerFactory, adapterConfig);
    }

    private Future<DownstreamSender> getOrCreateTelemetrySender(final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection
                .isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    final String key = AddressHelper.getTargetAddress(
                            TelemetryConstants.TELEMETRY_ENDPOINT,
                            tenantId,
                            null,
                            connection.getConfig());
                    getOrCreateSender(
                            key,
                            () -> TelemetrySenderImpl.create(
                                    connection,
                                    tenantId,
                                    samplerFactory.create(TelemetryConstants.TELEMETRY_ENDPOINT),
                                    onSenderClosed -> removeClient(key)),
                            result);
                }));
    }

    private Future<DownstreamSender> getOrCreateEventSender(final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection
                .isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    final String key = AddressHelper.getTargetAddress(EventConstants.EVENT_ENDPOINT, tenantId, null, connection.getConfig());
                    getOrCreateSender(
                            key,
                            () -> EventSenderImpl.create(
                                    connection,
                                    tenantId,
                                    samplerFactory.create(EventConstants.EVENT_ENDPOINT),
                                    onSenderClosed -> removeClient(key)),
                            result);
                }));
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
        Objects.requireNonNull(contentType);

        return getOrCreateTelemetrySender(tenant.getTenantId())
            .compose(sender -> {
                final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, tenant.getTenantId(), device.getDeviceId());
                final Message message = createMessage(tenant, device, qos, target, contentType, payload, properties);
                switch (qos) {
                case AT_MOST_ONCE:
                    return sender.send(message, context);
                default:
                    return sender.sendAndWaitForOutcome(message, context);
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
        Objects.requireNonNull(contentType);

        return getOrCreateEventSender(tenant.getTenantId())
                .compose(sender -> {
                    final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, tenant.getTenantId(), device.getDeviceId());
                    final Message message = createMessage(tenant, device, QoS.AT_LEAST_ONCE, target, contentType, payload, properties);
                    return sender.sendAndWaitForOutcome(message, context);
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

        final Map<String, Object> props = Optional.ofNullable(properties)
                .orElseGet(HashMap::new);
        props.put(MessageHelper.APP_PROPERTY_QOS, qos.ordinal());
        props.put(MessageHelper.APP_PROPERTY_DEVICE_ID, device.getDeviceId());

        return MessageHelper.newMessage(
                target,
                contentType,
                payload,
                tenant,
                props,
                device.getDefaults(),
                adapterConfig.isDefaultsEnabled(),
                adapterConfig.isJmsVendorPropsEnabled());
    }
}
