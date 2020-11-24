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
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.buffer.Buffer;

/**
 * A vertx-proton based base class for telemetry and event senders.
 */
public abstract class ProtonBasedDownstreamSender extends SenderCachingServiceClient {

    /**
     * Creates a new sender for a connection.
     *
     * @param connection The connection to the Hono service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected ProtonBasedDownstreamSender(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {
        super(connection, samplerFactory, adapterConfig);
    }

    /**
     * Creates a new downstream message.
     * <p>
     * It sets <em>qos</em> and <em>device_id</em> to the <em>application properties</em> of the message.
     *
     * @param tenant The tenant that the device belongs to.
     * @param device The registration assertion for the device that the data originates from.
     * @param qos The delivery semantics to use for sending the data.
     * @param target The target address of the message.
     * @param contentType The content type of the data.
     * @param payload The data to send.
     * @param properties Additional meta data that should be included in the downstream message.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the event has been sent downstream.
     *         <p>
     *         The future will be failed with a {@code org.eclipse.hono.client.ServerErrorException} if the data could
     *         not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if tenant, device, qos, target or contentType are {@code null}.
     */
    protected Message createMessage(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final QoS qos,
            final ResourceIdentifier target,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(qos);
        Objects.requireNonNull(target);
        Objects.requireNonNull(contentType);

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
