/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.server;

import static io.vertx.proton.ProtonHelper.condition;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_RESOURCE;
import static org.eclipse.hono.util.MessageHelper.getAnnotation;

import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.service.amqp.BaseEndpoint;
import org.eclipse.hono.service.amqp.UpstreamReceiver;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.CounterService;

/**
 * A base class for implementing Hono {@code Endpoint}s that forward messages
 * to a downstream container.
 *
 */
public abstract class MessageForwardingEndpoint extends BaseEndpoint {

    private CounterService                counterService = NullCounterService.getInstance();
    private DownstreamAdapter             downstreamAdapter;
    private MessageConsumer<String>       clientDisconnectListener;

    protected MessageForwardingEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    /**
     * Sets the spring boot counter service, will be based on Dropwizard Metrics, if in classpath.
     *
     * @param counterService The counter service.
     */
    @Autowired
    public final void setCounterService(final CounterService counterService) {
        this.counterService = counterService;
    }

    /**
     * Gets the spring boot gauge service implementation
     *
     * @return The metrics service or a null implementation - never {@code null}
     */
    public final CounterService getCounterService() {
        return counterService;
    }

    @Override
    protected final void doStart(Future<Void> startFuture) {
        if (downstreamAdapter == null) {
            startFuture.fail("no downstream adapter configured on Telemetry endpoint");
        } else {
            clientDisconnectListener = vertx.eventBus().consumer(
                    Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED,
                    msg -> onClientDisconnect(msg));
            downstreamAdapter.start(startFuture);
        }
    }

    @Override
    protected final void doStop(Future<Void> stopFuture) {
        if (downstreamAdapter == null) {
            stopFuture.complete();
        } else {
            clientDisconnectListener.unregister();
            downstreamAdapter.stop(stopFuture);
        }
    }

    /**
     * Sets the downstream adapter to forward messages to.
     * <p>
     * Subclasses must invoke this method to set the specific
     * downstream adapter they want to forward messages to.
     * 
     * @param adapter The adapter.
     */
    protected final void setDownstreamAdapter(final DownstreamAdapter adapter) {
        this.downstreamAdapter = Objects.requireNonNull(adapter);
    }

    private void onClientDisconnect(final io.vertx.core.eventbus.Message<String> conId) {
        this.downstreamAdapter.onClientDisconnect(conId.body());
    }

    @Override
    public final void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {

        final String linkId = UUID.randomUUID().toString();
        final UpstreamReceiver link = UpstreamReceiver.newUpstreamReceiver(linkId, receiver, getEndpointQos());

        downstreamAdapter.onClientAttach(link, s -> {
            if (s.succeeded()) {
                receiver.closeHandler(clientDetached -> {
                    // client has closed link -> inform TelemetryAdapter about client detach
                    onLinkDetach(link);
                    downstreamAdapter.onClientDetach(link);
                    counterService.decrement(MetricConstants.metricNameUpstreamLinks(targetAddress.toString()));
                }).handler((delivery, message) -> {
                    if (passesFormalVerification(targetAddress, message)) {
                        forwardMessage(link, delivery, message);
                    } else {
                        MessageHelper.rejected(delivery, AmqpError.DECODE_ERROR.toString(), "malformed message");
                        onLinkDetach(link, condition(AmqpError.DECODE_ERROR.toString(), "invalid message received"));
                    }
                }).open();
                logger.debug("accepted link from telemetry client [{}]", linkId);
                counterService.increment(MetricConstants.metricNameUpstreamLinks(targetAddress.toString()));
            } else {
                // we cannot connect to downstream container, reject client
                link.close(condition(AmqpError.PRECONDITION_FAILED, "no consumer available for target"));
            }
        });
    }

    private void forwardMessage(final UpstreamReceiver link, final ProtonDelivery delivery, final Message msg) {

        final ResourceIdentifier messageAddress = ResourceIdentifier.fromString(getAnnotation(msg, APP_PROPERTY_RESOURCE, String.class));
        checkDeviceEnabled(messageAddress, checkAttempt -> {
            if (checkAttempt.failed()) {
                MessageHelper.rejected(delivery, AmqpError.INTERNAL_ERROR.toString(), "cannot determine device status");
                link.close(condition(AmqpError.INTERNAL_ERROR.toString(), "internal error"));
            } else {
                boolean deviceEnabled = checkAttempt.result();
                if (deviceEnabled) {
                    downstreamAdapter.processMessage(link, delivery, msg);
                } else {
                    logger.debug("device {}/{} does not exist or is not enabled, closing link",
                            messageAddress.getTenantId(), messageAddress.getResourceId());
                    MessageHelper.rejected(delivery, AmqpError.PRECONDITION_FAILED.toString(), "device non-existent/disabled");
                    link.close(condition(AmqpError.PRECONDITION_FAILED.toString(), "device non-existent/disabled"));
                }
            }
        });
    }

    /**
     * Gets the Quality-of-Service this endpoint uses for messages received from upstream clients.
     * 
     * @return The QoS.
     */
    protected abstract ProtonQoS getEndpointQos();

    /**
     * Verifies that a message passes <em>formal</em> checks regarding e.g.
     * required headers, content type and payload format.
     * 
     * @param targetAddress The address the message has been received on.
     * @param message The message to check.
     * @return {@code true} if the message passes all checks and can be forwarded downstream.
     */
    protected abstract boolean passesFormalVerification(final ResourceIdentifier targetAddress, final Message message);
}
