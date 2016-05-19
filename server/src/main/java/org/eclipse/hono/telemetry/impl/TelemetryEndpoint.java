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

import static org.eclipse.hono.authorization.AuthorizationConstants.AUTH_SUBJECT_FIELD;
import static org.eclipse.hono.authorization.AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN;
import static org.eclipse.hono.authorization.AuthorizationConstants.PERMISSION_FIELD;
import static org.eclipse.hono.authorization.AuthorizationConstants.RESOURCE_FIELD;
import static org.eclipse.hono.telemetry.TelemetryConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.AmqpMessage;
import org.eclipse.hono.authorization.AuthorizationConstants;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.server.Endpoint;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.telemetry.TelemetryMessageFilter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A Hono {@code Endpoint} for uploading telemetry data.
 *
 */
@Component
public final class TelemetryEndpoint implements Endpoint {

    private static final Logger           LOG                          = LoggerFactory.getLogger(TelemetryEndpoint.class);
    private Map<String, LinkWrapper>      activeClients                = new HashMap<>();
    private Vertx                         vertx;
    private boolean                       singleTenant;
    private MessageConsumer<JsonObject>   flowControlConsumer;

    @Autowired
    public TelemetryEndpoint(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
        flowControlConsumer = this.vertx.eventBus().consumer(
                EVENT_BUS_ADDRESS_TELEMETRY_FLOW_CONTROL, this::handleFlowControlMsg);
    }

    private void handleFlowControlMsg(final io.vertx.core.eventbus.Message<JsonObject> msg) {
        vertx.runOnContext(run -> {
            if (msg.body() == null) {
                LOG.warn("received empty message from telemetry adapter, is this a bug?");
            } else if (isFlowControlMessage(msg.body())) {
                handleFlowControlMsg(msg.body().getJsonObject(MSG_TYPE_FLOW_CONTROL));
            } else if (isErrorMessage(msg.body())) {
                handleErrorMessage(msg.body().getJsonObject(MSG_TYPE_ERROR));
            } else {
                // discard message
                LOG.debug("received unsupported message from telemetry adapter: {}", msg.body().encode());
            }
        });
    }

    private void handleFlowControlMsg(final JsonObject msg) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("received flow control message from telemetry adapter: {}", msg.encodePrettily());
        }
        String linkId = msg.getString(FIELD_NAME_LINK_ID);
        LinkWrapper client = activeClients.get(linkId);
        if (client != null) {
            client.setSuspended(msg.getBoolean(FIELD_NAME_SUSPEND, false));
        }
    }

    private void handleErrorMessage(final JsonObject msg) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("received error message from telemetry adapter: {}", msg.encodePrettily());
        }
        String linkId = msg.getString(FIELD_NAME_LINK_ID);
        LinkWrapper client = activeClients.get(linkId);
        if (client != null) {
            boolean closeLink = msg.getBoolean(FIELD_NAME_CLOSE_LINK, false);
            if (closeLink) {
                onLinkDetach(client);
            }
        }
    }

    /**
     * Checks if Hono runs in single-tenant mode.
     * <p>
     * In single-tenant mode Hono will accept target addresses in {@code ATTACH} messages
     * that do not contain a tenant ID and will assume {@link Constants#DEFAULT_TENANT} instead.
     * </p>
     * <p>
     * The default value of this property is {@code false}.
     * </p>
     * 
     * @return {@code true} if Hono runs in single-tenant mode.
     */
    public boolean isSingleTenant() {
        return singleTenant;
    }

    /**
     * @param singleTenant {@code true} to configure Hono for single-tenant mode.
     */
    @Value(value = "${hono.single.tenant:false}")
    public void setSingleTenant(final boolean singleTenant) {
        this.singleTenant = singleTenant;
    }

    @Override
    public String getName() {
        return TelemetryConstants.TELEMETRY_ENDPOINT;
    }

    @Override
    public void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {
        if (ProtonQoS.AT_LEAST_ONCE.equals(receiver.getRemoteQoS())) {
            LOG.debug("client wants to use AT LEAST ONCE delivery mode, ignoring ...");
        }
        final String linkId = UUID.randomUUID().toString();
        final LinkWrapper client = new LinkWrapper(linkId, receiver);

        receiver.closeHandler(clientDetached -> {
            // client has closed link -> inform TelemetryAdapter about client detach
            onLinkDetach(client);
        }).handler((delivery, message) -> {
            final ResourceIdentifier messageAddress = getResourceIdentifier(message.getAddress());
            if (TelemetryMessageFilter.verify(targetAddress, messageAddress, message)) {
                sendTelemetryData(client, delivery, message, messageAddress);
            } else {
                onLinkDetach(client);
            }
        }).open();

        LOG.debug("registering new link for client [{}]", linkId);
        activeClients.put(linkId, client);
        JsonObject msg = getLinkAttachedMsg(linkId, targetAddress);
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_LINK_CONTROL, msg);
    }

    private void onLinkDetach(final LinkWrapper client) {
        LOG.debug("closing receiver for client [{}]", client.getLinkId());
        client.close();
        activeClients.remove(client.getLinkId());
        JsonObject msg = getLinkDetachedMsg(client.getLinkId());
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_LINK_CONTROL, msg);
    }

    private ResourceIdentifier getResourceIdentifier(final String address) {
        if (isSingleTenant()) {
            return ResourceIdentifier.fromStringAssumingDefaultTenant(address);
        } else {
            return ResourceIdentifier.fromString(address);
        }
    }

    private void sendTelemetryData(final LinkWrapper link, final ProtonDelivery delivery, final Message msg, final ResourceIdentifier messageAddress) {
        if (!delivery.remotelySettled()) {
            LOG.trace("received un-settled telemetry message on link [{}]", link.getLinkId());
        }
        checkPermission(messageAddress, permissionGranted -> {
            if (permissionGranted) {
                vertx.runOnContext(run -> {
                    final String messageId = UUID.randomUUID().toString();
                    final AmqpMessage amqpMessage = AmqpMessage.of(msg, delivery);
                    vertx.sharedData().getLocalMap(EVENT_BUS_ADDRESS_TELEMETRY_IN).put(messageId, amqpMessage);
                    sendAtMostOnce(link.getLinkId(), messageId, delivery);
                    int creditLeft = link.decrementAndGetRemainingCredit();
                    LOG.trace("forwarding msg [link: {}] to downstream adapter [credit left: {}]", link.getLinkId(), creditLeft);
                });
            } else {
                LOG.debug("client is not authorized to upload telemetry data for address [{}]", messageAddress);
                onLinkDetach(link); // inform downstream adapter about client detach
            }
        });
    }

    private void checkPermission(final ResourceIdentifier messageAddress, final Handler<Boolean> permissionCheckHandler)
    {
        final JsonObject authMsg = new JsonObject();
        // TODO how to obtain subject information?
        authMsg.put(AUTH_SUBJECT_FIELD, Constants.DEFAULT_SUBJECT);
        authMsg.put(RESOURCE_FIELD, messageAddress.toString());
        authMsg.put(PERMISSION_FIELD, Permission.WRITE.toString());

        vertx.eventBus().send(EVENT_BUS_ADDRESS_AUTHORIZATION_IN, authMsg,
           res -> permissionCheckHandler.handle(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())));
    }

    private void sendAtMostOnce(final String clientId, final String messageId, final ProtonDelivery delivery) {
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_IN, TelemetryConstants.getTelemetryMsg(messageId, clientId));
        ProtonHelper.accepted(delivery, true);
    }

    public static class LinkWrapper {
        private static final int INITIAL_CREDIT = 50;
        private ProtonReceiver link;
        private String id;
        private boolean suspended;
        private AtomicInteger credit = new AtomicInteger(INITIAL_CREDIT);

        /**
         * @param receiver
         */
        public LinkWrapper(final String linkId, final ProtonReceiver receiver) {
            this.id = Objects.requireNonNull(linkId);
            link = Objects.requireNonNull(receiver);
            link.setAutoAccept(false).setQoS(ProtonQoS.AT_MOST_ONCE).setPrefetch(0); // use manual flow control
            suspend();
        }

        /**
         * @return the creditAvailable
         */
        public boolean isSuspended() {
            return suspended;
        }

        public void suspend() {
            LOG.debug("suspending client [{}]", id);
            suspended = true;
        }

        public void resume() {
            credit.set(INITIAL_CREDIT);
            LOG.debug("replenishing client [{}] with {} credits", id, credit.get());
            link.flow(credit.get());
            suspended = false;
        }

        public void setSuspended(final boolean suspend) {
            if (suspend) {
                suspend();
            } else {
                resume();
            }
        }

        public int decrementAndGetRemainingCredit() {
            if (credit.decrementAndGet() == 0) {
                if (!suspended) {
                    // automatically replenish client with new credit
                    resume();
                }
            }
            return credit.get();
        }

        public void close() {
            link.close();
        }

        /**
         * @return the link
         */
        public ProtonReceiver getLink() {
            return link;
        }

        /**
         * @return the link ID
         */
        public String getLinkId() {
            return id;
        }
    }
}
