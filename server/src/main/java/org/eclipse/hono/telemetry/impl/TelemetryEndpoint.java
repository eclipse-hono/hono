/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.telemetry.impl;

import static io.vertx.proton.ProtonHelper.condition;
import static org.eclipse.hono.telemetry.TelemetryConstants.getDownstreamMessageAddress;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_RESOURCE;
import static org.eclipse.hono.util.MessageHelper.getAnnotation;

import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.AmqpMessage;
import org.eclipse.hono.server.BaseEndpoint;
import org.eclipse.hono.server.UpstreamReceiver;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.telemetry.TelemetryMessageFilter;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A Hono {@code Endpoint} for uploading telemetry data.
 *
 */
public final class TelemetryEndpoint extends BaseEndpoint {

    private final String     dataAddress;

    public TelemetryEndpoint(final Vertx vertx, final boolean singleTenant) {
        this(vertx, singleTenant, 0);
    }

    public TelemetryEndpoint(final Vertx vertx, final boolean singleTenant, final int instanceId) {
        super(Objects.requireNonNull(vertx), singleTenant, instanceId);
        dataAddress = getDownstreamMessageAddress(instanceNo);
        logger.info("publishing downstream telemetry messages on event bus [address: {}]", dataAddress);
    }

    @Override
    public String getName() {
        return TelemetryConstants.TELEMETRY_ENDPOINT;
    }

    @Override
    public void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {

        if (ProtonQoS.AT_LEAST_ONCE.equals(receiver.getRemoteQoS())) {
            logger.debug("client wants to use AT LEAST ONCE delivery mode, ignoring ...");
        }
        receiver.setQoS(ProtonQoS.AT_MOST_ONCE);
        final String linkId = UUID.randomUUID().toString();
        onLinkAttach(linkId, receiver, targetAddress);
    }

    void onLinkAttach(final String linkId, final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {

        final UpstreamReceiver link = new UpstreamReceiver(linkId, receiver);

        receiver.closeHandler(clientDetached -> {
            // client has closed link -> inform TelemetryAdapter about client detach
            onLinkDetach(link);
        }).handler((delivery, message) -> {
            if (TelemetryMessageFilter.verify(targetAddress, message)) {
                sendTelemetryData(link, delivery, message);
            } else {
                MessageHelper.rejected(delivery, AmqpError.DECODE_ERROR.toString(), "malformed telemetry message");
                onLinkDetach(link, condition(AmqpError.DECODE_ERROR.toString(), "invalid message received"));
            }
        }).open();

        logger.debug("registering new link for telemetry client [{}]", linkId);
        registerClientLink(link);
        sendLinkAttachMessage(link, targetAddress);
    }

    private void sendTelemetryData(final UpstreamReceiver link, final ProtonDelivery delivery, final Message msg) {
        if (!delivery.remotelySettled()) {
            logger.trace("received un-settled telemetry message on link [{}]", link.getLinkId());
        }
        final ResourceIdentifier messageAddress = ResourceIdentifier.fromString(getAnnotation(msg, APP_PROPERTY_RESOURCE, String.class));
        checkDeviceExists(messageAddress, deviceExists -> {
            if (deviceExists) {
                final String messageId = UUID.randomUUID().toString();
                final AmqpMessage amqpMessage = AmqpMessage.of(msg, delivery);
                vertx.sharedData().getLocalMap(dataAddress).put(messageId, amqpMessage);
                sendAtMostOnce(link, messageId, delivery);
            } else {
                logger.debug("device {}/{} does not exist, closing link",
                        messageAddress.getTenantId(), messageAddress.getResourceId());
                MessageHelper.rejected(delivery, AmqpError.PRECONDITION_FAILED.toString(), "device does not exist");
                onLinkDetach(link, condition(AmqpError.PRECONDITION_FAILED.toString(), "device does not exist"));
            }
        });
    }

    private void sendAtMostOnce(final UpstreamReceiver link, final String messageId, final ProtonDelivery delivery) {
        vertx.eventBus().send(dataAddress, TelemetryConstants.getTelemetryMsg(messageId, link.getLinkId()));
        ProtonHelper.accepted(delivery, true);
        int creditLeft = link.getCredit();
        logger.trace("publishing telemetry msg received via Link[id: {}, credit left: {}] to {}",
                link.getLinkId(), creditLeft, dataAddress);
    }
}
