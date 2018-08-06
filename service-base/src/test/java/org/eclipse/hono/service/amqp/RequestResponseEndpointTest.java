/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.hamcrest.MockitoHamcrest.booleanThat;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link RequestResponseEndpoint}.
 */
@RunWith(MockitoJUnitRunner.class)
public class RequestResponseEndpointTest {

    private static final ResourceIdentifier resource = ResourceIdentifier.from("endpoint", Constants.DEFAULT_TENANT, null);
    private static final ResourceIdentifier REPLY_RESOURCE = ResourceIdentifier.from("endpoint",
            Constants.DEFAULT_TENANT, "reply-to");

    @Mock
    private ProtonConnection connection;
    @Mock
    private Vertx vertx;
    @Mock
    private EventBus eventBus;
    @Mock
    private MessageConsumer<JsonObject> messageConsumer;

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

        when(eventBus.<JsonObject> consumer(any(), any())).thenReturn(messageConsumer);

        sender = mock(ProtonSender.class);
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
        endpoint.handleMessage(con, receiver, resource, delivery, msg);

        // THEN the link is closed and the message is rejected
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), booleanThat(is(Boolean.TRUE)));
        assertThat(deliveryState.getValue(), instanceOf(Rejected.class));
        verify(receiver, never()).close();
    }

    /**
     * Verifies that the endpoint rejects request messages for operations the client
     * is not authorized to invoke.
     */
    @Test
    public void testHandleMessageRejectsUnauthorizedRequests() {

        final Message msg = ProtonHelper.message();
        msg.setSubject("unauthorized");
        msg.setReplyTo(REPLY_RESOURCE.toString());
        final ProtonConnection con = mock(ProtonConnection.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(any(HonoUser.class), any(ResourceIdentifier.class), anyString())).thenReturn(Future.succeededFuture(Boolean.FALSE));
        final Future<Void> processingTracker = Future.future();
        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true, processingTracker);
        endpoint.setAuthorizationService(authService);
        endpoint.onLinkAttach(con, sender, REPLY_RESOURCE);

        // WHEN a request for an operation is received that the client is not authorized to invoke
        endpoint.handleMessage(con, receiver, resource, delivery, msg);

        // THEN the the message is rejected
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), booleanThat(is(Boolean.TRUE)));
        assertThat(deliveryState.getValue(), instanceOf(Rejected.class));
        verify(receiver, never()).close();
        verify(authService).isAuthorized(Constants.PRINCIPAL_ANONYMOUS, resource, "unauthorized");
        assertFalse(processingTracker.isComplete());
    }

    /**
     * Verifies that the endpoint processes request messages for operations the client
     * is authorized to invoke.
     */
    @Test
    public void testHandleMessageProcessesAuthorizedRequests() {

        final Message msg = ProtonHelper.message();
        msg.setSubject("get");
        msg.setReplyTo(REPLY_RESOURCE.toString());
        final ProtonConnection con = mock(ProtonConnection.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.isAuthorized(any(HonoUser.class), any(ResourceIdentifier.class), anyString())).thenReturn(Future.succeededFuture(Boolean.TRUE));

        final Future<Void> processingTracker = Future.future();
        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true, processingTracker);
        endpoint.setAuthorizationService(authService);

        endpoint.onLinkAttach(con, sender, REPLY_RESOURCE);

        // WHEN a request for an operation is received that the client is authorized to invoke
        endpoint.handleMessage(con, receiver, resource, delivery, msg);

        // THEN then the message gets processed
        final ArgumentCaptor<DeliveryState> deliveryState = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryState.capture(), booleanThat(is(Boolean.TRUE)));
        assertThat(deliveryState.getValue(), instanceOf(Accepted.class));
        verify(receiver, never()).close();
        verify(authService).isAuthorized(Constants.PRINCIPAL_ANONYMOUS, resource, "get");
        assertTrue(processingTracker.isComplete());
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

        final Future<Void> processingTracker = Future.future();
        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true, processingTracker);

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
        final ProtonConnection con2 = mock(ProtonConnection.class);

        final ProtonSender sender1 = mock(ProtonSender.class);
        final ProtonSender sender2 = mock(ProtonSender.class);

        final Future<Void> processingTracker = Future.future();
        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = getEndpoint(true, processingTracker);

        // WHEN a first sender attaches
        endpoint.onLinkAttach(con1, sender1, REPLY_RESOURCE);

        // THEN open has to be called
        verify(sender1).open();

        // WHEN the connection closed
        endpoint.onConnectionClosed(con1);

        // THEN the unregister method has to be called
        verify(messageConsumer).unregister();

        // WHEN a new link is attached
        endpoint.onLinkAttach(con2, sender2, REPLY_RESOURCE);

        // THEN open has to be called
        verify(sender2).open();
    }

    private RequestResponseEndpoint<ServiceConfigProperties> getEndpoint(final boolean passesFormalVerification) {
        return getEndpoint(passesFormalVerification, Future.future());
    }

    private RequestResponseEndpoint<ServiceConfigProperties> getEndpoint(final boolean passesFormalVerification, final Future<Void> processingTracker) {

        final RequestResponseEndpoint<ServiceConfigProperties> endpoint = new RequestResponseEndpoint<ServiceConfigProperties>(vertx) {

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public void processRequest(final Message message, final ResourceIdentifier targetAddress, final HonoUser clientPrincipal) {
                processingTracker.complete();
            }

            @Override
            protected Message getAmqpReply(final EventBusMessage message) {
                return null;
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
