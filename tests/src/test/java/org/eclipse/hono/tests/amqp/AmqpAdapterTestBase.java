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
package org.eclipse.hono.tests.amqp;

import static com.google.common.truth.Truth.assertThat;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.ErrorConverter;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.sasl.impl.ProtonSaslExternalImpl;
import io.vertx.proton.sasl.impl.ProtonSaslPlainImpl;

/**
 * Base class for the AMQP adapter integration tests.
 */
public abstract class AmqpAdapterTestBase {

    /**
     * Default options for connecting to the AMQP adapter.
     */
    protected static ProtonClientOptions defaultOptions;

    /**
     * The vert.x instance to run all tests on.
     */
    protected final Vertx vertx = Vertx.vertx();
    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * A helper for accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected IntegrationTestSupport helper;
    /**
     * The vert.x context of the device connection.
     */
    protected Context context;
    /**
     * The connection established between the device and the AMQP adapter.
     */
    protected ProtonConnection connection;
    /**
     * The sender for publishing messages to the AMQP adapter.
     */
    protected ProtonSender sender;

    /**
     * Creates default AMQP client options.
     */
    @BeforeAll
    public static void init() {

        defaultOptions = new ProtonClientOptions()
                .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
                .setHostnameVerificationAlgorithm("")
                .setSsl(true);
    }

    /**
     * Create a HTTP client for accessing the device registry (for registering devices and credentials) and
     * an AMQP 1.0 client for consuming messages from the messaging network.
     *
     * @param testInfo Meta info about the test being run.
     * @param ctx The Vert.x test context.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo, final VertxTestContext ctx) {

        log.info("running {}", testInfo.getDisplayName());
        helper = new IntegrationTestSupport(vertx);
        helper.init().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Shuts down the client connected to the messaging network.
     *
     * @param ctx The Vert.x test context.
     */
    @AfterEach
    public void closeConnectionToMessagingNetwork(final VertxTestContext ctx) {

        helper.disconnect().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Disconnect the AMQP 1.0 client connected to the AMQP Adapter and delete device registry entries created
     * during the test.
     *
     * @param ctx The Vert.x test context.
     */
    @AfterEach
    public void closeAdapterConnectionAndCleanupDeviceRegistry(final VertxTestContext ctx) {

        final Promise<ProtonConnection> connectionTracker = Promise.promise();

        if (connection == null || connection.isDisconnected()) {
            connectionTracker.complete();
        } else {
            context.runOnContext(go -> {
                if (connection.isDisconnected()) {
                    connectionTracker.complete();
                } else {
                    connection.closeHandler(connectionTracker);
                    connection.disconnectHandler(con -> {
                        if (connectionTracker.tryComplete()) {
                            log.debug("connection to AMQP adapter disconnected without CLOSE frame having been received");
                        }
                    });
                    connection.close();
                }
            });
        }

        connectionTracker.future()
                .onComplete(con -> {
                    Optional.ofNullable(connection).ifPresent(v -> log.info("connection to AMQP adapter closed"));
                    context = null;
                    connection = null;
                    sender = null;
                    // cleanup device registry - done after the adapter connection is closed because otherwise
                    // the adapter would close the connection from its end after having received the device deletion notification 
                    helper.deleteObjects(ctx);
                });
    }

    /**
     * Creates a sender based on the connection to the AMQP adapter.
     *
     * @param target The target address to create the sender for or {@code null}
     *               if an anonymous sender should be created.
     * @param senderQos The delivery semantics used by the device for uploading messages.
     * @return A future succeeding with the created sender.
     * @throws NullPointerException if qos is {@code null}.
     */
    protected Future<ProtonSender> createProducer(final String target, final ProtonQoS senderQos) {

        final Promise<ProtonSender> result = Promise.promise();
        if (context == null) {
            result.fail(new IllegalStateException("not connected"));
        } else {
            context.runOnContext(go -> {
                sender = connection.createSender(target);
                sender.setQoS(senderQos);
                sender.closeHandler(remoteClose -> {
                    if (remoteClose.failed()) {
                        log.info("peer closed sender link [exception: {}]", remoteClose.cause().getClass().getName());
                        result.tryFail(remoteClose.cause());
                    }
                });
                sender.openHandler(remoteAttach -> {
                    if (remoteAttach.failed()) {
                        log.info("peer rejects opening of sender link", remoteAttach.cause());
                        result.fail(remoteAttach.cause());
                    } else if (sender.getRemoteTarget() == null) {
                        log.info("peer wants to immediately close sender link");
                        result.fail("could not open sender link");
                    } else {
                        result.complete(sender);
                    }
                });
                sender.open();
            });
        }

        return result.future();
    }

    /**
     * Connects to the AMQP protocol adapter using a username and password.
     *
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @return A succeeded future containing the established connection.
     */
    protected Future<ProtonConnection> connectToAdapter(final String username, final String password) {
        return connectToAdapter(IntegrationTestSupport.TLS_VERSION_1_2, null, username, password);
    }

    /**
     * Connects to the AMQP protocol adapter using a username and password.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param cipherSuite The TLS cipher suite to use for connecting to the adapter or {@code null} if the
     *                    cipher suite should not be restricted.
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @return A succeeded future containing the established connection.
     */
    protected Future<ProtonConnection> connectToAdapter(
            final String tlsVersion,
            final String cipherSuite,
            final String username,
            final String password) {

        final Promise<ProtonConnection> result = Promise.promise();
        final ProtonClient client = ProtonClient.create(vertx);

        final ProtonClientOptions options = new ProtonClientOptions(defaultOptions);
        options.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);
        options.setEnabledSecureTransportProtocols(Set.of(tlsVersion));
        Optional.ofNullable(cipherSuite).ifPresent(options::addEnabledCipherSuite);
        client.connect(
                options,
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQPS_PORT,
                username,
                password,
                result);
        return result.future().compose(con -> handleConnectAttempt(con, IntegrationTestSupport.AMQP_HOST));
    }

    /**
     * Connects to the AMQP protocol adapter using a client certificate.
     *
     * @param clientCertificate The certificate to use for authentication.
     * @return A succeeded future containing the established connection.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected Future<ProtonConnection> connectToAdapter(final SelfSignedCertificate clientCertificate) {
        return connectToAdapter(IntegrationTestSupport.AMQP_HOST, clientCertificate, IntegrationTestSupport.TLS_VERSION_1_2);
    }

    /**
     * Connects to the AMQP protocol adapter using a client certificate.
     *
     * @param hostname The name of the host to connect to.
     * @param clientCertificate The certificate to use for authentication.
     * @param tlsProtocolVersion The TLS protocol version to use for connecting to the host.
     * @return A succeeded future containing the established connection.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected Future<ProtonConnection> connectToAdapter(
            final String hostname,
            final SelfSignedCertificate clientCertificate,
            final String tlsProtocolVersion) {

        Objects.requireNonNull(hostname);
        Objects.requireNonNull(clientCertificate);
        Objects.requireNonNull(tlsProtocolVersion);

        final Promise<ProtonConnection> result = Promise.promise();
        final ProtonClient client = ProtonClient.create(vertx);

        final ProtonClientOptions secureOptions = new ProtonClientOptions(defaultOptions);
        secureOptions.setKeyCertOptions(clientCertificate.keyCertOptions());
        secureOptions.addEnabledSaslMechanism(ProtonSaslExternalImpl.MECH_NAME);
        secureOptions.setEnabledSecureTransportProtocols(Set.of(tlsProtocolVersion));
        client.connect(
                secureOptions,
                hostname,
                IntegrationTestSupport.AMQPS_PORT,
                result);
        return result.future().compose(con -> handleConnectAttempt(con, hostname));
    }

    private Future<ProtonConnection> handleConnectAttempt(
            final ProtonConnection unopenedConnection,
            final String hostname) {

        final Promise<ProtonConnection> result = Promise.promise();

        unopenedConnection.openHandler(result);
        unopenedConnection.closeHandler(remoteClose -> {
            unopenedConnection.close();
        });
        unopenedConnection.open();

        return result.future()
                .map(con -> {
                    assertThat(unopenedConnection.getRemoteOfferedCapabilities()).asList()
                            .contains(AmqpUtils.CAP_ANONYMOUS_RELAY);
                    this.context = Vertx.currentContext();
                    this.connection = unopenedConnection;
                    log.info("AMQPS connection to adapter [{}:{}] established",
                            hostname, IntegrationTestSupport.AMQPS_PORT);
                    return con;
                })
                .recover(t -> {
                    log.info("failed to establish AMQPS connection to adapter [{}:{}]",
                            hostname, IntegrationTestSupport.AMQPS_PORT);

                    return Optional.ofNullable(unopenedConnection.getRemoteCondition())
                            .map(condition -> Future.<ProtonConnection>failedFuture(ErrorConverter.fromAttachError(condition)))
                            .orElseGet(() -> Future.failedFuture(t));
                });
    }
}
