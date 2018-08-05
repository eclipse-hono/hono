/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EndpointType;
import org.eclipse.hono.util.HonoProtonHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;
import io.vertx.proton.ProtonSession;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs using AMQP.
 */
public final class VertxBasedAmqpProtocolAdapter extends AbstractProtocolAdapterBase<ProtocolAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedAmqpProtocolAdapter.class);
    // These values should be made configurable.
    private static final int DEFAULT_MAX_FRAME_SIZE = 32 * 1024; // 32 KB
    private static final int DEFAULT_MAX_SESSION_WINDOW = 100 * DEFAULT_MAX_FRAME_SIZE;

    /**
     * The default insecure port that this adapter binds to for unencrypted connections.
     */
    private static final int DEFAULT_INSECURE_PORT = 4040;

    /**
     * The default secure port that this adapter binds to for TLS encrypted secure connections.
     */
    private static final int DEFAULT_SECURE_PORT = 4041;

    /**
     * The AMQP server instance that maps to a secure port.
     */
    private ProtonServer secureServer;

    /**
     * The AMQP server instance that listens for incoming request from an insecure port.
     */
    private ProtonServer insecureServer;

    private AtomicBoolean secureListening = new AtomicBoolean(false);

    /**
     * This adapter's custom SASL authenticator factory for handling the authentication process for devices.
     */
    private ProtonSaslAuthenticatorFactory authenticatorFactory;

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
        checkPortConfiguration()
                .compose(success -> {
                    if (authenticatorFactory == null && getConfig().isAuthenticationRequired()) {
                        final HonoClientBasedAuthProvider usernamePasswordAuthProvider = new UsernamePasswordAuthProvider(getCredentialsServiceClient(), getConfig());
                        authenticatorFactory = new AmqpAdapterSaslAuthenticatorFactory(usernamePasswordAuthProvider, getConfig());
                    }
                    return Future.succeededFuture();
                }).compose(succcess -> {
                    return CompositeFuture.all(bindSecureServer(), bindInsecureServer());
                }).compose(success -> {
                    startFuture.complete();
                }, startFuture);

    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {
        CompositeFuture.all(stopSecureServer(), stopInsecureServer())
        .compose(ok -> stopFuture.complete(), stopFuture);
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

    private Future<Void> stopSecureServer() {
        final Future<Void> result = Future.future();
        if (secureServer != null) {

            LOG.info("Shutting down secure server");
            secureListening.compareAndSet(Boolean.TRUE, Boolean.FALSE);
            secureServer.close(result.completer());

        } else {
            result.complete();
        }
        return result;
    }

    private Future<Void> bindInsecureServer() {
        if (isInsecurePortEnabled()) {
            final ProtonServerOptions options =
                    new ProtonServerOptions()
                    .setHost(getConfig().getInsecurePortBindAddress())
                    .setPort(determineInsecurePort());

            final Future<Void> result = Future.future();
            insecureServer = createServer(insecureServer, options);
            insecureServer.connectHandler(this::connectionRequestHandler).listen(ar -> {
                if (ar.succeeded()) {
                    LOG.info("insecure amqp server listening on [{}:{}]", getConfig().getInsecurePortBindAddress(), getActualInsecurePort());
                    result.complete();
                } else {
                    result.fail(ar.cause());
                }
            });
            return result;
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<Void> bindSecureServer() {
        if (isSecurePortEnabled()) {
            final ProtonServerOptions options =
                    new ProtonServerOptions()
                    .setHost(getConfig().getBindAddress())
                    .setPort(determineSecurePort())
                    .setMaxFrameSize(DEFAULT_MAX_FRAME_SIZE);
            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);

            final Future<Void> result = Future.future();
            secureServer = createServer(secureServer, options);
            secureServer.connectHandler(this::connectionRequestHandler).listen(ar -> {
                if (ar.succeeded()) {
                    secureListening.getAndSet(Boolean.TRUE);
                    LOG.info("secure amqp server listening on {}:{}", getConfig().getBindAddress(), getActualPort());
                    result.complete();
                } else {
                    LOG.error("cannot bind to secure port", ar.cause());
                    result.fail(ar.cause());
                }
            });
            return result;
        } else {
            return Future.succeededFuture();
        }
    }

    private ProtonServer createServer(final ProtonServer server, final ProtonServerOptions options) {
        final ProtonServer createdServer = (server != null) ? server : ProtonServer.create(this.vertx, options);
        if (getConfig().isAuthenticationRequired()) {
            createdServer.saslAuthenticatorFactory(authenticatorFactory);
        } else {
            // use proton's default authenticator -> SASL ANONYMOUS
            createdServer.saslAuthenticatorFactory(null);
        }
        return createdServer;
    }

    private void connectionRequestHandler(final ProtonConnection connRequest) {

        LOG.debug("Received connection request from client");

        if (secureListening.get()) {
            connRequest.setContainer(String.format("%s-%s:%d", "secure-server", getBindAddress(), getActualPort()));
        } else {
            connRequest.setContainer(String.format("%s-%s:%d", "insecure-server", getInsecurePortBindAddress(), getActualInsecurePort()));
        }
        connRequest.disconnectHandler(conn -> {
            LOG.debug("client [container: {}] has disconnected", conn.getRemoteContainer());
        });
        connRequest.closeHandler(remoteClose -> handleRemoteConnectionClose(connRequest, remoteClose));

        // when a BEGIN frame is received
        connRequest.sessionOpenHandler(session -> {
            HonoProtonHelper.setDefaultCloseHandler(session);
            handleSessionOpen(connRequest, session);
        });
        // when an OPEN is received
        connRequest.openHandler(remoteOpen -> {
            final ProtonConnection conn = remoteOpen.result();
            conn.setContainer(getTypeName());
            conn.open();
        });
        // when an Attach frame is received
        connRequest.receiverOpenHandler(receiver -> {
            HonoProtonHelper.setDefaultCloseHandler(receiver);
            handleRemoteReceiverOpen(receiver, connRequest);
        });
        connRequest.senderOpenHandler(sender -> {
            HonoProtonHelper.setDefaultCloseHandler(sender);
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
     * Sets the SASL authenticator factory to use for handling the authentication process of devices.
     * <p>
     * If not explicitly set using this method (and the adapter is enable for device authentication) a 
     * {@code AmqpAdapterSaslAuthenticatorFactory}, configured to use an auth provider based on a username
     * and password, will be created during startup.
     * 
     * @param authFactory The SASL authenticator factory.
     * @throws NullPointerException if the authFactory is {@code null}.
     */
    protected void setSaslAuthenticatorFactory(final ProtonSaslAuthenticatorFactory authFactory) {
        this.authenticatorFactory = Objects.requireNonNull(authFactory, "authFactory must not be null");
    }

    /**
     * This method is called when an AMQP BEGIN frame is received from a remote client. This method sets the incoming
     * capacity in its BEGIN Frame to be communicated to the remote peer
     *
     */
    private void handleSessionOpen(final ProtonConnection conn, final ProtonSession session) {
        LOG.debug("opening new session with client [container: {}]", conn.getRemoteContainer());
        session.setIncomingCapacity(DEFAULT_MAX_SESSION_WINDOW);
        session.open();
    }
    /**
     * Invoked when a client closes the connection with this server.
     * <p>
     * Copied from AmqpServiceBase#handleRemoteConnectionClose.
     * 
     * @param con The connection to close.
     * @param res The client's close frame.
     */
    private void handleRemoteConnectionClose(final ProtonConnection con, final AsyncResult<ProtonConnection> res) {
        if (res.succeeded()) {
            LOG.debug("client [container: {}] closed connection", con.getRemoteContainer());
        } else {
            LOG.debug("client [container: {}] closed connection with error", con.getRemoteContainer(), res.cause());
        }
        con.disconnectHandler(null);
        con.close();
        con.disconnect();
    }

    /**
     * This method is invoked when an AMQP Attach frame is received by this server. If the receiver link contains
     * a target address, this method simply closes the link, otherwise, it accept and open the link.
     * 
     * @param receiver The receiver link for receiving the data.
     * @param conn The connection through which the request is initiated.
     */
    protected void handleRemoteReceiverOpen(final ProtonReceiver receiver, final ProtonConnection conn) {
        if (receiver.getRemoteTarget() != null && receiver.getRemoteTarget().getAddress() != null) {
            if (!receiver.getRemoteTarget().getAddress().isEmpty()) {
                LOG.debug("Closing link due to the present of Target [address : {}]", receiver.getRemoteTarget().getAddress());
            }
            receiver.setCondition(
                    AmqpContext.getErrorCondition(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "This adapter does not accept a target address on receiver links")));
            receiver.close();
        } else {
            final Device authenticatedDevice = conn.attachments().get(AmqpAdapterConstants.KEY_CLIENT_DEVICE,
                    Device.class);
            LOG.debug("Established receiver link at [address: {}]",
                    (receiver.getRemoteTarget() != null) ? receiver.getRemoteTarget().getAddress() : null);

            receiver.setTarget(null);
            receiver.setQoS(receiver.getRemoteQoS());
            if (ProtonQoS.AT_LEAST_ONCE.equals(receiver.getRemoteQoS())) {
                // disable auto-accept for this transfer model.
                // in this case, the adapter will apply the required disposition
                receiver.setAutoAccept(false);
            }
            receiver.handler((delivery, message) -> {

                validateEndpoint(message.getAddress(), delivery)
                        .compose(address -> validateAddress(address, authenticatedDevice))
                        .compose(validAddress -> {
                            message.setAddress(validAddress.toString());
                            uploadMessage(new AmqpContext(delivery, message, authenticatedDevice));
                            return Future.succeededFuture();
                        })
                        .recover(t -> {
                            // invalid message address / endpoint
                            MessageHelper.rejected(delivery, AmqpContext.getErrorCondition(t));
                            return Future.failedFuture(t);
                        });
            });
            HonoProtonHelper.setCloseHandler(receiver, remoteDetach -> onLinkDetach(receiver));
            receiver.open();
        }
    }

    /**
     * Forwards a message received from a device to downstream consumers.
     *
     * @param context The context that the message has been received in.
     */
    protected void uploadMessage(final AmqpContext context) {
        final Future<Void> contentTypeCheck = Future.future();
        final String contentType = context.getMessageContentType();

        if (!isPayloadOfIndicatedType(context.getMessagePayload(), contentType)) {
            contentTypeCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("Content-Type: [%s] does not match payload", contentType)));
        } else {
            contentTypeCheck.complete();
        }

        contentTypeCheck.compose(ok -> {
            switch (EndpointType.fromString(context.getEndpoint())) {
            case TELEMETRY:
                LOG.trace("Received request to upload telemetry data to endpoint [with name: {}]",
                        context.getEndpoint());
                return doUploadMessage(context, getTelemetrySender(context.getTenantId()));
            case EVENT:
                LOG.trace("Received request to upload events to endpoint [with name: {}]", context.getEndpoint());
                return doUploadMessage(context, getEventSender(context.getTenantId()));
            default:
                return Future
                        .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unknown endpoint"));
            }
        }).recover(t -> {
            if (!context.isRemotelySettled()) {
                // client wants to be informed that the message cannot be processed.
                context.handleFailure(t);
            }
            return Future.failedFuture(t);
        });

    }

    @SuppressWarnings("deprecation")
    private Future<Void> doUploadMessage(final AmqpContext context, final Future<MessageSender> senderFuture) {

        final Future<JsonObject> tokenFuture = getRegistrationAssertion(context.getTenantId(), context.getDeviceId(),
                context.getAuthenticatedDevice());
        final Future<TenantObject> tenantConfigFuture = getTenantConfiguration(context.getTenantId());

        return CompositeFuture.all(tenantConfigFuture, tokenFuture, senderFuture).compose(ok -> {
            final TenantObject tenantObject = tenantConfigFuture.result();
            if (tenantObject.isAdapterEnabled(getTypeName())) {

                final MessageSender sender = senderFuture.result();
                final Message downstreamMessage = newMessage(context.getResourceIdentifier(),
                        sender.isRegistrationAssertionRequired(),
                        context.getEndpoint(), context.getMessageContentType(), context.getMessagePayload(),
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
                    context.getEndpoint(), t);
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
     * This method validates that a client tries to publish a message to a supported endpoint. If the endpoint is supported,
     * this method also validates that the quality service of the supported endpoint.
     * 
     * @param address The message address.
     * @param delivery The delivery through which this adapter receives the message.
     *
     * @return A future with the address upon success or a failed future.
     */
    Future<ResourceIdentifier> validateEndpoint(final String address, final ProtonDelivery delivery) {

        if (address == null || address.isEmpty()) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Message address cannot be null or empty"));
        }

        final Future<ResourceIdentifier> result = Future.future();
        final ResourceIdentifier resource = ResourceIdentifier.fromString(address);

        switch (EndpointType.fromString(resource.getEndpoint())) {
        case TELEMETRY:
            result.complete(resource);
            break;
        case EVENT:
            if (delivery.remotelySettled()) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "The Event endpoint only supports unsettled delivery for messages"));
            } else {
                result.complete(resource);
            }
            break;
        default:
            LOG.error("Endpoint with [name: {}] is not supported by this adapter ",
                    resource.getEndpoint());
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unsupported endpoint"));
            break;
        }
        return result;
    }

    /**
     * Validates the address contained in an AMQP 1.0 message.
     * 
     * @param address The message address to validate.
     * @param authenticatedDevice The authenticated device.
     * 
     * @return A succeeded future with the valid message address or a failed future if the message address is not valid.
     */
    private Future<ResourceIdentifier> validateAddress(final ResourceIdentifier address, final Device authenticatedDevice) {
        final Future<ResourceIdentifier> result = Future.future();

        if (authenticatedDevice == null) {
            if (address.getTenantId() == null || address.getResourceId() == null) {
                throw new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid address for unauthenticated devices");
            } else {
                result.complete(address);
            }
        } else {
            if (address.getTenantId() != null && address.getResourceId() == null) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "address of authenticated message must not contain tenant ID only"));
            } else if (address.getTenantId() == null && address.getResourceId() == null) {
                final ResourceIdentifier resource = ResourceIdentifier.from(address.getEndpoint(),
                        authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
                result.complete(resource);
            } else {
                result.complete(address);
            }
        }
        return result;
    }

    // -------------------------------------------< AbstractServiceBase >---
    /**
     * {@inheritDoc}
     */
    @Override
    public int getPortDefaultValue() {
        return DEFAULT_SECURE_PORT;
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
        return secureServer != null ? secureServer.actualPort() : Constants.PORT_UNCONFIGURED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getActualInsecurePort() {
        return insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED;
    }

}
