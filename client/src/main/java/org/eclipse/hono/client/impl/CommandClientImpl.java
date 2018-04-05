package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.CommandResult;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.UUID;

/**
 * A Vertx-Proton based client for Hono's Command and Control API.
 *
 */
public class CommandClientImpl extends AbstractRequestResponseClient<CommandResult> implements CommandClient {

    private static final Logger LOG = LoggerFactory.getLogger(CommandClientImpl.class);

    CommandClientImpl(Context context, ClientConfigProperties config, String tenantId, String deviceId) {
        super(context, config, tenantId+"/"+deviceId); // TODO
    }

    @Override
    protected String getName() {
        return CommandConstants.COMMAND_ENDPOINT;
    }

    @Override
    protected String createMessageId() {
        return String.format("cmd-client-%s", UUID.randomUUID());
    }

    @Override
    protected CommandResult getResult(int status, Message payload) {
        return CommandResult.from(status, MessageHelper.getPayloadAsBytes(payload));
    }

    @Override
    public Future<byte[]> command(String deviceId, byte[] data) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(data);

        final Future<CommandResult> responseTracker = Future.future();
        createAndSendRequest(CommandConstants.COMMAND_ENDPOINT, new AmqpValue(data),
                responseTracker.completer());

        return responseTracker.map(response -> {
            switch (response.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return response.getPayload();
            default:
                throw StatusCodeMapper.from(response);
            }
        });
    }

    public static final void create(
            final String tenantId,
            final String deviceId,
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<CommandClient>> creationHandler) {

        final CommandClientImpl client = new CommandClientImpl(context, clientConfig, tenantId, deviceId);
        client.createLinks(con, senderCloseHook, receiverCloseHook).setHandler(s -> {
            if (s.succeeded()) {
                LOG.debug("successfully created command client for [{}]", tenantId);
                creationHandler.handle(Future.succeededFuture(client));
            } else {
                LOG.debug("failed to create command client for [{}]", tenantId, s.cause());
                creationHandler.handle(Future.failedFuture(s.cause()));
            }
        });
    }

}