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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.Set;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
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
    private HonoMessagingConfigProperties config;

    /**
     * Initializes shared properties.
     */
    @Before
    public void init() {
        tokenValidator = mock(RegistrationAssertionHelper.class);
        config = new HonoMessagingConfigProperties();
    }

    /**
     * Verifies that the endpoint rejects messages that do not pass formal verification.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testMessageHandlerRejectsMalformedMessage() {

        // GIVEN an endpoint with an attached client
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString("telemetry/tenant");
        final ProtonConnection connection = mock(ProtonConnection.class);
        when(connection.getRemoteContainer()).thenReturn("test-client");
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        final DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.succeededFuture());
            return null;
        }).when(adapter).onClientAttach(any(UpstreamReceiver.class), any(Handler.class));

        final MessageForwardingEndpoint<HonoMessagingConfigProperties> endpoint = getEndpoint(false);
        endpoint.setDownstreamAdapter(adapter);
        endpoint.onLinkAttach(connection, receiver, targetAddress);
        final ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
        verify(receiver).handler(messageHandler.capture());

        // WHEN a client sends a malformed message
        final Message message = ProtonHelper.message("malformed");
        final ProtonDelivery upstreamDelivery = mock(ProtonDelivery.class);
        messageHandler.getValue().handle(upstreamDelivery, message);

        // THEN the endpoint rejects the message
        final ArgumentCaptor<Rejected> deliveryState = ArgumentCaptor.forClass(Rejected.class);
        verify(upstreamDelivery).disposition(deliveryState.capture(), eq(Boolean.TRUE));
        assertThat(deliveryState.getValue().getError().getCondition(), is(AmqpError.DECODE_ERROR));
        // but does not close the link
        verify(receiver, never()).close();
        // and the message is not forwarded to the downstream adapter
        verify(adapter, never()).processMessage(any(UpstreamReceiver.class), eq(upstreamDelivery), eq(message));
    }

    /**
     * Verifies that the endpoint offers the capability to validate
     * registration assertions if configuration property
     * <em>assertionValidationRequired</em> is {@code true}.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testOnLinkAttachOffersAssertionValidationCapability() {

        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString("telemetry/tenant");
        final ProtonConnection connection = mock(ProtonConnection.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        final DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(adapter).onClientAttach(any(UpstreamReceiver.class), any(Handler.class));

        final MessageForwardingEndpoint<HonoMessagingConfigProperties> endpoint = getEndpoint();
        endpoint.setDownstreamAdapter(adapter);
        endpoint.onLinkAttach(connection, receiver, targetAddress);

        verify(receiver).setOfferedCapabilities(any(Symbol[].class));
    }

    /**
     * Verifies that the endpoint does not open a link with a client if the
     * downstream messaging network is not available.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testOnLinkAttachClosesLinkIfDownstreamIsNotAvailable() {

        // GIVEN an endpoint without a connection to the downstream messaging network
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString("telemetry/tenant");
        final ProtonConnection connection = mock(ProtonConnection.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        final DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("downstream not available"));
            return null;
        }).when(adapter).onClientAttach(any(UpstreamReceiver.class), any(Handler.class));

        final MessageForwardingEndpoint<HonoMessagingConfigProperties> endpoint = getEndpoint();
        endpoint.setDownstreamAdapter(adapter);

        // WHEN a client tries to attach
        endpoint.onLinkAttach(connection, receiver, targetAddress);

        // THEN the endpoint closes the link
        final ArgumentCaptor<ErrorCondition> errorCondition = ArgumentCaptor.forClass(ErrorCondition.class);
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
        MessageForwardingEndpoint<HonoMessagingConfigProperties> endpoint = getEndpoint();

        // WHEN a client tries to attach using an unsupported delivery mode
        final ProtonConnection connection = mock(ProtonConnection.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString("telemetry/tenant");
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        endpoint.onLinkAttach(connection, receiver, targetAddress);

        // THEN the endpoint closes the link
        final ArgumentCaptor<ErrorCondition> errorCondition = ArgumentCaptor.forClass(ErrorCondition.class);
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
        final UpstreamReceiver client = mock(UpstreamReceiver.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        when(tokenValidator.isValid(validToken, "tenant", "4711")).thenReturn(Boolean.TRUE);
        final MessageForwardingEndpoint<HonoMessagingConfigProperties> endpoint = getEndpoint();
        endpoint.setRegistrationAssertionValidator(tokenValidator);
        endpoint.setDownstreamAdapter(adapter);

        // WHEN processing a message bearing a valid registration assertion
        final Message msg = ProtonHelper.message();
        MessageHelper.addRegistrationAssertion(msg, validToken);
        MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, "telemetry/tenant/4711");
        endpoint.forwardMessage(client, delivery, msg);

        // THEN the message is sent downstream
        verify(adapter).processMessage(client, delivery, msg);
        verify(client, never()).close(any(ErrorCondition.class));
        // and the assertion has been removed from the message
        assertThat("downstream message should not contain registration assertion",
                MessageHelper.getRegistrationAssertion(msg), is(nullValue()));
    }

    /**
     * Verifies that a message that does not contain a registration assertion is
     * forwarded to the downstream adapter if the adapter is configured to not
     * require registration assertion validation.
     */
    @Test
    public void testForwardMessageAcceptsMissingRegistrationAssertion() {

        // GIVEN an adapter that does not validate registration assertions
        final String validToken = getToken(SECRET, "tenant", "4711");
        final UpstreamReceiver client = mock(UpstreamReceiver.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final DownstreamAdapter adapter = mock(DownstreamAdapter.class);
        when(tokenValidator.isValid(validToken, "tenant", "4711")).thenReturn(Boolean.TRUE);
        final MessageForwardingEndpoint<HonoMessagingConfigProperties> endpoint = getEndpoint();
        endpoint.setRegistrationAssertionValidator(tokenValidator);
        endpoint.setDownstreamAdapter(adapter);
        config.setAssertionValidationRequired(false);

        // WHEN processing a message lacking a valid registration assertion
        final Message msg = ProtonHelper.message();
        MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, "telemetry/tenant/4711");
        endpoint.forwardMessage(client, delivery, msg);

        // THEN the message is sent downstream
        verify(adapter).processMessage(client, delivery, msg);
    }

    /**
     * Verifies that a message containing a registration assertion for a tenant
     * other than the one from the message's target address is rejected.
     */
    @Test
    public void testProcessMessageRejectsRegistrationAssertionForWrongTenant() {

        final String invalidToken = getToken(SECRET, "wrong-tenant", "4711");
        final UpstreamReceiver client = mock(UpstreamReceiver.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(tokenValidator.isValid(invalidToken, "tenant", "4711")).thenReturn(Boolean.FALSE);
        final MessageForwardingEndpoint<HonoMessagingConfigProperties> endpoint = getEndpoint();
        endpoint.setRegistrationAssertionValidator(tokenValidator);

        final Message msg = ProtonHelper.message();
        MessageHelper.addRegistrationAssertion(msg, invalidToken);
        MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, "telemetry/tenant/4711");
        endpoint.forwardMessage(client, delivery, msg);

        verify(delivery).disposition(any(Rejected.class), anyBoolean());
        verify(client, never()).close(any(ErrorCondition.class));
    }

    private MessageForwardingEndpoint<HonoMessagingConfigProperties> getEndpoint() {
        return getEndpoint(true);
    }

    private MessageForwardingEndpoint<HonoMessagingConfigProperties> getEndpoint(final boolean passFormalVerification) {

        final MessageForwardingEndpoint<HonoMessagingConfigProperties> endpoint = new MessageForwardingEndpoint<HonoMessagingConfigProperties>(vertx) {

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
        endpoint.setConfiguration(config);
        return endpoint;
    }

    private String getToken(final String secret, final String tenantId, final String deviceId) {

        return RegistrationAssertionHelperImpl.forSharedSecret(secret, 10).getAssertion(tenantId, deviceId);
    }
}
