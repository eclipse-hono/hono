/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp.impl;

import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.amqp.AmqpAdapterConstants;
import org.eclipse.hono.adapter.amqp.AmqpAdapterMetrics;
import org.eclipse.hono.adapter.amqp.AmqpAdapterProperties;
import org.eclipse.hono.adapter.amqp.AmqpAdapterSaslAuthenticatorFactory;
import org.eclipse.hono.adapter.amqp.SaslExternalAuthHandler;
import org.eclipse.hono.adapter.amqp.SaslPlainAuthHandler;
import org.eclipse.hono.adapter.amqp.SaslResponseContext;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.AdapterConnectionsExceededException;
import org.eclipse.hono.service.AdapterDisabledException;
import org.eclipse.hono.service.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.X509AuthProvider;
import org.eclipse.hono.service.limiting.ConnectionLimitManager;
import org.eclipse.hono.service.limiting.DefaultConnectionLimitManager;
import org.eclipse.hono.service.limiting.MemoryBasedConnectionLimitStrategy;
import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.HonoProtonHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;
import io.vertx.proton.ProtonSession;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs using AMQP.
 */
public final class VertxBasedAmqpProtocolAdapter extends AbstractProtocolAdapterBase<AmqpAdapterProperties> {

    private static final String KEY_CONNECTION_LOSS_HANDLERS = "connectionLossHandlers";

    // These values should be made configurable.
    /**
     * The minimum amount of memory that the adapter requires to run.
     */
    private static final int MINIMAL_MEMORY = 100_000_000; // 100MB: minimal memory necessary for startup

    /**
     * The amount of memory required for each connection.
     */
    private static final int MEMORY_PER_CONNECTION = 20_000; // 20KB: expected avg. memory consumption per connection

    /**
     * The AMQP server instance that maps to a secure port.
     */
    private ProtonServer secureServer;

    /**
     * The AMQP server instance that listens for incoming request from an insecure port.
     */
    private ProtonServer insecureServer;

    /**
     * This adapter's custom SASL authenticator factory for handling the authentication process for devices.
     */
    private ProtonSaslAuthenticatorFactory authenticatorFactory;
    private AmqpAdapterMetrics metrics = AmqpAdapterMetrics.NOOP;
    private AmqpContextTenantAndAuthIdProvider tenantObjectWithAuthIdProvider;

    // -----------------------------------------< AbstractProtocolAdapterBase >---
    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_AMQP;
    }

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    @Autowired
    public void setMetrics(final AmqpAdapterMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Gets the metrics for this service.
     *
     * @return The metrics
     */
    protected AmqpAdapterMetrics getMetrics() {
        return metrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStart(final Promise<Void> startPromise) {

        if (getConnectionLimitManager() == null) {
            setConnectionLimitManager(createConnectionLimitManager());
        }

        checkPortConfiguration()
        .compose(success -> {
            if (tenantObjectWithAuthIdProvider == null) {
                tenantObjectWithAuthIdProvider = new AmqpContextTenantAndAuthIdProvider(getConfig(), getTenantClientFactory());
            }
            if (authenticatorFactory == null && getConfig().isAuthenticationRequired()) {
                authenticatorFactory = new AmqpAdapterSaslAuthenticatorFactory(
                        getMetrics(),
                        () -> tracer.buildSpan("open connection")
                                .ignoreActiveSpan()
                                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                                .withTag(Tags.COMPONENT.getKey(), getTypeName())
                                .start(),
                        new SaslPlainAuthHandler(new UsernamePasswordAuthProvider(getCredentialsClientFactory(), tracer)),
                        new SaslExternalAuthHandler(
                                new TenantServiceBasedX509Authentication(getTenantClientFactory(), tracer),
                                new X509AuthProvider(getCredentialsClientFactory(), tracer)),
                        (saslResponseContext, span) -> applyTenantTraceSamplingPriority(saslResponseContext, span));
            }
            return Future.succeededFuture();
        })
        .compose(success -> CompositeFuture.all(bindSecureServer(), bindInsecureServer()))
        .map(ok -> (Void) null)
        .onComplete(startPromise);
    }

    private ConnectionLimitManager createConnectionLimitManager() {
        return new DefaultConnectionLimitManager(
                new MemoryBasedConnectionLimitStrategy(
                        MINIMAL_MEMORY, MEMORY_PER_CONNECTION + getConfig().getMaxSessionWindowSize()),
                () -> metrics.getNumberOfConnections(), getConfig());
    }

    @Override
    protected void doStop(final Promise<Void> stopPromise) {
        CompositeFuture.all(stopSecureServer(), stopInsecureServer())
        .map(ok -> (Void) null)
        .onComplete(stopPromise);
    }

    private Future<Void> stopInsecureServer() {
        final Promise<Void> result = Promise.promise();
        if (insecureServer != null) {
            log.info("Shutting down insecure server");
            insecureServer.close(result);
        } else {
            result.complete();
        }
        return result.future();
    }

    private Future<Void> stopSecureServer() {
        final Promise<Void> result = Promise.promise();
        if (secureServer != null) {

            log.info("Shutting down secure server");
            secureServer.close(result);

        } else {
            result.complete();
        }
        return result.future();
    }

    private Future<Void> bindInsecureServer() {
        if (isInsecurePortEnabled()) {
            final ProtonServerOptions options =
                    new ProtonServerOptions()
                        .setHost(getConfig().getInsecurePortBindAddress())
                        .setPort(determineInsecurePort())
                        .setMaxFrameSize(getConfig().getMaxFrameSize())
                        // set heart beat to half the idle timeout
                        .setHeartbeat(getConfig().getIdleTimeout() >> 1);

            final Promise<Void> result = Promise.promise();
            insecureServer = createServer(insecureServer, options);
            insecureServer.connectHandler(this::onConnectRequest).listen(ar -> {
                if (ar.succeeded()) {
                    log.info("insecure AMQP server listening on [{}:{}]", getConfig().getInsecurePortBindAddress(), getActualInsecurePort());
                    result.complete();
                } else {
                    result.fail(ar.cause());
                }
            });
            return result.future();
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
                        .setMaxFrameSize(getConfig().getMaxFrameSize())
                        // set heart beat to half the idle timeout
                        .setHeartbeat(getConfig().getIdleTimeout() >> 1);

            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);

            final Promise<Void> result = Promise.promise();
            secureServer = createServer(secureServer, options);
            secureServer.connectHandler(this::onConnectRequest).listen(ar -> {
                if (ar.succeeded()) {
                    log.info("secure AMQP server listening on {}:{}", getConfig().getBindAddress(), getActualPort());
                    result.complete();
                } else {
                    log.error("cannot bind to secure port", ar.cause());
                    result.fail(ar.cause());
                }
            });
            return result.future();
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

    /**
     * Handles a remote peer's request to open a connection.
     *
     * @param con The connection to be opened.
     */
    protected void onConnectRequest(final ProtonConnection con) {

        con.disconnectHandler(lostConnection -> {
            log.debug("lost connection to device [container: {}]", con.getRemoteContainer());
            onConnectionLoss(con);
            decrementConnectionCount(con);
        });
        con.closeHandler(remoteClose -> {
            handleRemoteConnectionClose(con, remoteClose);
            onConnectionLoss(con);
            decrementConnectionCount(con);
        });

        // when a begin frame is received
        con.sessionOpenHandler(session -> {
            HonoProtonHelper.setDefaultCloseHandler(session);
            handleSessionOpen(con, session);
        });
        // when the device wants to open a link for
        // uploading messages
        con.receiverOpenHandler(receiver -> {
            HonoProtonHelper.setDefaultCloseHandler(receiver);
            receiver.setMaxMessageSize(UnsignedLong.valueOf(getConfig().getMaxPayloadSize()));
            handleRemoteReceiverOpen(con, receiver);
        });
        // when the device wants to open a link for
        // receiving commands
        con.senderOpenHandler(sender -> {
            handleRemoteSenderOpenForCommands(con, sender);
        });
        con.openHandler(remoteOpen -> {
            final Device authenticatedDevice = getAuthenticatedDevice(con);
            if (authenticatedDevice == null) {
                metrics.incrementUnauthenticatedConnections();
            } else {
                metrics.incrementConnections(authenticatedDevice.getTenantId());
            }

            if (remoteOpen.failed()) {
                log.debug("ignoring device's open frame containing error", remoteOpen.cause());
            } else {
                processRemoteOpen(remoteOpen.result());
            }
        });
    }

    private void onConnectionLoss(final ProtonConnection con) {
        final Span span = newSpan("handle closing of command connection", getAuthenticatedDevice(con),
                getTraceSamplingPriority(con));
        @SuppressWarnings("rawtypes")
        final List<Future> handlerResults = getConnectionLossHandlers(con).stream().map(handler -> handler.apply(span))
                .collect(Collectors.toList());
        CompositeFuture.join(handlerResults).recover(thr -> {
            Tags.ERROR.set(span, true);
            return Future.failedFuture(thr);
        }).onComplete(v -> span.finish());
    }

    private void processRemoteOpen(final ProtonConnection con) {

        final Span span = Optional
                // try to pick up span that has been created during SASL handshake
                .ofNullable(con.attachments().get(AmqpAdapterConstants.KEY_CURRENT_SPAN, Span.class))
                // or create a fresh one if no SASL handshake has been performed
                .orElse(tracer.buildSpan("open connection")
                    .ignoreActiveSpan()
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                    .withTag(Tags.COMPONENT.getKey(), getTypeName())
                    .start());

        final Device authenticatedDevice = getAuthenticatedDevice(con);
        TracingHelper.TAG_AUTHENTICATED.set(span, authenticatedDevice != null);
        if (authenticatedDevice != null) {
            TracingHelper.setDeviceTags(span, authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
        }

        checkConnectionLimitForAdapter()
            .compose(ok -> checkAuthorizationAndResourceLimits(authenticatedDevice, con, span))
            .compose(ok -> sendConnectedEvent(
                    Optional.ofNullable(con.getRemoteContainer()).orElse("unknown"),
                    authenticatedDevice))
            .map(ok -> {
                con.setContainer(getTypeName());
                con.setOfferedCapabilities(new Symbol[] {Constants.CAP_ANONYMOUS_RELAY});
                con.open();
                log.debug("connection with device [container: {}] established", con.getRemoteContainer());
                span.log("connection established");
                metrics.reportConnectionAttempt(
                        ConnectionAttemptOutcome.SUCCEEDED,
                        Optional.ofNullable(authenticatedDevice).map(device -> device.getTenantId()).orElse(null));
                return null;
            })
            .otherwise(t -> {
                con.setCondition(AbstractProtocolAdapterBase.getErrorCondition(t));
                con.close();
                TracingHelper.logError(span, t);
                metrics.reportConnectionAttempt(
                        AbstractProtocolAdapterBase.getOutcome(t),
                        Optional.ofNullable(authenticatedDevice).map(device -> device.getTenantId()).orElse(null));
                return null;
            })
            .onComplete(s -> span.finish());
    }

    private Future<Void> checkAuthorizationAndResourceLimits(
            final Device authenticatedDevice,
            final ProtonConnection con,
            final Span span) {

        final Promise<Void> connectAuthorizationCheck = Promise.promise();

        if (getConfig().isAuthenticationRequired()) {

            if (authenticatedDevice == null) {
                connectAuthorizationCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED,
                        "anonymous devices not supported"));
            } else {
                log.trace("received connection request from {}", authenticatedDevice);
                // the SASL handshake will already have authenticated the device
                // we still need to verify that
                // the adapter is enabled for the tenant,
                // the device/gateway exists and is enabled and
                // that the connection limit for the tenant is not exceeded.

                CompositeFuture.all(
                        checkDeviceRegistration(authenticatedDevice, span.context()),
                        getTenantConfiguration(authenticatedDevice.getTenantId(), span.context())
                                .compose(tenantConfig -> CompositeFuture.all(
                                        isAdapterEnabled(tenantConfig).recover(t -> Future.failedFuture(
                                                new AdapterDisabledException(
                                                        authenticatedDevice.getTenantId(),
                                                        "adapter is disabled for tenant",
                                                        t))),
                                        checkConnectionLimit(tenantConfig, span.context()))))
                    .map(ok -> {
                        log.debug("{} is registered and enabled", authenticatedDevice);
                        span.log(String.format("device [%s] is registered and enabled", authenticatedDevice));
                        return (Void) null;
                    })
                    .onComplete(connectAuthorizationCheck);
            }

        } else {
            log.trace("received connection request from anonymous device [container: {}]", con.getRemoteContainer());
            connectAuthorizationCheck.complete();
        }

        return connectAuthorizationCheck.future();
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
     * Sets the provider that determines the tenant and auth-id associated with a request.
     *
     * @param tenantObjectWithAuthIdProvider the provider.
     * @throws NullPointerException if tenantObjectWithAuthIdProvider is {@code null}.
     */
    public void setTenantObjectWithAuthIdProvider(
            final AmqpContextTenantAndAuthIdProvider tenantObjectWithAuthIdProvider) {
        this.tenantObjectWithAuthIdProvider = Objects.requireNonNull(tenantObjectWithAuthIdProvider);
    }

    /**
     * This method is called when an AMQP BEGIN frame is received from a remote client. This method sets the incoming
     * capacity in its BEGIN Frame to be communicated to the remote peer
     *
     */
    private void handleSessionOpen(final ProtonConnection conn, final ProtonSession session) {
        log.debug("opening new session with client [container: {}, session window size: {}]",
                conn.getRemoteContainer(), getConfig().getMaxSessionWindowSize());
        session.setIncomingCapacity(getConfig().getMaxSessionWindowSize());
        session.open();
    }

    /**
     * Invoked when a client closes the connection with this server.
     *
     * @param con The connection to close.
     * @param res The client's close frame.
     */
    private void handleRemoteConnectionClose(final ProtonConnection con, final AsyncResult<ProtonConnection> res) {

        if (res.succeeded()) {
            log.debug("client [container: {}] closed connection", con.getRemoteContainer());
        } else {
            log.debug("client [container: {}] closed connection with error", con.getRemoteContainer(), res.cause());
        }
        con.disconnectHandler(null);
        con.close();
        con.disconnect();
    }

    private void decrementConnectionCount(final ProtonConnection con) {

        final Device device = getAuthenticatedDevice(con);
        if (device == null) {
            metrics.decrementUnauthenticatedConnections();
        } else {
            metrics.decrementConnections(device.getTenantId());
        }
        sendDisconnectedEvent(
                Optional.ofNullable(con.getRemoteContainer()).orElse("unknown"),
                device);
    }

    /**
     * This method is invoked when a device wants to open a link for uploading messages.
     * <p>
     * The same link is used by the device to upload telemetry data, events and command
     * responses to be forwarded downstream.
     * <p>
     * If the attach frame contains a target address, this method simply closes the link,
     * otherwise, it accepts and opens the link.
     *
     * @param conn The connection through which the request is initiated.
     * @param receiver The receiver link for receiving the data.
     */
    protected void handleRemoteReceiverOpen(final ProtonConnection conn, final ProtonReceiver receiver) {

        final Device authenticatedDevice = conn.attachments().get(AmqpAdapterConstants.KEY_CLIENT_DEVICE,
                Device.class);
        final OptionalInt traceSamplingPriority = getTraceSamplingPriority(conn);

        final Span span = newSpan("attach receiver", authenticatedDevice, traceSamplingPriority);

        if (ProtonQoS.AT_MOST_ONCE.equals(receiver.getRemoteQoS())) {
            // the device needs to send all types of message over this link
            // so we must make sure that it does not allow for pre-settled messages
            // to be sent only
            final Exception ex = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "unsupported snd-settle-mode: settled");
            closeLinkWithError(receiver, ex, span);
        } else  if (receiver.getRemoteTarget() != null && receiver.getRemoteTarget().getAddress() != null) {
            if (!receiver.getRemoteTarget().getAddress().isEmpty()) {
                log.debug("closing link due to the presence of target address [{}]", receiver.getRemoteTarget().getAddress());
            }
            final Exception ex = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "this adapter supports anonymous relay mode only");
            closeLinkWithError(receiver, ex, span);
        } else {

            receiver.setTarget(receiver.getRemoteTarget());
            receiver.setSource(receiver.getRemoteSource());
            receiver.setQoS(receiver.getRemoteQoS());
            receiver.setPrefetch(30);
            // manage disposition handling manually
            receiver.setAutoAccept(false);
            HonoProtonHelper.setCloseHandler(receiver, remoteDetach -> onLinkDetach(receiver));
            HonoProtonHelper.setDetachHandler(receiver, remoteDetach -> onLinkDetach(receiver));
            receiver.handler((delivery, message) -> {
                try {
                    final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, message);
                    final Span msgSpan = newSpan("upload message", authenticatedDevice, traceSamplingPriority, spanContext);
                    msgSpan.log(Collections.singletonMap(Tags.MESSAGE_BUS_DESTINATION.getKey(), message));

                    final AmqpContext ctx = AmqpContext.fromMessage(delivery, message, msgSpan, authenticatedDevice);
                    ctx.setTimer(metrics.startTimer());
                    if (authenticatedDevice == null) {
                        deriveAndSetTenantTraceSamplingPriority(ctx)
                                .onComplete(ar -> onMessageReceived(ctx)
                                        .onComplete(r -> msgSpan.finish()));
                    } else {
                        ctx.setTraceSamplingPriority(traceSamplingPriority);
                        onMessageReceived(ctx)
                                .onComplete(r -> msgSpan.finish());
                    }
                } catch (final Exception ex) {
                    log.warn("error handling message", ex);
                    ProtonHelper.released(delivery, true);
                }
            });
            receiver.open();
            if (authenticatedDevice == null) {
                log.debug("established link for receiving messages from device [container: {}]",
                        conn.getRemoteContainer());
            } else {
                log.debug("established link for receiving messages from device [tenant: {}, device-id: {}]]",
                        authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
            }
            span.log("link established");
        }
        span.finish();
    }

    /**
     * Derives the tenant from the given context and sets the trace sampling priority configured for that tenant on
     * the given context.
     * <p>
     * This is for unauthenticated AMQP connections where the tenant id and the device/auth id get taken from the
     * message address.
     *
     * @param context The execution context.
     * @return A succeeded future indicating the outcome of the operation. Its value will be an <em>OptionalInt</em>
     *         with the identified sampling priority or an empty <em>OptionalInt</em> if no priority was identified.
     * @throws NullPointerException if context is {@code null}.
     */
    protected Future<OptionalInt> deriveAndSetTenantTraceSamplingPriority(final AmqpContext context) {
        Objects.requireNonNull(context);
        return tenantObjectWithAuthIdProvider.get(context, null)
                .map(tenantObjectWithAuthId -> {
                    final OptionalInt traceSamplingPriority = TenantTraceSamplingHelper
                            .getTraceSamplingPriority(tenantObjectWithAuthId);
                    context.setTraceSamplingPriority(traceSamplingPriority);
                    return traceSamplingPriority;
                })
                .recover(t -> Future.succeededFuture(OptionalInt.empty()));
    }

    /**
     * Derives the tenant from the given SASL handshake information and applies the trace sampling priority configured
     * for that tenant to the given span.
     * <p>
     * Also stores the identified trace sampling priority in the attachments of the AMQP connection.
     *
     * @param context The context with information about the SASL handshake.
     * @param currentSpan The span to apply the configuration to.
     * @return A succeeded future indicating that the operation is finished.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected Future<Void> applyTenantTraceSamplingPriority(final SaslResponseContext context, final Span currentSpan) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(currentSpan);
        return tenantObjectWithAuthIdProvider.get(context, currentSpan.context())
                .map(tenantObjectWithAuthId -> {
                    final OptionalInt traceSamplingPriority = TenantTraceSamplingHelper
                            .applyTraceSamplingPriority(tenantObjectWithAuthId, currentSpan);
                    context.getProtonConnection().attachments().set(AmqpAdapterConstants.KEY_TRACE_SAMPLING_PRIORITY,
                            OptionalInt.class, traceSamplingPriority);
                    return (Void) null;
                })
                .recover(t -> Future.succeededFuture());
    }

    /**
     * Processes an AMQP message received from a device.
     * <p>
     * This method settles the transfer with the following outcomes:
     * <ul>
     * <li><em>accepted</em> if the message has been successfully processed.</li>
     * <li><em>rejected</em> if the message could not be processed due to a problem caused by the device.</li>
     * <li><em>released</em> if the message could not be forwarded to a downstream consumer.</li>
     * </ul>
     *
     * @param ctx The context for the message.
     * @return A future indicating the outcome of processing the message.
     *         The future will succeed if the message has been processed successfully, otherwise it
     *         will fail with a {@link ServiceInvocationException}.
     */
    protected Future<ProtonDelivery> onMessageReceived(final AmqpContext ctx) {

        final Span msgSpan = ctx.getTracingSpan();
        return validateEndpoint(ctx)
        .compose(validatedEndpoint -> validateAddress(validatedEndpoint.getAddress(), validatedEndpoint.getAuthenticatedDevice()))
        .compose(validatedAddress -> uploadMessage(ctx, validatedAddress, msgSpan))
        .map(d -> {
            ProtonHelper.accepted(ctx.delivery(), true);
            return d;
        }).recover(t -> {
            if (t instanceof ClientErrorException) {
                final ErrorCondition condition = AbstractProtocolAdapterBase.getErrorCondition(t);
                MessageHelper.rejected(ctx.delivery(), condition);
            } else {
                ProtonHelper.released(ctx.delivery(), true);
            }
            TracingHelper.logError(msgSpan, t);
            return Future.failedFuture(t);
        });
    }

    /**
     * This method is invoked when a device wants to open a link for receiving commands.
     * <p>
     * The link will be closed immediately if
     * <ul>
     *  <li>the device does not specify a source address for its receive link or</li>
     *  <li>the source address cannot be parsed or does not point to the command endpoint or</li>
     *  <li>the AMQP adapter is disabled for the tenant that the device belongs to.</li>
     * </ul>
     *
     * @param connection The AMQP connection to the device.
     * @param sender The link to use for sending commands to the device.
     */
    protected void handleRemoteSenderOpenForCommands(
            final ProtonConnection connection,
            final ProtonSender sender) {

        final Device authenticatedDevice = connection.attachments().get(AmqpAdapterConstants.KEY_CLIENT_DEVICE,
                Device.class);
        final OptionalInt traceSamplingPriority = getTraceSamplingPriority(connection);

        final Span span = newSpan("attach Command receiver", authenticatedDevice, traceSamplingPriority);

        getResourceIdentifier(sender.getRemoteSource())
        .compose(address -> validateAddress(address, authenticatedDevice))
        .map(validAddress -> {
            // validAddress ALWAYS contains the tenant and device ID
            if (CommandConstants.isCommandEndpoint(validAddress.getEndpoint())) {
                return openCommandSenderLink(connection, sender, validAddress, authenticatedDevice, span, traceSamplingPriority)
                        .map(consumer -> {
                            setConnectionLossHandler(connection, validAddress.toString(), connectionLossSpan -> {
                                // do not use the above created span for onCommandConnectionClose()
                                // because that span will (usually) be finished long before the
                                // connection is closed/lost
                                final Future<ProtonDelivery> sendEventFuture = sendDisconnectedTtdEvent(validAddress.getTenantId(), 
                                        validAddress.getResourceId(), authenticatedDevice, connectionLossSpan.context());
                                final Future<Void> closeConsumerFuture = consumer.close(connectionLossSpan.context());

                                return CompositeFuture.join(sendEventFuture, closeConsumerFuture).mapEmpty();
                            });
                            return consumer;
                        });
            } else {
                throw new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such node");
            }
        })
        .map(consumer -> {
            span.log("link established");
            return consumer;
        })
        .otherwise(t -> {
            if (t instanceof ServiceInvocationException) {
                closeLinkWithError(sender, t, span);
            } else {
                closeLinkWithError(sender,
                        new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid source address"),
                        span);
            }
            return null;
        })
        .onComplete(s -> {
            span.finish();
        });
    }

    private Span newSpan(final String operationName, final Device authenticatedDevice,
            final OptionalInt traceSamplingPriority) {
        return newSpan(operationName, authenticatedDevice, traceSamplingPriority, null);
    }

    private Span newSpan(final String operationName, final Device authenticatedDevice,
            final OptionalInt traceSamplingPriority, final SpanContext context) {
        final Span span = TracingHelper.buildChildSpan(tracer, context, operationName, getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .start();

        if (authenticatedDevice != null) {
            TracingHelper.setDeviceTags(span, authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
        }
        traceSamplingPriority.ifPresent(prio -> {
            TracingHelper.setTraceSamplingPriority(span, prio);
        });
        return span;
    }

    private Future<ProtocolAdapterCommandConsumer> openCommandSenderLink(
            final ProtonConnection connection,
            final ProtonSender sender,
            final ResourceIdentifier address,
            final Device authenticatedDevice,
            final Span span,
            final OptionalInt traceSamplingPriority) {

        return createCommandConsumer(sender, address, authenticatedDevice, span).map(consumer -> {

            final String tenantId = address.getTenantId();
            final String deviceId = address.getResourceId();
            sender.setSource(sender.getRemoteSource());
            sender.setTarget(sender.getRemoteTarget());

            sender.setQoS(ProtonQoS.AT_LEAST_ONCE);
            final Handler<AsyncResult<ProtonSender>> detachHandler = link -> {
                final Span detachHandlerSpan = newSpan("detach Command receiver", authenticatedDevice,
                        traceSamplingPriority);
                removeConnectionLossHandler(connection, address.toString());
                sendDisconnectedTtdEvent(tenantId, deviceId, authenticatedDevice, detachHandlerSpan.context());
                onLinkDetach(sender);
                consumer.close(detachHandlerSpan.context())
                        .onComplete(v -> detachHandlerSpan.finish());
            };
            HonoProtonHelper.setCloseHandler(sender, detachHandler);
            HonoProtonHelper.setDetachHandler(sender, detachHandler);
            sender.open();

            // At this point, the remote peer's receiver link is successfully opened and is ready to receive
            // commands. Send "device ready for command" notification downstream.
            log.debug("established link [address: {}] for sending commands to device", address);

            sendConnectedTtdEvent(tenantId, deviceId, authenticatedDevice, span.context());
            return consumer;
        }).recover(t -> Future.failedFuture(
                new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "cannot create command consumer")));
    }

    private Future<ProtocolAdapterCommandConsumer> createCommandConsumer(
            final ProtonSender sender,
            final ResourceIdentifier sourceAddress,
            final Device authenticatedDevice,
            final Span span) {

        final Handler<CommandContext> commandHandler = commandContext -> {

            final Sample timer = metrics.startTimer();
            addMicrometerSample(commandContext, timer);
            Tags.COMPONENT.set(commandContext.getTracingSpan(), getTypeName());
            final Command command = commandContext.getCommand();
            final Future<TenantObject> tenantTracker = getTenantConfiguration(sourceAddress.getTenantId(),
                    commandContext.getTracingContext());

            tenantTracker.compose(tenantObject -> {
                if (!command.isValid()) {
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "malformed command message"));
                }
                if (!sender.isOpen()) {
                    return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                            "sender link is not open"));
                }
                return checkMessageLimit(tenantObject, command.getPayloadSize(), commandContext.getTracingContext());
            }).compose(success -> {
                onCommandReceived(tenantTracker.result(), sender, commandContext);
                return Future.succeededFuture();
            }).otherwise(failure -> {
                if (failure instanceof ClientErrorException) {
                    commandContext.reject(getErrorCondition(failure));
                } else {
                    commandContext.release();
                }
                metrics.reportCommand(
                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                        sourceAddress.getTenantId(),
                        tenantTracker.result(),
                        ProcessingOutcome.from(failure),
                        command.getPayloadSize(),
                        timer);
                return null;
            });
        };
        if (authenticatedDevice != null && !authenticatedDevice.getDeviceId().equals(sourceAddress.getResourceId())) {
            // gateway scenario
            return getCommandConsumerFactory().createCommandConsumer(sourceAddress.getTenantId(),
                    sourceAddress.getResourceId(), authenticatedDevice.getDeviceId(), commandHandler, null, span.context());
        } else {
            return getCommandConsumerFactory().createCommandConsumer(sourceAddress.getTenantId(),
                    sourceAddress.getResourceId(), commandHandler, null, span.context());
        }
    }

    /**
     * Invoked for every valid command that has been received from
     * an application.
     * <p>
     * This implementation simply forwards the command to the device
     * via the given link.
     *
     * @param tenantObject The tenant configuration object.
     * @param sender The link for sending the command to the device.
     * @param commandContext The context in which the adapter receives the command message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected void onCommandReceived(final TenantObject tenantObject, final ProtonSender sender,
            final CommandContext commandContext) {

        Objects.requireNonNull(tenantObject);
        Objects.requireNonNull(sender);
        Objects.requireNonNull(commandContext);

        final Command command = commandContext.getCommand();
        final Message msg = command.getCommandMessage();
        if (msg.getCorrelationId() == null) {
            // fall back to message ID
            msg.setCorrelationId(msg.getMessageId());
        }
        if (command.isTargetedAtGateway()) {
            MessageHelper.addDeviceId(msg, command.getOriginalDeviceId());
        }

        final AtomicBoolean isCommandSettled = new AtomicBoolean(false);

        if (sender.sendQueueFull()) {
            log.debug("cannot send command to device: no credit available [{}]", command);
            final Exception ex = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                    "no credit available for sending command to device");
            TracingHelper.logError(commandContext.getTracingSpan(), ex);
            commandContext.release();
            reportSentCommand(tenantObject, commandContext, ProcessingOutcome.UNDELIVERABLE);
        } else {
            final Long timerId = getConfig().getSendMessageToDeviceTimeout() < 1 ? null
                    : vertx.setTimer(getConfig().getSendMessageToDeviceTimeout(), tid -> {
                log.debug("waiting for delivery update timed out after {}ms [{}]",
                        getConfig().getSendMessageToDeviceTimeout(), command);
                if (isCommandSettled.compareAndSet(false, true)) {
                    // timeout reached -> release command
                    final Exception ex = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                            "timeout waiting for delivery update from device");
                    TracingHelper.logError(commandContext.getTracingSpan(), ex);
                    commandContext.release();
                    reportSentCommand(tenantObject, commandContext, ProcessingOutcome.UNDELIVERABLE);
                } else {
                    log.trace("command is already settled and downstream application was already notified [{}]", command);
                }
            });

            sender.send(msg, delivery -> {

                if (timerId != null) {
                    // disposition received -> cancel timer
                    vertx.cancelTimer(timerId);
                }
                if (!isCommandSettled.compareAndSet(false, true)) {
                    log.trace("command is already settled and downstream application was already notified [{}]", command);
                } else {
                    // release the command message when the device either
                    // rejects or does not settle the command request message.
                    final DeliveryState remoteState = delivery.getRemoteState();
                    ProcessingOutcome outcome = null;
                    if (delivery.remotelySettled()) {
                        commandContext.disposition(remoteState);
                        if (Accepted.class.isInstance(remoteState)) {
                            outcome = ProcessingOutcome.FORWARDED;
                        } else if (Rejected.class.isInstance(remoteState)) {
                            outcome = ProcessingOutcome.UNPROCESSABLE;
                        } else if (Released.class.isInstance(remoteState)) {
                            outcome = ProcessingOutcome.UNDELIVERABLE;
                        } else if (Modified.class.isInstance(remoteState)) {
                            final Modified modified = (Modified) remoteState;
                            outcome = modified.getUndeliverableHere() ? ProcessingOutcome.UNPROCESSABLE : ProcessingOutcome.UNDELIVERABLE;
                        }
                    } else {
                        log.debug("device did not settle command message [remote state: {}, {}]", remoteState, command);
                        final Map<String, Object> logItems = new HashMap<>(2);
                        logItems.put(Fields.EVENT, "device did not settle command");
                        logItems.put("remote state", remoteState);
                        commandContext.getTracingSpan().log(logItems);
                        commandContext.release();
                        outcome = ProcessingOutcome.UNDELIVERABLE;
                    }
                    reportSentCommand(tenantObject, commandContext, outcome);
                }
            });

            final Map<String, Object> items = new HashMap<>(4);
            items.put(Fields.EVENT, "command sent to device");
            if (sender.getRemoteTarget() != null) {
                items.put(Tags.MESSAGE_BUS_DESTINATION.getKey(), sender.getRemoteTarget().getAddress());
            }
            items.put(TracingHelper.TAG_QOS.getKey(), sender.getQoS().name());
            items.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
            commandContext.getTracingSpan().log(items);
        }
    }

    private void reportSentCommand(final TenantObject tenantObject, final CommandContext commandContext,
            final ProcessingOutcome outcome) {
        metrics.reportCommand(
                commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                commandContext.getCommand().getTenant(),
                tenantObject,
                outcome,
                commandContext.getCommand().getPayloadSize(),
                getMicrometerSample(commandContext));
    }

    /**
     * Closes a link with an error condition.
     *
     * @param link The link to close.
     * @param t The throwable to create the error condition from.
     * @param span The span to log the error condition to (may be {@code null}).
     */
    private <T extends ProtonLink<T>> void closeLinkWithError(
            final ProtonLink<T> link,
            final Throwable t,
            final Span span) {

        final ErrorCondition ec = getErrorCondition(t);
        log.debug("closing link with error condition [symbol: {}, description: {}]", ec.getCondition(), ec.getDescription());
        link.setCondition(ec);
        link.close();
        if (span != null) {
            TracingHelper.logError(span, t);
        }
    }

    /**
     * Forwards a message received from a device to downstream consumers.
     * <p>
     * This method also handles disposition updates.
     *
     * @param context The context that the message has been received in.
     * @param resource The resource that the message should be uploaded to.
     * @param currentSpan The currently active OpenTracing span that is used to
     *                    trace the forwarding of the message.
     * @return A future indicating the outcome.
     */
    private Future<ProtonDelivery> uploadMessage(
            final AmqpContext context,
            final ResourceIdentifier resource,
            final Span currentSpan) {

        final Promise<Void> contentTypeCheck = Promise.promise();

        if (isPayloadOfIndicatedType(context.getMessagePayload(), context.getMessageContentType())) {
            contentTypeCheck.complete();
        } else {
            contentTypeCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "empty notifications must not contain payload"));
        }

        switch (context.getEndpoint()) {
        case TELEMETRY:
            return contentTypeCheck.future()
                    .compose(ok -> doUploadMessage(context, resource, getTelemetrySender(resource.getTenantId()),
                            currentSpan));
        case EVENT:
            return contentTypeCheck.future()
                    .compose(ok -> doUploadMessage(context, resource, getEventSender(resource.getTenantId()),
                            currentSpan));
        case COMMAND_RESPONSE:
            return doUploadCommandResponseMessage(context, resource, currentSpan);
        default:
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "unknown endpoint"));
        }
    }

    private Future<ProtonDelivery> doUploadMessage(
            final AmqpContext context,
            final ResourceIdentifier resource,
            final Future<DownstreamSender> senderFuture,
            final Span currentSpan) {

        log.trace("forwarding {} message", context.getEndpoint().getCanonicalName());

        final Future<JsonObject> tokenFuture = getRegistrationAssertion(resource.getTenantId(), resource.getResourceId(),
                context.getAuthenticatedDevice(), currentSpan.context());
        final Future<TenantObject> tenantTracker = getTenantConfiguration(resource.getTenantId(),
                currentSpan.context());
        final Future<TenantObject> tenantValidationTracker = tenantTracker
                .compose(tenantObject -> CompositeFuture
                        .all(isAdapterEnabled(tenantObject),
                                checkMessageLimit(tenantObject, context.getPayloadSize(), currentSpan.context()))
                        .map(success -> tenantObject));

        return CompositeFuture.all(tenantValidationTracker, tokenFuture, senderFuture)
                .compose(ok -> {

                    final DownstreamSender sender = senderFuture.result();
                    final Message downstreamMessage = addProperties(
                            context.getMessage(),
                            context.getRequestedQos(),
                            ResourceIdentifier.from(context.getEndpoint().getCanonicalName(), resource.getTenantId(), resource.getResourceId()),
                            context.getAddress().toString(),
                            tenantValidationTracker.result(),
                            tokenFuture.result(),
                            null); // no TTD

                    if (context.isRemotelySettled()) {
                        // client uses AT_MOST_ONCE delivery semantics -> fire and forget
                        return sender.send(downstreamMessage, currentSpan.context());
                    } else {
                        // client uses AT_LEAST_ONCE delivery semantics
                        return sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context());
                    }

                }).recover(t -> {

                    log.debug("cannot process {} message from device [tenant: {}, device-id: {}]",
                            context.getEndpoint().getCanonicalName(),
                            resource.getTenantId(),
                            resource.getResourceId(), t);
                    metrics.reportTelemetry(
                            context.getEndpoint(),
                            resource.getTenantId(),
                            tenantTracker.result(),
                            ProcessingOutcome.from(t),
                            context.isRemotelySettled() ? QoS.AT_MOST_ONCE : QoS.AT_LEAST_ONCE,
                            context.getPayloadSize(),
                            context.getTimer());
                    return Future.failedFuture(t);

                }).map(delivery -> {

                    metrics.reportTelemetry(
                            context.getEndpoint(),
                            resource.getTenantId(),
                            tenantTracker.result(),
                            ProcessingOutcome.FORWARDED,
                            context.isRemotelySettled() ? QoS.AT_MOST_ONCE : QoS.AT_LEAST_ONCE,
                            context.getPayloadSize(),
                            context.getTimer());
                    return delivery;
                });
    }

    private Future<ProtonDelivery> doUploadCommandResponseMessage(
            final AmqpContext context,
            final ResourceIdentifier resource,
            final Span currentSpan) {

        final Future<CommandResponse> responseTracker = Optional.ofNullable(CommandResponse.from(context.getMessage()))
                .map(r -> Future.succeededFuture(r))
                .orElseGet(() -> {
                    TracingHelper.logError(currentSpan,
                            String.format("invalid message (correlationId: %s, address: %s, status: %s)",
                                    context.getMessage().getCorrelationId(), context.getMessage().getAddress(),
                                    MessageHelper.getStatus(context.getMessage())));
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "malformed command response message"));
                });
        final Future<TenantObject> tenantTracker = getTenantConfiguration(resource.getTenantId(),
                currentSpan.context());

        return CompositeFuture.all(tenantTracker, responseTracker)
                .compose(ok -> {
                    final CommandResponse commandResponse = responseTracker.result();
                    log.trace("sending command response [device-id: {}, status: {}, correlation-id: {}, reply-to: {}]",
                            resource.getResourceId(), commandResponse.getStatus(), commandResponse.getCorrelationId(),
                            commandResponse.getReplyToId());

                    final Map<String, Object> items = new HashMap<>(3);
                    items.put(Fields.EVENT, "sending command response");
                    items.put(TracingHelper.TAG_CORRELATION_ID.getKey(), commandResponse.getCorrelationId());
                    items.put(MessageHelper.APP_PROPERTY_STATUS, commandResponse.getStatus());
                    currentSpan.log(items);

                    final Future<JsonObject> tokenFuture = getRegistrationAssertion(resource.getTenantId(),
                            resource.getResourceId(), context.getAuthenticatedDevice(), currentSpan.context());
                    final Future<TenantObject> tenantValidationTracker = CompositeFuture
                            .all(isAdapterEnabled(tenantTracker.result()),
                                    checkMessageLimit(tenantTracker.result(), context.getPayloadSize(),
                                            currentSpan.context()))
                            .map(success -> tenantTracker.result());

                    return CompositeFuture.all(tenantValidationTracker, tokenFuture)
                            .compose(success -> sendCommandResponse(resource.getTenantId(), commandResponse,
                                    currentSpan.context()));
                }).map(delivery -> {

                    log.trace("forwarded command response from device [tenant: {}, device-id: {}]",
                            resource.getTenantId(), resource.getResourceId());
                    metrics.reportCommand(
                            Direction.RESPONSE,
                            resource.getTenantId(),
                            tenantTracker.result(),
                            ProcessingOutcome.FORWARDED,
                            context.getPayloadSize(),
                            context.getTimer());
                    return delivery;

                }).recover(t -> {

                    log.debug("cannot process command response from device [tenant: {}, device-id: {}]",
                            resource.getTenantId(), resource.getResourceId(), t);
                    metrics.reportCommand(
                            Direction.RESPONSE,
                            resource.getTenantId(),
                            tenantTracker.result(),
                            ProcessingOutcome.from(t),
                            context.getPayloadSize(),
                            context.getTimer());

                    return Future.failedFuture(t);
                });
    }

    /**
     * Closes the specified receiver link.
     *
     * @param link The link to close.
     */
    private <T extends ProtonLink<T>> void onLinkDetach(final ProtonLink<T> link) {
        log.debug("closing link [{}]", link.getName());
        link.close();
    }

    /**
     * Checks if a message is targeted at a supported endpoint.
     * <p>
     * Also checks if the delivery semantics in use are appropriate for the
     * targeted endpoint.
     *
     * @param ctx The message to check.
     *
     * @return A succeeded future if validation was successful.
     */
    Future<AmqpContext> validateEndpoint(final AmqpContext ctx) {

        final Promise<AmqpContext> result = Promise.promise();
        if (ctx.getAddress() == null) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
        } else {
            switch (ctx.getEndpoint()) {
            case COMMAND_RESPONSE:
            case TELEMETRY:
                result.complete(ctx);
                break;
            case EVENT:
                if (ctx.isRemotelySettled()) {
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "event endpoint accepts unsettled messages only"));
                } else {
                    result.complete(ctx);
                }
                break;
            default:
                log.debug("device wants to send message for unsupported address [{}]", ctx.getAddress());
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "unsupported endpoint"));
            }
        }
        return result.future();
    }

    private static Future<ResourceIdentifier> getResourceIdentifier(final Source source) {

        if (source == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such node"));
        } else {
            final Promise<ResourceIdentifier> result = Promise.promise();
            try {
                if (Strings.isNullOrEmpty(source.getAddress())) {
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                            "no such node"));
                } else {
                    result.complete(ResourceIdentifier.fromString(source.getAddress()));
                }
            } catch (Throwable e) {
                result.fail(e);
            }
            return result.future();
        }
    }

    private static void setConnectionLossHandler(final ProtonConnection con, final String key, final Function<Span, Future<Void>> handler) {
        @SuppressWarnings("unchecked")
        final Map<String, Function<Span, Future<Void>>> handlers = Optional
                .ofNullable(con.attachments().get(KEY_CONNECTION_LOSS_HANDLERS, Map.class))
                .orElse(new HashMap<>());
        handlers.put(key, handler);
        con.attachments().set(KEY_CONNECTION_LOSS_HANDLERS, Map.class, handlers);
    }

    private static Collection<Function<Span, Future<Void>>> getConnectionLossHandlers(final ProtonConnection con) {
        @SuppressWarnings("unchecked")
        final Map<String, Function<Span, Future<Void>>> handlers = con.attachments().get(KEY_CONNECTION_LOSS_HANDLERS, Map.class);
        return handlers != null ? handlers.values() : Collections.emptyList();
    }

    private static boolean removeConnectionLossHandler(final ProtonConnection con, final String key) {
        @SuppressWarnings("unchecked")
        final Map<String, Function<Span, Future<Void>>> handlers = con.attachments().get(KEY_CONNECTION_LOSS_HANDLERS, Map.class);
        return handlers != null && handlers.remove(key) != null;
    }

    private static Device getAuthenticatedDevice(final ProtonConnection con) {
        return con.attachments().get(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class);
    }

    private static OptionalInt getTraceSamplingPriority(final ProtonConnection con) {
        return Optional.ofNullable(con.attachments().get(AmqpAdapterConstants.KEY_TRACE_SAMPLING_PRIORITY, OptionalInt.class))
                .orElse(OptionalInt.empty());
    }

    private Future<Void> checkConnectionLimitForAdapter() {
        final Promise<Void> result = Promise.promise();
        if (getConnectionLimitManager() != null && getConnectionLimitManager().isLimitExceeded()) {
            result.fail(new AdapterConnectionsExceededException(null, "connection limit for the adapter exceeded", null));
        } else {
            result.complete();
        }
        return result.future();
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
        return Constants.PORT_AMQP;
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
