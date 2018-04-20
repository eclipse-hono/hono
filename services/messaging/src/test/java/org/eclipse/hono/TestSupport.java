/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.amqp.Binary;
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
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Utility methods for running unit tests.
 */
public final class TestSupport {

    /**
     * The default number of credits to replenish a sender with.
     */
    public static final int DEFAULT_CREDITS = 20;

    private static final String CLIENT_ID = "protocol_adapter";
    private static final String CON_ID = "connection-1";
    private static final String DEFAULT_ADDRESS = "type/tenant";

    private TestSupport() {
        // prevent instantiation
    }

    /**
     * Prepares a Mockito mock {@code EventBus} to send a given reply for a message sent to a specific address.
     * 
     * @param bus the mock event bus.
     * @param address the address to expect the message to be sent to.
     * @param msg the message to expect.
     * @param reply the reply to send.
     * @param <T> The type of the reply message.
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
     * Creates a sender factory for a static sender.
     *
     * @param senderToCreate The sender to be created by the factory's
     *                 <em>createSender</em> method.
     * @return The factory.
     */
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
     * Creates a new mock upstream client for the default link ID
     * and default connection ID.
     * 
     * @return The new client.
     */
    public static UpstreamReceiver newClient() {
        return newClient(CLIENT_ID);
    }

    /**
     * Creates a new mock upstream client for a link ID and the default connection ID.
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

    /**
     * Creates a new sender.
     * <p>
     * The created sender will
     * <ul>
     * <li>have mocked attachments</li>
     * <li>be open</li>
     * <li>have the default number of credits available</li>
     * <li>have 0 messages queued</li>
     * <li>have the given value for its <em>drain</em> flag</li>
     * <li>invoke the <em>sendQueueDrainHandler</em> when the sender's <em>open</em>
     * method is invoked</li>
     * <li>have a default target address</li>
     * </ul>
     * 
     * @param drainFlag The value of the sender's <em>drain</em> flag.
     * @return The sender.
     */
    @SuppressWarnings("unchecked")
    public static ProtonSender newMockSender(final boolean drainFlag) {
        @SuppressWarnings("rawtypes")
        final ArgumentCaptor<Handler> drainHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
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

    /**
     * Creates a new connection factory.
     * 
     * @param failToCreate A flag indicating whether attempts to
     *           <em>connect</em> should fail.
     * @return The factory.
     */
    public static ConnectionFactory newMockConnectionFactory(final boolean failToCreate) {
        return newMockConnectionFactory(mock(ProtonConnection.class), failToCreate);
    }

    /**
     * Creates a new connection factory for a static connection.
     * 
     * @param connectionToReturn The connection to be returned when the factory's
     *           <em>connect</em> method is invoked.
     * @param failToCreate A flag indicating whether attempts to
     *           <em>connect</em> should fail.
     * @return The factory.
     */
    public static ConnectionFactory newMockConnectionFactory(final ProtonConnection connectionToReturn, final boolean failToCreate) {

        return new ConnectionFactory() {

            @Override
            public void connect(final ProtonClientOptions options,
                    final Handler<AsyncResult<ProtonConnection>> closeHandler,
                    final Handler<ProtonConnection> disconnectHandler,
                    final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {
                connect(options, null, null, closeHandler, disconnectHandler, connectionResultHandler);
            }

            @Override
            public void connect(final ProtonClientOptions options, final String username, final String password,
                    final Handler<AsyncResult<ProtonConnection>> closeHandler,
                    final Handler<ProtonConnection> disconnectHandler,
                    final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

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
