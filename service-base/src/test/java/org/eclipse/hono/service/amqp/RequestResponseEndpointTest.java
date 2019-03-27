/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.amqp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.booleanThat;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;

/**
 * Tests verifying behavior of {@link RequestResponseEndpoint}.
 */
@RunWith(MockitoJUnitRunner.class)
public class RequestResponseEndpointTest {

    /**
     * 
     */
    private static final String EVENT_BUS_ADDRESS = "requests";
    private static final ResourceIdentifier resource = ResourceIdentifier.from("endpoint", Constants.DEFAULT_TENANT, null);
    private static final ResourceIdentifier REPLY_RESOURCE = ResourceIdentifier.from("endpoint",
            Constants.DEFAULT_TENANT, "reply-to");

    @Mock
    private ProtonConnection connection;
    @Mock
    private Vertx vertx;
    @Mock
    private EventBus eventBus;

    private ProtonReceiver receiver;
    private ProtonSender sender;


    /**
     * Initializes common fixture.
     */
    @Before
    public void setUp() {

        receiver = mock(ProtonReceiver.class);
        when(receiver.handler(any())).thenReturn(receiver);
        when(receiver.closeHandler(any())).thenReturn(receiver);
        when(receiver.setAutoAccept(any(Boolean.class))).thenReturn(receiver);
        when(receiver.setPrefetch(any(Integer.class))).thenReturn(receiver);
        when(receiver.setQoS(any(ProtonQoS.class))).thenReturn(receiver);

        when(vertx.eventBus()).thenReturn(eventBus);

        final ProtonSession session = mock(ProtonSession.class);
        when(session.getConnection()).thenReturn(connection);
        sender = mock(ProtonSender.class);
        when(sender.getName()).thenReturn("mocked sender");
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(sender.getSession()).thenReturn(session);
    }

    /**
     * Verifies that the endpoint closes a receiver that wants to use <em>at-most-once</em>
     * delivery semantics.
     */
    @Test
    public void testOnLinkAttachClosesReceiverUsingAtMostOnceQoS() {

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        endpoint.onLinkAttach(connection, receiver, resource);

        verify(receiver).close();
    }

    /**
     * Verifies that the endpoint opens a receiver under normal circumstances.
     */
    @Test
    public void testOnLinkAttachOpensReceiver() {

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        endpoint.onLinkAttach(connection, receiver, resource);

        verify(receiver).handler(any(ProtonMessageHandler.class));
        verify(receiver).open();
        verify(receiver, never()).close();
    }

    /**
     * Verifies that the endpoint closes a sender that does not contain a source address
     * that is not suitable as a reply-to-address.
     */
    @Test
    public void testOnLinkAttachClosesSenderWithoutAppropriateReplyAddress() {

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true);

        endpoint.onLinkAttach(connection, sender, resource);

        verify(sender).setCondition(any());
        verify(sender).close();
    }

    /**
     * Verifies that the endpoint rejects malformed request messages.
     */
    @Test
    public void testHandleMessageRejectsMalformedMessage() {

        final Message msg = ProtonHelper.message();
        final ProtonConnection con = mock(ProtonConnection.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(false);

        // WHEN a malformed message is received
        endpoint.handleRequestMessage(con, receiver, resource, delivery, msg);

        // THEN the link is closed and the message is rejected
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), booleanThat(is(Boolean.TRUE)));
        assertThat(deliveryState.getValue(), instanceOf(Rejected.class));
        verify(receiver, never()).close();
        verify(receiver).flow(1);
    }

    /**
     * Verifies that the endpoint rejects request messages for operations the client
     * is not authorized to invoke.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandleMessageSendsResponseForUnauthorizedRequests() {

        final Message msg = ProtonHelper.message();
        msg.setSubject("unauthorized");
        msg.setReplyTo(REPLY_RESOURCE.toString());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(any(HonoUser.class), any(ResourceIdentifier.class), anyString())).thenReturn(Future.succeededFuture(Boolean.FALSE));
        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true);
        endpoint.setAuthorizationService(authService);
        endpoint.onLinkAttach(connection, sender, REPLY_RESOURCE);

        // WHEN a request for an operation is received that the client is not authorized to invoke
        endpoint.handleRequestMessage(connection, receiver, resource, delivery, msg);

        // THEN the message is accepted
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), booleanThat(is(Boolean.TRUE)));
        assertThat(deliveryState.getValue(), instanceOf(Accepted.class));
        verify(receiver, never()).close();
        verify(authService).isAuthorized(Constants.PRINCIPAL_ANONYMOUS, resource, "unauthorized");
        // but not forwarded to the service instance
        verify(eventBus, never()).send(anyString(), any(), any(DeliveryOptions.class), any(Handler.class));
        // and a response is sent to the client with status 403
        verify(sender).send(argThat(m -> hasStatusCode(m, HttpURLConnection.HTTP_FORBIDDEN)));
    }

    /**
     * Verifies that the endpoint sends a response with a 503 status code to the client if a request
     * times out internally.
     */
    @Test
    public void testHandleMessageSendsResponseForTimedOutRequests() {

        testHandleMessageSendsResponseWithStatusCode(new ReplyException(ReplyFailure.TIMEOUT), HttpURLConnection.HTTP_UNAVAILABLE);
    }

    /**
     * Verifies that the endpoint sends a response with a 500 status code to the client if a request
     * fails with an unknown error.
     */
    @Test
    public void testHandleMessageSendsResponseForFailedRequests() {

        testHandleMessageSendsResponseWithStatusCode(new RuntimeException(), HttpURLConnection.HTTP_INTERNAL_ERROR);
    }

    @SuppressWarnings("unchecked")
    private void testHandleMessageSendsResponseWithStatusCode(final Throwable error, final int expectedStatus) {

        final Message msg = ProtonHelper.message();
        msg.setSubject("get");
        msg.setReplyTo(REPLY_RESOURCE.toString());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(any(HonoUser.class), any(ResourceIdentifier.class), anyString())).thenReturn(Future.succeededFuture(Boolean.TRUE));

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true);
        endpoint.setAuthorizationService(authService);
        endpoint.onLinkAttach(connection, sender, REPLY_RESOURCE);

        // WHEN a request for an operation is received that the client is authorized to invoke
        endpoint.handleRequestMessage(connection, receiver, resource, delivery, msg);

        // THEN then the message is accepted
        verify(delivery).disposition(argThat(d -> d instanceof Accepted), booleanThat(is(Boolean.TRUE)));
        // and forwarded to the service instance
        final ArgumentCaptor<Handler<AsyncResult<io.vertx.core.eventbus.Message<Object>>>> replyHandler = ArgumentCaptor.forClass(Handler.class);
        verify(eventBus).send(eq(EVENT_BUS_ADDRESS), any(JsonObject.class), any(DeliveryOptions.class), replyHandler.capture());

        // WHEN the service invocation times out
        replyHandler.getValue().handle(Future.failedFuture(error));

        // THEN a response with status 500 is sent to the client
        verify(sender).send(argThat(m -> hasStatusCode(m, expectedStatus)));
        verify(receiver).flow(1);
    }

    /**
     * Verifies that the endpoint processes request messages for operations the client
     * is authorized to invoke.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandleMessageProcessesAuthorizedRequests() {

        final Message msg = ProtonHelper.message();
        msg.setSubject("get");
        msg.setReplyTo(REPLY_RESOURCE.toString());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(any(HonoUser.class), any(ResourceIdentifier.class), anyString())).thenReturn(Future.succeededFuture(Boolean.TRUE));

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true);
        endpoint.setAuthorizationService(authService);
        endpoint.onLinkAttach(connection, sender, REPLY_RESOURCE);

        // WHEN a request for an operation is received that the client is authorized to invoke
        endpoint.handleRequestMessage(connection, receiver, resource, delivery, msg);

        // THEN then the message gets processed
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), booleanThat(is(Boolean.TRUE)));
        assertThat(deliveryState.getValue(), instanceOf(Accepted.class));
        verify(receiver, never()).close();
        verify(authService).isAuthorized(Constants.PRINCIPAL_ANONYMOUS, resource, "get");
        // and forwarded to the service instance
        final ArgumentCaptor<Handler<AsyncResult<io.vertx.core.eventbus.Message<Object>>>> replyHandler = ArgumentCaptor.forClass(Handler.class);
        verify(eventBus).send(eq(EVENT_BUS_ADDRESS), any(JsonObject.class), any(DeliveryOptions.class), replyHandler.capture());

        // WHEN the service implementation sends the response
        final EventBusMessage response = EventBusMessage.forStatusCode(HttpURLConnection.HTTP_ACCEPTED);
        final io.vertx.core.eventbus.Message<Object> reply = mock(io.vertx.core.eventbus.Message.class);
        when(reply.body()).thenReturn(response.toJson());
        replyHandler.getValue().handle(Future.succeededFuture(reply));

        // THEN the response is sent to the client
        verify(sender).send(any(Message.class));
        verify(receiver).flow(1);
    }

    /**
     * Verify that a second response link to the same address is being rejected.
     */
    @Test
    public void testDuplicateSubscription() {

        final ProtonConnection con1 = mock(ProtonConnection.class);
        final ProtonConnection con2 = mock(ProtonConnection.class);

        final ProtonSender sender1 = mock(ProtonSender.class);
        final ProtonSender sender2 = mock(ProtonSender.class);

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true);

        // WHEN a first sender attaches
        endpoint.onLinkAttach(con1, sender1, REPLY_RESOURCE);

        // THEN open has to be called
        verify(sender1).open();

        // WHEN a second sender attaches
        endpoint.onLinkAttach(con2, sender2, REPLY_RESOURCE);

        // THEN close has to be called
        verify(sender2).close();
    }

    /**
     * Verify that a second response link to the same address is being accepted if the first is released before.
     */
    @Test
    public void testFreeSubscription() {

        final ProtonConnection con1 = mock(ProtonConnection.class);
        final ProtonSession session1 = mock(ProtonSession.class);
        when(session1.getConnection()).thenReturn(con1);
        final ProtonConnection con2 = mock(ProtonConnection.class);

        final ProtonSender sender1 = mock(ProtonSender.class);
        when(sender1.getSession()).thenReturn(session1);
        final ProtonSender sender2 = mock(ProtonSender.class);

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true);

        // WHEN a first sender attaches
        endpoint.onLinkAttach(con1, sender1, REPLY_RESOURCE);

        // THEN open has to be called
        verify(sender1).open();

        // WHEN the connection closed
        endpoint.onConnectionClosed(con1);

        // WHEN a new link is attached
        endpoint.onLinkAttach(con2, sender2, REPLY_RESOURCE);

        // THEN open has to be called
        verify(sender2).open();
    }

    private boolean hasStatusCode(final Message msg, final int expectedStatus) {
        return MessageHelper.getApplicationProperty(msg.getApplicationProperties(), MessageHelper.APP_PROPERTY_STATUS, Integer.class) == expectedStatus;
    }

    private RequestResponseEndpoint<ServiceConfigProperties> getEndpoint(final boolean passesFormalVerification) {

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = new RequestResponseEndpoint<ServiceConfigProperties>(vertx) {

            @Override
            public String getName() {
                return "test";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected String getEventBusServiceAddress() {
                return EVENT_BUS_ADDRESS;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected Future<EventBusMessage> createEventBusRequestMessage(
                    final Message requestMessage,
                    final ResourceIdentifier targetAddress,
                    final HonoUser clientPrincipal) {
                return Future.succeededFuture(EventBusMessage.forOperation(requestMessage)
                        .setJsonPayload(requestMessage));
            }

            @Override
            protected Message getAmqpReply(final EventBusMessage message) {
                final Message msg = ProtonHelper.message();
                MessageHelper.addProperty(msg, MessageHelper.APP_PROPERTY_STATUS, message.getStatus());
                msg.setAddress(message.getReplyToAddress());
                return msg;
            }

            @Override
            protected boolean passesFormalVerification(final ResourceIdentifier targetAddress, final Message message) {
                return passesFormalVerification;
            }
        };
        endpoint.setConfiguration(new ServiceConfigProperties());
        return endpoint;
    }
}
