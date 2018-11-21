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

package org.eclipse.hono.client.impl;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.opentracing.Span;
import io.vertx.core.buffer.Buffer;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
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
public class AbstractRequestResponseClientTest  {

    private static final String MESSAGE_ID = "messageid";

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private AbstractRequestResponseClient<SimpleRequestResponseResult> client;
    private ExpiringValueCache<Object, SimpleRequestResponseResult> cache;
    private Vertx vertx;
    private Context context;
    private ProtonReceiver receiver;
    private ProtonSender sender;


    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        vertx = mock(Vertx.class);
        context = HonoClientUnitTestHelper.mockContext(vertx);
        receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        sender = HonoClientUnitTestHelper.mockProtonSender();

        final Target target = mock(Target.class);
        when(target.getAddress()).thenReturn("peer/tenant");

        when(sender.getCredit()).thenReturn(10);
        when(sender.getRemoteTarget()).thenReturn(target);

        cache = mock(ExpiringValueCache.class);

        client = getClient("tenant", sender, receiver);
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
        client.createAndSendRequest("get", props, payload.toBuffer(), s -> {});

        // THEN the message is sent and the message being sent contains the headers as application properties
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        assertThat(messageCaptor.getValue(), is(notNullValue()));
        assertThat(messageCaptor.getValue().getBody(), is(notNullValue()));
        assertThat(messageCaptor.getValue().getBody(), instanceOf(Data.class));
        final Buffer body = MessageHelper.getPayload(messageCaptor.getValue());
        assertThat(body.getBytes(), is(payload.toBuffer().getBytes()));
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
        client.createAndSendRequest("get", null, payload.toBuffer(), ctx.asyncAssertFailure(t -> {
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
        client.createAndSendRequest("request", null, (Buffer) null, ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(200, s.getStatus());
            ctx.assertEquals("payload", s.getPayload().toString());
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
            final Handler<Long> task = invocation.getArgument(1);
            task.handle(1L);
            return null;
        }).when(vertx).setTimer(anyLong(), any(Handler.class));
        final Async requestFailure = ctx.async();
        client.createAndSendRequest("request", null, (Buffer) null, ctx.asyncAssertFailure(t -> {
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
        final Async requestFailure = ctx.async();
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
        when(receiver.isOpen()).thenReturn(Boolean.FALSE);

        // WHEN sending a request
        final Async requestFailure = ctx.async();
        client.createAndSendRequest("get", null, ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(ServerErrorException.class.isInstance(t));
            requestFailure.complete();
        }));

        // THEN the request fails immediately
        requestFailure.await();
    }

    /**
     * Verifies that the adapter puts the response from the service to the cache
     * using the default cache timeout if the response does not contain a
     * <em>no-cache</em> cache directive.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateAndSendRequestAddsResponseToCache(final TestContext ctx) {

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);

        // WHEN sending a request
        client.createAndSendRequest("get", (Buffer) null, ctx.asyncAssertSuccess(result -> {
            // THEN the response has been put to the cache
            verify(cache).put(eq("cacheKey"), any(SimpleRequestResponseResult.class),
                    eq(Duration.ofSeconds(RequestResponseClientConfigProperties.DEFAULT_RESPONSE_CACHE_TIMEOUT)));
        }), "cacheKey");
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message response = ProtonHelper.message("result");
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
    }

    /**
     * Verifies that the adapter puts the response from the service to the cache
     * using the max age indicated by a response's <em>max-age</em> cache directive.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateAndSendRequestAddsResponseToCacheWithMaxAge(final TestContext ctx) {

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);

        // WHEN sending a request
        client.createAndSendRequest("get", (Buffer) null, ctx.asyncAssertSuccess(result -> {
            // THEN the response has been put to the cache
            verify(cache).put(eq("cacheKey"), any(SimpleRequestResponseResult.class), eq(Duration.ofSeconds(35)));
        }), "cacheKey");
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message response = ProtonHelper.message("result");
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.maxAgeDirective(35));
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
    }

    /**
     * Verifies that the adapter does not put the response from the service to the cache
     * if the response contains a <em>no-cache</em> cache directive.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateAndSendRequestDoesNotAddResponseToCache(final TestContext ctx) {

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);

        // WHEN sending a request
        client.createAndSendRequest("get", (Buffer) null, ctx.asyncAssertSuccess(result -> {
            // THEN the response is not put to the cache
            verify(cache, never()).put(eq("cacheKey"), any(SimpleRequestResponseResult.class), any(Duration.class));
        }), "cacheKey");
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message response = ProtonHelper.message("result");
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        MessageHelper.addCacheDirective(response, CacheDirective.noCacheDirective());
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);
    }

    /**
     * Verifies that the adapter does not put a response from the service to the cache
     * that does not contain any cache directive but has a <em>non-cacheable</em> status code.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateAndSendRequestDoesNotAddNonCacheableResponseToCache(final TestContext ctx) {

        // GIVEN an adapter with an empty cache
        client.setResponseCache(cache);

        // WHEN getting a 404 response to a request which contains
        // no cache directive
        final Async invocation = ctx.async();
        client.createAndSendRequest("get", (Buffer) null, ctx.asyncAssertSuccess(result -> invocation.complete()), "cacheKey");

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        final Message response = ProtonHelper.message();
        response.setCorrelationId(messageCaptor.getValue().getMessageId());
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_NOT_FOUND);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);

        // THEN the response is not put to the cache
        invocation.await();
        verify(cache, never()).put(eq("cacheKey"), any(SimpleRequestResponseResult.class), any(Duration.class));
    }

    /**
     * Verifies that the client succeeds the result handler if the peer accepts
     * the request message for a one-way request.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testSendOneWayRequestSucceedsOnAcceptedMessage(final TestContext ctx) {

        // GIVEN a request-response client that times out requests after 200 ms
        client.setRequestTimeout(200);

        // WHEN sending a one-way request message with some headers and payload
        final Async sendSuccess = ctx.async();
        final JsonObject payload = new JsonObject().put("key", "value");
        final Map<String, Object> applicationProps = new HashMap<>();

        final Message request = ProtonHelper.message();
        request.setMessageId("12345");
        request.setCorrelationId("23456");
        request.setSubject("aRequest");
        request.setApplicationProperties(new ApplicationProperties(applicationProps));
        MessageHelper.setPayload(request, "application/json", payload.toBuffer());

        client.sendRequest(request, ctx.asyncAssertSuccess(t -> {
            sendSuccess.complete();
        }), null, mock(Span.class));
        // and the peer accepts the message
        final Accepted accepted = new Accepted();
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.getRemoteState()).thenReturn(accepted);
        final ArgumentCaptor<Handler> dispositionHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sender).send(any(Message.class), dispositionHandlerCaptor.capture());
        dispositionHandlerCaptor.getValue().handle(delivery);

        // THEN the result handler is succeeded
        sendSuccess.await();
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
            protected SimpleRequestResponseResult getResult(
                    final int status,
                    final String contentType,
                    final Buffer payload,
                    final CacheDirective cacheDirective) {
                return SimpleRequestResponseResult.from(status, payload, cacheDirective);
            }
        };
    }

    /**
     * Verifies credits available.
     *
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCredits() {
        when(sender.getCredit()).thenReturn(10);
        assertThat(client.getCredit(), is(10));
        when(sender.getCredit()).thenReturn(0);
        assertThat(client.getCredit(), is(0));
    }
}
