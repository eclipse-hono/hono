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
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_RESOURCE;
import static org.eclipse.hono.util.MessageHelper.getAnnotation;

import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.server.BaseEndpoint;
import org.eclipse.hono.server.DownstreamAdapter;
import org.eclipse.hono.server.UpstreamReceiver;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.eclipse.hono.telemetry.TelemetryMessageFilter;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A Hono {@code Endpoint} for uploading telemetry data.
 *
 */
@Component
@Scope("prototype")
@Qualifier("telemetry")
public final class TelemetryEndpoint extends BaseEndpoint {

    private DownstreamAdapter             downstreamAdapter;

    @Autowired
    public TelemetryEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    protected void doStart(Future<Void> startFuture) {
        if (downstreamAdapter == null) {
            startFuture.fail("no downstream adapter configured on Telemetry endpoint");
        } else {
            downstreamAdapter.start(startFuture);
        }
    }

    @Override
    protected void doStop(Future<Void> stopFuture) {
        if (downstreamAdapter == null) {
            stopFuture.complete();
        } else {
            downstreamAdapter.stop(stopFuture);
        }
    }

    @Autowired
    @Qualifier("telemetry")
    public final void setDownstreamAdapter(final DownstreamAdapter adapter) {
        this.downstreamAdapter = Objects.requireNonNull(adapter);
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

        final String linkId = UUID.randomUUID().toString();
        final UpstreamReceiver link = UpstreamReceiver.atMostOnceReceiver(linkId, receiver);

        downstreamAdapter.getDownstreamSender(link, s -> {
            if (s.succeeded()) {
                receiver.closeHandler(clientDetached -> {
                    // client has closed link -> inform TelemetryAdapter about client detach
                    onLinkDetach(link);
                    downstreamAdapter.onClientDetach(link);
                }).handler((delivery, message) -> {
                    if (TelemetryMessageFilter.verify(targetAddress, message)) {
                        sendTelemetryData(link, delivery, message);
                    } else {
                        MessageHelper.rejected(delivery, AmqpError.DECODE_ERROR.toString(), "malformed telemetry message");
                        onLinkDetach(link, condition(AmqpError.DECODE_ERROR.toString(), "invalid message received"));
                    }
                }).open();
                logger.debug("accepted link from telemetry client [{}]", linkId);
            } else {
                // we cannot connect to downstream container, reject client
                link.close(condition(AmqpError.PRECONDITION_FAILED, "no consumer available for target"));
            }
        });
    }

    private void sendTelemetryData(final UpstreamReceiver link, final ProtonDelivery delivery, final Message msg) {
        if (!delivery.remotelySettled()) {
            logger.trace("received un-settled telemetry message on link [{}]", link.getLinkId());
        }
        final ResourceIdentifier messageAddress = ResourceIdentifier.fromString(getAnnotation(msg, APP_PROPERTY_RESOURCE, String.class));
        checkDeviceExists(messageAddress, deviceExists -> {
            if (deviceExists) {
                downstreamAdapter.processMessage(link, delivery, msg);
            } else {
                logger.debug("device {}/{} does not exist, closing link",
                        messageAddress.getTenantId(), messageAddress.getResourceId());
                MessageHelper.rejected(delivery, AmqpError.PRECONDITION_FAILED.toString(), "device does not exist");
                link.close(condition(AmqpError.PRECONDITION_FAILED.toString(), "device does not exist"));
            }
        });
    }
}
