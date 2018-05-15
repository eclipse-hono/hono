package org.eclipse.hono.service.command;

import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.AbstractConsumer;
import org.eclipse.hono.client.impl.AbstractHonoClient;
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
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * Receiver and sender pair to get commands and send a response.
 */
public class CommandConsumer extends AbstractConsumer implements MessageConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CommandConsumer.class);

    /**
     * Creates a client for a vert.x context.
     *
     * @param context The context to run all interactions with the server on.
     * @param config The configuration properties to use.
     * @param protonReceiver The receiver for commands.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    private CommandConsumer(final Context context, final ClientConfigProperties config,
            final ProtonReceiver protonReceiver) {
        super(context, config, protonReceiver);
    }

    /**
     * Creates a new command adapter.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant.
     * @param deviceId The device.
     * @param messageConsumer Message consumer.
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
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        LOG.debug("creating new command adapter for [{}, {}]", tenantId, deviceId);

        final String address = ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId).toString();
        AbstractHonoClient
                .createReceiver(context, clientConfig, con, address, ProtonQoS.AT_LEAST_ONCE, messageConsumer::accept,
                        receiverCloseHook)
                .setHandler(s -> {
                    if (s.succeeded()) {
                        LOG.debug("successfully command adapter for [{}, {}]", tenantId, deviceId);
                        creationHandler
                                .handle(Future.succeededFuture(new CommandConsumer(context, clientConfig, s.result())));
                    } else {
                        LOG.debug("failed to create command adapter for [{}, {}]", tenantId, deviceId, s.cause());
                        creationHandler.handle(Future.failedFuture(s.cause()));
                    }
                });
    }

}
