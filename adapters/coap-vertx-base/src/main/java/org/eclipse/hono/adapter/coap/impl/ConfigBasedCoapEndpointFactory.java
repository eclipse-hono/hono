/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
import java.util.stream.Collectors;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.config.NetworkConfig.Keys;
import org.eclipse.californium.core.observe.ObservationStore;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.auth.ApplicationLevelInfoSupplier;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.dtls.x509.NewAdvancedCertificateVerifier;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CoapEndpointFactory;
import org.eclipse.hono.adapter.coap.DeviceInfoSupplier;
import org.eclipse.hono.adapter.limiting.MemoryBasedConnectionLimitStrategy;
import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.util.Constants;
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

    private boolean isSecurePortEnabled() {
        return config.isSecurePortEnabled() || config.getPort() > Constants.PORT_UNCONFIGURED;
    }

    private NetworkConfig newDefaultNetworkConfig() {
        final NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setInt(Keys.PROTOCOL_STAGE_THREAD_COUNT, config.getCoapThreads());
        networkConfig.setInt(Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, config.getConnectorThreads());
        networkConfig.setInt(Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, config.getConnectorThreads());
        networkConfig.setInt(Keys.MAX_RESOURCE_BODY_SIZE, config.getMaxPayloadSize());
        networkConfig.setInt(Keys.EXCHANGE_LIFETIME, config.getExchangeLifetime());
        networkConfig.setBoolean(Keys.USE_MESSAGE_OFFLOADING, config.isMessageOffloadingEnabled());
        networkConfig.setString(Keys.DEDUPLICATOR, Keys.DEDUPLICATOR_PEERS_MARK_AND_SWEEP);
        final int maxConnections = config.getMaxConnections();
        if (maxConnections == 0) {
            final MemoryBasedConnectionLimitStrategy limits = new MemoryBasedConnectionLimitStrategy(
                    config.isSubstrateVm() ? MINIMAL_MEMORY_SUBSTRATE : MINIMAL_MEMORY_JVM,
                    MEMORY_PER_CONNECTION);
            networkConfig.setInt(Keys.MAX_ACTIVE_PEERS, limits.getRecommendedLimit());
        } else {
            networkConfig.setInt(Keys.MAX_ACTIVE_PEERS, maxConnections);
        }
        return networkConfig;
    }

    /**
     * Loads Californium configuration properties from a file.
     *
     * @param fileName The absolute path to the properties file.
     * @param networkConfig The configuration to apply the properties to.
     * @return The updated configuration.
     */
    protected Future<NetworkConfig> loadNetworkConfig(final String fileName, final NetworkConfig networkConfig) {
        final Promise<NetworkConfig> result = Promise.promise();
        if (!Strings.isNullOrEmpty(fileName)) {
            vertx.fileSystem().readFile(fileName, readAttempt -> {
                if (readAttempt.succeeded()) {
                    try (InputStream is = new ByteArrayInputStream(readAttempt.result().getBytes())) {
                        networkConfig.load(is);
                        result.complete(networkConfig);
                    } catch (final IOException e) {
                        LOG.warn("error malformed NetworkConfig properties [{}]", fileName);
                        result.fail(e);
                    }
                } else {
                    LOG.warn("error reading NetworkConfig file [{}]", fileName, readAttempt.cause());
                    result.fail(readAttempt.cause());
                }
            });
        } else {
            result.complete(networkConfig);
        }
        return result.future();
    }

    /**
     * Gets the CoAP network configuration for the secure endpoint.
     * <ol>
     * <li>Creates a default CoAP network configuration based on {@link CoapAdapterProperties}.</li>
     * <li>Merge in network configuration loaded from {@link CoapAdapterProperties#getNetworkConfig()}.</li>
     * <li>Merge in network configuration loaded from {@link CoapAdapterProperties#getSecureNetworkConfig()}.</li>
     * </ol>
     *
     * @return The network configuration for the secure endpoint.
     */
    protected Future<NetworkConfig> getSecureNetworkConfig() {

        final NetworkConfig networkConfig = newDefaultNetworkConfig();
        networkConfig.setInt(Keys.BLOCKWISE_STATUS_LIFETIME, config.getBlockwiseStatusLifetime());
        networkConfig.setInt(Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, config.getDtlsThreads());
        return loadNetworkConfig(config.getNetworkConfig(), networkConfig)
                .compose(c -> loadNetworkConfig(config.getSecureNetworkConfig(), c));
    }

    /**
     * Gets the CoAP network configuration for the insecure endpoint.
     * <ol>
     * <li>Creates a default CoAP network configuration based on {@link CoapAdapterProperties}.</li>
     * <li>Merge in network configuration loaded from {@link CoapAdapterProperties#getNetworkConfig()}.</li>
     * <li>Merge in network configuration loaded from {@link CoapAdapterProperties#getInsecureNetworkConfig()}.</li>
     * </ol>
     *
     * @return The network configuration for the insecure endpoint.
     */
    protected Future<NetworkConfig> getInsecureNetworkConfig() {

        final NetworkConfig networkConfig = newDefaultNetworkConfig();
        networkConfig.setInt(Keys.BLOCKWISE_STATUS_LIFETIME, config.getBlockwiseStatusLifetime());
        return loadNetworkConfig(config.getNetworkConfig(), networkConfig)
                .compose(c -> loadNetworkConfig(config.getInsecureNetworkConfig(), c));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Endpoint> getInsecureEndpoint() {

        final Promise<Endpoint> result = Promise.promise();

        if (config.isInsecurePortEnabled()) {
            if (config.isAuthenticationRequired()) {
                LOG.warn("skipping creation of insecure endpoint, configuration requires authentication of devices");
                result.complete();
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

        return getInsecureNetworkConfig()
                .map(networkConfig -> {
                    final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
                    builder.setNetworkConfig(networkConfig);
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
                getSecureNetworkConfig()
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
            final NetworkConfig networkConfig) {

        if (deviceResolver == null) {
            return Future.failedFuture(new IllegalStateException("infoSupplier property must be set for secure endpoint"));
        }
        if (pskStore == null) {
            return Future.failedFuture(new IllegalStateException("pskStore property must be set for secure endpoint"));
        }

        LOG.info("creating secure endpoint");

        final DtlsConnectorConfig.Builder dtlsConfig = new DtlsConnectorConfig.Builder();
        // prevent session resumption
        dtlsConfig.setNoServerSessionId(true);
        dtlsConfig.setServerOnly(true);
        dtlsConfig.setRecommendedCipherSuitesOnly(true);
        dtlsConfig.setClientAuthenticationRequired(true);
        dtlsConfig.setAddress(new InetSocketAddress(config.getBindAddress(), port));
        dtlsConfig.setApplicationLevelInfoSupplier(deviceResolver);
        dtlsConfig.setAdvancedPskStore(pskStore);
        dtlsConfig.setRetransmissionTimeout(config.getDtlsRetransmissionTimeout());
        dtlsConfig.setMaxConnections(networkConfig.getInt(Keys.MAX_ACTIVE_PEERS));
        dtlsConfig.setSniEnabled(true);
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
            final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
            builder.setNetworkConfig(networkConfig);
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
            if (pk.getAlgorithm().equals("EC")) {
                // Californium's cipher suites support ECC based keys only
                LOG.info("using private key [{}] and certificate [{}] as server identity",
                        config.getKeyPath(), config.getCertPath());
                dtlsConfig.setIdentity(pk, certChain);
                Optional.ofNullable(certificateVerifier).ifPresent(dtlsConfig::setAdvancedCertificateVerifier);
            } else {
                LOG.warn("configured key is not ECC based, certificate based cipher suites will be disabled");
            }
        }
    }
}
