package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonConnection;

/**
 * A Vertx-Proton based client for Hono's Command and Control API.
 *
 */
public class CommandClientImpl extends AbstractRequestResponseClient<CommandResult> implements CommandClient {

    private static final Logger LOG = LoggerFactory.getLogger(CommandClientImpl.class);

    CommandClientImpl(final Context context, final ClientConfigProperties config, final String tenantId, final String deviceId) {
        super(context, config, tenantId, deviceId);
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
    protected CommandResult getResult(final int status, final Buffer payload, final CacheDirective cacheDirective) {
        return CommandResult.from(status, payload);
    }

    @Override
    public Future<byte[]> commandWithResponse(final String command, final byte[] data) {

        Objects.requireNonNull(command);
        Objects.requireNonNull(data);

        final Future<CommandResult> responseTracker = Future.future();
        createAndSendRequest(command, Buffer.buffer(data),
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

    @Override
    public Future<Void> command(final String command, final byte[] data) {

        // TODO

        return null;
    }

    /**
     * Creates a new command client for a tenant and device.
     *
     * @param tenantId The vert.x context to run all interactions with the server on.
     * @param deviceId The device id.
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
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