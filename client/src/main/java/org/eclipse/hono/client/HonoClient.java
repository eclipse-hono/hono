/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import org.apache.qpid.proton.amqp.Symbol;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A factory for creating clients for Hono's arbitrary APIs.
 * <p>
 * A factory maintains a single AMQP 1.0 connection and a single session to the peer. This session is shared by all AMQP
 * 1.0 links established for <em>senders</em>, <em>consumers</em> and <em>clients</em> created using the corresponding
 * factory methods.
 * <p>
 * The <em>getOrCreate</em> factory methods return an existing client for the given address if available. Note that
 * factory methods for creating consumers <em>always</em> return a new instance so that all messages received are only
 * processed by the handler passed in to the factory method.
 * <p>
 * Before any of the factory methods can be invoked successfully, the client needs to connect to Hono. This is done by
 * invoking one of the client's <em>connect</em> methods.
 * <p>
 * An AMQP connection is established in multiple stages:
 * <ol>
 * <li>The client establishes a TCP connection to the peer. For this to succeed, the peer must have registered a
 * socket listener on the IP address and port that the client is configured to use.</li>
 * <li>The client performs a SASL handshake with the peer if required by the peer. The client needs to be
 * configured with correct credentials in order for this stage to succeed.</li>
 * <li>Finally, the client and the peer need to agree on AMQP 1.0 specific connection parameters like capabilities
 * and session window size.</li>
 * </ol>
 * Some of the <em>connect</em> methods accept a {@code ProtonClientOptions} type parameter. Note that these options
 * only influence the client's behavior when establishing the TCP connection with the peer. The overall behavior of
 * the client regarding the establishment of the AMQP connection must be configured using the
 * {@link ClientConfigProperties} passed in to the <em>newClient</em> method. In particular, the
 * {@link ClientConfigProperties#setReconnectAttempts(int)} method can be used to specify, how many
 * times the client should try to establish a connection to the peer before giving up.
 * <p>
 * <em>NB</em> When the client tries to establish a connection to the peer, it stores the <em>current</em>
 * vert.x {@code Context} in a local variable and performs all further interactions with the peer running
 * on this Context. Invoking any of the methods of the client from a vert.x Context other than the one
 * used for establishing the connection may cause race conditions or even deadlocks because the handlers
 * registered on the {@code Future}s returned by these methods will be invoked from the stored Context.
 * It is the invoking code's responsibility to either ensure that the client's methods are always invoked
 * from the same Context or to make sure that the handlers are running on the correct Context, e.g. by using
 * the {@code Context}'s <em>runOnContext</em> method.
 */
public interface HonoClient extends ConnectionLifecycle,
                                    DownstreamSenderFactory,
                                    ApplicationClientFactory,
                                    CredentialsClientFactory,
                                    RegistrationClientFactory,
                                    TenantClientFactory {

    /**
     * Checks whether this client is connected to the service.
     *
     * @return A succeeded future if this client is connected. Otherwise, the future will fail with a
     *         {@link ServerErrorException}.
     */
    Future<Void> isConnected();

    /**
     * Connects to the Hono server using default TCP client options.
     * <p>
     * With the default options a client will try three times to establish a TCP connection to the peer
     * before giving up. Each attempt will be canceled after 200ms and the client will wait 500ms
     * before making the next attempt. Note that each connection attempt is made using the same IP
     * address that has been resolved when the method was initially invoked.
     * <p>
     * Once a TCP connection is established, the client performs a SASL handshake (if requested by the
     * peer) using the credentials set in the {@link ClientConfigProperties}. Finally, the client
     * opens the AMQP connection to the peer, based on the negotiated parameters.
     * <p>
     * The number of times that the client should try to establish the AMQP connection with the peer
     * can be configured by means of the <em>connectAttempts</em> property of the 
     * {@code ClientConfigProperties} passed in to the {@link #newClient(Vertx, ClientConfigProperties)}
     * method.
     * <p>
     * When an established connection to the peer fails, the client will automatically try to re-connect
     * to the peer using the same options and behavior as used for establishing the initial connection.
     *
     * @return A future that will be completed with the connected client once the connection has been established.
     *         The future will fail with a {@link ServiceInvocationException} if the connection cannot be
     *         established, e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the connection could be
     *         established.</li>
     *         <li>the maximum number of (unsuccessful) connection attempts have been made.</li>
     *         </ul>
     */
    Future<HonoClient> connect();

    /**
     * Connects to the Hono server using given TCP client options.
     * <p>
     * The client will try to establish a TCP connection to the peer based on the values of the
     * <em>connectTimeout</em>, <em>reconnectAttempts</em> and <em>reconnectInterval</em> properties
     * of the given options. Note that each connection attempt is made using the same IP
     * address that has been resolved when the method was initially invoked.
     * <p>
     * Once a TCP connection is established, the client performs a SASL handshake (if requested by the
     * peer) using the credentials set in the {@link ClientConfigProperties}. Finally, the client
     * opens the AMQP connection to the peer, based on the negotiated parameters.
     * <p>
     * The number of times that the client should try to establish the AMQP connection with the peer
     * can be configured by means of the <em>connectAttempts</em> property of the 
     * {@code ClientConfigProperties} passed in to the {@link #newClient(Vertx, ClientConfigProperties)}
     * method.
     * <p>
     * When an established connection to the peer fails, the client will automatically try to re-connect
     * to the peer using the same options and behavior as used for establishing the initial connection.
     *
     * @param options The options to use. If {@code null} a set of default properties will be used.
     * @return A future that will succeed with the connected client once the connection has been established. The future
     *         will fail with a {@link ServiceInvocationException} if the connection cannot be established, e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the connection could be
     *         established, or</li>
     *         <li>the maximum number of (unsuccessful) connection attempts have been made.</li>
     *         </ul>
     * @throws NullPointerException if the options are {@code null}.
     */
    Future<HonoClient> connect(ProtonClientOptions options);

    /**
     * Connects to the Hono server using default options.
     * <p>
     * With the default options a client will try three times to establish a TCP connection to the peer
     * before giving up. Each attempt will be canceled after 200ms and the client will wait 500ms
     * before making the next attempt. Note that each connection attempt is made using the same IP
     * address that has been resolved when the method was initially invoked.
     * <p>
     * Once a TCP connection is established, the client performs a SASL handshake (if requested by the
     * peer) using the credentials set in the {@link ClientConfigProperties}. Finally, the client
     * opens the AMQP connection to the peer, based on the negotiated parameters.
     * <p>
     * The number of times that the client should try to establish the AMQP connection with the peer
     * can be configured by means of the <em>connectAttempts</em> property of the 
     * {@code ClientConfigProperties} passed in to the {@link #newClient(Vertx, ClientConfigProperties)}
     * method.
     * <p>
     * When an established connection to the peer fails, the given disconnect handler will be invoked.
     * Note that the client will <em>not</em> automatically try to re-connect to the peer in this case.
     *
     * @param disconnectHandler A handler to notify about connection loss.
     * @return A future that will succeed with the connected client once the connection has been established. The future
     *         will fail with a {@link ServiceInvocationException} if the connection cannot be established, e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the connection could be
     *         established.</li>
     *         </ul>
     * @throws NullPointerException if the disconnect handler is {@code null}.
     */
    Future<HonoClient> connect(Handler<ProtonConnection> disconnectHandler);

    /**
     * Connects to the Hono server using given options.
     * <p>
     * The client will try to establish a TCP connection to the peer based on the values of the
     * <em>connectTimeout</em>, <em>reconnectAttempts</em> and <em>reconnectInterval</em> properties
     * of the given options. Note that each connection attempt is made using the same IP
     * address that has been resolved when the method was initially invoked.
     * <p>
     * Once a TCP connection is established, the client performs a SASL handshake (if requested by the
     * peer) using the credentials set in the {@link ClientConfigProperties}. Finally, the client
     * opens the AMQP connection to the peer, based on the negotiated parameters.
     * <p>
     * The number of times that the client should try to establish the AMQP connection with the peer
     * can be configured by means of the <em>connectAttempts</em> property of the 
     * {@code ClientConfigProperties} passed in to the {@link #newClient(Vertx, ClientConfigProperties)}
     * method.
     * <p>
     * When an established connection to the peer fails, the given disconnect handler will be invoked.
     * Note that the client will <em>not</em> automatically try to re-connect to the peer in this case.
     * If the disconnect handler is {@code null}, the client will automatically try to re-connect
     * to the peer using the same options and behavior as used for establishing the initial connection.
     *
     * @param options The options to use. If {@code null} the default properties will be used.
     * @param disconnectHandler A handler to notify about connection loss (may be {@code null}).
     * @return A future that will succeed with the connected client once the connection has been established. The future
     *         will fail with a {@link ServiceInvocationException} if the connection cannot be established, e.g. because
     *         <ul>
     *         <li>authentication of the client failed, or</li>
     *         <li>one of the client's <em>shutdown</em> methods has been invoked before the connection could be
     *         established, or</li>
     *         <li>the maximum number of (unsuccessful) (re-)connection attempts have been made.</li>
     *         </ul>
     */
    Future<HonoClient> connect(
            ProtonClientOptions options,
            Handler<ProtonConnection> disconnectHandler);

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
     *
     */
    @Override
    void disconnect();

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * This method waits for at most 5 seconds for the connection to be closed properly. Any subsequent attempts to
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
     * Create a new {@link HonoClient} using the default implementation.
     * <p>
     * <strong>Note:</strong> Instances of {@link ClientConfigProperties} are not thread safe and not immutable. They
     * must not be modified after calling this method.
     *
     * @param vertx The vertx instance to use. May be {@code null}, in which case a new instance will be created.
     * @param clientConfigProperties The client properties to use. Must not be {@code null}.
     * @return A new instance of a <em>Hono Client</em>.
     */
    static HonoClient newClient(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        return new HonoClientImpl(vertx, clientConfigProperties);
    }
}
