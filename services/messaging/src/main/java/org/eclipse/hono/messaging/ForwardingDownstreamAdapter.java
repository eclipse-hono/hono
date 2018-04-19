/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A downstream adapter that provides support for sending messages to an AMQP 1.0 container.
 *
 */
@Component
public abstract class ForwardingDownstreamAdapter implements DownstreamAdapter {

    /**
     * A logger to be shared with subclasses.
     */
    protected Logger                        logger     = LoggerFactory.getLogger(getClass());
    /**
     * The Hono configuration.
     */
    protected HonoMessagingConfigProperties honoConfig = new HonoMessagingConfigProperties();

    private final Map<UpstreamReceiver, ProtonSender> activeSenders          = new HashMap<>();
    private final Map<String, List<UpstreamReceiver>> receiversPerConnection = new HashMap<>();
    private final List<Handler<AsyncResult<Void>>>    clientAttachHandlers   = new ArrayList<>();
    private final Vertx                               vertx;

    private MessagingMetrics  metrics;
    private boolean           running                     = false;
    private boolean           retryOnFailedConnectAttempt = true;
    private ProtonConnection  downstreamConnection;
    private SenderFactory     senderFactory;
    private ConnectionFactory downstreamConnectionFactory;

    /**
     * Creates a new adapter instance for a sender factory.
     * 
     * @param vertx The Vert.x instance to run on.
     * @param senderFactory The factory to use for creating new senders for downstream telemetry data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected ForwardingDownstreamAdapter(final Vertx vertx, final SenderFactory senderFactory) {
        this.vertx = Objects.requireNonNull(vertx);
        this.senderFactory = Objects.requireNonNull(senderFactory);
    }

    /**
     * Sets the global Hono configuration properties.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     * @throws IllegalStateException if this adapter is already running.
     */
    @Autowired(required = false)
    public final void setHonoConfiguration(final HonoMessagingConfigProperties props) {
        if (running) {
            throw new IllegalStateException("configuration can not be set on running adapter");
        } else {
            this.honoConfig = Objects.requireNonNull(props);
        }
    }

    /**
     * Sets the factory to use for connecting to the downstream container.
     * 
     * @param factory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     * @throws IllegalStateException if this adapter is already running.
     */
    @Autowired
    @Qualifier(Constants.QUALIFIER_DOWNSTREAM)
    public final void setDownstreamConnectionFactory(final ConnectionFactory factory) {
        if (running) {
            throw new IllegalStateException("downstream container host can not be set on running adapter");
        } else {
            this.downstreamConnectionFactory = Objects.requireNonNull(factory);
        }
    }

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    @Autowired
    public final void setMetrics(final MessagingMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Connects to the downstream container.
     * 
     * @param startFuture The result of the connection attempt.
     * @throws IllegalStateException If the downstream container host is {@code null}
     *                               or the downstream container port is 0.
     */
    @Override
    public final void start(final Future<Void> startFuture) {

        if (running) {
            startFuture.complete();
        } else if (downstreamConnectionFactory == null) {
            throw new IllegalStateException("downstream connection factory is not set");
        } else {
            running = true;
            if (honoConfig.isWaitForDownstreamConnectionEnabled()) {
                logger.info("waiting for connection to downstream container");
                connectToDownstream(createClientOptions(), attempt -> {
                    if (attempt.succeeded()) {
                        startFuture.complete();
                    } else {
                        startFuture.fail(attempt.cause());
                    }
                });
            } else {
                connectToDownstream(createClientOptions());
                startFuture.complete();
            }
        }
    }

    /**
     * Closes the connection with the downstream container.
     * 
     * @param stopFuture Always succeeds.
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {

        if (running) {
            if (downstreamConnection != null && !downstreamConnection.isDisconnected()) {
                final String container = downstreamConnection.getRemoteContainer();
                logger.info("closing connection to downstream container [{}]", container);
                downstreamConnection.closeHandler(null).disconnectHandler(null).close();
                metrics.decrementDownStreamConnections();
            } else {
                logger.debug("downstream connection already closed");
            }
            running = false;
        }
        stopFuture.complete();
    }

    /**
     * Gets the name of the downstream AMQP 1.0 container this adapter is forwarding messages to.
     * 
     * @return The name or {@code null} if this adapter is currently not connected.
     */
    protected final String getDownstreamContainer() {
        if (downstreamConnection != null) {
            return downstreamConnection.getRemoteContainer();
        } else {
            return null;
        }
    }

    private ProtonClientOptions createClientOptions() {
        return new ProtonClientOptions()
                .setConnectTimeout(200)
                .setReconnectAttempts(1)
                .setReconnectInterval(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS);
    }

    private void connectToDownstream(final ProtonClientOptions options) {
        connectToDownstream(options, null);
    }

    private void connectToDownstream(final ProtonClientOptions options, final Handler<AsyncResult<ProtonConnection>> connectResultHandler) {

        downstreamConnectionFactory.connect(
                options,
                this::onRemoteClose,
                this::onDisconnectFromDownstreamContainer,
                connectAttempt -> {
                    if (connectAttempt.succeeded()) {
                        this.downstreamConnection = connectAttempt.result();
                        metrics.incrementDownStreamConnections();
                        if (connectResultHandler != null) {
                            connectResultHandler.handle(Future.succeededFuture(connectAttempt.result()));
                        }
                    } else {
                        logger.info("failed to connect to downstream container: {}", connectAttempt.cause().getMessage());
                        if (retryOnFailedConnectAttempt) {
                            reconnect(connectResultHandler);
                        } else if (connectResultHandler != null) {
                            connectResultHandler.handle(Future.failedFuture(connectAttempt.cause()));
                        }
                    }
                });
    }

    private void onRemoteClose(final AsyncResult<ProtonConnection> remoteClose) {

        if (remoteClose.succeeded()) {
            if (remoteClose.result() != downstreamConnection) {
                logger.warn("downstream container closed unknown connection");
                return;
            } else {
                logger.info("downstream container [{}] has closed connection", downstreamConnection.getRemoteContainer());
            }
        } else {
            logger.info("downstream container [{}] has closed connection: {}", downstreamConnection.getRemoteContainer(), remoteClose.cause().getMessage());
        }
        downstreamConnection.close();
        onDisconnectFromDownstreamContainer(downstreamConnection);
    }

    /**
     * Handles unexpected disconnection from downstream container.
     * <p>
     * Clears all internal state kept for the connection, e.g. open links etc, and then tries to
     * reconnect.
     * 
     * @param con The failed connection.
     */
    private void onDisconnectFromDownstreamContainer(final ProtonConnection con) {

        if (con != downstreamConnection) {
            logger.warn("unknown connection to downstream container has been disconnected");
        } else {
            // all links to downstream host will now be stale and unusable
            logger.warn("lost connection to downstream container [{}], closing upstream receivers ...", con.getRemoteContainer());

            for (UpstreamReceiver client : activeSenders.keySet()) {
                closeReceiver(client);
            }
            receiversPerConnection.clear();
            activeSenders.clear();
            downstreamConnection.attachments().clear();
            downstreamConnection.disconnectHandler(null);
            downstreamConnection.disconnect();
            metrics.decrementDownStreamConnections();

            for (Iterator<Handler<AsyncResult<Void>>> iter = clientAttachHandlers.iterator(); iter.hasNext(); ) {
                iter.next().handle(Future.failedFuture("connection to downstream container failed"));
                iter.remove();
            }

            reconnect(null);
        }
    }

    private void closeReceiver(final UpstreamReceiver receiver) {
        receiver.close(ErrorConditions.ERROR_NO_DOWNSTREAM_CONSUMER);
        metrics.decrementUpstreamLinks(receiver.getTargetAddress());
        metrics.decrementDownstreamSenders(receiver.getTargetAddress());
        metrics.submitDownstreamLinkCredits(receiver.getTargetAddress(), 0);
    }

    private void reconnect(final Handler<AsyncResult<ProtonConnection>> resultHandler) {

        if (!running) {
            logger.info("adapter is stopped, will not re-connect to downstream container");
        } else {
            final ProtonClientOptions clientOptions = createClientOptions();
            if (clientOptions.getReconnectAttempts() != 0) {
                vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
                    logger.info("attempting to re-connect to downstream container");
                    connectToDownstream(clientOptions, resultHandler);
                });
            }
        }
    }

    @Override
    public final void onClientAttach(final UpstreamReceiver client, final Handler<AsyncResult<Void>> resultHandler) {

        if (!running) {
            throw new IllegalStateException("adapter must be started first");
        }

        Objects.requireNonNull(client);
        Objects.requireNonNull(resultHandler);

        ProtonSender sender = activeSenders.get(client);
        if (sender != null && sender.isOpen()) {
            logger.info("reusing existing downstream sender [con: {}, link: {}]", client.getConnectionId(), client.getLinkId());
            resultHandler.handle(Future.succeededFuture());
        } else {
            removeSender(client);
            // register the result handler to be failed if the connection to the downstream container fails during
            // the attempt to create a downstream sender
            clientAttachHandlers.add(resultHandler);
            Future<Void> tracker = Future.future();
            tracker.setHandler(attempt -> {
                if (attempt.succeeded()) {
                    logger.info("created downstream sender [con: {}, link: {}]", client.getConnectionId(), client.getLinkId());
                } else {
                    logger.warn("can't create downstream sender [con: {}, link: {}]: {}", client.getConnectionId(), client.getLinkId(), attempt.cause().getMessage());
                }
                clientAttachHandlers.remove(resultHandler);
                resultHandler.handle(attempt);
            });

            final ResourceIdentifier targetAddress = ResourceIdentifier.fromString(client.getTargetAddress());
            createSender(targetAddress, replenishedSender -> handleFlow(replenishedSender, client), closeHook -> {
                removeSender(client);
                closeReceiver(client);
            }).compose(createdSender -> {
                addSender(client, createdSender);
                tracker.complete();
            }, tracker);
        }
    }

    /**
     * Invoked when a downstream sender receives link credit and/or a drain request from the downstream container.
     * <p>
     * The credits/drain request is forwarded to the corresponding upstream client.
     * 
     * @param replenishedSender The downstream sender that has received the FLOW.
     * @param client The upstream client associated with the sender.
     */
    public final void handleFlow(
            final ProtonSender replenishedSender,
            final UpstreamReceiver client) {

        logger.trace("received FLOW from downstream container [con:{}, link: {}, sendQueueFull: {}, credits: {}, queued: {}, drain: {}",
                client.getConnectionId(), client.getLinkId(), replenishedSender.sendQueueFull(), replenishedSender.getCredit(),
                replenishedSender.getQueued(), replenishedSender.getDrain());
        if (replenishedSender.getDrain()) {
            // send drain request upstream and act upon result of request to drain upstream client
            client.drain(10000, drainAttempt -> {
                if (drainAttempt.succeeded()) {
                    replenishedSender.drained();
                }
            });
        } else {
            int downstreamCredit = getAvailableDownstreamCredit(replenishedSender);
            client.replenish(downstreamCredit);
            metrics.submitDownstreamLinkCredits(client.getTargetAddress(), downstreamCredit);
        }
    }

    private static int getAvailableDownstreamCredit(final ProtonSender downstreamSender) {
        return Math.max(0, downstreamSender.getCredit());
    }

    private Future<ProtonSender> createSender(
            final ResourceIdentifier targetAddress,
            final Handler<ProtonSender> sendQueueDrainHandler,
            final Handler<Void> closeHook) {

        if (!isConnected()) {
            return Future.failedFuture("downstream connection must be opened before creating sender");
        } else {
            return senderFactory.createSender(downstreamConnection, targetAddress, getDownstreamQos(),
                    sendQueueDrainHandler, closeHook);
        }
    }

    /**
     * Associates a downstream sender with a corresponding upstream client.
     * 
     * @param link The upstream client.
     * @param sender The downstream sender.
     */
    public final void addSender(final UpstreamReceiver link, final ProtonSender sender) {
        sender.attachments().set(Constants.KEY_CONNECTION_ID, String.class, link.getConnectionId());
        sender.setAutoDrained(false); // we need to propagate drain requests upstream and wait for the result
        activeSenders.put(link, sender);
        List<UpstreamReceiver> senders = receiversPerConnection.get(link.getConnectionId());
        if (senders == null) {
            senders = new ArrayList<>();
            receiversPerConnection.put(link.getConnectionId(), senders);
        }
        senders.add(link);
        metrics.incrementDownstreamSenders(link.getTargetAddress());
    }

    /**
     * Removes all state kept for an upstream client.
     * <p>
     * Any downstream sender associated with the client is closed.
     * 
     * @param link The upstream client.
     */
    public final void removeSender(final UpstreamReceiver link) {
        List<UpstreamReceiver> senders = receiversPerConnection.get(link.getConnectionId());
        if (senders != null) {
            senders.remove(link);
        }
        closeSender(link);
    }

    @Override
    public final void onClientDetach(final UpstreamReceiver client) {

        if (!running) {
            throw new IllegalStateException("adapter must be started first");
        }

        Objects.requireNonNull(client);

        removeSender(client);
    }

    @Override
    public final void onClientDisconnect(final String connectionId) {

        if (!running) {
            throw new IllegalStateException("adapter must be started first");
        }

        List<UpstreamReceiver> upstreamReceivers = receiversPerConnection.remove(Objects.requireNonNull(connectionId));
        if (upstreamReceivers != null && !upstreamReceivers.isEmpty()) {
            logger.info("closing {} downstream senders for connection [id: {}]", upstreamReceivers.size(), connectionId);
            for (UpstreamReceiver link : upstreamReceivers) {
                closeSender(link);
                metrics.decrementUpstreamLinks(link.getTargetAddress());
            }
        }
    }

    private void closeSender(final UpstreamReceiver link) {
        ProtonSender sender = activeSenders.remove(link);
        if (sender != null && sender.isOpen()) {
            logger.info("closing downstream sender [con: {}, link: {}]", link.getConnectionId(), link.getLinkId());
            metrics.decrementDownstreamSenders(link.getTargetAddress());
            metrics.submitDownstreamLinkCredits(link.getTargetAddress(), 0);
            sender.close();
        }
    }

    @Override
    public final void processMessage(final UpstreamReceiver client, final ProtonDelivery upstreamDelivery, final Message msg) {

        if (!running) {
            throw new IllegalStateException("adapter must be started first");
        }

        Objects.requireNonNull(client);
        Objects.requireNonNull(msg);
        Objects.requireNonNull(upstreamDelivery);
        ProtonSender sender = activeSenders.get(client);
        if (sender == null) {
            logger.info("no downstream sender for link [{}] available, discarding message and closing link with client", client.getLinkId());
            client.close(ErrorConditions.ERROR_NO_DOWNSTREAM_CONSUMER);
        } else if (sender.isOpen()) {
            if (sender.sendQueueFull()) {
                if (upstreamDelivery.remotelySettled()) {
                    // sender has sent the message pre-settled, i.e. we can simply discard the message
                    logger.debug("no downstream credit available for link [{}], discarding message [{}]",
                            client.getLinkId(), msg.getMessageId());
                    ProtonHelper.accepted(upstreamDelivery, true);
                    metrics.incrementDiscardedMessages(sender.getTarget().getAddress());
                } else {
                    // sender needs to be informed that we cannot process the message
                    logger.debug("no downstream credit available for link [{}], releasing message [{}]",
                            client.getLinkId(), msg.getMessageId());
                    ProtonHelper.released(upstreamDelivery, true);
                    metrics.incrementUndeliverableMessages(sender.getTarget().getAddress());
                }
            } else {
                logger.trace("forwarding message [id: {}, to: {}, content-type: {}] to downstream container [{}], credit available: {}, queued: {}",
                        msg.getMessageId(), msg.getAddress(), msg.getContentType(), getDownstreamContainer(), sender.getCredit(), sender.getQueued());
                forwardMessage(sender, msg, upstreamDelivery);
                metrics.incrementProcessedMessages(sender.getTarget().getAddress());
            }
        } else {
            logger.warn("downstream sender for link [{}] is not open, discarding message and closing link with client", client.getLinkId());
            client.close(ErrorConditions.ERROR_NO_DOWNSTREAM_CONSUMER);
            onClientDetach(client);
            metrics.incrementDiscardedMessages(sender.getTarget().getAddress());
        }
    }

    /**
     * Checks if this adapter has an open connection to the downstream container.
     *
     * @return {@code true} if the connection is open (and thus usable).
     */
    @Override
    public final boolean isConnected() {
        return downstreamConnection != null && !downstreamConnection.isDisconnected();
    }

    final void disableRetryOnFailedConnectAttempt() {
        retryOnFailedConnectAttempt = false;
    }

    /**
     * Checks if there are any downstream senders associated with upstream clients.
     * 
     * @return {@code true} if there are.
     */
    protected final boolean isActiveSendersEmpty() {
        return activeSenders != null && activeSenders.isEmpty();
    }

    /**
     * Checks if there are any downstream senders associated with a particular upstream client connection.
     * 
     * @return {@code true} if there are.
     */
    protected final boolean isSendersPerConnectionEmpty() {
        return receiversPerConnection != null && receiversPerConnection.isEmpty();
    }

    /**
     * Forwards the message to the downstream container.
     * <p>
     * It is the implementer's responsibility to handle message disposition and settlement with the upstream
     * client using the <em>delivery</em> object.
     * 
     * @param sender The link to the downstream container.
     * @param msg The message to send.
     * @param delivery The handle for settling the message with the client.
     */
    protected abstract void forwardMessage(ProtonSender sender, Message msg, ProtonDelivery delivery);

    /**
     * Gets the Quality-of-Service type used for the link with the downstream container.
     * 
     * @return The QoS.
     */
    protected abstract ProtonQoS getDownstreamQos();
}
