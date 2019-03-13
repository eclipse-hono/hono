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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ClientConfigProperties;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * A factory for creating clients for Hono's APIs.
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
public interface HonoApplicationClient extends HonoClient {

    /**
     * Creates a client for consuming data from Hono's north bound <em>Telemetry API</em>.
     *
     * @param tenantId The tenant to consume data for.
     * @param telemetryConsumer The handler to invoke with every message received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createTelemetryConsumer(String tenantId, Consumer<Message> telemetryConsumer,
                                                    Handler<Void> closeHandler);

    /**
     * Creates a client for consuming events from Hono's north bound <em>Event API</em>.
     * <p>
     * The events passed in to the event consumer will be settled automatically if the consumer does not throw an
     * exception.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createEventConsumer(String tenantId, Consumer<Message> eventConsumer,
                                                Handler<Void> closeHandler);

    /**
     * Creates a client for consuming events from Hono's north bound <em>Event API</em>.
     * <p>
     * The events passed in to the event consumer will be settled automatically if the consumer does not throw an
     * exception and does not manually handle the message disposition using the passed in delivery.
     *
     * @param tenantId The tenant to consume events for.
     * @param eventConsumer The handler to invoke with every event received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<MessageConsumer> createEventConsumer(String tenantId, BiConsumer<ProtonDelivery, Message> eventConsumer,
                                                Handler<Void> closeHandler);

    /**
     * Gets a client for sending commands to a device.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given device.
     * <p>
     * This method will use an implementation specific mechanism (e.g. a UUID) to create
     * a unique reply-to address to be included in commands sent to the device. The protocol
     * adapters need to convey an encoding of the reply-to address to the device when delivering
     * the command. Consequently, the number of bytes transferred to the device depends on the
     * length of the reply-to address being used. In situations where this is a major concern it
     * might be advisable to use {@link #getOrCreateCommandClient(String, String, String)} for
     * creating a command client and provide custom (and shorter) <em>reply-to identifier</em>
     * to be used in the reply-to address.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the commands to.
     * @return A future that will complete with the command and control client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant and device is already being executed.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    Future<CommandClient> getOrCreateCommandClient(String tenantId, String deviceId);

    /**
     * Gets a client for sending commands to a device.
     * <p>
     * The client returned may be either newly created or it may be an existing
     * client for the given device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the commands to.
     * @param replyId An arbitrary string which (in conjunction with the tenant and device ID) uniquely
     *                identifies this command client.
     *                This identifier will only be used for creating a <em>new</em> client for the device.
     *                If this method returns an existing client for the device then the client will use
     *                the reply-to address determined during its initial creation. In particular, this
     *                means that if the (existing) client has originally been created using the
     *                {@link #getOrCreateCommandClient(String, String)} method, then the reply-to address
     *                used by the client will most likely not contain the given identifier.
     * @return A future that will complete with the command and control client (if successful) or
     *         fail if the client cannot be created, e.g. because the underlying connection
     *         is not established or if a concurrent request to create a client for the same
     *         tenant and device is already being executed.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    Future<CommandClient> getOrCreateCommandClient(String tenantId, String deviceId, String replyId);

}
