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
import static org.eclipse.hono.util.MessageHelper.getAnnotation;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.BaseEndpoint;
import org.eclipse.hono.service.amqp.UpstreamReceiver;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.metrics.CounterService;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
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
public abstract class MessageForwardingEndpoint<T extends ServiceConfigProperties> extends BaseEndpoint<T> {

    private CounterService                counterService = NullCounterService.getInstance();
    private DownstreamAdapter             downstreamAdapter;
    private MessageConsumer<String>       clientDisconnectListener;
    private RegistrationAssertionHelper   registrationAssertionValidator;

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
            startFuture.fail("no downstream adapter configured on endpoint");
        } else if (registrationAssertionValidator == null) {
            startFuture.fail("no registration assertion validator has been set");
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

        if (!Arrays.stream(getEndpointQos()).anyMatch(qos -> qos.equals(receiver.getRemoteQoS()))) {
            logger.debug("client [{}] wants to use unsupported delivery mode {} for endpoint [name: {}, QoS: {}], closing link", 
                    con.getRemoteContainer(), receiver.getRemoteQoS(), getName(), getEndpointQos());
            receiver.setCondition(ErrorConditions.ERROR_UNSUPPORTED_DELIVERY_MODE);
            receiver.close();
        } else {
            receiver.setQoS(receiver.getRemoteQoS());
            final String linkId = UUID.randomUUID().toString();
            final UpstreamReceiver link = UpstreamReceiver.newUpstreamReceiver(linkId, receiver, receiver.getRemoteQoS());

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
                            rejectMessage(delivery, ProtonHelper.condition(AmqpError.DECODE_ERROR, "malformed message"), link);
                            onLinkDetach(link, condition(AmqpError.DECODE_ERROR.toString(), "invalid message received"));
                        }
                    }).open();
                    logger.debug("establishing link with client [{}]", con.getRemoteContainer());
                    counterService.increment(MetricConstants.metricNameUpstreamLinks(targetAddress.toString()));
                } else {
                    // we cannot connect to downstream container, reject client
                    link.close(condition(AmqpError.PRECONDITION_FAILED, "no consumer available for target"));
                }
            });
        }
    }

    final void forwardMessage(final UpstreamReceiver link, final ProtonDelivery delivery, final Message msg) {

        final ResourceIdentifier messageAddress = ResourceIdentifier.fromString(getAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, String.class));
        final String token = MessageHelper.getRegistrationAssertion(msg);

        if (assertRegistration(token, messageAddress)) {
            downstreamAdapter.processMessage(link, delivery, msg);
        } else {
            logger.debug("failed to validate device registration status");
            rejectMessage(delivery, ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, "device non-existent/disabled"), link);
            link.close(condition(AmqpError.PRECONDITION_FAILED.toString(), "device non-existent/disabled"));
        }
    }

    private boolean assertRegistration(final String token, final ResourceIdentifier resource) {

        if (token == null) {
            logger.debug("token is null");
            return false;
        } else {
            return registrationAssertionValidator.isValid(token, resource.getTenantId(), resource.getResourceId());
        }
    }

    private void rejectMessage(final ProtonDelivery deliveryToReject, final ErrorCondition error, final UpstreamReceiver client) {
        MessageHelper.rejected(deliveryToReject, error);
    }

    /**
     * Gets the delivery modes this endpoint supports.
     * <p>
     * The {@link #onLinkAttach(ProtonConnection, ProtonReceiver, ResourceIdentifier)} method will reject a client's
     * attempt to establish a link that does not match at least one of the delivery modes returned by this method.
     * 
     * @return The delivery modes this endpoint supports.
     */
    protected abstract ProtonQoS[] getEndpointQos();
}
