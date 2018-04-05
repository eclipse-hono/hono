package org.eclipse.hono.service.command;

import java.util.Map;

import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Handles connection between adapter and AMQP 1.0 network.
 */
public class CommandConnection extends HonoClientImpl {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    public CommandConnection(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        super(vertx, clientConfigProperties);
    }

    public Future<Void> createCommandResponder(final String tenantId, final String deviceId,
                                                           final Handler<Command> commandHandler) {
        LOG.debug("create a command receiver for [tenant: {}, device-id: {}]", tenantId, deviceId);
        Future<Void> result = Future.future();
        connect().setHandler(h -> {
            getConnection().createReceiver(ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId).toString()) // TODO
                    .openHandler(oh -> {
                        if (oh.succeeded()) {
                            LOG.debug("command receiver successfully opened for [tenant: {}, device-id: {}]", tenantId,
                                    deviceId);
                            ProtonReceiver protonReceiver = oh.result();
                            CommandResponder responder = new CommandResponder(protonReceiver);
                            protonReceiver.handler((delivery, message) -> {
                                LOG.debug("command message received on [address: {}]", message.getAddress());
                                commandHandler.handle(new Command(responder,message));
                            });
                            result.complete();
                        } else {
                            LOG.debug("command receiver failed opening for [tenant: {}, device-id: {}] : {}", tenantId,
                                    deviceId, oh.cause().getMessage());
                            result.fail(oh.cause());
                        }
                    }).open();
        });
        return result;
    }

    public Future<Void> sendCommandRespond(final Command command, byte[] data, Map<String, Object> properties,
            Handler<ProtonDelivery> update) {
        LOG.debug("create a command responder (sender link) for [replyAddress: {}]", command.getReplyAddress());
        Future<Void> result = Future.future();
        synchronized (command) { // TODO
            ProtonSender sender = command.getResponder().getSender();
            if (sender == null || !sender.isOpen()) {
                connect().setHandler(h -> {
                    getConnection().createSender(command.getReplyAddress())
                            .openHandler(oh -> {
                                if (oh.succeeded()) {
                                    command.getResponder().setSender(oh.result());
                                    command.sendResponse(data, properties, update);
                                    result.complete();
                                } else {
                                    result.fail(oh.cause());
                                }
                            }).open();
                });
            } else {
                command.sendResponse(data, properties, update);
                result.complete();
            }
        }
        return result;
    }

}
