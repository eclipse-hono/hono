/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Collector;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Event.Type;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class ProtonTransport extends BaseHandler {
    private static Logger LOG = LoggerFactory.getLogger(ProtonTransport.class);

    private final Connection connection;
    private final Vertx vertx;
    private final NetClient netClient;
    private final NetSocket socket;
    private final Transport transport = Proton.transport();
    private final Collector collector = Proton.collector();
    private ProtonSaslAuthenticator authenticator;

    private volatile Long idleTimeoutCheckTimerId; //TODO: cancel when closing etc?

    private boolean failed;

    ProtonTransport(Connection connection, Vertx vertx, NetClient netClient, NetSocket socket, ProtonSaslAuthenticator authenticator) {
        this.connection = connection;
        this.vertx = vertx;
        this.netClient = netClient;
        this.socket = socket;
        transport.setMaxFrameSize(1024 * 32); //TODO: make configurable
        if(authenticator != null) {
            authenticator.init(transport);
        }
        this.authenticator = authenticator;
        transport.bind(connection);
        connection.collect(collector);
        socket.endHandler(this::handleSocketEnd);
        socket.handler(this::handleSocketBuffer);
    }

    private void handleSocketEnd(Void arg) {
        transport.unbind();
        transport.close();
        if( this.netClient!=null ) {
            this.netClient.close();
        } else {
            this.socket.close();
        }
        ((ProtonConnectionImpl) this.connection.getContext()).fireDisconnect();
    }

    private void handleSocketBuffer(Buffer buff) {

        pumpInbound(ByteBuffer.wrap(buff.getBytes()));
        Event protonEvent = null;
        while ((protonEvent = collector.peek()) != null) {
            ProtonConnectionImpl connnection = (ProtonConnectionImpl) protonEvent.getConnection().getContext();

            Type eventType = protonEvent.getType();
            if (LOG.isTraceEnabled() && !eventType.equals(Type.TRANSPORT)) {
                LOG.trace("New Proton Event: {0}", eventType);
            }

            switch (eventType) {
                case CONNECTION_REMOTE_OPEN: {
                    connnection.fireRemoteOpen();
                    initiateIdleTimeoutChecks();
                    break;
                }
                case CONNECTION_REMOTE_CLOSE: {
                    connnection.fireRemoteClose();
                    break;
                }
                case SESSION_REMOTE_OPEN: {
                    ProtonSessionImpl session = (ProtonSessionImpl) protonEvent.getSession().getContext();
                    if( session == null ) {
                        connnection.fireRemoteSessionOpen(protonEvent.getSession());
                    } else {
                        session.fireRemoteOpen();
                    }
                    break;
                }
                case SESSION_REMOTE_CLOSE: {
                    ProtonSessionImpl session = (ProtonSessionImpl) protonEvent.getSession().getContext();
                    session.fireRemoteClose();
                    break;
                }
                case LINK_REMOTE_OPEN: {
                    ProtonLinkImpl<?> link = (ProtonLinkImpl<?>) protonEvent.getLink().getContext();
                    if( link == null ) {
                        connnection.fireRemoteLinkOpen(protonEvent.getLink());
                    } else {
                        link.fireRemoteOpen();
                    }
                    break;
                }
                case LINK_REMOTE_CLOSE: {
                    ProtonLinkImpl<?> link = (ProtonLinkImpl<?>) protonEvent.getLink().getContext();
                    link.fireRemoteClose();
                    break;
                }
                case LINK_FLOW:{
                    ProtonLinkImpl<?> link = (ProtonLinkImpl<?>) protonEvent.getLink().getContext();
                    link.fireLinkFlow();
                    break;
                }
                case DELIVERY: {
                    ProtonDeliveryImpl delivery = (ProtonDeliveryImpl) protonEvent.getDelivery().getContext();
                    if (delivery != null) {
                        delivery.fireUpdate();
                    } else {
                        ProtonReceiverImpl receiver = (ProtonReceiverImpl) protonEvent.getLink().getContext();
                        receiver.onDelivery();
                    }
                    break;
                }
                case TRANSPORT_ERROR: {
                    failed = true;
                    break;
                }

                case CONNECTION_INIT:
                case CONNECTION_BOUND:
                case CONNECTION_UNBOUND:
                case CONNECTION_LOCAL_OPEN:
                case CONNECTION_LOCAL_CLOSE:
                case CONNECTION_FINAL:

                case SESSION_INIT:
                case SESSION_LOCAL_OPEN:
                case SESSION_LOCAL_CLOSE:
                case SESSION_FINAL:

                case LINK_INIT:
                case LINK_LOCAL_OPEN:
                case LINK_LOCAL_DETACH:
                case LINK_REMOTE_DETACH:
                case LINK_LOCAL_CLOSE:
                case LINK_FINAL:
            }
            collector.pop();
        }

        processSaslAuthentication();

        flush();
    }

    private void processSaslAuthentication() {
        if (authenticator == null) {
            return;
        }

        if (authenticator.process()) {
            authenticator = null;
        }
    }

    private void initiateIdleTimeoutChecks() {
        // Using nano time since it is not related to the wall clock, which may change
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        long deadline = transport.tick(now);
        if (deadline > 0) {
            long delay = deadline - now;
            LOG.trace("IdleTimeoutCheck being initiated, initial delay: {0}", delay);
            idleTimeoutCheckTimerId = vertx.setTimer(delay, new IdleTimeoutCheck());
        }
    }

    private void pumpInbound(ByteBuffer bytes) {
        if(failed) {
            LOG.trace("Skipping processing of data following transport error: {0}", bytes);
            return;
        }

        // Lets push bytes from vert.x to proton engine.
        ByteBuffer inputBuffer = transport.getInputBuffer();
        while (bytes.hasRemaining() && inputBuffer.hasRemaining()) {
            inputBuffer.put(bytes.get());
            try{
                transport.processInput().checkIsOk();
            } catch(TransportException te) {
                failed = true;
            }
        }
    }

    void flush() {
        boolean done = false;
        while (!done) {
            ByteBuffer outputBuffer = transport.getOutputBuffer();
            if (outputBuffer != null && outputBuffer.hasRemaining()) {
                byte buffer[] = new byte[outputBuffer.remaining()];
                outputBuffer.get(buffer);
                socket.write(Buffer.buffer(buffer));
                transport.outputConsumed();
            } else {
                done = true;
            }
        }
    }


    public void disconnect() {
        if (netClient != null) {
            netClient.close();
        } else {
            socket.close();
        }
    }

    private final class IdleTimeoutCheck implements Handler<Long> {
        @Override
        public void handle(Long event) {
            boolean checkScheduled = false;

            if (connection.getLocalState() == EndpointState.ACTIVE) {
                // Using nano time since it is not related to the wall clock, which may change
                long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                long deadline = transport.tick(now);

                flush();

                if (transport.isClosed()) {
                    LOG.info("IdleTimeoutCheck closed the transport due to the peer exceeding our requested idle-timeout.");
                    disconnect();
                } else {
                    if (deadline > 0) {
                        long delay = deadline - now;
                        checkScheduled = true;
                        LOG.trace("IdleTimeoutCheck rescheduling with delay: {0}", delay);
                        idleTimeoutCheckTimerId = vertx.setTimer(delay, this);
                    }
                }
            } else {
                LOG.trace("IdleTimeoutCheck skipping check, connection is not active.");
            }

            if (!checkScheduled) {
                idleTimeoutCheckTimerId = null;
                LOG.trace("IdleTimeoutCheck exiting");
            }
        }
    }
}
