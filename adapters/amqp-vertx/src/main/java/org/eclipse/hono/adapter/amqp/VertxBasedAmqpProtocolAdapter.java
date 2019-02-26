/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
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
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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

    // These values should be made configurable.
    private static final long DEFAULT_COMMAND_CONSUMER_CHECK_INTERVAL_MILLIS = 10000; // 10 seconds

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
    protected void doStart(final Future<Void> startFuture) {
        checkPortConfiguration()
                .compose(success -> {
                    if (authenticatorFactory == null && getConfig().isAuthenticationRequired()) {
                        authenticatorFactory = new AmqpAdapterSaslAuthenticatorFactory(
                                getTenantServiceClient(),
                                getCredentialsServiceClient(),
                                getConfig(),
                                tracer,
                                () -> tracer.buildSpan("open connection")
                                    .ignoreActiveSpan()
                                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                                    .withTag(Tags.COMPONENT.getKey(), getTypeName())
                                    .start());
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
                        .setPort(determineInsecurePort())
                        .setMaxFrameSize(getConfig().getMaxFrameSize());

            final Future<Void> result = Future.future();
            insecureServer = createServer(insecureServer, options);
            insecureServer.connectHandler(this::onConnectRequest).listen(ar -> {
                if (ar.succeeded()) {
                    LOG.info("insecure AMQP server listening on [{}:{}]", getConfig().getInsecurePortBindAddress(), getActualInsecurePort());
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
                        .setMaxFrameSize(getConfig().getMaxFrameSize());
            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);

            final Future<Void> result = Future.future();
            secureServer = createServer(secureServer, options);
            secureServer.connectHandler(this::onConnectRequest).listen(ar -> {
                if (ar.succeeded()) {
                    LOG.info("secure AMQP server listening on {}:{}", getConfig().getBindAddress(), getActualPort());
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

    /**
     * Handles a remote peer's request to open a connection.
     * 
     * @param con The connection to be opened.
     */
    protected void onConnectRequest(final ProtonConnection con) {

        con.disconnectHandler(lostConnection -> {
            LOG.debug("lost connection to device [container: {}]", con.getRemoteContainer());
            Optional.ofNullable(getConnectionLossHandler(con)).ifPresent(handler -> handler.handle(null));
            decrementConnectionCount(con);
        });
        con.closeHandler(remoteClose -> {
            handleRemoteConnectionClose(con, remoteClose);
            Optional.ofNullable(getConnectionLossHandler(con)).ifPresent(handler -> handler.handle(null));
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
            if (remoteOpen.failed()) {
                LOG.debug("ignoring device's open frame containing error", remoteOpen.cause());
            } else {
                processRemoteOpen(remoteOpen.result());
            }
        });
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
            span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, authenticatedDevice.getTenantId());
            span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, authenticatedDevice.getDeviceId());
        }

        final Future<Void> connectAuthorizationCheck = Future.future();

        if (getConfig().isAuthenticationRequired()) {

            if (authenticatedDevice == null) {
                connectAuthorizationCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "anonymous devices not supported"));
            } else {
                LOG.trace("received connection request from {}", authenticatedDevice);
                // the SASL handshake will already have authenticated the device
                // and will have verified that the adapter is enabled for the tenant
                // we still need to check if the device/gateway exists and is enabled
                checkDeviceRegistration(authenticatedDevice, span.context())
                .map(ok -> {
                    LOG.debug("{} is registered and enabled", authenticatedDevice);
                    span.log("device is registered and enabled");
                    return ok;
                }).setHandler(connectAuthorizationCheck);
            }

        } else {
            LOG.trace("received connection request from anonymous device [container: {}]", con.getRemoteContainer());
            connectAuthorizationCheck.complete();
        }

        connectAuthorizationCheck
        .map(ok -> {
            con.setContainer(getTypeName());
            con.open();
            LOG.debug("connection with device [container: {}] established", con.getRemoteContainer());
            span.log("connection established");
            if (authenticatedDevice == null) {
                metrics.incrementUnauthenticatedConnections();
            } else {
                metrics.incrementConnections(authenticatedDevice.getTenantId());
            }
            return null;
        }).otherwise(t -> {
            con.setCondition(AmqpContext.getErrorCondition(t));
            con.close();
            TracingHelper.logError(span, t);
            return null;
        }).setHandler(s -> span.finish());
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
        LOG.debug("opening new session with client [container: {}, session window size: {}]",
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
            LOG.debug("client [container: {}] closed connection", con.getRemoteContainer());
        } else {
            LOG.debug("client [container: {}] closed connection with error", con.getRemoteContainer(), res.cause());
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

        final Span span = newSpan("attach receiver", authenticatedDevice);

        if (ProtonQoS.AT_MOST_ONCE.equals(receiver.getRemoteQoS())) {
            // the device needs to send all types of message over this link
            // so we must make sure that it does not allow for pre-settled messages
            // to be sent only
            final Exception ex = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "unsupported snd-settle-mode: settled");
            closeLinkWithError(receiver, ex, span);
        } else  if (receiver.getRemoteTarget() != null && receiver.getRemoteTarget().getAddress() != null) {
            if (!receiver.getRemoteTarget().getAddress().isEmpty()) {
                LOG.debug("Closing link due to the present of Target [address : {}]", receiver.getRemoteTarget().getAddress());
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
                final AmqpContext ctx = AmqpContext.fromMessage(delivery, message, authenticatedDevice);
                ctx.setTimer(metrics.startTimer());
                onMessageReceived(ctx);
            });
            receiver.open();
            if (authenticatedDevice == null) {
                LOG.debug("established link for receiving messages from device [container: {}]",
                        conn.getRemoteContainer());
            } else {
                LOG.debug("established link for receiving messages from device [tenant: {}, device-id: {}]]",
                        authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
            }
            span.log("link established");
        }
        span.finish();
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

        final Span msgSpan = newSpan("upload message", ctx.getAuthenticatedDevice());
        msgSpan.log(Collections.singletonMap(Tags.MESSAGE_BUS_DESTINATION.getKey(), ctx.getAddress()));

        return validateEndpoint(ctx)
        .compose(validatedEndpoint -> validateAddress(validatedEndpoint.getAddress(), validatedEndpoint.getAuthenticatedDevice()))
        .compose(validatedAddress -> uploadMessage(ctx, validatedAddress, msgSpan))
        .map(d -> {
            ProtonHelper.accepted(ctx.delivery(), true);
            msgSpan.finish();
            return d;
        }).recover(t -> {
            if (t instanceof ClientErrorException) {
                final ErrorCondition condition = AmqpContext.getErrorCondition(t);
                MessageHelper.rejected(ctx.delivery(), condition);
            } else {
                ProtonHelper.released(ctx.delivery(), true);
            }
            TracingHelper.logError(msgSpan, t);
            msgSpan.finish();
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

        final Span span = newSpan("attach Command receiver", authenticatedDevice);

        getResourceIdentifier(sender.getRemoteSource())
        .compose(address -> validateAddress(address, authenticatedDevice))
        .map(validAddress -> {
            // validAddress ALWAYS contains the tenant and device ID
            if (CommandConstants.isCommandEndpoint(validAddress.getEndpoint())) {
                return openCommandSenderLink(sender, validAddress, authenticatedDevice, span)
                        .map(consumer -> {
                            addConnectionLossHandler(connection, connectionLost -> {
                                // do not use the current span for sending the disconnected event
                                // because that span will (usually) be finished long before the
                                // connection is closed/lost
                                sendDisconnectedTtdEvent(
                                        validAddress.getTenantId(),
                                        validAddress.getResourceId(),
                                        authenticatedDevice,
                                        null)
                                .setHandler(sendAttempt -> {
                                    consumer.close(null);
                                });
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
        .setHandler(s -> {
            span.finish();
        });
    }

    private Span newSpan(final String operationName, final Device authenticatedDevice) {
        final Span span = tracer.buildSpan(operationName)
                .ignoreActiveSpan()
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), getTypeName())
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .start();

        if (authenticatedDevice != null) {
            span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, authenticatedDevice.getTenantId());
            span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, authenticatedDevice.getDeviceId());
        }
        return span;
    }

    private Future<MessageConsumer> openCommandSenderLink(
            final ProtonSender sender,
            final ResourceIdentifier address,
            final Device authenticatedDevice,
            final Span span) {

        return createCommandConsumer(sender, address).map(consumer -> {

            final String tenantId = address.getTenantId();
            final String deviceId = address.getResourceId();
            sender.setSource(sender.getRemoteSource());
            sender.setTarget(sender.getRemoteTarget());

            sender.setQoS(ProtonQoS.AT_LEAST_ONCE);
            final Handler<AsyncResult<ProtonSender>> detachHandler = link -> {
                final Span detachHandlerSpan = newSpan("detach Command receiver", authenticatedDevice);
                sendDisconnectedTtdEvent(tenantId, deviceId, authenticatedDevice, detachHandlerSpan.context());
                consumer.close(null);
                onLinkDetach(sender);
                detachHandlerSpan.finish();
            };
            HonoProtonHelper.setCloseHandler(sender, detachHandler);
            HonoProtonHelper.setDetachHandler(sender, detachHandler);
            sender.open();

            // At this point, the remote peer's receiver link is successfully opened and is ready to receive
            // commands. Send "device ready for command" notification downstream.
            LOG.debug("established link [address: {}] for sending commands to device", address);

            sendConnectedTtdEvent(tenantId, deviceId, authenticatedDevice, span.context());
            return consumer;
        }).otherwise(t -> {
            throw new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "cannot create command consumer");
        });
    }

    private Future<MessageConsumer> createCommandConsumer(
            final ProtonSender sender,
            final ResourceIdentifier sourceAddress) {

        return getCommandConnection().createCommandConsumer(
                sourceAddress.getTenantId(),
                sourceAddress.getResourceId(),
                commandContext -> {

                    final Sample timer = metrics.startTimer();
                    addMicrometerSample(commandContext, timer);
                    Tags.COMPONENT.set(commandContext.getCurrentSpan(), getTypeName());
                    final Command command = commandContext.getCommand();

                    if (!command.isValid()) {
                        final Exception ex = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed command message");
                        commandContext.reject(AmqpContext.getErrorCondition(ex), 1);
                        metrics.reportCommand(
                                command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                sourceAddress.getTenantId(),
                                ProcessingOutcome.UNPROCESSABLE,
                                command.getPayloadSize(),
                                timer);
                    } else if (!sender.isOpen()) {
                        commandContext.release(1);
                        metrics.reportCommand(
                                command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                sourceAddress.getTenantId(),
                                ProcessingOutcome.UNDELIVERABLE,
                                command.getPayloadSize(),
                                timer);
                    } else {
                        onCommandReceived(sender, commandContext);
                    }
                }, closeHandler -> {},
                DEFAULT_COMMAND_CONSUMER_CHECK_INTERVAL_MILLIS);
    }

    /**
     * Invoked for every valid command that has been received from
     * an application.
     * <p>
     * This implementation simply forwards the command to the device
     * via the given link and flows a single credit back to the application.
     * 
     * @param sender The link for sending the command to the device.
     * @param commandContext The context in which the adapter receives the command message.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected void onCommandReceived(final ProtonSender sender, final CommandContext commandContext) {

        Objects.requireNonNull(sender);
        Objects.requireNonNull(commandContext);

        final Command command = commandContext.getCommand();
        final Message msg = command.getCommandMessage();
        if (msg.getCorrelationId() == null) {
            // fall back to message ID
            msg.setCorrelationId(msg.getMessageId());
        }

        // TODO time out waiting for disposition update
        sender.send(msg, delivery -> {
            // release the command message when the device either
            // rejects or does not settle the command request message.
            final DeliveryState remoteState = delivery.getRemoteState();
            final Map<String, Object> logItems = new HashMap<>(2);
            ProcessingOutcome outcome = null;
            if (delivery.remotelySettled()) {
                if (Accepted.class.isInstance(remoteState)) {
                    LOG.trace("device accepted command message [command: {}]", command.getName());
                    logItems.put(Fields.EVENT, "device has accepted command");
                    commandContext.getCurrentSpan().log(logItems);
                    commandContext.accept(1);
                    outcome = ProcessingOutcome.FORWARDED;
                } else if (Rejected.class.isInstance(remoteState)) {
                    final ErrorCondition error = ((Rejected) remoteState).getError();
                    LOG.debug("device rejected command message [command: {}, error: {}]", command.getName(), error);
                    logItems.put(Fields.EVENT, "device has rejected command");
                    commandContext.getCurrentSpan().log(logItems);
                    commandContext.reject(error, 1);
                    outcome = ProcessingOutcome.UNPROCESSABLE;
                } else if (Released.class.isInstance(remoteState)) {
                    LOG.debug("device released command message [command: {}]", command.getName());
                    logItems.put(Fields.EVENT, "device has released command");
                    commandContext.getCurrentSpan().log(logItems);
                    commandContext.release(1);
                    outcome = ProcessingOutcome.UNDELIVERABLE;
                } else if (Modified.class.isInstance(remoteState)) {
                    final Modified modified = (Modified) remoteState;
                    LOG.debug("device modified command message [command: {}]", command.getName());
                    logItems.put(Fields.EVENT, "device has modified command");
                    commandContext.getCurrentSpan().log(logItems);
                    commandContext.modify(modified.getDeliveryFailed(), modified.getUndeliverableHere(), 1);
                    outcome = modified.getUndeliverableHere() ? ProcessingOutcome.UNPROCESSABLE : ProcessingOutcome.UNDELIVERABLE;
                }
            } else {
                LOG.debug("device did not settle command message [command: {}, remote state: {}]", command.getName(),
                        remoteState.getClass().getSimpleName());
                logItems.put(Fields.EVENT, "device did not settle command");
                logItems.put("remote state", remoteState.getClass().getSimpleName());
                commandContext.getCurrentSpan().log(logItems);
                commandContext.release(1);
                outcome = ProcessingOutcome.UNDELIVERABLE;
            }
            metrics.reportCommand(
                    command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                    command.getTenant(),
                    outcome,
                    command.getPayloadSize(),
                    getMicrometerSample(commandContext));
        });
        final Map<String, Object> items = new HashMap<>(4);
        items.put(Fields.EVENT, "command sent to device");
        if (sender.getRemoteTarget() != null) {
            items.put(Tags.MESSAGE_BUS_DESTINATION.getKey(), sender.getRemoteTarget().getAddress());
        }
        items.put(TracingHelper.TAG_QOS.getKey(), sender.getQoS().name());
        items.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
        commandContext.getCurrentSpan().log(items);
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

        final ErrorCondition ec = AmqpContext.getErrorCondition(t);
        LOG.debug("closing link with error condition [symbol: {}, description: {}]", ec.getCondition(), ec.getDescription());
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

        final Future<Void> contentTypeCheck = Future.future();

        if (isPayloadOfIndicatedType(context.getMessagePayload(), context.getMessageContentType())) {
            contentTypeCheck.complete();
        } else {
            contentTypeCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "empty notifications must not contain payload"));
        }

        return contentTypeCheck.compose(ok -> {
            switch (context.getEndpoint()) {
            case TELEMETRY:
                return doUploadMessage(context, resource, getTelemetrySender(resource.getTenantId()), currentSpan);
            case EVENT:
                return doUploadMessage(context, resource, getEventSender(resource.getTenantId()), currentSpan);
            case CONTROL:
                return doUploadCommandResponseMessage(context, resource, currentSpan);
            default:
                return Future
                        .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "unknown endpoint"));
            }
        });
    }

    private Future<ProtonDelivery> doUploadMessage(
            final AmqpContext context,
            final ResourceIdentifier resource,
            final Future<MessageSender> senderFuture,
            final Span currentSpan) {

        LOG.trace("forwarding {} message", context.getEndpoint().getCanonicalName());

        final Future<JsonObject> tokenFuture = getRegistrationAssertion(resource.getTenantId(), resource.getResourceId(),
                context.getAuthenticatedDevice(), currentSpan.context());
        final Future<TenantObject> tenantEnabledFuture = getTenantConfiguration(resource.getTenantId(),
                currentSpan.context()).compose(tenantObject -> isAdapterEnabled(tenantObject));

        return CompositeFuture.all(tenantEnabledFuture, tokenFuture, senderFuture)
                .compose(ok -> {

                    final MessageSender sender = senderFuture.result();
                    final Message downstreamMessage = addProperties(
                            context.getMessage(),
                            ResourceIdentifier.from(context.getEndpoint().getCanonicalName(), resource.getTenantId(), resource.getResourceId()),
                            sender.isRegistrationAssertionRequired(),
                            context.getAddress().toString(),
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

                    LOG.debug("cannot process {} message from device [tenant: {}, device-id: {}]",
                            context.getEndpoint().getCanonicalName(),
                            resource.getTenantId(),
                            resource.getResourceId(), t);
                    final ProcessingOutcome outcome = t instanceof ClientErrorException ? ProcessingOutcome.UNPROCESSABLE : ProcessingOutcome.UNDELIVERABLE;
                    metrics.reportTelemetry(
                            context.getEndpoint(),
                            resource.getTenantId(),
                            outcome,
                            context.isRemotelySettled() ? QoS.AT_MOST_ONCE : QoS.AT_LEAST_ONCE,
                            context.getPayloadSize(),
                            context.getTimer());
                    return Future.failedFuture(t);

                }).map(delivery -> {

                    metrics.reportTelemetry(
                            context.getEndpoint(),
                            resource.getTenantId(),
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

        final Future<CommandResponse> response = Optional.ofNullable(CommandResponse.from(context.getMessage()))
                .map(r -> Future.succeededFuture(r))
                .orElse(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "malformed command response message")));

        return response.compose(commandResponse -> {

            LOG.trace("sending command response [device-id: {}, status: {}, correlation-id: {}, reply-to: {}]",
                    resource.getResourceId(), commandResponse.getStatus(), commandResponse.getCorrelationId(),
                    commandResponse.getReplyToId());

            final Map<String, Object> items = new HashMap<>(3);
            items.put(Fields.EVENT, "sending command response");
            items.put(TracingHelper.TAG_CORRELATION_ID.getKey(), commandResponse.getCorrelationId());
            items.put(MessageHelper.APP_PROPERTY_STATUS, commandResponse.getStatus());
            currentSpan.log(items);

            return sendCommandResponse(resource.getTenantId(), commandResponse, currentSpan.context());

        }).map(delivery -> {

            LOG.trace("forwarded command response from device [tenant: {}, device-id: {}]",
                    resource.getTenantId(), resource.getResourceId());
            metrics.reportCommand(
                    Direction.RESPONSE,
                    resource.getTenantId(),
                    ProcessingOutcome.FORWARDED,
                    context.getPayloadSize(),
                    context.getTimer());
            return delivery;

        }).recover(t -> {

            LOG.debug("cannot process command response from device [tenant: {}, device-id: {}]",
                    resource.getTenantId(), resource.getResourceId(), t);
            final ProcessingOutcome outcome = t instanceof ClientErrorException ? ProcessingOutcome.UNPROCESSABLE : ProcessingOutcome.UNDELIVERABLE;
            metrics.reportCommand(
                    Direction.RESPONSE,
                    resource.getTenantId(),
                    outcome,
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
        LOG.debug("closing link [{}]", link.getName());
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

        final Future<AmqpContext> result = Future.future();
        if (ctx.getAddress() == null) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
        } else {
            switch (ctx.getEndpoint()) {
            case CONTROL:
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
                LOG.debug("device wants to send message for unsupported address [{}]", ctx.getAddress());
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "unsupported endpoint"));
            }
        }
        return result;
    }

    private static Future<ResourceIdentifier> getResourceIdentifier(final Source source) {

        if (source == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such node"));
        } else {
            final Future<ResourceIdentifier> result = Future.future();
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
            return result;
        }
    }

    private static void addConnectionLossHandler(final ProtonConnection con, final Handler<Void> handler) {

        con.attachments().set("connectionLossHandler", Handler.class, handler);
    }

    @SuppressWarnings("unchecked")
    private static Handler<Void> getConnectionLossHandler(final ProtonConnection con) {

        return con.attachments().get("connectionLossHandler", Handler.class);
    }

    private static Device getAuthenticatedDevice(final ProtonConnection con) {
        return Optional.ofNullable(con.attachments())
                .map(attachments -> attachments.get(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class))
                .orElse(null);
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
