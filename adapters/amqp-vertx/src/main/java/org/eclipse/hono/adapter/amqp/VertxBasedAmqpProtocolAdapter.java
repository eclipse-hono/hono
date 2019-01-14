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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.Accepted;
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
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EndpointType;
import org.eclipse.hono.util.HonoProtonHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;

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

    private void onConnectRequest(final ProtonConnection con) {

        final Span span = Optional
                // try to pick up span that has been created during SASL handshake
                .ofNullable(con.attachments().get(AmqpAdapterConstants.KEY_CURRENT_SPAN, Span.class))
                // or create a fresh one if no SASL handshake has been performed
                .orElse(tracer.buildSpan("open connection")
                    .ignoreActiveSpan()
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                    .withTag(Tags.COMPONENT.getKey(), getTypeName())
                    .start());

        final Device authenticatedDevice = con.attachments()
                .get(AmqpAdapterConstants.KEY_CLIENT_DEVICE, Device.class);
        TracingHelper.TAG_AUTHENTICATED.set(span, authenticatedDevice != null);

        final Future<Void> connectAuthorizationCheck = Future.future();

        if (getConfig().isAuthenticationRequired()) {

            if (authenticatedDevice == null) {
                connectAuthorizationCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED));
            } else {
                LOG.trace("received connection request from {}", authenticatedDevice);
                // the SASL handshake will already have authenticated the device
                // and will have verified that the adapter is enabled for the tenant
                // we still need to check if the device/gateway exists and is enabled
                checkDeviceRegistration(authenticatedDevice, span.context())
                .map(ok -> {
                    LOG.debug("device [tenant-id: {}, device-id: {}] is registered and enabled",
                            authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
                    span.log("device is registered and enabled");
                    return (Void) null;
                })
                .setHandler(connectAuthorizationCheck);
            }

        } else {
            LOG.trace("received connection request from anonymous device [container: {}]", con.getRemoteContainer());
            connectAuthorizationCheck.complete();
        }

        connectAuthorizationCheck.map(ok -> {
            con.setContainer(getTypeName());
            setConnectionHandlers(con);
            con.open();
            if (authenticatedDevice != null) {
                span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, authenticatedDevice.getTenantId());
                span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, authenticatedDevice.getDeviceId());
            }
            span.log("connection accepted");
            return null;
        }).otherwise(t -> {
            con.setCondition(AmqpContext.getErrorCondition(t));
            con.close();
            TracingHelper.logError(span, t);
            return null;
        }).setHandler(conAttempt -> {
            span.finish();
        });
    }

    private void setConnectionHandlers(final ProtonConnection con) {

        con.disconnectHandler(lostConnection -> {
            LOG.debug("lost connection to device [container: {}]", con.getRemoteContainer());
            Optional.ofNullable(getConnectionLossHandler(con)).ifPresent(handler -> handler.handle(null));
        });
        con.closeHandler(remoteClose -> {
            handleRemoteConnectionClose(con, remoteClose);
            Optional.ofNullable(getConnectionLossHandler(con)).ifPresent(handler -> handler.handle(null));
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

        final Span span = newSpan("attach anonymous sender", authenticatedDevice);

        if (ProtonQoS.AT_MOST_ONCE.equals(receiver.getRemoteQoS())) {
            // the device needs to send all types of message over this link
            // so we must make sure that it does not allow for pre-settled messages
            // to be sent only
            final Exception ex = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "link must not use snd-settle-mode 'settled'");
            TracingHelper.logError(span, ex);
            closeLinkWithError(receiver, ex);
        } else  if (receiver.getRemoteTarget() != null && receiver.getRemoteTarget().getAddress() != null) {
            if (!receiver.getRemoteTarget().getAddress().isEmpty()) {
                LOG.debug("Closing link due to the present of Target [address : {}]", receiver.getRemoteTarget().getAddress());
            }
            final Exception ex = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "this adapter supports anonymous relay mode only");
            TracingHelper.logError(span, ex);
            closeLinkWithError(receiver, ex);
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

                final Span msgSpan = newSpan("upload message", authenticatedDevice);

                final Map<String, Object> items = new HashMap<>(1);
                items.put(Tags.MESSAGE_BUS_DESTINATION.getKey(), message.getAddress());
                msgSpan.log(items);

                validateEndpoint(message.getAddress(), delivery)
                .compose(address -> validateAddress(address, authenticatedDevice))
                .recover(t -> {
                    // invalid address / endpoint
                    MessageHelper.rejected(delivery, AmqpContext.getErrorCondition(t));
                    return Future.failedFuture(t);
                })
                .map(validatedAddress -> createContext(validatedAddress, delivery, message, authenticatedDevice))
                .compose(context -> uploadMessage(context, msgSpan))
                .otherwise(t -> {
                    TracingHelper.logError(msgSpan, t);
                    return null;
                })
                .setHandler(s -> {
                    msgSpan.finish();
                });
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

    private AmqpContext createContext(
            final ResourceIdentifier validatedAddress,
            final ProtonDelivery delivery,
            final Message message,
            final Device authenticatedDevice) {

        final String to = validatedAddress.toString();
        if (!to.equals(message.getAddress())) {
            LOG.debug("adjusting message's address [orig: {}, updated: {}]", message.getAddress(), to);
            message.setAddress(to);
        }
        return new AmqpContext(delivery, message, authenticatedDevice);
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
            TracingHelper.logError(span, t);
            if (t instanceof ServiceInvocationException) {
                closeLinkWithError(sender, t);
            } else {
                closeLinkWithError(sender,
                        new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Invalid source address"));
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

                    Tags.COMPONENT.set(commandContext.getCurrentSpan(), getTypeName());
                    final Command command = commandContext.getCommand();

                    if (!command.isValid()) {
                        final Exception ex = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed command message");
                        commandContext.reject(AmqpContext.getErrorCondition(ex), 1);
                    } else if (!sender.isOpen()) {
                        commandContext.release(1);
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

        sender.send(msg, delivery -> {
            // release the command message when the device either
            // rejects or does not settle the command request message.
            final DeliveryState remoteState = delivery.getRemoteState();
            final Map<String, Object> logItems = new HashMap<>(2);
            if (delivery.remotelySettled()) {
                if (Accepted.class.isInstance(remoteState)) {
                    LOG.trace("device accepted command message [command: {}]", command.getName());
                    logItems.put(Fields.EVENT, "device accepted command");
                    commandContext.getCurrentSpan().log(logItems);
                    commandContext.accept(1);
                } else if (Rejected.class.isInstance(remoteState)) {
                    final ErrorCondition error = ((Rejected) remoteState).getError();
                    LOG.debug("device rejected command message [command: {}, error: {}]", command.getName(), error);
                    logItems.put(Fields.EVENT, "device rejected command");
                    commandContext.getCurrentSpan().log(logItems);
                    commandContext.reject(error, 1);
                } else if (Released.class.isInstance(remoteState)) {
                    LOG.debug("device released command message [command: {}]", command.getName());
                    logItems.put(Fields.EVENT, "device released command");
                    commandContext.getCurrentSpan().log(logItems);
                    commandContext.release(1);
                }
            } else {
                LOG.debug("device did not settle command message [command: {}, remote state: {}]", command.getName(),
                        remoteState.getClass().getSimpleName());
                logItems.put(Fields.EVENT, "device did not settle command");
                logItems.put("remote state", remoteState.getClass().getSimpleName());
                commandContext.getCurrentSpan().log(logItems);
                commandContext.release(1);
            }
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
     * Closes the specified link using the given throwable to set the local ErrorCondition object for the link.
     *
     * @param link The link to close.
     * @param t The throwable to use to determine the error condition object.
     */
    private <T extends ProtonLink<T>> void closeLinkWithError(final ProtonLink<T> link, final Throwable t) {
        final ErrorCondition ec = AmqpContext.getErrorCondition(t);
        LOG.debug("closing link with error condition [symbol: {}, description: {}]", ec.getCondition(), ec.getDescription());
        link.setCondition(ec);
        link.close();
    }

    /**
     * Forwards a message received from a device to downstream consumers.
     * <p>
     * This method also handles disposition updates.
     * 
     * @param context The context that the message has been received in.
     * @param currentSpan The currently active OpenTracing span that is used to
     *                    trace the forwarding of the message.
     * @return A future indicating the outcome.
     */
    protected Future<Void> uploadMessage(final AmqpContext context, final Span currentSpan) {

        final Future<Void> contentTypeCheck = Future.future();

        if (isPayloadOfIndicatedType(context.getMessagePayload(), context.getMessageContentType())) {
            contentTypeCheck.complete();
        } else {
            contentTypeCheck.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "empty notifications must not contain payload"));
        }

        return contentTypeCheck.compose(ok -> {
            switch (EndpointType.fromString(context.getEndpoint())) {
            case TELEMETRY:
                LOG.trace("forwarding telemetry data");
                return doUploadMessage(context, getTelemetrySender(context.getTenantId()), currentSpan);
            case EVENT:
                LOG.trace("forwarding event");
                return doUploadMessage(context, getEventSender(context.getTenantId()), currentSpan);
            case CONTROL:
                LOG.trace("forwarding command response");
                return doUploadCommandResponseMessage(context, currentSpan);
            default:
                return Future
                        .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "unknown endpoint"));
            }
        })
        .map(downstreamDelivery -> {
            context.accept();
            return (Void) null;
        })
        .recover(t -> {
            context.handleFailure(t);
            return Future.failedFuture(t);
        });

    }

    private Future<ProtonDelivery> doUploadMessage(
            final AmqpContext context,
            final Future<MessageSender> senderFuture,
            final Span currentSpan) {

        final Future<JsonObject> tokenFuture = getRegistrationAssertion(context.getTenantId(), context.getDeviceId(),
                context.getAuthenticatedDevice(), currentSpan.context());
        final Future<TenantObject> tenantConfigFuture = getTenantConfiguration(context.getTenantId(), currentSpan.context());

        return CompositeFuture.all(tenantConfigFuture, tokenFuture, senderFuture)
                .compose(ok -> {
                    final TenantObject tenantObject = tenantConfigFuture.result();
                    if (tenantObject.isAdapterEnabled(getTypeName())) {

                        final MessageSender sender = senderFuture.result();
                        final Message downstreamMessage = newMessage(context.getResourceIdentifier(),
                                sender.isRegistrationAssertionRequired(),
                                context.getEndpoint(), context.getMessageContentType(), context.getMessagePayload(),
                                tokenFuture.result(), null);

                        if (context.isRemotelySettled()) {
                            // client uses AT_MOST_ONCE delivery semantics -> fire and forget
                            return sender.send(downstreamMessage, currentSpan.context());
                        } else {
                            // client uses AT_LEAST_ONCE delivery semantics
                            return sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context());
                        }
                    } else {
                        // this adapter is not enabled for tenant
                        return Future.failedFuture(
                                new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                                        String.format("This adapter is not enabled for tenant [tenantId: %s].",
                                                context.getTenantId())));
                    }
                }).recover(t -> {
                    LOG.debug("cannot process {} message from device [tenant: {}, device-id: {}]",
                            context.getEndpoint(),
                            context.getTenantId(),
                            context.getDeviceId(), t);
                    return Future.failedFuture(t);
                });
    }

    private Future<ProtonDelivery> doUploadCommandResponseMessage(final AmqpContext context, final Span currentSpan) {

        final String correlationId = Optional.ofNullable(context.getMessage().getCorrelationId())
                .map(id -> {
                    if (id instanceof String) {
                        return (String) id;
                    } else {
                        return null;
                    }
                }).orElse(null);

        final Integer statusCode = MessageHelper.getApplicationProperty(context.getMessage().getApplicationProperties(),
                MessageHelper.APP_PROPERTY_STATUS, Integer.class);

        final Map<String, Object> items = new HashMap<>(2);
        items.put(MessageHelper.APP_PROPERTY_STATUS, statusCode);
        items.put(TracingHelper.TAG_CORRELATION_ID.getKey(), correlationId);
        currentSpan.log(items);

        LOG.debug("creating command response [status: {}, correlation-id: {}, reply-to: {}]",
                statusCode, correlationId, context.getResourceIdentifier().toString());
        final CommandResponse commandResponse = CommandResponse.from(context.getMessagePayload(),
                context.getMessageContentType(), statusCode, correlationId, context.getResourceIdentifier());

        if (commandResponse == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "malformed command response message"));
        } else {
            return sendCommandResponse(context.getTenantId(), commandResponse, currentSpan.context())
                    .map(delivery -> {
                        LOG.trace("forwarded command response from device [tenant: {}, device-id: {}]",
                                context.getTenantId(), context.getDeviceId());
                        return delivery;
                    }).recover(t -> {
                        LOG.debug("cannot process command response from device [tenant: {}, device-id: {}]",
                                context.getTenantId(),
                                context.getDeviceId(), t);

                        return Future.failedFuture(t);
                    });
        }
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
     * This method validates that a client tries to publish a message to a supported endpoint. If the endpoint is supported,
     * this method also validates that the quality service of the supported endpoint.
     * 
     * @param address The message address.
     * @param delivery The delivery through which this adapter receives the message.
     *
     * @return A future with the address upon success or a failed future.
     */
    Future<ResourceIdentifier> validateEndpoint(final String address, final ProtonDelivery delivery) {

        return getResourceIdentifier(address)
                .map(resource -> {
                    switch (EndpointType.fromString(resource.getEndpoint())) {
                    case TELEMETRY:
                        return resource;
                    case EVENT:
                        if (delivery.remotelySettled()) {
                            throw new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                                    "event endpoint accepts unsettled messages only");
                        } else {
                            return resource;
                        }
                    case CONTROL:
                        // for publishing a response to a command
                        return resource;
                    default:
                        LOG.debug("device wants to send message for unsupported endpoint [{}]", resource.getEndpoint());
                        throw new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "unsupported endpoint");
                    }
                });
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
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                        "unauthenticated clients must provide tenant and device ID in message's address"));
            } else {
                result.complete(address);
            }
        } else {
            if (address.getTenantId() != null && address.getResourceId() == null) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "message's address must not contain tenant ID only"));
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

    private static Future<ResourceIdentifier> getResourceIdentifier(final Source source) {

        if (source == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such node"));
        } else {
            return getResourceIdentifier(source.getAddress());
        }
    }

    private static Future<ResourceIdentifier> getResourceIdentifier(final String resource) {

        final Future<ResourceIdentifier> result = Future.future();
        try {
            if (Strings.isNullOrEmpty(resource)) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                        "no such node"));
            } else {
                result.complete(ResourceIdentifier.fromString(resource));
            }
        } catch (Throwable e) {
            result.fail(e);
        }
        return result;
    }

    private static void addConnectionLossHandler(final ProtonConnection con, final Handler<Void> handler) {

        con.attachments().set("connectionLossHandler", Handler.class, handler);
    }

    @SuppressWarnings("unchecked")
    private static Handler<Void> getConnectionLossHandler(final ProtonConnection con) {

        return con.attachments().get("connectionLossHandler", Handler.class);
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
