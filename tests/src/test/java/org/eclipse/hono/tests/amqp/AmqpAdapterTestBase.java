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
package org.eclipse.hono.tests.amqp;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.TestContext;
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
     * The vert.x instance to run all tests on.
     */
    protected static Vertx VERTX;
    /**
     * A helper for accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;
    /**
     * Default options for connecting to the AMQP adapter.
     */
    protected static ProtonClientOptions defaultOptions;

    /**
     * Support outputting current test's name.
     */
    @Rule
    public TestName testName = new TestName();

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * The vert.x context of the device connection.
     */
    protected Context context;
    /**
     * The connection established between the device and the AMQP adapter.
     */
    protected ProtonConnection connection;

    /**
     * Create a HTTP client for accessing the device registry (for registering devices and credentials) and
     * an AMQP 1.0 client for consuming messages from the messaging network.
     * 
     * @param ctx The Vert.x test context.
     */
    @BeforeClass
    public static void setup(final TestContext ctx) {
        VERTX = Vertx.vertx();
        helper = new IntegrationTestSupport(VERTX);
        helper.init(ctx);

        defaultOptions = new ProtonClientOptions()
                .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
                .setHostnameVerificationAlgorithm("")
                .setSsl(true);
    }

    /**
     * Shut down the client connected to the messaging network.
     * 
     * @param ctx The Vert.x test context.
     */
    @AfterClass
    public static void disconnect(final TestContext ctx) {
        helper.disconnect(ctx);
        VERTX.close(ctx.asyncAssertSuccess());
    }

    /**
     * Creates a sender based on the connection to the AMQP adapter.
     * 
     * @param target The target address to create the sender for or {@code null}
     *               if an anonymous sender should be created.
     * @return A future succeeding with the created sender.
     */
    protected Future<ProtonSender> createProducer(final String target) {

        final Future<ProtonSender>  result = Future.future();
        if (context == null) {
            result.fail(new IllegalStateException("not connected"));
        } else {
            context.runOnContext(go -> {
                final ProtonSender sender = connection.createSender(target);
                sender.setQoS(ProtonQoS.AT_LEAST_ONCE);
                sender.closeHandler(remoteClose -> {
                    if (remoteClose.failed()) {
                        log.info("peer closed sender link [exception: {}]", remoteClose.cause().getClass().getName());
                        result.tryFail(remoteClose.cause());
                    }
                });
                sender.openHandler(remoteAttach -> {
                    if (remoteAttach.failed()) {
                        log.info("peer rejects opening of sender link [exception: {}]", remoteAttach.cause().getClass().getName());
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

        return result;
    }

    /**
     * Connects to the AMQP protocol adapter using a username and password.
     * 
     * 
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @return A succeeded future containing the established connection.
     */
    protected Future<ProtonConnection> connectToAdapter(final String username, final String password) {

        final Future<ProtonConnection> result = Future.future();
        final ProtonClient client = ProtonClient.create(VERTX);

        final ProtonClientOptions options = new ProtonClientOptions(defaultOptions);
        options.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);
        client.connect(
                options,
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQPS_PORT,
                username,
                password,
                result);
        return result.compose(this::handleConnectAttempt);
    }

    /**
     * Connects to the AMQP protocol adapter using a client certificate.
     * 
     * @param clientCertificate The certificate to use for authentication.
     * @return A succeeded future containing the established connection.
     */
    protected Future<ProtonConnection> connectToAdapter(final SelfSignedCertificate clientCertificate) {

        final Future<ProtonConnection> result = Future.future();
        final ProtonClient client = ProtonClient.create(VERTX);

        final ProtonClientOptions secureOptions = new ProtonClientOptions(defaultOptions);
        secureOptions.setKeyCertOptions(clientCertificate.keyCertOptions());
        secureOptions.addEnabledSaslMechanism(ProtonSaslExternalImpl.MECH_NAME);
        client.connect(
                secureOptions,
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQPS_PORT,
                result);
        return result.compose(this::handleConnectAttempt);
    }

    private Future<ProtonConnection> handleConnectAttempt(final ProtonConnection unopenedConnection) {

        final Future<ProtonConnection> result = Future.future();
        unopenedConnection.openHandler(result);
        unopenedConnection.closeHandler(remoteClose -> {
            unopenedConnection.close();
        });
        unopenedConnection.open();
        return result.map(con -> {
            this.context = Vertx.currentContext();
            this.connection = unopenedConnection;
            return con;
        });
    }
}
