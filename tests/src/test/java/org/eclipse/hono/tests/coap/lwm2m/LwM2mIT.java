/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.tests.coap.lwm2m;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.ClientHandshaker;
import org.eclipse.californium.scandium.dtls.DTLSSession;
import org.eclipse.californium.scandium.dtls.HandshakeException;
import org.eclipse.californium.scandium.dtls.Handshaker;
import org.eclipse.californium.scandium.dtls.ResumingClientHandshaker;
import org.eclipse.californium.scandium.dtls.ResumingServerHandshaker;
import org.eclipse.californium.scandium.dtls.ServerHandshaker;
import org.eclipse.californium.scandium.dtls.SessionAdapter;
import org.eclipse.californium.scandium.dtls.SessionId;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.coap.CoapTestBase;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.engine.DefaultRegistrationEngineFactory;
import org.eclipse.leshan.client.object.Security;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.resource.listener.ObjectsListenerAdapter;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.LwM2mId;
import org.eclipse.leshan.core.californium.DefaultEndpointFactory;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.StaticModel;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mNodeDecoder;
import org.eclipse.leshan.core.node.codec.DefaultLwM2mNodeEncoder;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.request.UpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Integration tests for sending commands to a device connected to the CoAP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
public class LwM2mIT extends CoapTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(LwM2mIT.class);

    private LeshanClient client;
    private ExampleDevice deviceObject;
    private ExampleFirmware firmwareObject;

    @BeforeEach
    void createClientObjects() {
        deviceObject = new ExampleDevice();
        firmwareObject = new ExampleFirmware();
    }

    @AfterEach
    void stopClient() {
        if (client != null) {
            client.destroy(true);
        }
    }

    /**
     * Verifies that a LwM2M device using binding mode <em>U</em> can successfully register, update its
     * registration and unregister using the CoAP adapter's resource directory resource.
     * <p>
     * Also verifies that registration, updating the registration and de-registration trigger empty downstream
     * notifications with a TTD reflecting the device's lifetime and that the adapter establishes observations
     * for resources configured for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testNonQueueModeRegistration(final VertxTestContext ctx) {

        final Checkpoint deviceAcceptsCommands = ctx.checkpoint(2);
        final Checkpoint deviceNoLongerAcceptsCommands = ctx.checkpoint();
        final Promise<Void> firmwareNotificationReceived = Promise.promise();
        final Promise<Void> deviceNotificationReceived = Promise.promise();

        final int lifetime = 24 * 60 * 60; // 24h
        final int communicationPeriod = 300_000; // update registration every 5m
        final String endpoint = "test-device";

        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
            .compose(ok -> helper.applicationClient.createEventConsumer(
                    tenantId,
                    msg -> {
                        final String origAddress = msg.getProperties().getProperty(MessageHelper.APP_PROPERTY_ORIG_ADDRESS, String.class);
                        logger.info("received event [content-type: {}, orig_address: {}]", msg.getContentType(), origAddress);
                        Optional.ofNullable(msg.getTimeTillDisconnect())
                            .ifPresent(ttd -> {
                                switch (ttd) {
                                case lifetime:
                                    deviceAcceptsCommands.flag();
                                    break;
                                case 0:
                                    deviceNoLongerAcceptsCommands.flag();
                                    // fall through
                                default:
                                    break;
                                }
                            });
                        if (msg.getContentType().startsWith("application/vnd.oma.lwm2m")) {
                            Optional.ofNullable(origAddress)
                                .filter(s -> s.equals("/5/0"))
                                .ifPresent(s -> firmwareNotificationReceived.complete());
                        }
                    },
                    remoteClose -> {}))
            .compose(eventConsumer -> helper.applicationClient.createTelemetryConsumer(
                    tenantId,
                    msg -> {
                        final String origAddress = msg.getProperties().getProperty(MessageHelper.APP_PROPERTY_ORIG_ADDRESS, String.class);
                        logger.info("received telemetry message [content-type: {}, orig_address: {}]", msg.getContentType(), origAddress);
                        if (msg.getContentType().startsWith("application/vnd.oma.lwm2m")) {
                            Optional.ofNullable(origAddress)
                                .filter(s -> s.equals("/3/0"))
                                .ifPresent(s -> deviceNotificationReceived.complete());
                        }
                    },
                    remoteClose -> {}))
            .compose(eventConsumer -> {
                final Promise<LeshanClient> result = Promise.promise();
                try {
                    final var leshanClient = createLeshanClient(endpoint, BindingMode.U, lifetime, communicationPeriod);
                    result.complete(leshanClient);
                } catch (final CertificateEncodingException e) {
                    result.fail(e);
                }
                return result.future();
            })
            .compose(leshanClient -> {
                client = leshanClient;
                final Promise<Void> registered = Promise.promise();
                client.addObserver(new LeshanClientObserver() {
                    @Override
                    public void onRegistrationSuccess(
                            final ServerIdentity server,
                            final RegisterRequest request,
                            final String registrationID) {
                        registered.complete();
                    }
                });
                client.start();
                return CompositeFuture.all(
                        registered.future(),
                        deviceNotificationReceived.future(),
                        firmwareNotificationReceived.future());
            })
            .compose(ok -> {
                final Promise<Void> updated = Promise.promise();
                client.addObserver(new LeshanClientObserver() {
                    @Override
                    public void onUpdateSuccess(
                            final ServerIdentity server,
                            final UpdateRequest request) {
                        updated.complete();
                    }
                });
                client.triggerRegistrationUpdate();
                return updated.future();
            })
            .onSuccess(ok -> client.stop(true));
    }

    /**
     * Verifies that a de-registration request to the CoAP adapter's resource directory
     * resource triggers an event with a TTD.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the fixture cannot be created.
     * @throws ConnectorException if the CoAP adapter is not available.
     * @throws IOException if the CoAP adapter is not available.
     */
    @Test
    @Disabled
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testDeregistrationTriggersEvent(final VertxTestContext ctx) throws InterruptedException, ConnectorException, IOException {

        final Checkpoint notificationReceived = ctx.checkpoint();

        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
        .compose(ok -> helper.applicationClient.createEventConsumer(
                tenantId,
                msg -> {
                    logger.trace("received event: {}", msg);
                    msg.getTimeUntilDisconnectNotification()
                        .ifPresent(notification -> {
                            ctx.verify(() -> assertThat(notification.getTtd()).isEqualTo(0));
                            notificationReceived.flag();
                        });
                },
                remoteClose -> {}))
        .compose(c -> {
            final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
            final Request request = new Request(Code.DELETE);
            request.setURI(getCoapsRequestUri(String.format("/rd/%s:%s", tenantId, deviceId)));
            final Promise<OptionSet> result = Promise.promise();
            client.advanced(getHandler(result, ResponseCode.DELETED), request);
            return result.future();
        })
        .onComplete(ctx.completing());
    }

    private LeshanClient createLeshanClient(
            final String endpoint,
            final BindingMode bindingMode,
            final int lifetime,
            final Integer communicationPeriod) throws CertificateEncodingException {

        final var serverURI = getCoapsRequestUri(null);

        return createLeshanClient(
                endpoint,
                bindingMode,
                null,
                lifetime,
                communicationPeriod,
                serverURI.toString(),
                IntegrationTestSupport.getUsername(deviceId, tenantId).getBytes(StandardCharsets.UTF_8),
                SECRET.getBytes(StandardCharsets.UTF_8),
                null,
                null,
                null,
                null,
                null,
                false, // do not support deprecated content encoding
                false, // do not support deprecated ciphers
                false, // do not perform new DTLS handshake before updating registration
                false, // try to resume DTLS session
                null); // use all supported ciphers
    }

    private LeshanClient createLeshanClient(
            final String endpoint,
            final BindingMode bindingMode,
            final Map<String, String> additionalAttributes,
            final int lifetime,
            final Integer communicationPeriod,
            final String serverURI,
            final byte[] pskIdentity,
            final byte[] pskKey,
            final PrivateKey clientPrivateKey,
            final PublicKey clientPublicKey,
            final PublicKey serverPublicKey,
            final X509Certificate clientCertificate,
            final X509Certificate serverCertificate,
            final boolean supportOldFormat,
            final boolean supportDeprecatedCiphers,
            final boolean reconnectOnUpdate,
            final boolean forceFullhandshake,
            final List<CipherSuite> ciphers) throws CertificateEncodingException {

        // Initialize model
        final List<ObjectModel> models = ObjectLoader.loadDefault();

        // Initialize object list
        final LwM2mModel model = new StaticModel(models);
        final ObjectsInitializer initializer = new ObjectsInitializer(model);
        if (pskIdentity != null) {
            initializer.setInstancesForObject(LwM2mId.SECURITY, Security.psk(serverURI, 123, pskIdentity, pskKey));
            initializer.setInstancesForObject(LwM2mId.SERVER, new Server(123, lifetime, bindingMode, false));
        } else if (clientPublicKey != null) {
            initializer.setInstancesForObject(LwM2mId.SECURITY, Security.rpk(serverURI, 123, clientPublicKey.getEncoded(),
                    clientPrivateKey.getEncoded(), serverPublicKey.getEncoded()));
            initializer.setInstancesForObject(LwM2mId.SERVER, new Server(123, lifetime, bindingMode, false));
        } else if (clientCertificate != null) {
            initializer.setInstancesForObject(LwM2mId.SECURITY, Security.x509(serverURI, 123, clientCertificate.getEncoded(),
                    clientPrivateKey.getEncoded(), serverCertificate.getEncoded()));
            initializer.setInstancesForObject(LwM2mId.SERVER, new Server(123, lifetime, bindingMode, false));
        } else {
            initializer.setInstancesForObject(LwM2mId.SECURITY, Security.noSec(serverURI, 123));
            initializer.setInstancesForObject(LwM2mId.SERVER, new Server(123, lifetime, bindingMode, false));
        }
        initializer.setInstancesForObject(LwM2mId.DEVICE, deviceObject);
        initializer.setInstancesForObject(LwM2mId.FIRMWARE, firmwareObject);

        final List<LwM2mObjectEnabler> enablers = initializer.createAll();

        // Create CoAP Config
        final NetworkConfig coapConfig = LeshanClientBuilder.createDefaultNetworkConfig();

        // Create DTLS Config
        final DtlsConnectorConfig.Builder dtlsConfig = new DtlsConnectorConfig.Builder();
        dtlsConfig.setRecommendedCipherSuitesOnly(!supportDeprecatedCiphers);
        if (ciphers != null) {
            dtlsConfig.setSupportedCipherSuites(ciphers);
        }

        // Configure Registration Engine
        final DefaultRegistrationEngineFactory engineFactory = new DefaultRegistrationEngineFactory();
        engineFactory.setCommunicationPeriod(communicationPeriod);
        engineFactory.setReconnectOnUpdate(reconnectOnUpdate);
        engineFactory.setResumeOnConnect(!forceFullhandshake);

        // configure EndpointFactory
        final DefaultEndpointFactory endpointFactory = new DefaultEndpointFactory("LWM2M CLIENT") {
            @Override
            protected Connector createSecuredConnector(final DtlsConnectorConfig dtlsConfig) {

                return new DTLSConnector(dtlsConfig) {
                    @Override
                    protected void onInitializeHandshaker(final Handshaker handshaker) {
                        handshaker.addSessionListener(new SessionAdapter() {

                            private SessionId sessionIdentifier = null;

                            @Override
                            public void handshakeStarted(final Handshaker handshaker) throws HandshakeException {
                                if (handshaker instanceof ResumingServerHandshaker) {
                                    LOG.info("DTLS abbreviated Handshake initiated by server : STARTED ...");
                                } else if (handshaker instanceof ServerHandshaker) {
                                    LOG.info("DTLS Full Handshake initiated by server : STARTED ...");
                                } else if (handshaker instanceof ResumingClientHandshaker) {
                                    sessionIdentifier = handshaker.getSession().getSessionIdentifier();
                                    LOG.info("DTLS abbreviated Handshake initiated by client : STARTED ...");
                                } else if (handshaker instanceof ClientHandshaker) {
                                    LOG.info("DTLS Full Handshake initiated by client : STARTED ...");
                                }
                            }

                            @Override
                            public void sessionEstablished(final Handshaker handshaker, final DTLSSession establishedSession)
                                    throws HandshakeException {
                                if (handshaker instanceof ResumingServerHandshaker) {
                                    LOG.info("DTLS abbreviated Handshake initiated by server : SUCCEED");
                                } else if (handshaker instanceof ServerHandshaker) {
                                    LOG.info("DTLS Full Handshake initiated by server : SUCCEED");
                                } else if (handshaker instanceof ResumingClientHandshaker) {
                                    if (sessionIdentifier != null && sessionIdentifier
                                            .equals(handshaker.getSession().getSessionIdentifier())) {
                                        LOG.info("DTLS abbreviated Handshake initiated by client : SUCCEED");
                                    } else {
                                        LOG.info(
                                                "DTLS abbreviated turns into Full Handshake initiated by client : SUCCEED");
                                    }
                                } else if (handshaker instanceof ClientHandshaker) {
                                    LOG.info("DTLS Full Handshake initiated by client : SUCCEED");
                                }
                            }

                            @Override
                            public void handshakeFailed(final Handshaker handshaker, final Throwable error) {
                                // get cause
                                final String cause;
                                if (error != null) {
                                    if (error.getMessage() != null) {
                                        cause = error.getMessage();
                                    } else {
                                        cause = error.getClass().getName();
                                    }
                                } else {
                                    cause = "unknown cause";
                                }

                                if (handshaker instanceof ResumingServerHandshaker) {
                                    LOG.info("DTLS abbreviated Handshake initiated by server : FAILED ({})", cause);
                                } else if (handshaker instanceof ServerHandshaker) {
                                    LOG.info("DTLS Full Handshake initiated by server : FAILED ({})", cause);
                                } else if (handshaker instanceof ResumingClientHandshaker) {
                                    LOG.info("DTLS abbreviated Handshake initiated by client : FAILED ({})", cause);
                                } else if (handshaker instanceof ClientHandshaker) {
                                    LOG.info("DTLS Full Handshake initiated by client : FAILED ({})", cause);
                                }
                            }
                        });
                    }
                };
            }
        };

        // Create client
        final LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
        builder.setObjects(enablers);
        builder.setCoapConfig(coapConfig);
        builder.setDtlsConfig(dtlsConfig);
        builder.setRegistrationEngineFactory(engineFactory);
        builder.setEndpointFactory(endpointFactory);
        if (supportOldFormat) {
            builder.setDecoder(new DefaultLwM2mNodeDecoder(true));
            builder.setEncoder(new DefaultLwM2mNodeEncoder(true));
        }
        builder.setAdditionalAttributes(additionalAttributes);
        final LeshanClient client = builder.build();
        client.getObjectTree().addListener(new ObjectsListenerAdapter() {
            @Override
            public void objectRemoved(final LwM2mObjectEnabler object) {
                LOG.info("Object {} disabled.", object.getId());
            }

            @Override
            public void objectAdded(final LwM2mObjectEnabler object) {
                LOG.info("Object {} enabled.", object.getId());
            }
        });

        return client;
    }

}
