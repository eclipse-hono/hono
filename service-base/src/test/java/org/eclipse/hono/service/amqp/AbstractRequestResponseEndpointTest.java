/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.function.BiFunction;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RequestResponseResult;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
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
 * Tests verifying behavior of {@link AbstractRequestResponseEndpoint}.
 */
public class AbstractRequestResponseEndpointTest {

    private static final ResourceIdentifier resource = ResourceIdentifier.from("endpoint", Constants.DEFAULT_TENANT, null);
    private static final ResourceIdentifier REPLY_RESOURCE = ResourceIdentifier.from("endpoint",
            Constants.DEFAULT_TENANT, "reply-to");

    private ProtonConnection connection;
    private Vertx vertx;
    private EventBus eventBus;
    private ProtonReceiver receiver;
    private ProtonSender sender;
    private BiFunction<Message, ResourceIdentifier, Future<Message>> requestMessageHandler;
    private BiFunction<ResourceIdentifier, Message, Boolean> formalMessageVerification;
    private AuthorizationService authService;
    private AbstractRequestResponseEndpoint<ServiceConfigProperties> endpoint;

    /**
     * Initializes common fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {

        connection = mock(ProtonConnection.class);
        vertx = mock(Vertx.class);
        eventBus = mock(EventBus.class);
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

        authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(any(HonoUser.class), any(ResourceIdentifier.class), anyString()))
            .thenReturn(Future.succeededFuture(Boolean.TRUE));
        formalMessageVerification = mock(BiFunction.class);
        when(formalMessageVerification.apply(any(ResourceIdentifier.class), any(Message.class)))
                .thenReturn(Boolean.TRUE);
        requestMessageHandler = mock(BiFunction.class);
        endpoint = getEndpoint();
    }

    private boolean hasStatusCode(final Message msg, final int expectedStatus) {
        return AmqpUtils.getStatus(msg) == expectedStatus;
    }

    private AbstractRequestResponseEndpoint<ServiceConfigProperties> getEndpoint() {

        final AbstractRequestResponseEndpoint<ServiceConfigProperties> endpoint = new AbstractRequestResponseEndpoint<>(vertx) {

            @Override
            public String getName() {
                return "test";
            }

            @Override
            protected boolean passesFormalVerification(final ResourceIdentifier targetAddress, final Message message) {
                return formalMessageVerification.apply(targetAddress, message);
            }

            @Override
            protected Future<Message> handleRequestMessage(
                    final Message requestMessage,
                    final ResourceIdentifier targetAddress,
                    final SpanContext spanContext) {
                return requestMessageHandler.apply(requestMessage, targetAddress);
            }
        };
        endpoint.setConfiguration(new ServiceConfigProperties());
        endpoint.setAuthorizationService(authService);
        return endpoint;
    }

    /**
     * Verifies that the endpoint closes a receiver that wants to use <em>at-most-once</em>
     * delivery semantics.
     */
    @Test
    public void testOnLinkAttachClosesReceiverUsingAtMostOnceQoS() {

        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_MOST_ONCE);
        endpoint.onLinkAttach(connection, receiver, resource);

        verify(receiver).close();
    }

    /**
     * Verifies that the endpoint opens a receiver under normal circumstances.
     */
    @Test
    public void testOnLinkAttachOpensReceiver() {

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

        endpoint.onLinkAttach(connection, sender, resource);

        verify(sender).setCondition(any());
        verify(sender).close();
    }

    /**
     * Verifies that the endpoint rejects malformed request messages.
     */
    @Test
    public void testHandleRequestMessageRejectsMalformedMessage() {

        final Message msg = ProtonHelper.message();
        final ProtonConnection con = mock(ProtonConnection.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);

        // WHEN a malformed message is received
        when(formalMessageVerification.apply(any(ResourceIdentifier.class), any(Message.class)))
            .thenReturn(Boolean.FALSE);
        endpoint.handleRequestMessage(con, receiver, resource, delivery, msg);

        // THEN the message is rejected
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), eq(Boolean.TRUE));
        assertThat(deliveryState.getValue()).isInstanceOf(Rejected.class);
        verify(receiver, never()).close();
        verify(receiver).flow(1);
        // and not being processed
        verify(requestMessageHandler, never()).apply(any(Message.class), any(ResourceIdentifier.class));
    }

    /**
     * Verifies that the endpoint rejects request messages for operations the client
     * is not authorized to invoke.
     */
    @Test
    public void testHandleRequestMessageRejectsUnauthorizedRequests() {

        final Message msg = ProtonHelper.message();
        msg.setSubject("unauthorized");
        msg.setReplyTo(REPLY_RESOURCE.toString());
        msg.setCorrelationId(UUID.randomUUID().toString());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(authService.isAuthorized(any(HonoUser.class), any(ResourceIdentifier.class), anyString()))
            .thenReturn(Future.succeededFuture(Boolean.FALSE));
        endpoint.onLinkAttach(connection, sender, REPLY_RESOURCE);

        // WHEN a request for an operation is received that the client is not authorized to invoke
        endpoint.handleRequestMessage(connection, receiver, resource, delivery, msg);

        // THEN the message is accepted
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), eq(Boolean.TRUE));
        assertThat(deliveryState.getValue()).isInstanceOf(Accepted.class);
        verify(receiver, never()).close();
        verify(authService).isAuthorized(AmqpUtils.PRINCIPAL_ANONYMOUS, resource, "unauthorized");
        // but not being processed
        verify(requestMessageHandler, never()).apply(any(Message.class), any(ResourceIdentifier.class));
        // and a response is sent to the client with status 403
        verify(sender).send(argThat(m -> hasStatusCode(m, HttpURLConnection.HTTP_FORBIDDEN)));
    }

    /**
     * Verifies that the endpoint sends a response with a 503 status code to the client if a request
     * cannot be processed because of a downstream service being unavailable.
     */
    @Test
    public void testHandleRequestMessageSendsResponseForMalformedPayload() {

        testHandleRequestMessageSendsResponseWithStatusCode(
                new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST),
                HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that the endpoint sends a response with a 500 status code to the client if a request
     * fails with an unknown error.
     */
    @Test
    public void testHandleRequestMessageSendsResponseForFailedRequests() {

        testHandleRequestMessageSendsResponseWithStatusCode(new RuntimeException(), HttpURLConnection.HTTP_INTERNAL_ERROR);
    }

    private void testHandleRequestMessageSendsResponseWithStatusCode(final Throwable error, final int expectedStatus) {

        final Message msg = ProtonHelper.message();
        msg.setSubject("get");
        msg.setReplyTo(REPLY_RESOURCE.toString());
        msg.setCorrelationId(UUID.randomUUID().toString());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);

        when(requestMessageHandler.apply(any(Message.class), any(ResourceIdentifier.class)))
            .thenReturn(Future.failedFuture(error));

        endpoint.onLinkAttach(connection, sender, REPLY_RESOURCE);

        // WHEN a request for an operation is received that the client is authorized to invoke
        endpoint.handleRequestMessage(connection, receiver, resource, delivery, msg);

        // THEN then the message is accepted
        verify(delivery).disposition(argThat(d -> d instanceof Accepted), eq(Boolean.TRUE));
        // and being processed
        verify(requestMessageHandler).apply(any(Message.class), any(ResourceIdentifier.class));

        // and a response with status 500 is sent to the client
        verify(sender).send(argThat(m -> hasStatusCode(m, expectedStatus)));
        verify(receiver).flow(1);
    }

    /**
     * Verifies that the endpoint processes request messages for operations the client
     * is authorized to invoke.
     */
    @Test
    public void testHandleRequestMessageProcessesAuthorizedRequests() {

        final Message request = ProtonHelper.message();
        request.setSubject("get");
        request.setReplyTo(REPLY_RESOURCE.toString());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final Message response = ProtonHelper.message();
        response.setAddress(REPLY_RESOURCE.toString());
        when(requestMessageHandler.apply(any(Message.class), any(ResourceIdentifier.class)))
            .thenReturn(Future.succeededFuture(response));

        endpoint.onLinkAttach(connection, sender, REPLY_RESOURCE);

        // WHEN a request for an operation is received that the client is authorized to invoke
        endpoint.handleRequestMessage(connection, receiver, resource, delivery, request);

        // THEN then the message is accepted
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), eq(Boolean.TRUE));
        assertThat(deliveryState.getValue()).isInstanceOf(Accepted.class);
        verify(receiver, never()).close();
        verify(authService).isAuthorized(AmqpUtils.PRINCIPAL_ANONYMOUS, resource, "get");
        // and being processed
        verify(requestMessageHandler).apply(any(Message.class), any(ResourceIdentifier.class));

        // and the response is sent to the client
        verify(sender).send(eq(response));
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

    /**
     * Verifies that the AMQP reply created by the helper from a JSON response
     * contains a cache control property.
     */
    @Test
    public void testGetAmqpReplyAddsCacheDirective() {

        // GIVEN a response that is not supposed to be cached by a client
        final CacheDirective directive = CacheDirective.noCacheDirective();
        final Message request = ProtonHelper.message();
        request.setCorrelationId("message-id");
        final var response = new RequestResponseResult<JsonObject>(
                HttpURLConnection.HTTP_OK,
                new JsonObject(),
                directive,
                null);

        // WHEN creating the AMQP message for the response
        final Message reply = AbstractRequestResponseEndpoint.getAmqpReply(
                "endpoint",
                "my-tenant",
                request,
                response);

        // THEN the message contains the corresponding cache control property
        assertThat(AmqpUtils.getCacheDirective(reply)).isEqualTo(directive.toString());
    }
}
