/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
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

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.Set;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.messaging.DownstreamAdapter;
import org.eclipse.hono.messaging.ErrorConditions;
import org.eclipse.hono.messaging.MessageForwardingEndpoint;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * Verifies behavior of the {@link MessageForwardingEndpoint}.
 *
 */
public class MessageForwardingEndpointTest {

    private static final String SECRET = "hfguisdauifsuifhwebfjkhsdfuigsdafigsdaozfgaDSBCMBGQ";
    private Vertx vertx = Vertx.vertx();
    private RegistrationAssertionHelper tokenValidator;

    /**
     * Initializes shared properties.
     */
    @Before
    public void init() {
        tokenValidator = mock(RegistrationAssertionHelper.class);
    }

    /**
     * Verifies that the endpoint rejects messages that do not pass formal verification.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageHandlerRejectsMalformedMessage() {

        // GIVEN an endpoint with an attached client
        final ProtonConnection connection = mock(ProtonConnection.class);
        when(connection.getRemoteContainer()).thenReturn("test-client");
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString("telemetry/tenant");
        ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
        when(receiver.handler(messageHandler.capture())).thenReturn(receiver);
        when(receiver.closeHandler(any(Handler.class))).thenReturn(receiver);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        final DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        doAnswer(invocation -> {
            invocation.getArgumentAt(1, Handler.class).handle(Future.succeededFuture(null));
            return null;
        }).when(adapter).onClientAttach(any(UpstreamReceiver.class), any(Handler.class));

        MessageForwardingEndpoint<ServiceConfigProperties> endpoint = getEndpoint(false);
        endpoint.setDownstreamAdapter(adapter);
        endpoint.onLinkAttach(connection, receiver, targetAddress);

        // WHEN a client sends a malformed message
        Message message = ProtonHelper.message("malformed");
        ProtonDelivery upstreamDelivery = mock(ProtonDelivery.class);
        messageHandler.getValue().handle(upstreamDelivery, message);

        // THEN a the endpoint rejects the message
        ArgumentCaptor<Rejected> deliveryState = ArgumentCaptor.forClass(Rejected.class);
        verify(upstreamDelivery).disposition(deliveryState.capture(), eq(Boolean.TRUE));
        assertThat(deliveryState.getValue().getError().getCondition(), is(AmqpError.DECODE_ERROR));
        // but does not close the link
        verify(receiver, never()).close();
    }

    /**
     * Verifies that the endpoint does not open a link with a client if the
     * downstream messaging network is not available.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testOnLinkAttachClosesLinkIfDownstreamIsNotAvailable() {

        // GIVEN an endpoint without a connection to the downstream messaging network
        final ProtonConnection connection = mock(ProtonConnection.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString("telemetry/tenant");
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        final DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        doAnswer(invocation -> {
            invocation.getArgumentAt(1, Handler.class).handle(Future.failedFuture("downstream not available"));
            return null;
        }).when(adapter).onClientAttach(any(UpstreamReceiver.class), any(Handler.class));

        MessageForwardingEndpoint<ServiceConfigProperties> endpoint = getEndpoint(false);
        endpoint.setDownstreamAdapter(adapter);

        // WHEN a client tries to attach
        endpoint.onLinkAttach(connection, receiver, targetAddress);

        // THEN the endpoint closes the link
        ArgumentCaptor<ErrorCondition> errorCondition = ArgumentCaptor.forClass(ErrorCondition.class);
        verify(receiver).setCondition(errorCondition.capture());
        assertThat(errorCondition.getValue().getCondition(), is(AmqpError.PRECONDITION_FAILED));
        verify(receiver).close();
    }

    /**
     * Verifies that the endpoint does not open a link with a client that uses an unsupported
     * delivery mode.
     */
    @Test
    public void testOnLinkAttachClosesLinkIfClientWantsToUseUnsupportedDeliveryMode() {

        // GIVEN an endpoint
        MessageForwardingEndpoint<ServiceConfigProperties> endpoint = getEndpoint();

        // WHEN a client tries to attach using an unsupported delivery mode
        final ProtonConnection connection = mock(ProtonConnection.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString("telemetry/tenant");
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        endpoint.onLinkAttach(connection, receiver, targetAddress);

        // THEN the endpoint closes the link
        ArgumentCaptor<ErrorCondition> errorCondition = ArgumentCaptor.forClass(ErrorCondition.class);
        verify(receiver).setCondition(errorCondition.capture());
        assertThat(errorCondition.getValue(), is(ErrorConditions.ERROR_UNSUPPORTED_DELIVERY_MODE));
        verify(receiver).close();
    }

    /**
     * Verifies that a message containing a matching registration assertion is
     * forwarded to the downstream adapter.
     */
    @Test
    public void testForwardMessageAcceptsCorrectRegistrationAssertion() {

        final String validToken = getToken(SECRET, "tenant", "4711");
        UpstreamReceiver client = mock(UpstreamReceiver.class);
        ProtonDelivery delivery = mock(ProtonDelivery.class);
        DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        when(tokenValidator.isValid(validToken, "tenant", "4711")).thenReturn(Boolean.TRUE);
        MessageForwardingEndpoint<ServiceConfigProperties> endpoint = getEndpoint();
        endpoint.setRegistrationAssertionValidator(tokenValidator);
        endpoint.setDownstreamAdapter(adapter);

        // WHEN processing a message bearing a valid registration assertion
        Message msg = ProtonHelper.message();
        MessageHelper.addRegistrationAssertion(msg, validToken);
        MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, "telemetry/tenant/4711");
        endpoint.forwardMessage(client, delivery, msg);

        // THEN the message is sent downstream
        verify(adapter).processMessage(client, delivery, msg);
        verify(client, times(0)).close(any(ErrorCondition.class));
        // and the assertion has been removed from the message
        assertThat("downstream message should not contain registration assertion",
                MessageHelper.getRegistrationAssertion(msg), is(nullValue()));
    }

    /**
     * Verifies that a message containing a registration assertion for a tenant
     * other than the one from the message's target address is rejected.
     */
    @Test
    public void testProcessMessageRejectsRegistrationAssertionForWrongTenant() {

        final String invalidToken = getToken(SECRET, "wrong-tenant", "4711");
        UpstreamReceiver client = mock(UpstreamReceiver.class);
        ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(tokenValidator.isValid(invalidToken, "tenant", "4711")).thenReturn(Boolean.FALSE);
        MessageForwardingEndpoint<ServiceConfigProperties> endpoint = getEndpoint();
        endpoint.setRegistrationAssertionValidator(tokenValidator);

        Message msg = ProtonHelper.message();
        MessageHelper.addRegistrationAssertion(msg, invalidToken);
        MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, "telemetry/tenant/4711");
        endpoint.forwardMessage(client, delivery, msg);

        verify(delivery).disposition(any(Rejected.class), anyBoolean());
        verify(client, never()).close(any(ErrorCondition.class));
    }

    private MessageForwardingEndpoint<ServiceConfigProperties> getEndpoint() {
        return getEndpoint(true);
    }

    private MessageForwardingEndpoint<ServiceConfigProperties> getEndpoint(final boolean passFormalVerification) {

        MessageForwardingEndpoint<ServiceConfigProperties> endpoint = new MessageForwardingEndpoint<ServiceConfigProperties>(vertx) {

            @Override
            public String getName() {
                return "test";
            }

            @Override
            protected boolean passesFormalVerification(final ResourceIdentifier targetAddress, final Message message) {
                return passFormalVerification;
            }

            @Override
            protected Set<ProtonQoS> getEndpointQos() {
                return EnumSet.of(ProtonQoS.AT_MOST_ONCE);
            }
        };
        endpoint.setMetrics(mock(MessagingMetrics.class));
        return endpoint;
    }

    private String getToken(final String secret, final String tenantId, final String deviceId) {

        return RegistrationAssertionHelperImpl.forSharedSecret(secret, 10).getAssertion(tenantId, deviceId);
    }
}
