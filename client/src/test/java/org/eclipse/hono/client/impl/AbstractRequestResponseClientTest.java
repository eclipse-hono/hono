/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link AbstractRequestResponseClient}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AbstractRequestResponseClientTest {

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private static final String MESSAGE_ID = "messageid";
    private ProtonReceiver recv;
    private ProtonSender sender;
    private Context context;
    private Vertx vertx;
    private AbstractRequestResponseClient<SimpleRequestResponseResult> client;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        vertx = mock(Vertx.class);
        context = mock(Context.class);
        doAnswer(invocation -> {
            Handler<Void> handler = invocation.getArgumentAt(0, Handler.class);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(any(Handler.class));
        when(context.owner()).thenReturn(vertx);

        Target target = mock(Target.class);
        when(target.getAddress()).thenReturn("peer/tenant");

        recv = mock(ProtonReceiver.class);
        when(recv.isOpen()).thenReturn(Boolean.TRUE);
        sender = mock(ProtonSender.class);
        when(sender.getCredit()).thenReturn(10);
        when(sender.getRemoteTarget()).thenReturn(target);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);

        client = getClient("tenant", sender, recv);
        // do not time out requests by default
        client.setRequestTimeout(0);
    }

    /**
     * Verifies that the client fails the handler for sending a request message
     * with a {@link ServerErrorException} if the link to the peer has no credit left.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsWithServerErrorExceptionIfSendQueueFull(final TestContext ctx) {

        // GIVEN a request-response client with a full send queue
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);

        // WHEN sending a request message
        final Async sendFailure = ctx.async();
        client.createAndSendRequest("get", null, ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(ServerErrorException.class.isInstance(t));
            sendFailure.complete();
        }));

        // THEN the message is not sent and the request result handler is failed
        sendFailure.await();
        verify(sender, never()).send(any(Message.class));
    }

    /**
     * Verifies that the client creates and sends a message based on provided headers and payload
     * and sets a timer for canceling the request if no response is received.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateAndSendRequestSendsProperRequestMessage(final TestContext ctx) {

        // GIVEN a request-response client that times out requests after 200 ms
        client.setRequestTimeout(200);

        // WHEN sending a request message with some headers and payload
        final JsonObject payload = new JsonObject().put("key", "value");
        final Map<String, Object> props = Collections.singletonMap("test-key", "test-value");
        client.createAndSendRequest("get", props, payload, s -> {});

        // THEN the message is sent and the message being sent contains the headers as application properties
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        assertThat(messageCaptor.getValue(), is(notNullValue()));
        assertThat(messageCaptor.getValue().getBody(), is(notNullValue()));
        assertThat(messageCaptor.getValue().getBody(), instanceOf(AmqpValue.class));
        AmqpValue body = (AmqpValue) messageCaptor.getValue().getBody();
        assertThat(body.getValue(), is(payload.encode()));
        assertThat(messageCaptor.getValue().getApplicationProperties(), is(notNullValue()));
        assertThat(messageCaptor.getValue().getApplicationProperties().getValue().get("test-key"), is("test-value"));
        // and a timer has been set to time out the request after 200 ms
        verify(vertx).setTimer(eq(200L), any(Handler.class));
    }

    /**
     * Verifies that the client fails the result handler if the peer rejects
     * the request message.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCreateAndSendRequestFailsOnRejectedMessage(final TestContext ctx) {

        // GIVEN a request-response client that times out requests after 200 ms
        client.setRequestTimeout(200);

        // WHEN sending a request message with some headers and payload
        final Async sendFailure = ctx.async();
        final JsonObject payload = new JsonObject().put("key", "value");
        client.createAndSendRequest("get", null, payload, ctx.asyncAssertFailure(t -> {
            sendFailure.complete();
        }));
        // and the peer rejects the message
        final Rejected rejected = new Rejected();
        rejected.setError(ProtonHelper.condition("bad-request", "request message is malformed"));
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.getRemoteState()).thenReturn(rejected);
        final ArgumentCaptor<Handler> dispositionHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sender).send(any(Message.class), dispositionHandlerCaptor.capture());
        dispositionHandlerCaptor.getValue().handle(delivery);

        // THEN the result handler is failed
        sendFailure.await();
    }

    /**
     * Verifies that the client passes a response message to the handler registered for the request that
     * the response correlates with.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandleResponseInvokesHandlerForMatchingCorrelationId(final TestContext ctx) {

        // GIVEN a request message that has been sent to a peer
        final Async responseReceived = ctx.async();
        client.createAndSendRequest("request", null, null, ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(200, s.getStatus());
            ctx.assertEquals("payload", s.getPayload());
            responseReceived.complete();
        }));

        // WHEN a response is received for the request
        final Message response = ProtonHelper.message("payload");
        response.setCorrelationId(MESSAGE_ID);
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, 200);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);

        // THEN the response is passed to the handler registered with the request
        responseReceived.await();
        verify(vertx, never()).setTimer(anyLong(), any(Handler.class));
    }

    /**
     * Verifies that the client cancels and fails a request for which no response
     * has been received after a certain amount of time. The request is then
     * failed with a {@link ServerErrorException}.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCancelRequestFailsResponseHandler(final TestContext ctx) {

        // GIVEN a request-response client which times out requests after 200 ms
        client.setRequestTimeout(200);

        // WHEN no response is received for a request sent to the peer
        doAnswer(invocation -> {
            // do not wait 200ms before running the timeout task but instead
            // run it immediately
            Handler<Long> task = invocation.getArgumentAt(1, Handler.class);
            task.handle(1L);
            return null;
        }).when(vertx).setTimer(anyLong(), any(Handler.class));
        final Async requestFailure = ctx.async();
        client.createAndSendRequest("request", null, null, ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(ServerErrorException.class.isInstance(t));
            requestFailure.complete();
        }));

        // THEN the request handler is failed
        requestFailure.await();
    }

    /**
     * Verifies that a response handler is immediately failed with a
     * {@link ServerErrorException} when the sender link is not open (yet).
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsIfSenderIsNotOpen(final TestContext ctx) {

        // GIVEN a client whose sender and receiver are not open
        when(sender.isOpen()).thenReturn(Boolean.FALSE);

        // WHEN sending a request
        Async requestFailure = ctx.async();
        client.createAndSendRequest("get", null, ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(ServerErrorException.class.isInstance(t));
            requestFailure.complete();
        }));

        // THEN the request fails immediately
        requestFailure.await();
    }

    /**
     * Verifies that a response handler is immediately failed with a
     * {@link ServerErrorException} when the receiver link is not open (yet).
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsIfReceiverIsNotOpen(final TestContext ctx) {

        // GIVEN a client whose sender and receiver are not open
        when(recv.isOpen()).thenReturn(Boolean.FALSE);

        // WHEN sending a request
        Async requestFailure = ctx.async();
        client.createAndSendRequest("get", null, ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(ServerErrorException.class.isInstance(t));
            requestFailure.complete();
        }));

        // THEN the request fails immediately
        requestFailure.await();
    }

    private AbstractRequestResponseClient<SimpleRequestResponseResult> getClient(final String tenant, final ProtonSender sender, final ProtonReceiver receiver) {

        return new AbstractRequestResponseClient<SimpleRequestResponseResult>(context, new ClientConfigProperties(), tenant, sender, receiver) {

            @Override
            protected String getName() {
                return "peer";
            }

            @Override
            protected String createMessageId() {
                return MESSAGE_ID;
            }

            @Override
            protected SimpleRequestResponseResult getResult(int status, String payload) {
                return SimpleRequestResponseResult.from(status, payload);
            }
        };
    }
}
