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

import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.impl.TelemetrySenderImpl;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A vertx-proton based sender for telemetry messages.
 */
public class ProtonBasedTelemetrySender extends ProtonBasedDownstreamSender implements TelemetrySender {

    /**
     * Creates a new telemetry sender for a connection.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ProtonBasedTelemetrySender(final HonoConnection connection, final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {
        super(connection, samplerFactory, adapterConfig);
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
                    final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT,
                            tenant.getTenantId(), device.getDeviceId());
                    final Message message = createMessage(tenant, device, qos, target, contentType, payload,
                            properties);
                    switch (qos) {
                    case AT_MOST_ONCE:
                        return sender.send(message, context);
                    default:
                        return sender.sendAndWaitForOutcome(message, context);
                    }
                })
                .mapEmpty();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new StringBuilder(ProtonBasedTelemetrySender.class.getName())
                .append(" via AMQP 1.0 Messaging Network")
                .toString();
    }
}
