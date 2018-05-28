package org.eclipse.hono.adapter.amqp;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EndpointType;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;
import io.vertx.proton.ProtonSession;

/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs using AMQP.
 */
public final class VertxBasedAmqpProtocolAdapter extends AbstractProtocolAdapterBase<ProtocolAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedAmqpProtocolAdapter.class);
    // These values should be made configurable.
    private static final int DEFAULT_MAX_FRAME_SIZE = 32 * 1024; // 32 KB
    private static final int DEFAULT_MAX_SESSION_WINDOW = 100 * DEFAULT_MAX_FRAME_SIZE;

    /**
     * The default insecure port that this adapter binds to.
     */
    private static final int DEFAULT_INSECURE_PORT = 4040;

    /**
     * The AMQP server instance that listens for incoming request from an insecure port.
     */
    private ProtonServer insecureServer;

    // -----------------------------------------< AbstractProtocolAdapterBase >---
    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_AMQP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStart(final Future<Void> startFuture) {
        final ProtonServerOptions options = new ProtonServerOptions();
        options.setHost(getConfig().getInsecurePortBindAddress());
        options.setPort(determineInsecurePort());
        // options.setHeartbeat(6000); // set local-idle-timeout expiry
        // TODO: communicate max-frame size
        options.setMaxFrameSize(DEFAULT_MAX_FRAME_SIZE);
        checkPortConfiguration()
                .compose(check -> {
                    // TODO: check that adapter is connected to all its services.
                    return bindServer(insecureServer, options);
                }).compose(server -> {
                    insecureServer = server;
                    startFuture.complete();
                }, startFuture);

    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {
        stopInsecureServer().compose(stopped -> stopFuture.complete(), stopFuture);
    }

    private Future<Void> stopInsecureServer() {
        final Future<Void> result = Future.future();
        if (insecureServer != null) {
            LOG.info("Shutting down insecure server");
            insecureServer.close(result.completer());
        } else {
            result.complete();
        }
        return result;
    }

    private Future<ProtonServer> bindServer(final ProtonServer server, final ProtonServerOptions options) {
        final Future<ProtonServer> result = Future.future();
        final ProtonServer createdServer = (server == null) ? ProtonServer.create(this.vertx, options) : server;

        createdServer.connectHandler(this::connectionRequestHandler).listen(res -> {
            if (res.succeeded()) {
                LOG.info("AMQP server running on [{}:{}]", getConfig().getInsecurePortBindAddress(),
                        getConfig().getInsecurePort());
                result.complete(res.result());
            } else {
                result.fail(res.cause());
            }
        });
        return result;
    }

    private void connectionRequestHandler(final ProtonConnection connRequest) {
        LOG.info("Received connection request from client");
        connRequest.disconnectHandler(conn -> {
            LOG.error("Connection disconnected " + conn.getCondition().getDescription());
        });
        // when a BEGIN frame is received
        connRequest.sessionOpenHandler(session -> handleSessionOpen(connRequest, session));
        // when an OPEN is received
        connRequest.openHandler(remoteOpen -> {
            final ProtonConnection conn = remoteOpen.result();
            conn.setContainer(getTypeName());
            conn.open();
        });
        // when an Attach frame is received
        connRequest
                .receiverOpenHandler(remoteReceiverOpen -> handleRemoteReceiverOpen(remoteReceiverOpen, connRequest));
        connRequest.senderOpenHandler(sender -> {
            // this should not happen -> no request-response for device clients.
            LOG.debug("client [container: {}] wants to open a link [address: {}] for receiving messages",
                    connRequest.getRemoteContainer(), sender.getRemoteSource());
            sender.setCondition(ProtonHelper.condition(AmqpError.NOT_ALLOWED,
                    "this adapter only forwards message to downstream applications"));
            sender.close();
        });
    }

    /**
     * Sets the AMQP server for handling insecure AMQP connections.
     * 
     * @param server The insecure server instance.
     * @throws NullPointerException If the server is {@code null}.
     */
    protected void setInsecureAmqpServer(final ProtonServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalArgumentException("AMQP Server should not be running");
        } else {
            this.insecureServer = server;
        }
    }

    /**
     * This method is called when an AMQP BEGIN frame is received from a remote client. This method sets the incoming
     * capacity in its BEGIN Frame to be communicated to the remote peer
     *
     */
    private void handleSessionOpen(final ProtonConnection conn, final ProtonSession session) {
        LOG.debug("opening new session with client [container: {}]", conn.getRemoteContainer());
        session.setIncomingCapacity(DEFAULT_MAX_SESSION_WINDOW);
        session.closeHandler(remoteSessionClose -> {
            LOG.debug("Client closes session with this server");
            session.close();
        }).open();
    }

    /**
     * This method is invoked when an AMQP Attach frame is received by this server.
     * 
     * @param receiver The receiver link for receiving the data.
     * @param conn The connection through which the request is initiated.
     */
    protected void handleRemoteReceiverOpen(final ProtonReceiver receiver, final ProtonConnection conn) {
        if (receiver.getRemoteTarget() == null) {
            receiver.setCondition(
                    ProtonHelper.condition(AmqpError.NOT_ALLOWED, "anonymous relay not supported by this adapter"));
            receiver.close();
        } else {
            validateEndpoint(receiver)
                    .recover(t -> {
                        LOG.debug(
                                "fail to establish a receiving link with client [{}] due to an invalid endpoint [{}]."
                                        + " closing the link",
                                receiver.getName(), receiver.getRemoteQoS());
                        if (ClientErrorException.class.isInstance(t)) {
                            final ClientErrorException error = (ClientErrorException) t;
                            receiver.setCondition(
                                    ProtonHelper.condition(AmqpError.NOT_FOUND, error.getMessage()));
                            receiver.close();
                        }
                        return Future.failedFuture(t);
                    })
                    .compose(resource -> {
                        receiver.setTarget(receiver.getRemoteTarget());
                        receiver.setQoS(receiver.getRemoteQoS());
                        LOG.debug("Established receiver link at [address: {}]",
                                receiver.getRemoteTarget().getAddress());
                        receiver.handler(
                                (delivery, message) -> {
                                    uploadMessage(new AmqpContext(delivery, message, resource));
                                })
                                .closeHandler(remoteDetach -> onLinkDetach(receiver))
                                .open();
                        return Future.succeededFuture();
                    });
        }
    }

    protected void uploadMessage(final AmqpContext context) {
        final Future<Void> formalCheck = Future.future();
        final String contentType = context.getMessageContentType();

        if (isPayloadOfIndicatedType(context.getMessagePayload(), contentType)) {
            formalCheck.complete();
        } else {
            formalCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("Content-Type: %s does not match payload", contentType)));
        }
        formalCheck.compose(ok -> {
            switch (EndpointType.fromString(context.getEndpoint())) {
            case TELEMETRY:
                LOG.trace("Received request to upload telemetry data to endpoint [with name: {}]",
                        context.getEndpoint());
                doUploadMessage(context, getTelemetrySender(context.getTenantId()),
                        TelemetryConstants.TELEMETRY_ENDPOINT);
                break;
            case EVENT:
                LOG.trace("Received request to upload events to endpoint [with name: {}]", context.getEndpoint());
                doUploadMessage(context, getEventSender(context.getTenantId()), EventConstants.EVENT_ENDPOINT);
                break;
            default:
                return Future
                        .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unknown endpoint"));
            }
            return Future.succeededFuture();
        }).otherwise(t -> {
            context.handleFailure((ServiceInvocationException) t);
            return Future.failedFuture(t);
        });
    }

    private void doUploadMessage(final AmqpContext context, final Future<MessageSender> senderFuture,
            final String endpointName) {

        final Future<JsonObject> tokenFuture = getRegistrationAssertion(context.getTenantId(), context.getDeviceId(),
                null);
        final Future<TenantObject> tenantConfigFuture = getTenantConfiguration(context.getTenantId());

        CompositeFuture.all(tenantConfigFuture, tokenFuture, senderFuture).compose(ok -> {
            final TenantObject tenantObject = tenantConfigFuture.result();
            if (tenantObject.isAdapterEnabled(context.getTenantId())) {
                final MessageSender sender = senderFuture.result();
                final Message downstreamMessage = newMessage(context.getResourceIdentifier(),
                        sender.isRegistrationAssertionRequired(),
                        endpointName, context.getMessageContentType(), context.getMessagePayload(),
                        tokenFuture.result(), null);

                if (context.isRemotelySettled()) {
                    // client uses AT_MOST_ONCE delivery semantics -> fire and forget
                    return sender.send(downstreamMessage);
                } else {
                    // client uses AT_LEAST_ONCE delivery semantics
                    return sender.sendAndWaitForOutcome(downstreamMessage);
                }
            } else {
                // this adapter is not enabled for tenant
                return Future.failedFuture(
                        new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                                String.format("This adapter is not enabled for tenant [tenantId: %s].",
                                        context.getTenantId())));
            }
        }).compose(downstreamDelivery -> {
            LOG.trace("Successfully process message for Device [deviceId: {}] with Tenant [tenantId: {}]",
                    context.getDeviceId(),
                    context.getTenantId());
            if (context.isRemotelySettled()) {
                // client uses AT_MOST_ONCE delivery semantics
                // accept & settle the message regardless of whether
                // the downstream peer accepted/rejected the message.
                context.accept();
            } else {
                // client uses AT_LEAST_ONCE delivery semantics
                // forward disposition received from downstream peer back to the client device.
                context.updateDelivery(downstreamDelivery);
            }
            return Future.<Void> succeededFuture();
        }).recover(t -> {
            LOG.debug("Cannot process message for Device [tenantId: {}, deviceId: {}, endpoint: {}]",
                    context.getTenantId(),
                    context.getDeviceId(),
                    context.getEndpoint());
            if (!context.isRemotelySettled()) {
                // client wants to be informed that the message cannot be processed.
                context.handleFailure((ServiceInvocationException) t);
            }
            return Future.failedFuture(t);
        });
    }

    /**
     * Closes the specified receiver link.
     * 
     * @param receiver The link to close.
     */
    private void onLinkDetach(final ProtonReceiver receiver) {
        LOG.debug("closing link [{}]", receiver.getName());
        receiver.close();
    }

    /**
     * This method validates that a client tries to publish a message to a supported endpoint. If the endpoint is
     * supported, the method returns a succeeded future containing the valid address.
     * 
     * @param receiver The link with which the message is received.
     * @return A future with the address upon success or a failed future.
     */
    Future<ResourceIdentifier> validateEndpoint(final ProtonReceiver receiver) {
        final Future<ResourceIdentifier> result = Future.future();
        final ResourceIdentifier address = ResourceIdentifier.fromString(receiver.getRemoteTarget().getAddress());
        switch (EndpointType.fromString(address.getEndpoint())) {
        case TELEMETRY:
            if (!ProtonQoS.AT_LEAST_ONCE
                    .equals(receiver.getRemoteQoS()) && !ProtonQoS.AT_MOST_ONCE.equals(receiver.getRemoteQoS())) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "Telemetry endpoint only supports either AT_LEAST_ONCE or AT_MOST_ONCE delivery semantics."));
            } else {
                result.complete(address);
            }
            break;
        case EVENT:
            if (!ProtonQoS.AT_LEAST_ONCE.equals(receiver.getRemoteQoS())) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "Event endpoint only supports AT_LEAST_ONCE delivery semantics."));
            } else {
                result.complete(address);
            }
            break;
        default:
            LOG.error("Endpoint with [name: {}] is not supported by this adapter ",
                    address.getEndpoint());
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unsupported endpoint"));
            break;
        }
        return result;
    }

    // -------------------------------------------< AbstractServiceBase >---
    /**
     * {@inheritDoc}
     */
    @Override
    public int getPortDefaultValue() {
        return Constants.PORT_AMQPS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInsecurePortDefaultValue() {
        return DEFAULT_INSECURE_PORT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getActualPort() {
        // TODO not-yet-implemented
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getActualInsecurePort() {
        return insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED;
    }

}
