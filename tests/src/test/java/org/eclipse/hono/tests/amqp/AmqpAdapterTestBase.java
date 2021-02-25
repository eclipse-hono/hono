/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
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
        helper.init().onComplete(ctx.completing());
    }

    /**
     * Shuts down the client connected to the messaging network.
     *
     * @param ctx The Vert.x test context.
     */
    @AfterEach
    public void closeConnectionToMessagingNetwork(final VertxTestContext ctx) {

        helper.disconnect().onComplete(ctx.completing());
    }

    /**
     * Disconnect the AMQP 1.0 client connected to the AMQP Adapter and close senders and consumers.
     *
     * @param ctx The Vert.x test context.
     */
    @AfterEach
    public void closeConnectionToAdapter(final VertxTestContext ctx) {

        final Promise<ProtonConnection> connectionTracker = Promise.promise();

        if (connection == null || connection.isDisconnected()) {
            connectionTracker.complete();
        } else {
            context.runOnContext(go -> {
                connection.closeHandler(connectionTracker);
                connection.close();
            });
        }

        connectionTracker.future()
                .onComplete(con -> {
                    log.info("connection to AMQP adapter closed");
                    context = null;
                    connection = null;
                    sender = null;
                    ctx.completeNow();
                });
    }

    /**
     * Clean up after the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void cleanupDeviceRegistry(final VertxTestContext ctx) {
        helper.deleteObjects(ctx);
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
        return connectToAdapter("TLSv1.2", username, password);
    }

    /**
     * Connects to the AMQP protocol adapter using a username and password.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @return A succeeded future containing the established connection.
     */
    protected Future<ProtonConnection> connectToAdapter(final String tlsVersion, final String username, final String password) {

        final Promise<ProtonConnection> result = Promise.promise();
        final ProtonClient client = ProtonClient.create(vertx);

        final ProtonClientOptions options = new ProtonClientOptions(defaultOptions);
        options.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);
        options.setEnabledSecureTransportProtocols(Set.of(tlsVersion));
        client.connect(
                options,
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQPS_PORT,
                username,
                password,
                result);
        return result.future().compose(this::handleConnectAttempt);
    }

    /**
     * Connects to the AMQP protocol adapter using a client certificate.
     *
     * @param clientCertificate The certificate to use for authentication.
     * @return A succeeded future containing the established connection.
     */
    protected Future<ProtonConnection> connectToAdapter(final SelfSignedCertificate clientCertificate) {

        final Promise<ProtonConnection> result = Promise.promise();
        final ProtonClient client = ProtonClient.create(vertx);

        final ProtonClientOptions secureOptions = new ProtonClientOptions(defaultOptions);
        secureOptions.setKeyCertOptions(clientCertificate.keyCertOptions());
        secureOptions.addEnabledSaslMechanism(ProtonSaslExternalImpl.MECH_NAME);
        client.connect(
                secureOptions,
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQPS_PORT,
                result);
        return result.future().compose(this::handleConnectAttempt);
    }

    private Future<ProtonConnection> handleConnectAttempt(final ProtonConnection unopenedConnection) {

        final Promise<ProtonConnection> result = Promise.promise();

        unopenedConnection.openHandler(result);
        unopenedConnection.closeHandler(remoteClose -> {
            unopenedConnection.close();
        });
        unopenedConnection.open();

        return result.future()
                .map(con -> {
                    assertThat(unopenedConnection.getRemoteOfferedCapabilities()).contains(Constants.CAP_ANONYMOUS_RELAY);
                    this.context = Vertx.currentContext();
                    this.connection = unopenedConnection;
                    log.info("AMQPS connection to adapter [{}:{}] established",
                            IntegrationTestSupport.AMQP_HOST, IntegrationTestSupport.AMQPS_PORT);
                    return con;
                })
                .recover(t -> {
                    log.info("failed to establish AMQPS connection to adapter [{}:{}]",
                            IntegrationTestSupport.AMQP_HOST, IntegrationTestSupport.AMQPS_PORT);

                    return Optional.ofNullable(unopenedConnection.getRemoteCondition())
                            .map(condition -> Future.<ProtonConnection>failedFuture(StatusCodeMapper.fromAttachError(condition)))
                            .orElseGet(() -> Future.failedFuture(t));
                });
    }
}
