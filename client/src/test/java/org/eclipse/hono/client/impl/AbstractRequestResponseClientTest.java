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

package org.eclipse.hono.client.impl;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
    private ProtonReceiver receiver;
    private ProtonSender sender;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        final SpanContext spanContext = mock(SpanContext.class);

        span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);

        vertx = mock(Vertx.class);
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
        client.createAndSendRequest(
                "get",
                Buffer.buffer("hello"),
                ctx.asyncAssertFailure(t -> {
                    // THEN the message is not sent
                    verify(sender, never()).send(any(Message.class));
                    // and the request result handler is failed with a 503
                    assertFailureCause(ctx, span, t, HttpURLConnection.HTTP_UNAVAILABLE);
                }),
                span);
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
        final JsonObject payload = new JsonObject().put("key", "value");
        client.createAndSendRequest(
                "get",
                payload.toBuffer(),
                ctx.asyncAssertFailure(t -> {
                    // THEN the result handler is failed with a 400 status code
                    assertFailureCause(ctx, span, t, HttpURLConnection.HTTP_BAD_REQUEST);
                }),
                span);
        // and the peer rejects the message
        final Rejected rejected = new Rejected();
        rejected.setError(ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, "request message is malformed"));
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.getRemoteState()).thenReturn(rejected);
        final ArgumentCaptor<Handler> dispositionHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sender).send(any(Message.class), dispositionHandlerCaptor.capture());
        dispositionHandlerCaptor.getValue().handle(delivery);
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
        client.createAndSendRequest(
                "request",
                Buffer.buffer("hello"),
                ctx.asyncAssertSuccess(s -> {
                    ctx.assertEquals(200, s.getStatus());
                    ctx.assertEquals("payload", s.getPayload().toString());
                    responseReceived.complete();
                }),
                span);

        // WHEN a response is received for the request
        final Message response = ProtonHelper.message("payload");
        response.setCorrelationId(MESSAGE_ID);
        MessageHelper.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, 200);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        client.handleResponse(delivery, response);

        // THEN the response is passed to the handler registered with the request
        responseReceived.await();
        // and the status code conveyed in the response is set on the span
        verify(span).setTag(Tags.HTTP_STATUS.getKey(), 200);
        // and no response time-out handler has been set
        verify(vertx, never()).setTimer(anyLong(), any(Handler.class));
    }

    /**
     * Verifies that the client cancels and fails a request for which no response
     * has been received after a certain amount of time. The request is then
     * failed with a {@link ServerErrorException} with a 503 status code.
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

        client.createAndSendRequest(
                "request",
                Buffer.buffer("hello"),
                ctx.asyncAssertFailure(t -> {
                    // THEN the request handler is failed
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_UNAVAILABLE,
                            ((ServerErrorException) t).getErrorCode());
                    verify(span).setTag(Tags.HTTP_STATUS.getKey(), HttpURLConnection.HTTP_UNAVAILABLE);
                    verify(span, never()).finish();
                }),
                span);
    }

    /**
     * Verifies that a response handler is immediately failed with a
     * {@link ServerErrorException} when the sender link is not open (yet).
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateAndSendRequestFailsIfSenderIsNotOpen(final TestContext ctx) {

        // GIVEN a client whose sender is not open
        when(sender.isOpen()).thenReturn(Boolean.FALSE);

        // WHEN sending a request
        client.createAndSendRequest(
                "get",
                Buffer.buffer("hello"),
                ctx.asyncAssertFailure(t -> {
                    // THEN the request fails immediately with a 503
                    verify(sender, never()).send(any(Message.class), any(Handler.class));
                    assertFailureCause(ctx, span, t, HttpURLConnection.HTTP_UNAVAILABLE);
                }),
                span);
    }

    /**
     * Verifies that a response handler is immediately failed with a
     * {@link ServerErrorException} when the receiver link is not open (yet).
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsIfReceiverIsNotOpen(final TestContext ctx) {

        // GIVEN a client whose receiver is not open
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

        final SpanContext spanContext = mock(SpanContext.class);
        final Span span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);

        client.sendRequest(request, ctx.asyncAssertSuccess(t -> {
            sendSuccess.complete();
        }), null, span);
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

    /**
     * Verifies credits available.
     *
     */
    @Test
    public void testGetCreditsReturnsCreditsOfSenderLink() {
        when(sender.getCredit()).thenReturn(10, 0);
        assertThat(client.getCredit(), is(10));
        assertThat(client.getCredit(), is(0));
    }

    private AbstractRequestResponseClient<SimpleRequestResponseResult> getClient(final String tenant, final ProtonSender sender, final ProtonReceiver receiver) {

        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
        return new AbstractRequestResponseClient<SimpleRequestResponseResult>(connection, tenant, sender, receiver) {

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
                    final CacheDirective cacheDirective,
                    final ApplicationProperties applicationProperties) {
                return SimpleRequestResponseResult.from(status, payload, cacheDirective, applicationProperties);
            }
        };
    }

    private void assertFailureCause(
            final TestContext ctx,
            final Span span,
            final Throwable cause,
            final int expectedErrorCode) {

        ctx.assertEquals(
                expectedErrorCode,
                ((ServiceInvocationException) cause).getErrorCode());
        verify(span).setTag(Tags.HTTP_STATUS.getKey(), expectedErrorCode);
        verify(span, never()).finish();
    }
}
