/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.connection;

import java.util.Optional;

import org.apache.qpid.proton.amqp.Symbol;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.impl.HonoConnectionImpl;

import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A wrapper around a single AMQP 1.0 connection and a single session to a Hono service endpoint.
 * <p>
 * The session is shared by all AMQP 1.0 links established for <em>sender</em> and <em>receiver</em>
 * links created using the corresponding factory methods.
 * <p>
 * Before any links can be created, the underlying AMQP connection needs to be established. This is done by
 * invoking one of the <em>connect</em> methods.
 * <p>
 * This class represents the <em>client</em> side when establishing the AMQP connection:
 * <ol>
 * <li>The client tries to establish a TCP/TLS connection to the peer. For this to succeed, the peer must have registered a
 * socket listener on the IP address and port that the client is configured to use.</li>
 * <li>The client initiates a SASL handshake if requested by the peer. The client needs to be
 * configured with correct credentials in order for this stage to succeed.</li>
 * <li>Finally, the client and the peer need to agree on AMQP 1.0 specific connection parameters like capabilities
 * and session window size.</li>
 * </ol>
 * Some of the <em>connect</em> methods accept a {@code ProtonClientOptions} type parameter. Note that these options
 * only influence the client's behavior when establishing the TCP/TLS connection with the peer. The overall behavior of
 * the client regarding the establishment of the AMQP connection must be configured using the
 * {@link ClientConfigProperties} passed in to the {@link #newConnection(Vertx, ClientConfigProperties)} method.
 * In particular, the {@link ClientConfigProperties#setReconnectAttempts(int)} method can be used to specify,
 * how many times the client should try to establish a connection to the peer before giving up.
 * <p>
 * <em>NB</em> When the client tries to establish a connection to the peer, it stores the <em>current</em>
 * vert.x {@code Context} in a local variable and performs all further interactions with the peer running
 * on this Context. Invoking any of the connection's methods from a vert.x Context other than the one
 * used for establishing the connection may cause race conditions or even deadlocks, because the handlers
 * registered on the {@code Future}s returned by these methods will be invoked from the stored Context.
 * It is the invoking code's responsibility to either ensure that the connection's methods are always invoked
 * from the same Context or to make sure that the handlers are running on the correct Context, e.g. by using
 * the {@link #executeOnContext(Handler)} method. 
 */
public interface HonoConnection extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new connection using the default implementation.
     * <p>
     * <strong>Note:</strong> Instances of {@link ClientConfigProperties} are not thread safe and not immutable.
     * They must therefore not be modified after calling this method.
     *
     * @param vertx The vert.x instance to use.
     * @param clientConfigProperties The client properties to use.
     * @return The newly created connection. Note that the underlying AMQP connection will not be established
     *         until one of its <em>connect</em> methods is invoked.
     * @throws NullPointerException if vertx or clientConfigProperties is {@code null}.
     */
    static HonoConnection newConnection(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        return new HonoConnectionImpl(vertx, clientConfigProperties);
    }

    /**
     * Creates a new connection using the default implementation.
     * <p>
     * <strong>Note:</strong> Instances of {@link ClientConfigProperties} are not thread safe and not immutable.
     * They must therefore not be modified after calling this method.
     *
     * @param vertx The vert.x instance to use.
     * @param clientConfigProperties The client properties to use.
     * @param tracer The OpenTracing tracer or {@code null} if no tracer should be associated with the connection.
     * @return The newly created connection. Note that the underlying AMQP connection will not be established
     *         until one of its <em>connect</em> methods is invoked.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    static HonoConnection newConnection(
            final Vertx vertx,
            final ClientConfigProperties clientConfigProperties,
            final Tracer tracer) {

        final HonoConnectionImpl connection = new HonoConnectionImpl(vertx, clientConfigProperties);
        Optional.ofNullable(tracer).ifPresent(connection::setTracer);
        return connection;
    }

    /**
     * Gets the vert.x instance used by this connection.
     * <p>
     * The returned instance may be used to e.g. schedule timers.
     *
     * @return The vert.x instance.
     */
    Vertx getVertx();

    /**
     * Gets the <em>OpenTracing</em> {@code Tracer} used for tracking
     * distributed interactions across process boundaries.
     *
     * @return The tracer.
     */
    Tracer getTracer();

    /**
     * Gets the configuration properties used for creating this connection.
     *
     * @return The configuration.
     */
    ClientConfigProperties getConfig();

    /**
     * {@inheritDoc}
     *
     * The connection will be established using default options.
     * <p>
     * With the default options the client will try to establish a TCP connection to the peer a single
     * time and then give up.
     * <p>
     * Once a TCP connection is established, the client performs a SASL handshake (if requested by the
     * peer) using the credentials set in the {@link ClientConfigProperties}. Finally, the client
     * opens the AMQP connection to the peer, based on the negotiated parameters.
     * <p>
     * The overall number of times that the client should try to establish the AMQP connection with the peer
     * can be configured by means of the <em>connectAttempts</em> property of the 
     * {@code ClientConfigProperties} passed in to the {@link #newConnection(Vertx, ClientConfigProperties)}
     * method. The client will perform a new DNS lookup of the peer's hostname with each attempt to 
     * establish the AMQP connection.
     * <p>
     * When an established connection to the peer fails, the client will automatically try to re-connect
     * to the peer using the same options and behavior as used for establishing the initial connection.
     *
     * @return A future that will be completed with the connected client once the connection has been established.
     *         The future will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} if the connection
     *         cannot be established, e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the connection could be
     *         established, or</li>
     *         <li>the maximum number of (unsuccessful) connection attempts have been made.</li>
     *         </ul>
     */
    @Override
    Future<HonoConnection> connect();

    /**
     * Connects to the Hono server using given TCP client options.
     * <p>
     * The client will try to establish a TCP connection to the peer based on the given options.
     * If no options are given, the used default properties will have the <em>connectTimeout</em> and
     * <em>heartBeat</em> values from the {@link ClientConfigProperties}. Note that each connection
     * attempt is made using the same IP address that has been resolved when the method was initially
     * invoked.
     * <p>
     * Once a TCP connection is established, the client performs a SASL handshake (if requested by the
     * peer) using the credentials set in the {@link ClientConfigProperties}. Finally, the client
     * opens the AMQP connection to the peer, based on the negotiated parameters.
     * <p>
     * The number of times that the client should try to establish the AMQP connection with the peer
     * can be configured by means of the <em>connectAttempts</em> property of the 
     * {@code ClientConfigProperties} passed in to the {@link #newConnection(Vertx, ClientConfigProperties)}
     * method. The client will perform a new DNS lookup of the peer's hostname with each attempt to 
     * establish the AMQP connection.
     * <p>
     * When an established connection to the peer fails, the client will automatically try to re-connect
     * to the peer using the same options and behavior as used for establishing the initial connection.
     *
     * @param options The options to use. If {@code null} a set of default properties will be used.
     * @return A future that will succeed with the connected client once the connection has been established. The future
     *         will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} if the connection cannot be
     *         established, e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the connection could be
     *         established, or</li>
     *         <li>the maximum number of (unsuccessful) connection attempts have been made.</li>
     *         </ul>
     * @throws NullPointerException if the options are {@code null}.
     */
    Future<HonoConnection> connect(ProtonClientOptions options);

    /**
     * {@inheritDoc}
     *
     * To re-connect to the server, an explicit call to {@code #connect()} should be made.
     * Unlike {@code #shutdown()}, which does not allow to connect back to the server,
     * this method allows to connect back to the server.
     * <p>
     * Disconnecting from the Hono server is necessary when, for instance, the open frame of the connection contains
     * permission information from an authorization service. If after connecting to the server the permissions
     * from the service have changed, then it will be necessary to drop the connection and connect back to the server
     * to retrieve the updated permissions.
     * <p>
     * If not called from a Vert.x event loop thread, this method waits for at most half of the configured
     * connect timeout for the connection to be closed properly.
     */
    @Override
    void disconnect();

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * If not called from a Vert.x event loop thread, this method waits for at most half of the configured
     * connect timeout for the connection to be closed properly.
     * <p>
     * Any senders or consumers opened by this client will be implicitly closed as well. Any subsequent attempts to
     * connect this client again will fail.
     */
    void shutdown();

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * Any senders or consumers opened by this client will be implicitly closed as well. Any subsequent attempts to
     * connect this client again will fail.
     *
     * @param completionHandler The handler to invoke with the result of the operation.
     * @throws NullPointerException if the handler is {@code null}.
     */
    void shutdown(Handler<AsyncResult<Void>> completionHandler);

    /**
     * Checks if this connection is shut down.
     *
     * @return {@code true} if this connection is shut down already
     *         or is in the process of shutting down.
     */
    boolean isShutdown();

    /**
     * Gets the remote container id as advertised by the peer.
     *
     * @return The remote container id or {@code null}.
     */
    String getRemoteContainerId();

    /**
     * Gets the container id that is advertised to the peer.
     * <p>
     * The identifier is supposed to be unique for this HonoConnection.
     *
     * @return The container id.
     */
    String getContainerId();

    /**
     * Checks if this client supports a certain capability.
     * <p>
     * The result of this method should only be considered reliable if this client is connected to the server.
     *
     * @param capability The capability to check support for.
     * @return {@code true} if the capability is included in the list of capabilities that the server has offered in its
     *         AMQP <em>open</em> frame, {@code false} otherwise.
     */
    boolean supportsCapability(Symbol capability);

    /**
     * Executes some code on the vert.x Context that has been used to establish the
     * connection to the peer.
     *
     * @param <T> The type of the result that the code produces.
     * @param codeToRun The code to execute. The code is required to either complete or
     *                  fail the promise that is passed into the handler.
     * @return The future containing the result of the promise passed in to the handler
     *         for executing the code. The future thus indicates the outcome of executing
     *         the code. The future will always be failed with a {@link org.eclipse.hono.client.ServerErrorException}
     *         if this connection's <em>context</em> property is {@code null}.
     */
    <T> Future<T> executeOnContext(Handler<Promise<T>> codeToRun);

    /**
     * Creates a sender link.
     *
     * @param targetAddress The target address of the link. If the address is {@code null}, the
     *                      sender link will be established to the 'anonymous relay' and each
     *                      message must specify its destination address.
     * @param qos The quality of service to use for the link.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} if the link cannot
     *         be opened.
     * @throws NullPointerException if qos is {@code null}.
     */
    Future<ProtonSender> createSender(
            String targetAddress,
            ProtonQoS qos,
            Handler<String> remoteCloseHook);

    /**
     * Creates a receiver link.
     * <p>
     * The receiver will be created with its <em>autoAccept</em> property set to {@code true}
     * and with the connection's default pre-fetch size.
     *
     * @param sourceAddress The address to receive messages from.
     * @param qos The quality of service to use for the link.
     * @param messageHandler The handler to invoke with every message received.
     * @param remoteCloseHook The handler to invoke when the link is closed at the peer's request (may be {@code null}).
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} if the link cannot
     *         be opened.
     * @throws NullPointerException if any of the arguments other than close hook is {@code null}.
     */
    Future<ProtonReceiver> createReceiver(
            String sourceAddress,
            ProtonQoS qos,
            ProtonMessageHandler messageHandler,
            Handler<String> remoteCloseHook);

    /**
     * Creates a receiver link.
     * <p>
     * The receiver will be created with its <em>autoAccept</em> property set to {@code true}.
     *
     * @param sourceAddress The address to receive messages from.
     * @param qos The quality of service to use for the link.
     * @param messageHandler The handler to invoke with every message received.
     * @param preFetchSize The number of credits to flow to the peer as soon as the link
     *                     has been established. A value of 0 prevents pre-fetching and
     *                     allows for manual flow control using the returned receiver's
     *                     <em>flow</em> method.
     * @param remoteCloseHook The handler to invoke when the link is closed at the peer's request (may be {@code null}).
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} if the link cannot
     *         be opened.
     * @throws NullPointerException if any of the arguments other than close hook is {@code null}.
     * @throws IllegalArgumentException if the pre-fetch size is &lt; 0.
     */
    Future<ProtonReceiver> createReceiver(
            String sourceAddress,
            ProtonQoS qos,
            ProtonMessageHandler messageHandler,
            int preFetchSize,
            Handler<String> remoteCloseHook);

    /**
     * Creates a receiver link.
     *
     * @param sourceAddress The address to receive messages from.
     * @param qos The quality of service to use for the link.
     * @param messageHandler The handler to invoke with every message received.
     * @param preFetchSize The number of credits to flow to the peer as soon as the link
     *                     has been established. A value of 0 prevents pre-fetching and
     *                     allows for manual flow control using the returned receiver's
     *                     <em>flow</em> method.
     * @param autoAccept {@code true} if received deliveries should be automatically accepted (and settled)
     *                   after the message handler runs for them, if no other disposition has been applied
     *                   during handling.
     * @param remoteCloseHook The handler to invoke when the link is closed at the peer's request (may be {@code null}).
     * @return A future for the created link. The future will be completed once the link is open.
     *         The future will fail with a {@link org.eclipse.hono.client.ServiceInvocationException} if the link cannot
     *         be opened.
     * @throws NullPointerException if any of the arguments other than close hook is {@code null}.
     * @throws IllegalArgumentException if the pre-fetch size is &lt; 0.
     */
    Future<ProtonReceiver> createReceiver(
            String sourceAddress,
            ProtonQoS qos,
            ProtonMessageHandler messageHandler,
            int preFetchSize,
            boolean autoAccept,
            Handler<String> remoteCloseHook);

    /**
     * Closes an AMQP link and frees up its allocated resources.
     * <p>
     * This method is equivalent to {@link #closeAndFree(ProtonLink, long, Handler)}
     * but will use an implementation specific default time-out value.
     * <p>
     * If this connection is not established, the given handler is invoked immediately.
     *
     * @param link The link to close. If {@code null}, the given handler is invoked immediately.
     * @param closeHandler The handler to notify once the link has been closed.
     * @throws NullPointerException if close handler is {@code null}.
     */
    void closeAndFree(ProtonLink<?> link, Handler<Void> closeHandler);

    /**
     * Closes an AMQP link and frees up its allocated resources.
     * <p>
     * This method will invoke the given handler as soon as
     * <ul>
     * <li>the peer's <em>detach</em> frame has been received or</li>
     * <li>the given number of milliseconds have passed</li>
     * </ul>
     * Afterwards the link's resources are freed up.
     * <p>
     * If this connection is not established, the given handler is invoked immediately.
     *
     * @param link The link to close. If {@code null}, the given handler is invoked immediately.
     * @param detachTimeOut The maximum number of milliseconds to wait for the peer's
     *                      detach frame or 0, if this method should wait indefinitely
     *                      for the peer's detach frame.
     * @param closeHandler The handler to notify once the link has been closed.
     * @throws NullPointerException if close handler is {@code null}.
     * @throws IllegalArgumentException if detach time-out is &lt; 0.
     */
    void closeAndFree(
            ProtonLink<?> link,
            long detachTimeOut,
            Handler<Void> closeHandler);
}
