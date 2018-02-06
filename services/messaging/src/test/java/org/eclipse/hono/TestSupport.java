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
package org.eclipse.hono;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.messaging.SenderFactory;
import org.eclipse.hono.messaging.UpstreamReceiver;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 *
 */
public final class TestSupport {

    public static final String CLIENT_CONTAINER = "hono-client";
    public static final String CLIENT_ID = "protocol_adapter";
    public static final String CON_ID = "connection-1";
    public static final String DEFAULT_ADDRESS = "type/tenant";
    public static final int DEFAULT_CREDITS = 20;

    public static ProtonConnection openConnection(final TestContext ctx, final Vertx vertx, final String host, final int port) {
        final Async connected = ctx.async();
        final AtomicReference<ProtonConnection> protonConnection = new AtomicReference<>();

        final ProtonClient client = ProtonClient.create(vertx);

        client.connect(host, port, ar -> {
            if (ar.succeeded()) {
                protonConnection.set(ar.result());
                protonConnection.get().setContainer(CLIENT_CONTAINER).open();
                connected.complete();
            }
            else
            {
                ctx.fail(ar.cause());
            }
        });

        connected.awaitSuccess(2000);
        return protonConnection.get();
    }

    /**
     * Prepares a Mockito mock {@code EventBus} to send a given reply for a message sent to a specific address.
     * 
     * @param bus the mock event bus.
     * @param address the address to expect the message to be sent to.
     * @param msg the message to expect.
     * @param reply the reply to send.
     */
    @SuppressWarnings("unchecked")
    public static <T> void expectReplyForMessage(final EventBus bus, final String address, final Object msg, final T reply) {
        when(bus.send(eq(address), eq(msg), any(Handler.class)))
            .then(invocation -> {
                io.vertx.core.eventbus.Message<T> response = mock(io.vertx.core.eventbus.Message.class);
                when(response.body()).thenReturn(reply);
                Future<io.vertx.core.eventbus.Message<T>> future = Future.succeededFuture(response);
                Handler<AsyncResult<io.vertx.core.eventbus.Message<T>>> handler =
                        (Handler<AsyncResult<io.vertx.core.eventbus.Message<T>>>) invocation.getArguments()[2];
                handler.handle(future);
                return bus;
            });
    }

    /**
     * Creates a new <em>Proton</em> message containing a JSON encoded temperature reading.
     * 
     * @param messageId the value to set as the message ID.
     * @param deviceId the ID of the device that produced the reading.
     * @param temperature the temperature in degrees centigrade.
     * @return the message containing the reading as a binary payload.
     */
    public static Message newTelemetryData(final String messageId, final String deviceId, final int temperature) {
        final Message message = ProtonHelper.message();
        message.setMessageId(messageId);
        message.setContentType("application/json");
        MessageHelper.addDeviceId(message, deviceId);
        message.setBody(new Data(new Binary(String.format("{\"temp\" : %d}", temperature).getBytes())));
        return message;
    }

    /**
     * Creates a new <em>Proton</em> message containing a JSON encoded temperature reading.
     *
     * @param messageId the value to set as the message ID.
     * @param action The value to set for the message's <em>action</em> application property.
     * @param tenantId the ID of the tenant the device belongs to.
     * @param deviceId the ID of the device that produced the reading.
     * @param replyTo The reply-to address to set on the message.
     * @return the message containing the reading as a binary payload.
     */
    public static Message newRegistrationMessage(final String messageId, final String action, final String tenantId, final String deviceId, final String replyTo) {
        final Message message = ProtonHelper.message();
        message.setMessageId(messageId);
        message.setReplyTo(replyTo);
        final HashMap<String, String> map = new HashMap<>();
        map.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        map.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        map.put(MessageHelper.SYS_PROPERTY_SUBJECT, action);
        final ApplicationProperties applicationProperties = new ApplicationProperties(map);
        message.setApplicationProperties(applicationProperties);
        return message;
    }

    public static SenderFactory newMockSenderFactory(final ProtonSender senderToCreate) {

        return (connection, address, qos, drainHandler, closeHook) -> {
            senderToCreate.sendQueueDrainHandler(drainHandler);
            senderToCreate.open();
            senderToCreate.closeHandler(remoteClose -> closeHook.handle(null));
            senderToCreate.detachHandler(remoteDetach -> closeHook.handle(null));
            return Future.succeededFuture(senderToCreate);
        };
    }

    /**
     * Creates an authentication service that always returns a given user,
     * regardless of the authentication request.
     * 
     * @param returnedUser The User to return.
     * @return The service.
     */
    public static AuthenticationService createAuthenticationService(final HonoUser returnedUser) {
        return new AuthenticationService() {

            @Override
            public void authenticate(final JsonObject authRequest, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
                authenticationResultHandler.handle(Future.succeededFuture(returnedUser));
            }
        };
    }

    /**
     * Creates a new mock upstream client for the {@linkplain #CLIENT_ID default link ID}
     * and {@linkplain #CON_ID default connection ID}.
     * 
     * @return The new client.
     */
    public static UpstreamReceiver newClient() {
        return newClient(CLIENT_ID);
    }

    /**
     * Creates a new mock upstream client for a link ID and the {@linkplain #CON_ID default connection ID}.
     * 
     * @param linkId The client's link ID.
     * @return The new client.
     */
    public static UpstreamReceiver newClient(final String linkId) {

        return newClient(linkId, CON_ID);
    }

    /**
     * Creates a new mock upstream client for a link and connection ID.
     * 
     * @param linkId The client's link ID.
     * @param connectionId The client's conenction ID.
     * @return The new client.
     */
    public static UpstreamReceiver newClient(final String linkId, final String connectionId) {

        UpstreamReceiver client = mock(UpstreamReceiver.class);
        when(client.getLinkId()).thenReturn(linkId);
        when(client.getConnectionId()).thenReturn(connectionId);
        when(client.getTargetAddress()).thenReturn(DEFAULT_ADDRESS);
        return client;
    }

    @SuppressWarnings("unchecked")
    public static ProtonSender newMockSender(final boolean drainFlag) {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Handler> drainHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        Record attachments = mock(Record.class);
        ProtonSender sender = mock(ProtonSender.class);
        when(sender.attachments()).thenReturn(attachments);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(sender.getCredit()).thenReturn(DEFAULT_CREDITS);
        when(sender.getQueued()).thenReturn(0);
        when(sender.getDrain()).thenReturn(drainFlag);
        when(sender.open()).then(invocation -> {
            drainHandlerCaptor.getValue().handle(sender);
            return sender;
        });
        when(sender.sendQueueDrainHandler(drainHandlerCaptor.capture())).then(invocation -> {
            return sender;
        });
        Target target = new Target();
        target.setAddress(DEFAULT_ADDRESS);
        when(sender.getTarget()).thenReturn(target);
        return sender;
    }

    public static ConnectionFactory newMockConnectionFactory(final boolean failToCreate) {
        return newMockConnectionFactory(mock(ProtonConnection.class), failToCreate);
    }

    public static ConnectionFactory newMockConnectionFactory(final ProtonConnection connectionToReturn, final boolean failToCreate) {
        return new ConnectionFactory() {

            @Override
            public void connect(ProtonClientOptions options, Handler<AsyncResult<ProtonConnection>> closeHandler,
                    Handler<ProtonConnection> disconnectHandler,
                    Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {
                connect(options, null, null, closeHandler, disconnectHandler, connectionResultHandler);
            }

            @Override
            public void connect(ProtonClientOptions options, final String username, final String password,
                    Handler<AsyncResult<ProtonConnection>> closeHandler,
                    Handler<ProtonConnection> disconnectHandler,
                    Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

                if (failToCreate) {
                    connectionResultHandler.handle(Future.failedFuture("remote host unreachable"));
                } else {
                    connectionResultHandler.handle(Future.succeededFuture(connectionToReturn));
                }
            }

            @Override
            public String getName() {
                return "client";
            }

            @Override
            public String getHost() {
                return "server";
            }

            @Override
            public int getPort() {
                return 5672;
            }

            @Override
            public String getPathSeparator() {
                return Constants.DEFAULT_PATH_SEPARATOR;
            }
        };
    }
}
