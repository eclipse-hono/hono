/*******************************************************************************
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.adapter.AdapterConnectionsExceededException;
import org.eclipse.hono.adapter.AdapterDisabledException;
import org.eclipse.hono.adapter.AuthorizationException;
import org.eclipse.hono.adapter.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.adapter.auth.device.DeviceCredentials;
import org.eclipse.hono.adapter.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.adapter.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.adapter.auth.device.X509AuthProvider;
import org.eclipse.hono.adapter.limiting.ConnectionLimitManager;
import org.eclipse.hono.adapter.limiting.DefaultConnectionLimitManager;
import org.eclipse.hono.adapter.limiting.MemoryBasedConnectionLimitStrategy;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.HonoProtonHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;

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
import io.vertx.proton.ProtonConnection;
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
     * The minimum amount of memory (bytes) that the adapter requires to run on a standard Java VM.
     */
    private static final int MINIMAL_MEMORY_JVM = 100_000_000;
    /**
     * The minimum amount of memory (bytes) that the adapter requires to run on a Substrate VM (i.e. when running
     * as a GraalVM native image).
     */
    private static final int MINIMAL_MEMORY_SUBSTRATE = 35_000_000;

    /**
     * The amount of memory (bytes) required for each connection.
     */
    private static final int MEMORY_PER_CONNECTION = 20_000;

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

    // -----------------------------------------< AbstractProtocolAdapterBase >---
    /**
     * {@inheritDoc}
     */
    @Override
    public String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_AMQP;
    }

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    public void setMetrics(final AmqpAdapterMetrics metrics) {
        Optional.ofNullable(metrics)
            .ifPresent(m -> log.info("reporting metrics using [{}]", metrics.getClass().getName()));
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
            if (authenticatorFactory == null && getConfig().isAuthenticationRequired()) {
                authenticatorFactory = new AmqpAdapterSaslAuthenticatorFactory(
                        getMetrics(),
                        () -> tracer.buildSpan("open connection")
                                .ignoreActiveSpan()
                                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                                .withTag(Tags.COMPONENT.getKey(), getTypeName())
                                .start(),
                        new SaslPlainAuthHandler(
                                new UsernamePasswordAuthProvider(getCredentialsClient(), tracer),
                                this::handleBeforeCredentialsValidation),
                        new SaslExternalAuthHandler(
                                new TenantServiceBasedX509Authentication(getTenantClient(), tracer),
                                new X509AuthProvider(getCredentialsClient(), tracer),
                                this::handleBeforeCredentialsValidation));
            }
            return Future.succeededFuture();
        })
        .compose(success -> CompositeFuture.all(bindSecureServer(), bindInsecureServer()))
        .map(ok -> (Void) null)
        .onComplete(startPromise);
    }

    /**
     * Handles any operations that should be invoked as part of the authentication process after the credentials got
     * determined and before they get validated. Can be used to perform checks using the credentials and tenant
     * information before the potentially expensive credentials validation is done
     * <p>
     * The default implementation updates the trace sampling priority in the execution context tracing span.
     * <p>
     * Subclasses should override this method in order to perform additional operations after calling this super method.
     *
     * @param credentials The credentials.
     * @param executionContext The execution context, including the TenantObject.
     * @return A future indicating the outcome of the operation. A failed future will fail the authentication attempt.
     */
    protected Future<Void> handleBeforeCredentialsValidation(final DeviceCredentials credentials,
            final SaslResponseContext executionContext) {

        final String tenantId = credentials.getTenantId();
        final Span span = executionContext.getTracingSpan();
        final String authId = credentials.getAuthId();

        return getTenantConfiguration(tenantId, span.context())
                .recover(t -> Future.failedFuture(CredentialsApiAuthProvider.mapNotFoundToBadCredentialsException(t)))
                .compose(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantId, null, authId);
                    final OptionalInt traceSamplingPriority = TenantTraceSamplingHelper.applyTraceSamplingPriority(
                            tenantObject, authId, span);
                    executionContext.getProtonConnection().attachments().set(
                            AmqpAdapterConstants.KEY_TRACE_SAMPLING_PRIORITY, OptionalInt.class, traceSamplingPriority);
                    return Future.succeededFuture();
                });
    }

    private ConnectionLimitManager createConnectionLimitManager() {
        return new DefaultConnectionLimitManager(
                new MemoryBasedConnectionLimitStrategy(
                        getConfig().isSubstrateVm() ? MINIMAL_MEMORY_SUBSTRATE : MINIMAL_MEMORY_JVM,
                        MEMORY_PER_CONNECTION + getConfig().getMaxSessionWindowSize()),
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
        final Span span = newSpan("handle closing of connection", getAuthenticatedDevice(con),
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
                .orElseGet(() -> tracer.buildSpan("open connection")
                    .ignoreActiveSpan()
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                    .withTag(Tags.COMPONENT.getKey(), getTypeName())
                    .start());

        final Device authenticatedDevice = getAuthenticatedDevice(con);
        TracingHelper.TAG_AUTHENTICATED.set(span, authenticatedDevice != null);
        if (authenticatedDevice != null) {
            TracingHelper.setDeviceTags(span, authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
        }
        final String cipherSuite = con.attachments().get(AmqpAdapterConstants.KEY_TLS_CIPHER_SUITE, String.class);
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
                        Optional.ofNullable(authenticatedDevice).map(Device::getTenantId).orElse(null),
                        cipherSuite);
                return null;
            })
            .otherwise(t -> {
                con.setCondition(getErrorCondition(t));
                con.close();
                TracingHelper.logError(span, t);
                metrics.reportConnectionAttempt(
                        AbstractProtocolAdapterBase.getOutcome(t),
                        Optional.ofNullable(authenticatedDevice).map(Device::getTenantId).orElse(null),
                        cipherSuite);
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
                                        isAdapterEnabled(tenantConfig),
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
     * @throws IllegalStateException if this adapter is already running.
     */
    protected void setInsecureAmqpServer(final ProtonServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalStateException("AMQP Server should not be running");
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

        final Device authenticatedDevice = getAuthenticatedDevice(conn);
        final OptionalInt traceSamplingPriority = getTraceSamplingPriority(conn);

        final Span span = newSpan("attach device sender link", authenticatedDevice, traceSamplingPriority);
        span.log(Map.of("snd-settle-mode", receiver.getRemoteQoS()));

        final String remoteTargetAddress = Optional.ofNullable(receiver.getRemoteTarget()).map(Target::getAddress)
                .orElse(null);
        if (!Strings.isNullOrEmpty(remoteTargetAddress)) {
            log.debug("client provided target address [{}] in open frame, closing link [container: {}, {}]",
                    remoteTargetAddress, conn.getRemoteContainer(), authenticatedDevice);
            span.log(Map.of("target address", remoteTargetAddress));
            final Exception ex = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "container supports anonymous terminus only");
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
                    HonoProtonHelper.onReceivedMessageDeliveryUpdatedFromRemote(delivery, d -> {
                        log.debug("got unexpected disposition update for message received from device [remote state: {}, container: {}, {}]",
                                delivery.getRemoteState(), conn.getRemoteContainer(), authenticatedDevice);
                        msgSpan.log("got unexpected disposition from device [remote state: " + delivery.getRemoteState() + "]");
                    });
                    msgSpan.log(Map.of(
                            Tags.MESSAGE_BUS_DESTINATION.getKey(), message.getAddress(),
                            "settled", delivery.remotelySettled()));

                    final AmqpContext ctx = AmqpContext.fromMessage(delivery, message, msgSpan, authenticatedDevice);
                    ctx.setTimer(metrics.startTimer());

                    final Future<Void> spanPreparationFuture = authenticatedDevice == null
                            ? applyTraceSamplingPriorityForAddressTenant(ctx.getAddress(), msgSpan)
                            : Future.succeededFuture();

                    spanPreparationFuture
                            .compose(ar -> onMessageReceived(ctx)
                                    .onSuccess(ok -> msgSpan.finish())
                                    .onFailure(error -> closeConnectionOnTerminalError(error, conn, ctx, msgSpan)));
                } catch (final Exception ex) {
                    log.warn("error handling message [container: {}, {}]", conn.getRemoteContainer(),
                            authenticatedDevice, ex);
                    if (!conn.isDisconnected()) {
                        ProtonHelper.released(delivery, true);
                    }
                }
            });
            receiver.open();
            log.debug("established link for receiving messages from device [container: {}, {}]",
                    conn.getRemoteContainer(), authenticatedDevice);
            span.log("link established");
        }
        span.finish();
    }

    /**
     * Applies the trace sampling priority configured for the tenant derived from the given address on the given span.
     * <p>
     * This is for unauthenticated AMQP connections where the tenant id gets taken from the message address.
     *
     * @param address The message address (may be {@code null}).
     * @param span The <em>OpenTracing</em> span to apply the configuration to.
     * @return A succeeded future indicating the outcome of the operation. A failure to determine the tenant is ignored
     *         here.
     * @throws NullPointerException if span is {@code null}.
     */
    protected Future<Void> applyTraceSamplingPriorityForAddressTenant(final ResourceIdentifier address, final Span span) {
        Objects.requireNonNull(span);
        if (address == null || address.getTenantId() == null) {
            // an invalid address is ignored here
            return Future.succeededFuture();
        }
        return getTenantConfiguration(address.getTenantId(), span.context())
                .map(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantObject.getTenantId(), null);
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, null, span);
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
    protected Future<?> onMessageReceived(final AmqpContext ctx) {

        log.trace("processing message [address: {}, qos: {}]", ctx.getAddress(), ctx.getRequestedQos());
        final Span msgSpan = ctx.getTracingSpan();
        return validateEndpoint(ctx)
            .compose(validatedEndpoint -> validateAddress(validatedEndpoint.getAddress(), validatedEndpoint.getAuthenticatedDevice()))
            .compose(validatedAddress -> uploadMessage(ctx, validatedAddress, msgSpan))
            .map(d -> {
                ProtonHelper.accepted(ctx.delivery(), true);
                return d;
            }).recover(t -> {
                if (t instanceof ClientErrorException) {
                    MessageHelper.rejected(ctx.delivery(), getErrorCondition(t));
                } else {
                    ProtonHelper.released(ctx.delivery(), true);
                }
                log.debug("failed to process message from device", t);
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

        final Device authenticatedDevice = getAuthenticatedDevice(connection);
        final OptionalInt traceSamplingPriority = getTraceSamplingPriority(connection);

        final Span span = newSpan("attach device command receiver link", authenticatedDevice, traceSamplingPriority);

        getResourceIdentifier(sender.getRemoteSource())
        .compose(address -> validateAddress(address, authenticatedDevice))
        .compose(validAddress -> {
            // validAddress ALWAYS contains the tenant and device ID
            if (CommandConstants.isCommandEndpoint(validAddress.getEndpoint())) {
                return openCommandSenderLink(connection, sender, validAddress, authenticatedDevice, span, traceSamplingPriority)
                        .map(consumer -> {
                            setConnectionLossHandler(connection, validAddress.toString(), connectionLossSpan -> {
                                // do not use the above created span for closing the command consumer
                                // because that span will (usually) be finished long before the
                                // connection is closed/lost
                                return closeCommandConsumer(consumer, validAddress.getTenantId(),
                                        validAddress.getResourceId(), authenticatedDevice, connectionLossSpan);
                            });
                            return consumer;
                        });
            } else {
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such node"));
            }
        })
        .map(consumer -> {
            span.log("link established");
            return consumer;
        })
        .recover(t -> {
            if (t instanceof ServiceInvocationException) {
                closeLinkWithError(sender, t, span);
            } else {
                closeLinkWithError(sender,
                        new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid source address"),
                        span);
            }
            return Future.failedFuture(t);
        })
        .onComplete(s -> {
            span.finish();
        });
    }

    private void closeConnectionOnTerminalError(final Throwable error, final ProtonConnection conn,
            final AmqpContext ctx, final Span span) {
        final ResourceIdentifier address = ctx.getAddress();
        if (address != null) {
            isTerminalError(error, address.getResourceId(), ctx.getAuthenticatedDevice(), span.context())
                    .onSuccess(isTerminalError -> {
                        if (isTerminalError) {
                            span.log("closing connection to device");
                            conn.close();
                            conn.disconnect();
                        }
                    })
                    .onComplete(o -> span.finish());
        } else {
            span.finish();
        }
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
        traceSamplingPriority.ifPresent(prio -> TracingHelper.setTraceSamplingPriority(span, prio));
        return span;
    }

    private Future<CommandConsumer> openCommandSenderLink(
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
                final Span detachHandlerSpan = newSpan("detach device command receiver link",
                        authenticatedDevice, traceSamplingPriority);
                removeConnectionLossHandler(connection, address.toString());
                onLinkDetach(sender);
                closeCommandConsumer(consumer, tenantId, deviceId, authenticatedDevice, detachHandlerSpan)
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

    private Future<Void> closeCommandConsumer(
            final CommandConsumer consumer,
            final String tenantId,
            final String deviceId,
            final Device authenticatedDevice,
            final Span span) {

        return consumer.close(span.context())
                .recover(thr -> {
                    TracingHelper.logError(span, thr);
                    // ignore all but precon-failed errors
                    if (ServiceInvocationException.extractStatusCode(thr) == HttpURLConnection.HTTP_PRECON_FAILED) {
                        log.debug("command consumer wasn't active anymore - skip sending disconnected event [tenant: {}, device-id: {}]",
                                tenantId, deviceId);
                        span.log("command consumer wasn't active anymore - skip sending disconnected event");
                        return Future.failedFuture(thr);
                    }
                    return Future.succeededFuture();
                })
                .compose(v -> sendDisconnectedTtdEvent(tenantId, deviceId, authenticatedDevice, span.context()))
                .mapEmpty();
    }

    private Future<CommandConsumer> createCommandConsumer(
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
                if (!HonoProtonHelper.isLinkOpenAndConnected(sender)) {
                    return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                            "sender link is not open"));
                }
                return checkMessageLimit(tenantObject, command.getPayloadSize(), commandContext.getTracingContext());
            }).compose(success -> {
                // in case of a gateway having subscribed for a specific device,
                // check the via-gateways, ensuring that the gateway may act on behalf of the device at this point in time
                if (authenticatedDevice != null && !authenticatedDevice.getDeviceId().equals(sourceAddress.getResourceId())) {
                    return getRegistrationAssertion(
                            authenticatedDevice.getTenantId(),
                            sourceAddress.getResourceId(),
                            authenticatedDevice,
                            commandContext.getTracingContext());
                }
                return Future.succeededFuture();
            }).compose(success -> {
                onCommandReceived(tenantTracker.result(), sender, commandContext);
                return Future.succeededFuture();
            }).otherwise(failure -> {
                if (failure instanceof ClientErrorException) {
                    commandContext.reject(failure);
                } else {
                    commandContext.release(failure);
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

        final Future<RegistrationAssertion> tokenTracker = Optional.ofNullable(authenticatedDevice)
                .map(v -> getRegistrationAssertion(
                        authenticatedDevice.getTenantId(),
                        sourceAddress.getResourceId(),
                        authenticatedDevice,
                        span.context()))
                .orElseGet(Future::succeededFuture);

        if (authenticatedDevice != null && !authenticatedDevice.getDeviceId().equals(sourceAddress.getResourceId())) {
            // gateway scenario
            return tokenTracker.compose(v -> getCommandConsumerFactory().createCommandConsumer(
                    sourceAddress.getTenantId(),
                    sourceAddress.getResourceId(),
                    authenticatedDevice.getDeviceId(),
                    commandHandler,
                    null,
                    span.context()));
        } else {
            return tokenTracker.compose(v -> getCommandConsumerFactory().createCommandConsumer(
                    sourceAddress.getTenantId(),
                    sourceAddress.getResourceId(),
                    commandHandler,
                    null,
                    span.context()));
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
    protected void onCommandReceived(
            final TenantObject tenantObject,
            final ProtonSender sender,
            final CommandContext commandContext) {

        Objects.requireNonNull(tenantObject);
        Objects.requireNonNull(sender);
        Objects.requireNonNull(commandContext);

        final Command command = commandContext.getCommand();

        final AtomicBoolean isCommandSettled = new AtomicBoolean(false);

        if (sender.sendQueueFull()) {
            log.debug("cannot send command to device: no credit available [{}]", command);
            commandContext.release(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                    "no credit available for sending command to device"));
            reportSentCommand(tenantObject, commandContext, ProcessingOutcome.UNDELIVERABLE);
        } else {

            final Message msg = ProtonHelper.message();
            msg.setAddress(String.format("%s/%s/%s",
                    CommandConstants.COMMAND_ENDPOINT,
                    command.getTenant(),
                    command.getDeviceId()));
            msg.setCorrelationId(command.getCorrelationId());
            msg.setSubject(command.getName());
            MessageHelper.setPayload(msg, command.getContentType(), command.getPayload());

            if (command.isTargetedAtGateway()) {
                MessageHelper.addDeviceId(msg, command.getDeviceId());
            }
            if (!command.isOneWay()) {
                msg.setReplyTo(String.format("%s/%s/%s",
                        CommandConstants.COMMAND_RESPONSE_ENDPOINT,
                        command.getTenant(),
                        Commands.getDeviceFacingReplyToId(command.getReplyToId(), command.getDeviceId(), command.getMessagingType())));
            }

            final Long timerId = getConfig().getSendMessageToDeviceTimeout() < 1 ? null
                    : vertx.setTimer(getConfig().getSendMessageToDeviceTimeout(), tid -> {
                final String linkOrConnectionClosedInfo = HonoProtonHelper.isLinkOpenAndConnected(sender)
                        ? "" : " (link or connection already closed)";
                log.debug("waiting for delivery update timed out after {}ms{} [{}]",
                        getConfig().getSendMessageToDeviceTimeout(), linkOrConnectionClosedInfo, command);
                if (isCommandSettled.compareAndSet(false, true)) {
                    // timeout reached -> release command
                    commandContext.release(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                            "timeout waiting for delivery update from device"));
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
                        if (Accepted.class.isInstance(remoteState)) {
                            outcome = ProcessingOutcome.FORWARDED;
                            commandContext.accept();
                        } else if (Rejected.class.isInstance(remoteState)) {
                            outcome = ProcessingOutcome.UNPROCESSABLE;
                            final String cause = Optional.ofNullable(((Rejected) remoteState).getError())
                                    .map(ErrorCondition::getDescription)
                                    .orElse(null);
                            commandContext.reject(cause);
                        } else if (Released.class.isInstance(remoteState)) {
                            outcome = ProcessingOutcome.UNDELIVERABLE;
                            commandContext.release();
                        } else if (Modified.class.isInstance(remoteState)) {
                            final Modified modified = (Modified) remoteState;
                            outcome = modified.getUndeliverableHere() ? ProcessingOutcome.UNPROCESSABLE : ProcessingOutcome.UNDELIVERABLE;
                            commandContext.modify(modified.getDeliveryFailed(), modified.getUndeliverableHere());
                        }
                    } else {
                        log.debug("device did not settle command message [remote state: {}, {}]", remoteState, command);
                        final Map<String, Object> logItems = new HashMap<>(2);
                        logItems.put(Fields.EVENT, "device did not settle command");
                        logItems.put("remote state", remoteState);
                        commandContext.getTracingSpan().log(logItems);
                        commandContext.release(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                                "device did not settle command"));
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

    private void reportSentCommand(
            final TenantObject tenantObject,
            final CommandContext commandContext,
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
    private Future<?> uploadMessage(
            final AmqpContext context,
            final ResourceIdentifier resource,
            final Span currentSpan) {

        switch (context.getEndpoint()) {
        case TELEMETRY:
        case EVENT:
            final Promise<Void> contentTypeCheck = Promise.promise();

            if (isPayloadOfIndicatedType(context.getMessagePayload(), context.getMessageContentType())) {
                contentTypeCheck.complete();
            } else {
                contentTypeCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "empty notifications must not contain payload"));
            }
            return contentTypeCheck.future()
                    .compose(ok -> doUploadMessage(context, resource, currentSpan));
        case COMMAND_RESPONSE:
            return doUploadCommandResponseMessage(context, resource, currentSpan);
        default:
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "unknown endpoint"));
        }
    }

    private Future<?> doUploadMessage(
            final AmqpContext context,
            final ResourceIdentifier resource,
            final Span currentSpan) {

        log.trace("forwarding {} message", context.getEndpoint().getCanonicalName());

        final Future<RegistrationAssertion> tokenFuture = getRegistrationAssertion(
                resource.getTenantId(),
                resource.getResourceId(),
                context.getAuthenticatedDevice(),
                currentSpan.context());
        final Future<TenantObject> tenantTracker = getTenantConfiguration(resource.getTenantId(),
                currentSpan.context());
        final Future<TenantObject> tenantValidationTracker = tenantTracker
                .compose(tenantObject -> CompositeFuture
                        .all(isAdapterEnabled(tenantObject),
                                checkMessageLimit(tenantObject, context.getPayloadSize(), currentSpan.context()))
                        .map(success -> tenantObject));

        return CompositeFuture.all(tenantValidationTracker, tokenFuture)
                .compose(ok -> {
                    final Map<String, Object> props = getDownstreamMessageProperties(context);

                    if (context.getEndpoint() == EndpointType.TELEMETRY) {
                        return getTelemetrySender(tenantValidationTracker.result()).sendTelemetry(
                                tenantValidationTracker.result(),
                                tokenFuture.result(),
                                context.getRequestedQos(),
                                context.getMessageContentType(),
                                context.getMessagePayload(),
                                props,
                                currentSpan.context());
                    } else {
                        return getEventSender(tenantValidationTracker.result()).sendEvent(
                                tenantValidationTracker.result(),
                                tokenFuture.result(),
                                context.getMessageContentType(),
                                context.getMessagePayload(),
                                props,
                                currentSpan.context());
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

                }).map(ok -> {

                    metrics.reportTelemetry(
                            context.getEndpoint(),
                            resource.getTenantId(),
                            tenantTracker.result(),
                            ProcessingOutcome.FORWARDED,
                            context.isRemotelySettled() ? QoS.AT_MOST_ONCE : QoS.AT_LEAST_ONCE,
                            context.getPayloadSize(),
                            context.getTimer());
                    return ok;
                });
    }

    private CommandResponse getCommandResponse(final Message message) {

        return CommandResponse.fromAddressAndCorrelationId(
                message.getAddress(),
                message.getCorrelationId() instanceof String ? (String) message.getCorrelationId() : null,
                MessageHelper.getPayload(message),
                message.getContentType(),
                MessageHelper.getStatus(message));
    }

    private Future<Void> doUploadCommandResponseMessage(
            final AmqpContext context,
            final ResourceIdentifier resource,
            final Span currentSpan) {

        final Future<CommandResponse> responseTracker = Optional.ofNullable(getCommandResponse(context.getMessage()))
                .map(Future::succeededFuture)
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

                    final Future<RegistrationAssertion> tokenFuture = getRegistrationAssertion(
                            resource.getTenantId(),
                            resource.getResourceId(),
                            context.getAuthenticatedDevice(),
                            currentSpan.context());
                    final Future<TenantObject> tenantValidationTracker = CompositeFuture
                            .all(isAdapterEnabled(tenantTracker.result()),
                                    checkMessageLimit(tenantTracker.result(), context.getPayloadSize(),
                                            currentSpan.context()))
                            .map(success -> tenantTracker.result());

                    return CompositeFuture.all(tenantValidationTracker, tokenFuture)
                            .compose(success -> sendCommandResponse(commandResponse, tenantTracker.result(),
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
                    // note that the exception thrown here will not result
                    // in the message being rejected because the message
                    // is presettled and thus the corresponding disposition
                    // frame with the error condition will not reach the client
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

        if (source == null || !ResourceIdentifier.isValid(source.getAddress())) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such node"));
        } else {
            return Future.succeededFuture(ResourceIdentifier.fromString(source.getAddress()));
        }
    }

    /**
     * Creates an AMQP error condition for a throwable.
     * <p>
     * Unknown error types are mapped to {@link AmqpError#PRECONDITION_FAILED}.
     *
     * @param t The throwable to map to an error condition.
     * @return The error condition.
     */
    protected static ErrorCondition getErrorCondition(final Throwable t) {
        final String errorMessage = ServiceInvocationException.getErrorMessageForExternalClient(t);
        if (t instanceof AuthorizationException || t instanceof AdapterDisabledException) {
            return ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, errorMessage);
        } else if (ServiceInvocationException.class.isInstance(t)) {
            final ServiceInvocationException error = (ServiceInvocationException) t;
            switch (error.getErrorCode()) {
            case HttpURLConnection.HTTP_BAD_REQUEST:
                return ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, errorMessage);
            case HttpURLConnection.HTTP_FORBIDDEN:
                return ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, errorMessage);
            case HttpUtils.HTTP_TOO_MANY_REQUESTS:
                return ProtonHelper.condition(AmqpError.RESOURCE_LIMIT_EXCEEDED, errorMessage);
            default:
                return ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, errorMessage);
            }
        } else {
            return ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, errorMessage);
        }
    }

    private static void setConnectionLossHandler(final ProtonConnection con, final String key, final Function<Span, Future<Void>> handler) {
        @SuppressWarnings("unchecked")
        final Map<String, Function<Span, Future<Void>>> handlers = Optional
                .ofNullable(con.attachments().get(KEY_CONNECTION_LOSS_HANDLERS, Map.class))
                .orElseGet(HashMap::new);
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
        if (getConnectionLimitManager() != null && getConnectionLimitManager().isLimitExceeded()) {
            return Future.failedFuture(
                    new AdapterConnectionsExceededException(null, "connection limit for the adapter exceeded", null));
        }
        return Future.succeededFuture();
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
