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
import static org.eclipse.hono.telemetry.TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN;

import java.util.Objects;
import java.util.UUID;

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
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;

/**
 * A Hono {@code Endpoint} for uploading telemetry data.
 *
 */
@Component
public final class TelemetryEndpoint implements Endpoint {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryEndpoint.class);
    private Vertx               vertx;
    private boolean             singleTenant;

    @Autowired
    public TelemetryEndpoint(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * @return the singleTenant
     */
    public boolean isSingleTenant() {
        return singleTenant;
    }

    /**
     * @param singleTenant the singleTenant to set
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
    public void handleReceiverOpen(final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
        checkAuthorizationToAttach(targetResource,
                isAuthorized -> {
                    if (!isAuthorized) {
                        LOG.debug(
                                "client is not authorized to upload telemetry data for tenant [id: {}], closing link",
                                targetResource.getTenantId());
                        receiver.close();
                    } else {
                        receiver.setAutoAccept(false);
                        LOG.debug("client uses QoS: {}", receiver.getRemoteQoS());
                        receiver.handler((delivery, message) -> {
                            final ResourceIdentifier messageAddress = getResourceIdentifier(message.getAddress());
                            if (TelemetryMessageFilter.verify(targetResource, messageAddress, message)) {
                                sendTelemetryData(delivery, message, messageAddress);
                            } else {
                                ProtonHelper.rejected(delivery, true);
                            }
                        }).flow(20).open();
                    }
                });
    }

    private ResourceIdentifier getResourceIdentifier(final String address) {
        if (isSingleTenant()) {
            return ResourceIdentifier.fromStringAssumingDefaultTenant(address);
        } else {
            return ResourceIdentifier.fromString(address);
        }
    }

    private void checkAuthorizationToAttach(final ResourceIdentifier targetResource, final Handler<Boolean> handler) {
        final JsonObject body = new JsonObject();
        // TODO how to obtain subject information?
        body.put(AUTH_SUBJECT_FIELD, Constants.DEFAULT_SUBJECT);
        body.put(RESOURCE_FIELD, targetResource.toString());
        body.put(PERMISSION_FIELD, Permission.WRITE.toString());
        vertx.eventBus().send(EVENT_BUS_ADDRESS_AUTHORIZATION_IN, body,
                res -> handler.handle(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())));
    }

    private void sendTelemetryData(final ProtonDelivery delivery, final Message msg, final ResourceIdentifier messageAddress) {
        final String messageId = UUID.randomUUID().toString();
        final AmqpMessage amqpMessage = AmqpMessage.of(msg, delivery);
        vertx.sharedData().getLocalMap(EVENT_BUS_ADDRESS_TELEMETRY_IN).put(messageId, amqpMessage);
        checkPermissionAndSend(messageId, amqpMessage, messageAddress);
    }

    private void checkPermissionAndSend(final String messageId, final AmqpMessage amqpMessage, final ResourceIdentifier messageAddress)
    {
        final JsonObject authMsg = new JsonObject();
        // TODO how to obtain subject information?
        authMsg.put(AUTH_SUBJECT_FIELD, Constants.DEFAULT_SUBJECT);
        authMsg.put(RESOURCE_FIELD, messageAddress.toString());
        authMsg.put(PERMISSION_FIELD, Permission.WRITE.toString());

        vertx.eventBus().send(EVENT_BUS_ADDRESS_AUTHORIZATION_IN, authMsg,
           res -> {
               if (res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())) {
                   vertx.runOnContext(run -> {
                       final ProtonDelivery delivery = amqpMessage.getDelivery();
                       if (delivery.remotelySettled()) {
                           // client uses AT MOST ONCE semantics
                           sendAtMostOnce(messageId, delivery, messageAddress);
                       } else {
                           // client uses AT LEAST ONCE semantics
                           sendAtLeastOnce(messageId, delivery, messageAddress);
                       }
                   });
               } else {
                   LOG.debug("not allowed to upload telemetry data", res.cause());
               }
           });
    }

    private void sendAtMostOnce(final String messageId, final ProtonDelivery delivery, final ResourceIdentifier messageAddress) {
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_IN, TelemetryConstants.getTelemetryMsg(messageId, messageAddress));
        ProtonHelper.accepted(delivery, true);
    }

    private void sendAtLeastOnce(final String messageId, final ProtonDelivery delivery, final ResourceIdentifier messageAddress) {
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_IN, TelemetryConstants.getTelemetryMsg(messageId, messageAddress),
                res -> {
                    if (res.succeeded() && TelemetryConstants.RESULT_ACCEPTED.equals(res.result().body())) {
                        vertx.runOnContext(run -> ProtonHelper.accepted(delivery, true));
                    } else {
                        LOG.debug("did not receive response for telemetry data message", res.cause());
                        vertx.runOnContext(run -> ProtonHelper.rejected(delivery, true));
                    }
                });
    }
}
