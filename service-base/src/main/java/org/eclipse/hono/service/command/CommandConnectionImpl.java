/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.service.auth.device.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * Implements a connection between an Adapter and the AMQP 1.0 network to receive commands and send a response.
 */
public class CommandConnectionImpl extends HonoClientImpl implements CommandConnection {

    private static final Logger LOG = LoggerFactory.getLogger(CommandConnectionImpl.class);

    /**
     * The consumers that can be used to receive command messages.
     * The device, which belongs to a tenant is used as the key, e.g. <em>DEFAULT_TENANT/4711</em>.
     */
    private final Map<String, CommandConsumer> commandReceivers = new HashMap<>();

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using
     * {@link ConnectionFactory#newConnectionFactory(Vertx, ClientConfigProperties)}.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    public CommandConnectionImpl(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        super(vertx, clientConfigProperties);
    }

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using
     * {@link ConnectionFactory#newConnectionFactory(Vertx, ClientConfigProperties)}.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param connectionFactory Factory to invoke for a new connection.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    public CommandConnectionImpl(final Vertx vertx, final ConnectionFactory connectionFactory, final ClientConfigProperties clientConfigProperties) {
        super(vertx, connectionFactory, clientConfigProperties);
    }

    /**
     * Initiate the connection for the command Hono client with handlers that
     * <ul>
     *     <li>close open command consumers on a disconnect</li>
     *     <li>call the {@link CommandConsumer#recreate(ProtonConnection)} method on a (re)connect</li>
     * </ul>
     * The passed handlers are wrapped and invoked after the close or recreate calls to the command consumers.
     *
     * @param options The options to use. If {@code null} the default properties will be used.
     * @param connectionHandler A handler to notify when a connection is established (may be {@code null}).
     * @param disconnectHandler A handler to notify about connection loss (may be {@code null}).
     */
    @Override
    protected void connect(final ProtonClientOptions options,
                           final Handler<AsyncResult<HonoClient>> connectionHandler,
                           final Handler<ProtonConnection> disconnectHandler) {
        final Handler<ProtonConnection> receiverLinkAwareDisconnectHandler = con -> {
            LOG.trace("Lost connection - found {} command receiver links to recover", commandReceivers.size());
            commandReceivers.keySet().stream().forEach(linkAddress -> {
                final CommandConsumer cc = commandReceivers.get(linkAddress);
                LOG.trace("closing link to recover on reconnect: address {}, link {}", linkAddress, cc);
                cc.close(v -> {});
            });
            Optional.ofNullable(disconnectHandler).map(h -> {
                h.handle(con);
                return con;
            });
        };
        final Handler<AsyncResult<HonoClient>> connectionRecreateLinksHandler = clientAsyncResult -> {
            if (clientAsyncResult.succeeded()) {
                LOG.trace("Now recovering #{} command receiver links", commandReceivers.size());
                commandReceivers.keySet().stream().forEach(linkAddress -> {
                    final CommandConsumer cc = commandReceivers.get(linkAddress);
                    LOG.trace("link address to recover: {}", linkAddress);

                    cc.recreate(getConnection());
                });
            }
            Optional.ofNullable(connectionHandler).map(h -> {
                h.handle(clientAsyncResult);
                return clientAsyncResult;
            });
        };

        super.connect(options, connectionRecreateLinksHandler, receiverLinkAwareDisconnectHandler);
    }

    /**
     * {@inheritDoc}
    */
    public final Future<MessageConsumer> getOrCreateCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final Handler<Void> closeHandler) {
        final MessageConsumer messageConsumer = commandReceivers.get(Device.asAddress(tenantId, deviceId));
        if (messageConsumer != null) {
            return Future.succeededFuture(messageConsumer);
        } else {
            return createConsumer(
                    tenantId,
                    () -> newCommandConsumer(tenantId, deviceId, commandConsumer, closeHandler));
        }
    }

    private Future<MessageConsumer> newCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final Handler<Void> closeHandler) {

        return checkConnected().compose(con -> {
            final Future<MessageConsumer> result = Future.future();
            CommandConsumer.create(context, clientConfigProperties, connection, tenantId, deviceId,
                    commandConsumer, closeHook -> {
                        closeCommandConsumer(tenantId, deviceId);
                        if (closeHandler != null) {
                            closeHandler.handle((Void) null);
                        }
                    }, creation -> {
                        if (creation.succeeded()) {
                            commandReceivers.put(Device.asAddress(tenantId, deviceId), creation.result());
                        }
                        result.complete(creation.result());
                    }, getTracer());
            return result;
        });
    }

    /**
     * {@inheritDoc}
     */
    public Future<Void> closeCommandConsumer(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Future<Void> result = Future.future();
        final String deviceAddress = Device.asAddress(tenantId, deviceId);

        Optional.ofNullable(commandReceivers.remove(deviceAddress)).map(commandReceiverLink -> {
            commandReceiverLink.close(result);
            return commandReceiverLink;
        });

        return result;
    }

    /**
     * {@inheritDoc}
     * 
     * This implementation always creates a new sender link.
     */
    public Future<CommandResponseSender> getCommandResponseSender(
            final String tenantId,
            final String replyId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);
        return checkConnected().compose(connected -> {
            final Future<CommandResponseSender> result = Future.future();
            CommandResponseSenderImpl.create(context, clientConfigProperties, connection, tenantId, replyId,
                    onSenderClosed -> {},
                    result.completer(),
                    getTracer());
            return result;
        });
    }
}
