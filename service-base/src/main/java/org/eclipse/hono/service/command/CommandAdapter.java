package org.eclipse.hono.service.command;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.impl.AbstractHonoClient;
import org.eclipse.hono.client.impl.RegistrationClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Receiver and sender pair to get commands and send a response.
 */
public class CommandAdapter extends AbstractHonoClient {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationClientImpl.class);

    /**
     * Creates a client for a vert.x context.
     *
     * @param context The context to run all interactions with the server on.
     * @param config The configuration properties to use.
     * @param protonReceiver The receiver for commands.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected CommandAdapter(final Context context, final ClientConfigProperties config,
            final ProtonReceiver protonReceiver) {
        super(context, config);
        this.receiver = protonReceiver;
    }

    Future<ProtonSender> createCommandSender(
            final ProtonConnection con,
            final String targetAddress) {
        final Future<ProtonSender> result = Future.future();
        AbstractHonoClient.createSender(context, config, con, targetAddress, ProtonQoS.AT_LEAST_ONCE, null)
                .setHandler(h -> {
                    if (h.succeeded()) {
                        sender = h.result();
                        result.complete(sender);
                    } else {
                        result.fail(h.cause());
                    }
                });
        return result;
    }

    void sendResponse(final Message message, final Handler<ProtonDelivery> onUpdated) {
        context.runOnContext(a -> {
            sender.send(message, onUpdated);
        });
    }

    final void close() {
        closeLinks(h->{});
    }

    /**
     * Creates a new command adapter.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant.
     * @param deviceId The device.
     * @param messageHandler Message handler.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters other than cache manager is {@code null}.
     */
    public static final void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String deviceId,
            final ProtonMessageHandler messageHandler,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<CommandAdapter>> creationHandler) {

        LOG.debug("creating new command adapter for [{}, {}]", tenantId, deviceId);

        final String address = ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId)
                .toString();
        AbstractHonoClient.createReceiver(context, clientConfig, con, address, ProtonQoS.AT_LEAST_ONCE, messageHandler,
                receiverCloseHook).setHandler(s -> {
                    if (s.succeeded()) {
                        LOG.debug("successfully command adapter for [{}, {}]", tenantId, deviceId);
                        creationHandler
                                .handle(Future.succeededFuture(new CommandAdapter(context, clientConfig, s.result())));
                    } else {
                        LOG.debug("failed to create command adapter for [{}, {}]", tenantId, deviceId, s.cause());
                        creationHandler.handle(Future.failedFuture(s.cause()));
                    }
                });
    }

}
