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
package org.eclipse.hono.tests.amqp;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.client.ClientTestBase;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.sasl.impl.ProtonSaslExternalImpl;
import io.vertx.proton.sasl.impl.ProtonSaslPlainImpl;

/**
 * Base class for the AMQP adapter integration tests.
 */
public abstract class AmqpAdapterTestBase extends ClientTestBase {

    private static final String DEVICE_PASSWORD = "device-password";
    private static final String INVALID_CONTENT_TYPE = "application/vnd.eclipse-hono-empty-notification";

    /**
     * Support outputting current test's name.
     */
    @Rule
    public TestName testName = new TestName();

    /**
     * A helper for accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;
    /**
     * The connection established between the device and the AMQP adapter.
     */
    protected ProtonConnection connection;

    private ProtonSender sender;
    private MessageConsumer consumer;

    private static Vertx vertx;
    private Context context;

    private static ProtonClientOptions defaultOptions;
    private SelfSignedCertificate deviceCert;

    /**
     * Perform additional checks on a received message.
     * <p>
     * This default implementation does nothing. Subclasses should override this method to implement
     * reasonable checks.
     * 
     * @param ctx The test context.
     * @param msg The message to perform checks on.
     */
    protected void assertAdditionalMessageProperties(final TestContext ctx, final Message msg) {
        // empty
    }

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(String tenantId, Consumer<Message> messageConsumer);

    /**
     * Gets the endpoint name.
     * 
     * @return The name of the endpoint.
     */
    protected abstract String getEndpointName();

    /**
     * Create a HTTP client for accessing the device registry (for registering devices and credentials) and
     * an AMQP 1.0 client for consuming messages from the messaging network.
     * 
     * @param ctx The Vert.x test context.
     */
    @BeforeClass
    public static void setup(final TestContext ctx) {
        vertx = Vertx.vertx();
        helper = new IntegrationTestSupport(vertx);
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
    }

    /**
     * Logs a message before running a test case.
     */
    @Before
    public void before() {
        log.info("running {}", testName.getMethodName());
        deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
    }

    /**
     * Disconnect the AMQP 1.0 client connected to the AMQP Adapter and close senders and consumers.
     * Also delete all random tenants and devices generated during the execution of a test case.
     * 
     * @param context The Vert.x test context.
     */
    @After
    public void after(final TestContext context) {
        helper.deleteObjects(context);
        if (deviceCert != null) {
            deviceCert.delete();
        }
        close(context);
    }

    /**
     * Verifies that a BAD inbound message (i.e when the message content-type does not match the payload) is rejected by
     * the adapter.
     * 
     * @param context The Vert.x context for running asynchronous tests.
     */
    @Test
    public void testAdapterRejectsBadInboundMessage(final TestContext context) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final Async setup = context.async();
        setupProtocolAdapter(tenantId, deviceId, false).map(s -> {
            sender = s;
            return s;
        }).setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Async completionTracker = context.async();
        final Message msg = ProtonHelper.message("some payload");
        msg.setContentType(INVALID_CONTENT_TYPE);
        msg.setAddress(getEndpointName());
        sender.send(msg, delivery -> {

            context.assertTrue(Rejected.class.isInstance(delivery.getRemoteState()));

            final Rejected rejected = (Rejected) delivery.getRemoteState();
            final ErrorCondition error = rejected.getError();
            context.assertEquals(Constants.AMQP_BAD_REQUEST, error.getCondition());

            completionTracker.complete();
        });
        completionTracker.await();
    }

    /**
     * Verifies that the AMQP Adapter will fail to authenticate a device whose username does not match the expected pattern
     * {@code [<authId>@<tenantId>]}.
     * 
     * @param context The Vert.x test context.
     */
    @Test
    public void testAuthenticationWithInvalidUsernamePattern(final TestContext context) {
        connectToAdapter("invalidaUsername", DEVICE_PASSWORD)
        .setHandler(context.asyncAssertFailure(t -> {
            // SASL handshake failed
            context.assertTrue(SecurityException.class.isInstance(t));
        }));
    }

    /**
     * Verifies that the AMQP adapter rejects messages from a device that belongs to a tenant for which it has been
     * disabled. This test case only considers events since telemetry senders do not wait for response from the adapter.
     * 
     * @param context The Vert.x test context.
     */
    @Test
    public void testUploadMessageFailsForTenantDisabledAdapter(final TestContext context) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final Async setup = context.async();
        setupProtocolAdapter(tenantId, deviceId, true).map(s -> {
            sender = s;
            return s;
        }).setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Async completionTracker = context.async();
        final Message msg = ProtonHelper.message("some payload");
        msg.setAddress(getEndpointName());

        sender.send(msg, delivery -> {
            context.assertTrue(Rejected.class.isInstance(delivery.getRemoteState()));

            final Rejected rejected = (Rejected) delivery.getRemoteState();
            final ErrorCondition error = rejected.getError();
            context.assertEquals(AmqpError.UNAUTHORIZED_ACCESS, error.getCondition());
            completionTracker.complete();
        });
        completionTracker.await();

    }

    /**
     * Verifies that the AMQP Adapter rejects (closes) AMQP links that contains a target address.
     * @param context The Vert.x test context.
     */
    @Test
    public void testAnonymousRelayRequired(final TestContext context) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String targetAddress = String.format("%s/%s/%s", getEndpointName(), tenantId, deviceId);

        final TenantObject tenant = TenantObject.from(tenantId, true);
        final Async setup = context.async();
        helper.registry
                .addDeviceForTenant(tenant, deviceId, DEVICE_PASSWORD)
                .setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);

        // connect and create sender (with a valid target address)
        connectToAdapter(username, DEVICE_PASSWORD).map(con -> {
            this.connection = con;
            final Target target = new Target();
            target.setAddress(targetAddress);
            return target;
        }).compose(target -> createProducer(target)).setHandler(context.asyncAssertFailure(t -> {
            log.info("got {}", t.getClass().getName());
            final ErrorCondition expected = ProtonHelper.condition(Constants.AMQP_BAD_REQUEST,
                    "This adapter does not accept a target address on receiver links");

            context.assertEquals(expected.toString(), t.getMessage());
        }));
    }

    /**
     * Verifies that a number of messages published through the AMQP adapter can be successfully consumed by
     * applications connected to the AMQP messaging network.
     *
     * @param ctx The Vert.x test context.
     * @throws InterruptedException Exception.
     */
    @Test
    public void testUploadingXnumberOfMessages(final TestContext ctx) throws InterruptedException {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final Async setup = ctx.async();
        setupProtocolAdapter(tenantId, deviceId, false)
        .compose(s -> {
            sender = s;
            return Future.succeededFuture();
        }).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        testUploadMessages(tenantId, ctx);
    }

    /**
     * Verifies that a number of messages uploaded to the AMQP adapter using client certificate
     * based authentication can be successfully consumed via the AMQP Messaging Network.
     *
     * @param ctx The test context.
     * @throws InterruptedException if test execution is interrupted.
     */
    @Test
    public void testUploadMessagesUsingClientCertificate(final TestContext ctx) throws InterruptedException {
        final Async setup = ctx.async();
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        helper.getCertificate(deviceCert.certificatePath()).compose(cert -> {
            final TenantObject tenant = TenantObject.from(tenantId, true);
            tenant.setTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal());
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        }).compose(ok -> connectToAdapter(deviceCert))
        .compose(con -> createProducer(new Target()))
        .map(s -> {
            sender = s;
            setup.complete();
            return s;
        }).recover(t -> {
            log.error("error setting up AMQP protocol adapter", t);
            return Future.failedFuture(t);
        });

        setup.await();

        testUploadMessages(tenantId, ctx);
    }

    /**
     * Verifies that the adapter fails to authorize a device using a client certificate
     * if the public key that is registered for the tenant that the device belongs to can
     * not be parsed into a trust anchor.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadFailsForMalformedCaPublicKey(final TestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final TenantObject tenant = TenantObject.from(tenantId, true);

        // GIVEN a tenant configured with an invalid Base64 encoding of the
        // trust anchor public key
        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            tenant.setProperty(
                    TenantConstants.FIELD_PAYLOAD_TRUSTED_CA,
                    new JsonObject()
                        .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, cert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                        .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "notBase64"));
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        })
        .compose(ok -> {
            // WHEN a device tries to connect to the adapter
            // using a client certificate
            return connectToAdapter(deviceCert);
        })
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is not established
            ctx.assertTrue(t instanceof SecurityException);
        }));
    }

    /**
     * Verifies that the adapter fails to authenticate a device if the device's client
     * certificate's signature cannot be validated using the trust anchor that is registered
     * for the tenant that the device belongs to.
     *
     * @param ctx The test context.
     * @throws GeneralSecurityException if the tenant's trust anchor cannot be generated
     */
    @Test
    public void testConnectFailsForFailsForNonMatchingTrustAnchor(final TestContext ctx) throws GeneralSecurityException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final TenantObject tenant = TenantObject.from(tenantId, true);

        final KeyPair keyPair = helper.newEcKeyPair();

        // GIVEN a tenant configured with a trust anchor
        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            tenant.setProperty(
                    TenantConstants.FIELD_PAYLOAD_TRUSTED_CA,
                    new JsonObject()
                        .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, cert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                        .put(TenantConstants.FIELD_ADAPTERS_TYPE, "EC")
                        .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())));
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        })
        .compose(ok -> {
            // WHEN a device tries to connect to the adapter
            // using a client certificate that cannot be validated
            // using the trust anchor registered for the device's tenant
            return connectToAdapter(deviceCert);
        })
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is not established
            ctx.assertTrue(t instanceof SecurityException);
        }));
    }

    //------------------------------------------< private methods >---

    private void testUploadMessages(final String tenantId, final TestContext ctx) throws InterruptedException {
        final Function<Handler<Void>, Future<Void>> receiver = callback -> {
            return createConsumer(tenantId, msg -> {
                callback.handle(null);
                assertAdditionalMessageProperties(ctx, msg);
            }).map(c -> {
                consumer = c;
                return null;
            });
        };

        doUploadMessages(ctx, receiver, payload -> {
            final Async sendingComplete = ctx.async();
            final Message msg = ProtonHelper.message(payload);
            msg.setAddress(getEndpointName());
            context.runOnContext(go -> {
                sender.send(msg, delivery -> {
                    ctx.assertTrue(Accepted.class.isInstance(delivery.getRemoteState()));
                    sendingComplete.complete();
                });
            });
            sendingComplete.await();
        });
    }

    /**
     * Creates a test specific message sender.
     * 
     * @param target   The tenant to create the sender for.
     * @return    A future succeeding with the created sender.
     * 
     * @throws NullPointerException if the target or connection is null.
     */
    private Future<ProtonSender> createProducer(final Target target) {

        Objects.requireNonNull(target, "Target cannot be null");
        if (context == null) {
            throw new IllegalStateException("not connected");
        }

        final Future<ProtonSender>  result = Future.future();
        context.runOnContext(go -> {
            final ProtonSender sender = connection.createSender(target.getAddress());
            sender.setQoS(ProtonQoS.AT_LEAST_ONCE);
            sender.openHandler(remoteAttach -> {
                if (remoteAttach.succeeded()) {
                    result.complete(sender);
                } else {
                    result.fail(remoteAttach.cause());
                }
            });
            sender.open();
        });
        return result;
    }

    /**
     * Sets up the protocol adapter by doing the following:
     * <ul>
     * <li>Add a device (with credentials) for the tenant identified by the given tenantId.</li>
     * <li>Create an AMQP 1.0 client and authenticate it to the protocol adapter with username: {@code [device-id@tenantId]}.</li>
     * <li>After a successful connection, create a producer/sender for sending messages to the protocol adapter.</li>
     * </ul>
     *
     * @param tenantId The tenant to register with the device registry.
     * @param deviceId The device to add to the tenant identified by tenantId.
     * @param disableTenant If true, disable the protocol adapter for the tenant.
     * 
     * @return A future succeeding with the created sender.
     */
    private Future<ProtonSender> setupProtocolAdapter(final String tenantId, final String deviceId, final boolean disableTenant) {

        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);

        final TenantObject tenant = TenantObject.from(tenantId, true);
        if (disableTenant) {
            tenant.addAdapterConfiguration(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_AMQP, false));
        }

        return helper.registry
                .addDeviceForTenant(tenant, deviceId, DEVICE_PASSWORD)
                .compose(ok -> connectToAdapter(username, DEVICE_PASSWORD))
                .compose(con -> createProducer(new Target())).recover(t -> {
                    log.error("error setting up AMQP protocol adapter", t);
                    return Future.failedFuture(t);
                });
    }

    private Future<ProtonConnection> connectToAdapter(final String username, final String password) {

        final Future<ProtonConnection> result = Future.future();
        final ProtonClient client = ProtonClient.create(vertx);

        defaultOptions.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);
        client.connect(
                defaultOptions,
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQPS_PORT,
                username,
                password,
                conAttempt -> handleConnectionAttemptResult(conAttempt, result.completer()));
        return result;
    }

    private Future<ProtonConnection> connectToAdapter(final SelfSignedCertificate clientCertificate) {

        final Future<ProtonConnection> result = Future.future();
        final ProtonClient client = ProtonClient.create(vertx);

        final ProtonClientOptions secureOptions = new ProtonClientOptions(defaultOptions);
        secureOptions.setKeyCertOptions(clientCertificate.keyCertOptions());
        secureOptions.addEnabledSaslMechanism(ProtonSaslExternalImpl.MECH_NAME);
        client.connect(
                secureOptions,
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQPS_PORT,
                conAttempt -> handleConnectionAttemptResult(conAttempt, result.completer()));
        return result;
    }

    private void handleConnectionAttemptResult(final AsyncResult<ProtonConnection> conAttempt, final Handler<AsyncResult<ProtonConnection>> handler) {
        if (conAttempt.failed()) {
            handler.handle(Future.failedFuture(conAttempt.cause()));
        } else {
            this.context = Vertx.currentContext();
            this.connection = conAttempt.result();
            connection.openHandler(remoteOpen -> {
                if (remoteOpen.succeeded()) {
                    handler.handle(Future.succeededFuture(connection));
                } else {
                    handler.handle(Future.failedFuture(remoteOpen.cause()));
                }
            });
            connection.closeHandler(remoteClose -> {
                connection.close();
            });
            connection.open();
        }

    }

    private void close(final TestContext ctx) {
        final Async shutdown = ctx.async();
        final Future<ProtonConnection> connectionTracker = Future.future();
        final Future<ProtonSender> senderTracker = Future.future();
        final Future<Void> receiverTracker = Future.future();

        if (sender == null) {
            senderTracker.complete();
        } else {
            context.runOnContext(go -> {
                sender.closeHandler(senderTracker);
                sender.close();
            });
        }

        if (consumer == null) {
            receiverTracker.complete();
        } else {
            consumer.close(receiverTracker);
        }

        if (connection == null || connection.isDisconnected()) {
            connectionTracker.complete();
        } else {
            context.runOnContext(go -> {
                connection.closeHandler(connectionTracker);
                connection.close();
            });
        }

        CompositeFuture.join(connectionTracker, senderTracker, receiverTracker).setHandler(c -> {
           context = null;
           shutdown.complete();
        });
        shutdown.await();
    }
}
