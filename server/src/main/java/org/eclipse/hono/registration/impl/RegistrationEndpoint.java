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
package org.eclipse.hono.registration.impl;

import static org.eclipse.hono.registration.RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN;
import static org.eclipse.hono.registration.RegistrationConstants.getAmqpReply;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_RESOURCE_ID;
import static org.eclipse.hono.util.MessageHelper.getLinkName;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.registration.RegistrationConstants;
import org.eclipse.hono.registration.RegistrationMessageFilter;
import org.eclipse.hono.server.BaseEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.impl.ProtonReceiverImpl;

/**
 * A Hono {@code Endpoint} for managing devices.
 */
@Component
public final class RegistrationEndpoint extends BaseEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationEndpoint.class);

    @Autowired
    public RegistrationEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    public void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {
        if (ProtonQoS.AT_LEAST_ONCE.equals(receiver.getRemoteQoS())) {
            LOG.debug("client wants to use AT LEAST ONCE delivery mode, ignoring ...");
        }
        final ProtonReceiverImpl receiverImpl = (ProtonReceiverImpl) receiver;

        receiver.closeHandler(clientDetached -> onLinkDetach(clientDetached.result()))
                .handler((delivery, message) -> {

                    LOG.debug("incoming message [{}]: {}", receiverImpl.getName(), message);
                    LOG.debug("app properties: {}", message.getApplicationProperties());

                    if (RegistrationMessageFilter.verify(targetAddress, message)) {
                        sendRegistrationData(delivery, message);
                    } else {
                        onLinkDetach(receiver);
                    }
                }).open();

        LOG.debug("registering new link for client [{}]", receiverImpl.getName());
    }

    @Override public void onLinkAttach(final ProtonSender sender, final ResourceIdentifier targetResource) {
        LOG.debug("link attach [{}]: {}", getLinkName(sender), targetResource);

        vertx.eventBus().consumer(RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_REPLY,
                (Handler<io.vertx.core.eventbus.Message<JsonObject>>) (message) -> processReplyMessage(sender, message));

        sender.open();
    }

    private void onLinkDetach(final ProtonReceiver client) {
        LOG.debug("closing receiver for client [{}]", getLinkName(client));
        client.close();
    }

    private void sendRegistrationData(final ProtonDelivery delivery, final Message msg) {
        final ResourceIdentifier messageAddress = ResourceIdentifier.fromString(MessageHelper.getAnnotation(msg, APP_PROPERTY_RESOURCE_ID));
        checkPermission(messageAddress, permissionGranted -> {
            if (permissionGranted) {
                vertx.runOnContext(run -> {
                    final JsonObject registrationMsg = RegistrationConstants.getRegistrationMsg(msg);
                    LOG.debug("Publishing to eventBus {}", registrationMsg);
                    vertx.eventBus().send(EVENT_BUS_ADDRESS_REGISTRATION_IN, registrationMsg);
                    ProtonHelper.accepted(delivery, true);
                });
            } else {
                LOG.debug("client is not authorized to register devices at [{}]", messageAddress);
            }
        });
    }

    private void processReplyMessage(final ProtonSender reply, final io.vertx.core.eventbus.Message<JsonObject> message) {
        if (reply != null && reply.isOpen())
        {
            final JsonObject body = message.body();
            final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
            final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);
            final String status = body.getString(RegistrationConstants.APP_PROPERTY_STATUS);
            final String msgId = body.getString(RegistrationConstants.APP_PROPERTY_MESSAGE_ID);
            final Message replyMsg = getAmqpReply(status, msgId, tenantId, deviceId);
            LOG.debug("Sending reply on link [{}]: {}", getLinkName(reply), replyMsg);
            reply.send(replyMsg);
        } else {
            LOG.info("Cannot send reply, sender is not open.");
        }
    }
}
