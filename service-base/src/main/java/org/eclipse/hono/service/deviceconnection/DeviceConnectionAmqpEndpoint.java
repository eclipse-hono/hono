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

package org.eclipse.hono.service.deviceconnection;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.RequestResponseEndpoint;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;

/**
 * An {@code AmqpEndpoint} for managing device connection information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device
 * Connection API</a>. It receives AMQP 1.0 messages representing requests and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in a response message.
 * @deprecated This class will be removed in future versions as AMQP endpoint does not use event bus anymore.
 *             Please use {@link org.eclipse.hono.service.deviceconnection.AbstractDeviceConnectionAmqpEndpoint} based implementation in the future.
 */
@Deprecated
public class DeviceConnectionAmqpEndpoint extends RequestResponseEndpoint<ServiceConfigProperties> {

    /**
     * Creates a new device connection endpoint for a vertx instance.
     *
     * @param vertx The vertx instance to use.
     */
    @Autowired
    public DeviceConnectionAmqpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }


    @Override
    public final String getName() {
        return DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final String getEventBusServiceAddress() {
        return DeviceConnectionConstants.EVENT_BUS_ADDRESS_DEVICE_CONNECTION_IN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<EventBusMessage> createEventBusRequestMessage(
            final Message requestMessage,
            final ResourceIdentifier targetAddress,
            final HonoUser clientPrincipal) {

        try {
            return Future.succeededFuture(EventBusMessage.forOperation(requestMessage)
                    .setCorrelationId(requestMessage)
                    .setTenant(targetAddress.getTenantId())
                    .setDeviceId(requestMessage)
                    .setGatewayId(requestMessage)
                    .setJsonPayload(requestMessage));
        } catch (DecodeException e) {
            logger.debug("failed to create EventBusMessage from AMQP request message", e);
            return Future.failedFuture(
                    new ClientErrorException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "request message body contains malformed JSON"));
        }
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return DeviceConnectionMessageFilter.verify(linkTarget, msg);
    }

    @Override
    protected final Message getAmqpReply(final EventBusMessage message) {
        return RequestResponseApiConstants.getAmqpReply(DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT, message);
    }
}
