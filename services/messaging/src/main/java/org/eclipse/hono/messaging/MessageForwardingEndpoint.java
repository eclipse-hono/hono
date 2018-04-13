/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */
package org.eclipse.hono.messaging;

import static io.vertx.proton.ProtonHelper.condition;
import static org.eclipse.hono.util.MessageHelper.getAnnotation;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.service.amqp.AbstractAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A base class for implementing Hono {@code Endpoint}s that forward messages
 * to a downstream container.
 * 
 * @param <T> The type of configuration properties this endpoint understands.
 */
public abstract class MessageForwardingEndpoint<T extends HonoMessagingConfigProperties> extends AbstractAmqpEndpoint<T> {

    private static final Symbol[] OFFERED_CAPS = new Symbol[] { Constants.CAP_REG_ASSERTION_VALIDATION };

    private MessagingMetrics            metrics;
    private DownstreamAdapter           downstreamAdapter;
    private MessageConsumer<String>     clientDisconnectListener;
    private RegistrationAssertionHelper registrationAssertionValidator;

    /**
     * Creates an endpoint for a Vertx instance.
     * 
     * @param vertx The Vertx instance to use.
     */
    protected MessageForwardingEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    /**
     * Sets the object to use for validatingJWT tokens asserting a device's registration
     * status.
     * 
     * @param validator The validator.
     * @throws NullPointerException if validator is {@code null}.
     */
    @Autowired
    @Qualifier("validation")
    public void setRegistrationAssertionValidator(final RegistrationAssertionHelper validator) {
        registrationAssertionValidator = Objects.requireNonNull(validator);
    }

    /**
     * Sets the metric for this service.
     *
     * @param metrics The metric
     */
    @Autowired
    public final void setMetrics(final MessagingMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected final void doStart(final Future<Void> startFuture) {
        if (downstreamAdapter == null) {
            startFuture.fail("no downstream adapter configured on endpoint");
        } else if (config.isAssertionValidationRequired() && registrationAssertionValidator == null) {
            startFuture.fail("no registration assertion validator has been set");
        } else {
            if (!config.isAssertionValidationRequired()) {
                final String msg = new StringBuilder()
                        .append("validation of registration assertions is disabled, ")
                        .append("all clients may publish data on behalf of any device")
                        .toString();
                logger.warn(msg);
            }
            clientDisconnectListener = vertx.eventBus().consumer(
                    Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED,
                    msg -> onClientDisconnect(msg));
            downstreamAdapter.start(startFuture);
        }
    }

    @Override
    protected final void doStop(final Future<Void> stopFuture) {
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
     * @throws NullPointerException if the adapter is {@code null}.
     */
    protected final void setDownstreamAdapter(final DownstreamAdapter adapter) {
        this.downstreamAdapter = Objects.requireNonNull(adapter);
    }

    private void onClientDisconnect(final io.vertx.core.eventbus.Message<String> conId) {
        this.downstreamAdapter.onClientDisconnect(conId.body());
    }

    @Override
    public final void onLinkAttach(final ProtonConnection con, final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {

        if (!getEndpointQos().contains(receiver.getRemoteQoS())) {
            logger.debug("client [{}] wants to use unsupported delivery mode {} for endpoint [name: {}, QoS: {}], closing link", 
                    con.getRemoteContainer(), receiver.getRemoteQoS(), getName(), getEndpointQos());
            receiver.setCondition(ErrorConditions.ERROR_UNSUPPORTED_DELIVERY_MODE);
            receiver.close();
        } else {
            receiver.setQoS(receiver.getRemoteQoS());
            receiver.setTarget(receiver.getRemoteTarget());
            if (config.isAssertionValidationRequired()) {
                receiver.setOfferedCapabilities(OFFERED_CAPS);
            }
            final String linkId = UUID.randomUUID().toString();
            final UpstreamReceiver link = UpstreamReceiver.newUpstreamReceiver(linkId, receiver);

            downstreamAdapter.onClientAttach(link, s -> {
                if (s.succeeded()) {
                    receiver.closeHandler(clientDetached -> {
                        // client has closed link -> inform TelemetryAdapter about client detach
                        onLinkDetach(link);
                        downstreamAdapter.onClientDetach(link);
                        metrics.decrementUpstreamLinks(targetAddress.toString());
                    });
                    receiver.handler((delivery, message) -> {
                        if (passesFormalVerification(targetAddress, message)) {
                            forwardMessage(link, delivery, message);
                        } else {
                            rejectMessage(delivery, ProtonHelper.condition(AmqpError.DECODE_ERROR, "malformed message"), link);
                        }
                    });
                    receiver.open();
                    logger.debug("establishing link with client [{}]", con.getRemoteContainer());
                    metrics.incrementUpstreamLinks(targetAddress.toString());
                } else {
                    // we cannot connect to downstream container, reject client
                    link.close(condition(AmqpError.PRECONDITION_FAILED, "no consumer available for target"));
                }
            });
        }
    }

    /**
     * Closes the link to an upstream client and removes all state kept for it.
     * 
     * @param client The client to detach.
     */
    protected final void onLinkDetach(final UpstreamReceiver client) {
        onLinkDetach(client, null);
    }

    /**
     * Closes the link to an upstream client and removes all state kept for it.
     * 
     * @param client The client to detach.
     * @param error The error condition to convey to the client when closing the link.
     */
    protected final void onLinkDetach(final UpstreamReceiver client, final ErrorCondition error) {
        if (error == null) {
            logger.debug("closing receiver for client [{}]", client.getLinkId());
        } else {
            logger.debug("closing receiver for client [{}]: {}", client.getLinkId(), error.getDescription());
        }
        client.close(error);
    }

    final void forwardMessage(final UpstreamReceiver link, final ProtonDelivery delivery, final Message msg) {

        final ResourceIdentifier messageAddress = ResourceIdentifier.fromString(getAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, String.class));
        final String token = MessageHelper.getAndRemoveRegistrationAssertion(msg);

        if (assertRegistration(token, messageAddress)) {
            downstreamAdapter.processMessage(link, delivery, msg);
        } else {
            logger.debug("failed to validate device registration status");
            rejectMessage(delivery, ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, "device non-existent/disabled"), link);
        }
    }

    private boolean assertRegistration(final String token, final ResourceIdentifier resource) {

        if (config.isAssertionValidationRequired()) {
            if (token == null) {
                logger.debug("registration assertion validation failed due to missing token");
                return false;
            } else {
                return registrationAssertionValidator.isValid(token, resource.getTenantId(), resource.getResourceId());
            }
        } else {
            // validation has been disabled explicitly
            return true;
        }
    }

    private void rejectMessage(final ProtonDelivery deliveryToReject, final ErrorCondition error, final UpstreamReceiver client) {
        MessageHelper.rejected(deliveryToReject, error);
        client.replenish(1);
    }

    /**
     * Gets the delivery modes this endpoint supports.
     * <p>
     * The {@link #onLinkAttach(ProtonConnection, ProtonReceiver, ResourceIdentifier)} method will reject a client's
     * attempt to establish a link that does not match at least one of the delivery modes returned by this method.
     * <p>
     * Changes to the returned collection must not have any impact on the original data set. Returning an
     * unmodifiable collection is recommended.
     * 
     * @return The delivery modes this endpoint supports.
     */
    protected abstract Set<ProtonQoS> getEndpointQos();

    /**
     * Registers a check that succeeds if this endpoint has a usable connection to its
     * downstream container.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        handler.register(getName() + "-endpoint-downstream-connection", status -> {
            if (downstreamAdapter == null) {
                status.complete(Status.KO());
            } else if (downstreamAdapter.isConnected()) {
                status.complete(Status.OK());
            } else {
                status.complete(Status.KO());
            }
        });
    }
}
