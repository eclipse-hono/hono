/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.coap.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.option.MapBasedOptionRegistry;
import org.eclipse.californium.core.coap.option.StandardOptionRegistry;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.observe.ObservationStore;
import org.eclipse.californium.elements.config.CertificateAuthenticationMode;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.auth.ApplicationLevelInfoSupplier;
import org.eclipse.californium.scandium.config.DtlsConfig;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.dtls.x509.NewAdvancedCertificateVerifier;
import org.eclipse.californium.scandium.dtls.x509.SingleCertificateProvider;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CoapEndpointFactory;
import org.eclipse.hono.adapter.coap.DeviceInfoSupplier;
import org.eclipse.hono.adapter.coap.option.TimeOption;
import org.eclipse.hono.adapter.limiting.MemoryBasedConnectionLimitStrategy;
import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;


/**
 * A factory that creates endpoints based on CoAP adapter configuration properties.
 *
 */
public class ConfigBasedCoapEndpointFactory implements CoapEndpointFactory {

    static {
        CoapConfig.register();
        DtlsConfig.register();
        UdpConfig.register();
    }

    /**
     * The minimum amount of memory (bytes) that the adapter requires to run on a standard Java VM.
     */
    private static final int MINIMAL_MEMORY_JVM = 100_000_000; // 100MB: minimal memory necessary for startup
    /**
     * The minimum amount of memory (bytes) that the adapter requires to run on a Substrate VM (i.e. when running
     * as a native executable).
     */
    private static final int MINIMAL_MEMORY_SUBSTRATE = 35_000_000;
    /**
     * The amount of memory required for each connection.
     */
    private static final int MEMORY_PER_CONNECTION = 10_000; // 10KB: expected avg. memory consumption per connection
    private static final Logger LOG = LoggerFactory.getLogger(ConfigBasedCoapEndpointFactory.class);

    private final CoapAdapterProperties config;
    private final Vertx vertx;

    private AdvancedPskStore pskStore;
    private NewAdvancedCertificateVerifier certificateVerifier;
    private ApplicationLevelInfoSupplier deviceResolver = new DeviceInfoSupplier();
    private ObservationStore observationStore;

    /**
     * Creates a new factory for configuration properties.
     *
     * @param vertx The vert.x instance to use for accessing the file system.
     * @param config The configuration properties.
     */
    public ConfigBasedCoapEndpointFactory(
            final Vertx vertx,
            final CoapAdapterProperties config) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);
        StandardOptionRegistry.setDefaultOptionRegistry(new MapBasedOptionRegistry(
                StandardOptionRegistry.getDefaultOptionRegistry(),
                TimeOption.DEFINITION));
    }

    /**
     * Sets the component to use for looking up pre-shared keys for clients authenticating using PSK
     * based ciphers in a DTLS handshake.
     * <p>
     * If not set, the secure endpoint will not support authentication of devices using PSK based ciphers.
     *
     * @param store The component.
     * @throws NullPointerException if store is {@code null}.
     */
    public void setPskStore(final AdvancedPskStore store) {
        this.pskStore = Objects.requireNonNull(store);
    }

    /**
     * Sets the component to use for determining the tenant and device identifier for an authenticated CoAP client.
     * <p>
     * If not set explicitly, a default resolver is used.
     *
     * @param resolver The component to use.
     * @throws NullPointerException if store is {@code null}.
     */
    public void setDeviceResolver(final ApplicationLevelInfoSupplier resolver) {
        this.deviceResolver = Objects.requireNonNull(resolver);
    }

    /**
     * Sets the component to use for verifying certificates of clients authenticating
     * using ECDSA based ciphers in a DTLS handshake.
     * <p>
     * If not set, the secure endpoint will not support authentication of devices using ECDSA based ciphers.
     *
     * @param certificateVerifier The component.
     * @throws NullPointerException if verifier is {@code null}.
     */
    public final void setCertificateVerifier(final NewAdvancedCertificateVerifier certificateVerifier) {
        this.certificateVerifier = Objects.requireNonNull(certificateVerifier);
    }

    /**
     * Sets the component to use for keeping track of CoAP resources observed on devices.
     * <p>
     * If not set explicitly, the adapter will use a default in-memory implementation which is not suitable for
     * sharing observation astate across multiple CoAP adapter instances.
     *
     * @param store The component.
     * @throws NullPointerException if store is {@code null}.
     */
    public void setObservationStore(final ObservationStore store) {
        this.observationStore = Objects.requireNonNull(store);
    }

    @Override
    public Future<Configuration> getCoapServerConfiguration() {
        final Configuration networkConfig = newDefaultConfiguration();
        return loadConfiguration(config.getNetworkConfig(), networkConfig);
    }

    private boolean isSecurePortEnabled() {
        return config.isSecurePortEnabled() || config.getPort() > Constants.PORT_UNCONFIGURED;
    }

    private Configuration newDefaultConfiguration() {
        final Configuration configuration = new Configuration();
        configuration.set(CoapConfig.PROTOCOL_STAGE_THREAD_COUNT, config.getCoapThreads());
        configuration.set(CoapConfig.MAX_RESOURCE_BODY_SIZE, config.getMaxPayloadSize());
        configuration.set(CoapConfig.EXCHANGE_LIFETIME, config.getExchangeLifetime(), TimeUnit.MILLISECONDS);
        configuration.set(CoapConfig.BLOCKWISE_STATUS_LIFETIME, config.getBlockwiseStatusLifetime(), TimeUnit.MILLISECONDS);
        configuration.set(CoapConfig.USE_MESSAGE_OFFLOADING, config.isMessageOffloadingEnabled());
        configuration.set(CoapConfig.DEDUPLICATOR, CoapConfig.DEDUPLICATOR_PEERS_MARK_AND_SWEEP);
        final int maxConnections = config.getMaxConnections();
        if (maxConnections == 0) {
            final MemoryBasedConnectionLimitStrategy limits = MemoryBasedConnectionLimitStrategy.forParams(
                    config.isSubstrateVm() ? MINIMAL_MEMORY_SUBSTRATE : MINIMAL_MEMORY_JVM,
                    MEMORY_PER_CONNECTION,
                    config.getGcHeapPercentage());
            configuration.set(CoapConfig.MAX_ACTIVE_PEERS, limits.getRecommendedLimit());
        } else {
            configuration.set(CoapConfig.MAX_ACTIVE_PEERS, maxConnections);
        }
        return configuration;
    }

    /**
     * Loads Californium configuration properties from a file.
     *
     * @param fileName The absolute path to the properties file.
     * @param config The configuration to apply the properties to.
     * @return The updated configuration.
     */
    protected Future<Configuration> loadConfiguration(final String fileName, final Configuration config) {
        final Promise<Configuration> result = Promise.promise();
        if (!Strings.isNullOrEmpty(fileName)) {
            vertx.fileSystem().readFile(fileName, readAttempt -> {
                if (readAttempt.succeeded()) {
                    try (InputStream is = new ByteArrayInputStream(readAttempt.result().getBytes())) {
                        config.load(is);
                        result.complete(config);
                    } catch (final IOException e) {
                        LOG.warn("error malformed Configuration properties [{}]", fileName);
                        result.fail(e);
                    }
                } else {
                    LOG.warn("error reading Configuration file [{}]", fileName, readAttempt.cause());
                    result.fail(readAttempt.cause());
                }
            });
        } else {
            result.complete(config);
        }
        return result.future();
    }

    /**
     * Gets the CoAP configuration for the secure endpoint.
     * <ol>
     * <li>Creates a default CoAP network configuration based on {@link CoapAdapterProperties}.</li>
     * <li>Merge in network configuration loaded from {@link CoapAdapterProperties#getNetworkConfig()}.</li>
     * <li>Merge in network configuration loaded from {@link CoapAdapterProperties#getSecureNetworkConfig()}.</li>
     * </ol>
     *
     * @return The configuration for the secure endpoint.
     */
    protected Future<Configuration> getSecureConfiguration() {

        final Configuration networkConfig = newDefaultConfiguration();
        networkConfig.set(DtlsConfig.DTLS_RECEIVER_THREAD_COUNT, config.getConnectorThreads());
        networkConfig.set(DtlsConfig.DTLS_CONNECTOR_THREAD_COUNT, config.getDtlsThreads());
        return loadConfiguration(config.getNetworkConfig(), networkConfig)
                .compose(c -> loadConfiguration(config.getSecureNetworkConfig(), c));
    }

    /**
     * Gets the CoAP configuration for the insecure endpoint.
     * <ol>
     * <li>Creates a default CoAP network configuration based on {@link CoapAdapterProperties}.</li>
     * <li>Merge in network configuration loaded from {@link CoapAdapterProperties#getNetworkConfig()}.</li>
     * <li>Merge in network configuration loaded from {@link CoapAdapterProperties#getInsecureNetworkConfig()}.</li>
     * </ol>
     *
     * @return The configuration for the insecure endpoint.
     */
    protected Future<Configuration> getInsecureConfiguration() {

        final Configuration configuration = newDefaultConfiguration();
        configuration.set(UdpConfig.UDP_RECEIVER_THREAD_COUNT, config.getConnectorThreads());
        configuration.set(UdpConfig.UDP_SENDER_THREAD_COUNT, config.getConnectorThreads());
        return loadConfiguration(config.getNetworkConfig(), configuration)
                .compose(c -> loadConfiguration(config.getInsecureNetworkConfig(), c));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Endpoint> getInsecureEndpoint() {

        final Promise<Endpoint> result = Promise.promise();

        if (config.isInsecurePortEnabled()) {
            if (config.isAuthenticationRequired()) {
                LOG.debug("skipping creation of insecure endpoint, configuration requires authentication of devices");
                result.fail("configuration requires authentication of devices");
            } else {
                final int insecurePort = config.getInsecurePort(CoAP.DEFAULT_COAP_PORT);
                final int securePort = isSecurePortEnabled() ? config.getPort(CoAP.DEFAULT_COAP_SECURE_PORT) : Constants.PORT_UNCONFIGURED;
                if (isSecurePortEnabled() && securePort == insecurePort) {
                    LOG.error("secure and insecure ports must be configured to bind to different port numbers");
                    result.fail("secure and insecure ports configured to bind to same port number");
                } else {
                    createInsecureEndpoint(insecurePort).onComplete(result);
                }
            }
        } else if (!isSecurePortEnabled()) {
            result.fail(new IllegalStateException("neither secure nor insecure port configured"));
        } else {
            LOG.info("insecure port is not configured, won't create insecure endpoint");
            result.fail(new IllegalStateException("insecure port is not configured"));
        }
        return result.future();
    }

    private Future<Endpoint> createInsecureEndpoint(final int port) {

        LOG.info("creating insecure endpoint");

        return getInsecureConfiguration()
                .map(networkConfig -> {
                    final CoapEndpoint.Builder builder = CoapEndpoint.builder();
                    builder.setConfiguration(networkConfig);
                    builder.setInetSocketAddress(new InetSocketAddress(
                            config.getInsecurePortBindAddress(),
                            port));
                    builder.setObservationStore(observationStore);
                    return builder.build();
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Endpoint> getSecureEndpoint() {

        final Promise<Endpoint> result = Promise.promise();
        if (isSecurePortEnabled()) {
            final int securePort = config.getPort(CoAP.DEFAULT_COAP_SECURE_PORT);
            final int insecurePort = config.isInsecurePortEnabled() ? config.getInsecurePort(CoAP.DEFAULT_COAP_PORT) : Constants.PORT_UNCONFIGURED;
            if (config.isInsecurePortEnabled() && insecurePort == securePort) {
                LOG.error("secure and insecure ports must be configured to bind to different port numbers");
                result.fail("secure and insecure ports configured to bind to same port number");
            } else {
                getSecureConfiguration()
                    .compose(secureNetworkConfig -> createSecureEndpoint(securePort, secureNetworkConfig))
                    .onComplete(result);
            }
        } else if (!config.isInsecurePortEnabled()) {
            result.fail(new IllegalStateException("neither secure nor insecure port configured"));
        } else {
            LOG.info("neither key/cert nor secure port are configured, won't create secure endpoint");
            result.fail(new IllegalStateException("neither key/cert nor secure port are configured"));
        }
        return result.future();
    }

    private Future<Endpoint> createSecureEndpoint(
            final int port,
            final Configuration networkConfig) {

        if (deviceResolver == null) {
            return Future.failedFuture(new IllegalStateException("infoSupplier property must be set for secure endpoint"));
        }
        if (pskStore == null) {
            return Future.failedFuture(new IllegalStateException("pskStore property must be set for secure endpoint"));
        }

        LOG.info("creating secure endpoint");

        final DtlsConnectorConfig.Builder dtlsConfig = DtlsConnectorConfig.builder(networkConfig);
        // prevent session resumption
        dtlsConfig.set(DtlsConfig.DTLS_SERVER_USE_SESSION_ID, false);
        dtlsConfig.set(DtlsConfig.DTLS_ROLE, DtlsConfig.DtlsRole.SERVER_ONLY);
        dtlsConfig.set(DtlsConfig.DTLS_RECOMMENDED_CIPHER_SUITES_ONLY, true);
        dtlsConfig.set(DtlsConfig.DTLS_CLIENT_AUTHENTICATION_MODE, CertificateAuthenticationMode.NEEDED);
        dtlsConfig.set(DtlsConfig.DTLS_RETRANSMISSION_TIMEOUT, config.getDtlsRetransmissionTimeout(), TimeUnit.MILLISECONDS);
        dtlsConfig.set(DtlsConfig.DTLS_MAX_CONNECTIONS, networkConfig.get(CoapConfig.MAX_ACTIVE_PEERS));
        dtlsConfig.set(DtlsConfig.DTLS_USE_SERVER_NAME_INDICATION, true);
        dtlsConfig.setAddress(new InetSocketAddress(config.getBindAddress(), port));
        dtlsConfig.setApplicationLevelInfoSupplier(deviceResolver);
        dtlsConfig.setAdvancedPskStore(pskStore);
        addIdentity(dtlsConfig);
        try {
            final DtlsConnectorConfig dtlsConnectorConfig = dtlsConfig.build();
            if (LOG.isInfoEnabled()) {
                final String ciphers = dtlsConnectorConfig.getSupportedCipherSuites()
                        .stream()
                        .map(cipher -> cipher.name())
                        .collect(Collectors.joining(", "));
                LOG.info("creating secure endpoint supporting ciphers: {}", ciphers);
            }
            final DTLSConnector dtlsConnector = new DTLSConnector(dtlsConnectorConfig);
            final CoapEndpoint.Builder builder = CoapEndpoint.builder();
            builder.setConfiguration(networkConfig);
            builder.setConnector(dtlsConnector);
            builder.setObservationStore(observationStore);
            return Future.succeededFuture(builder.build());

        } catch (final IllegalStateException ex) {
            LOG.warn("failed to create secure endpoint", ex);
            return Future.failedFuture(ex);
        }
    }

    private void addIdentity(final DtlsConnectorConfig.Builder dtlsConfig) {

        final KeyLoader keyLoader = KeyLoader.fromFiles(vertx, config.getKeyPath(), config.getCertPath());
        final PrivateKey pk = keyLoader.getPrivateKey();
        final Certificate[] certChain = keyLoader.getCertificateChain();
        if (pk == null) {
            LOG.warn("no private private key configured");
        } else if (certChain == null) {
            LOG.warn("no server certificate configured");
        } else {
            if (pk.getAlgorithm().equals(CredentialsConstants.EC_ALG)) {
                // Californium's cipher suites support ECC based keys only
                LOG.info("using private key [{}] and certificate [{}] as server identity",
                        config.getKeyPath(), config.getCertPath());
                dtlsConfig.setCertificateIdentityProvider(new SingleCertificateProvider(pk, certChain));
                Optional.ofNullable(certificateVerifier).ifPresent(dtlsConfig::setAdvancedCertificateVerifier);
            } else {
                LOG.warn("configured key is not ECC based, certificate based cipher suites will be disabled");
            }
        }
    }

}
