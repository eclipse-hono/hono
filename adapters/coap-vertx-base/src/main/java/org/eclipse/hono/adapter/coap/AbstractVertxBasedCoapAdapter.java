/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.coap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.config.NetworkConfig.Keys;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.auth.ApplicationLevelInfoSupplier;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.dtls.x509.NewAdvancedCertificateVerifier;
import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.adapter.auth.device.X509AuthProvider;
import org.eclipse.hono.adapter.limiting.MemoryBasedConnectionLimitStrategy;
import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.Futures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * Base class for a vert.x based Hono protocol adapter that uses CoAP.
 * <p>
 * Provides support for exposing CoAP resources using plain UDP and DTLS.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractVertxBasedCoapAdapter<T extends CoapAdapterProperties>
        extends AbstractProtocolAdapterBase<T> implements CoapProtocolAdapter {

    /**
     * The minimum amount of memory that the adapter requires to run.
     */
    private static final int MINIMAL_MEMORY = 100_000_000; // 100MB: minimal memory necessary for startup
    /**
     * The amount of memory required for each connection.
     */
    private static final int MEMORY_PER_CONNECTION = 10_000; // 10KB: expected avg. memory consumption per connection

    /**
     * A logger shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Set<Resource> resourcesToAdd = new HashSet<>();

    private CoapServer server;
    private CoapAdapterMetrics metrics = CoapAdapterMetrics.NOOP;
    private ApplicationLevelInfoSupplier honoDeviceResolver;
    private AdvancedPskStore pskStore;
    private NewAdvancedCertificateVerifier certificateVerifier;
    private DeviceCredentialsAuthProvider<SubjectDnCredentials> clientCertAuthProvider;

    private volatile Endpoint secureEndpoint;
    private volatile Endpoint insecureEndpoint;

    /**
     * Sets the service to use for resolving an authenticated CoAP client to a Hono device.
     *
     * @param deviceIdentityResolver The resolver to use.
     */
    public final void setHonoDeviceResolver(final ApplicationLevelInfoSupplier deviceIdentityResolver) {
        this.honoDeviceResolver = Objects.requireNonNull(deviceIdentityResolver);
    }

    /**
     * Sets the service to use for looking up pre-shared keys for clients authenticating using PSK
     * based ciphers in a DTLS handshake.
     *
     * @param pskStore The service to use.
     */
    public final void setPskStore(final AdvancedPskStore pskStore) {
        this.pskStore = Objects.requireNonNull(pskStore);
    }

    /**
     * Sets the service to use for verifying certificates for clients authenticating using x509 based ciphers in a DTLS
     * handshake.
     *
     * @param certificateVerifier The service to use.
     */
    public final void setCertificateVerifier(final NewAdvancedCertificateVerifier certificateVerifier) {
        this.certificateVerifier = Objects.requireNonNull(certificateVerifier);
    }

    /**
     * Sets the provider to use for authenticating devices based on a client certificate.
     * <p>
     * If not set explicitly using this method, a {@code SubjectDnAuthProvider} will be created during startup.
     *
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public final void setClientCertAuthProvider(final DeviceCredentialsAuthProvider<SubjectDnCredentials> provider) {
        this.clientCertAuthProvider = Objects.requireNonNull(provider);
    }

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    public final void setMetrics(final CoapAdapterMetrics metrics) {
        Optional.ofNullable(metrics)
            .ifPresent(m -> log.info("reporting metrics using [{}]", metrics.getClass().getName()));
        this.metrics = metrics;
    }

    @Override
    public final CoapAdapterMetrics getMetrics() {
        return metrics;
    }

    /**
     * Adds CoAP resources that should be added to the CoAP server
     * managed by this class.
     *
     * @param resources The resources.
     * @throws NullPointerException if resources is {@code null}.
     */
    public final void addResources(final Set<Resource> resources) {
        this.resourcesToAdd.addAll(Objects.requireNonNull(resources));
    }

    /**
     * @return {@link CoAP#DEFAULT_COAP_SECURE_PORT}
     */
    @Override
    public final int getPortDefaultValue() {
        return CoAP.DEFAULT_COAP_SECURE_PORT;
    }

    /**
     * @return {@link CoAP#DEFAULT_COAP_PORT}
     */
    @Override
    public final int getInsecurePortDefaultValue() {
        return CoAP.DEFAULT_COAP_PORT;
    }

    @Override
    protected final int getActualPort() {
        int port = Constants.PORT_UNCONFIGURED;
        final Endpoint endpoint = secureEndpoint;
        if (endpoint != null) {
            port = endpoint.getAddress().getPort();
        }
        return port;
    }

    @Override
    protected final int getActualInsecurePort() {
        int port = Constants.PORT_UNCONFIGURED;
        final Endpoint endpoint = insecureEndpoint;
        if (endpoint != null) {
            port = endpoint.getAddress().getPort();
        }
        return port;
    }

    /**
     * Sets the coap server instance configured to serve requests.
     * <p>
     * If no server is set using this method, then a server instance is created during startup of this adapter based on
     * the <em>config</em> properties.
     *
     * @param server The coap server.
     * @throws NullPointerException if server is {@code null}.
     */
    public final void setCoapServer(final CoapServer server) {
        Objects.requireNonNull(server);
        this.server = server;
    }

    @Override
    public final Endpoint getSecureEndpoint() {
        return secureEndpoint;
    }

    @Override
    public final Endpoint getInsecureEndpoint() {
        return insecureEndpoint;
    }

    @Override
    public final void runOnContext(final Handler<Void> codeToRun) {
        context.runOnContext(codeToRun);
    }

    @Override
    public final void doStart(final Promise<Void> startPromise) {

        Optional.ofNullable(server)
                .map(Future::succeededFuture)
                .orElseGet(this::createServer)
                .compose(serverToStart -> preStartup().map(serverToStart))
                .onSuccess(this::addResources)
                .compose(serverToStart -> Futures.executeBlocking(vertx, () -> {
                    serverToStart.start();
                    return serverToStart;
                }))
                .compose(serverToStart -> {
                    try {
                        onStartupSuccess();
                        return Future.succeededFuture((Void) null);
                    } catch (final Throwable t) {
                        log.error("error executing onStartupSuccess", t);
                        return Future.failedFuture(t);
                    }
                })
                .onComplete(startPromise);
    }

    private Future<CoapServer> createServer() {

        return checkCoapPortConfiguration()
                .compose(portConfigOk -> {
                    log.info("creating new CoAP server");
                    final CoapServer newServer = new CoapServer(NetworkConfig.createStandardWithoutFile());
                    final Future<Endpoint> secureEndpoint;
                    final Future<Endpoint> insecureEndpoint;

                    if (isSecurePortEnabled()) {
                        secureEndpoint = createSecureEndpoint()
                                .map(ep -> {
                                    newServer.addEndpoint(ep);
                                    this.secureEndpoint = ep;
                                    return ep;
                                });
                    } else {
                        log.info("neither key/cert nor secure port are configured, won't start secure endpoint");
                        secureEndpoint = Future.succeededFuture();
                    }

                    if (isInsecurePortEnabled()) {
                        if (getConfig().isAuthenticationRequired()) {
                            log.warn("skipping start up of insecure endpoint, configuration requires authentication of devices");
                            insecureEndpoint = Future.succeededFuture();
                        } else {
                            insecureEndpoint = createInsecureEndpoint()
                                    .map(ep -> {
                                        newServer.addEndpoint(ep);
                                        this.insecureEndpoint = ep;
                                        return ep;
                                    });
                        }
                    } else {
                        log.info("insecure port is not configured, won't start insecure endpoint");
                        insecureEndpoint = Future.succeededFuture();
                    }

                    return CompositeFuture.all(insecureEndpoint, secureEndpoint)
                            .map(ok -> {
                                this.server = newServer;
                                return newServer;
                            });
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isSecurePortEnabled() {
        return getConfig().isSecurePortEnabled() || getConfig().getPort() > Constants.PORT_UNCONFIGURED;
    }

    private Future<Void> checkCoapPortConfiguration() {

        final Promise<Void> result = Promise.promise();

        final boolean securePortEnabled = isSecurePortEnabled();
        final int securePort = securePortEnabled ? getConfig().getPort(getPortDefaultValue()) : Constants.PORT_UNCONFIGURED;

        if (!securePortEnabled) {
            if (getConfig().isInsecurePortEnabled()) {
                result.complete();
            } else {
                log.error("configuration must have at least one of secure or insecure port set to start up");
                result.fail("no ports configured");
            }
        } else if (getConfig().isInsecurePortEnabled() && securePort == getConfig().getInsecurePort(getInsecurePortDefaultValue())) {
            log.error("secure and insecure ports must be configured to bind to different port numbers");
            result.fail("secure and insecure ports configured to bind to same port number");
        } else {
            if (getConfig().getKeyCertOptions() == null) {
                log.warn("secure port configured, but no certificate/key is set. Will only enable ciphers that do not require server certificate!");
            }
            result.complete();
        }

        return result.future();
    }

    private void addResources(final CoapServer startingServer) {
        resourcesToAdd.forEach(resource -> {
            log.info("adding resource to CoAP server [name: {}]", resource.getName());
            startingServer.add(new VertxCoapResource(resource, context));
        });
        resourcesToAdd.clear();
    }

    private boolean addIdentity(final DtlsConnectorConfig.Builder dtlsConfig) {

        final KeyLoader keyLoader = KeyLoader.fromFiles(vertx, getConfig().getKeyPath(), getConfig().getCertPath());
        final PrivateKey pk = keyLoader.getPrivateKey();
        final Certificate[] certChain = keyLoader.getCertificateChain();
        if (pk != null && certChain != null) {
            if (pk.getAlgorithm().equals("EC")) {
                // Californium's cipher suites support ECC based keys only
                log.info("using private key [{}] and certificate [{}] as server identity",
                        getConfig().getKeyPath(), getConfig().getCertPath());
                dtlsConfig.setIdentity(pk, certChain);
                return true;
            } else {
                log.warn("configured key is not ECC based, certificate based cipher suites will be disabled");
            }
        } else if (certChain == null) {
            log.warn("Missing configured certificate");
        } else if (pk == null) {
            log.warn("Missing configured private key");
        }
        return false;
    }

    private Future<Endpoint> createSecureEndpoint() {

        return getSecureNetworkConfig().compose(config -> createSecureEndpoint(config));
    }

    private Future<Endpoint> createSecureEndpoint(final NetworkConfig config) {
        final ApplicationLevelInfoSupplier deviceResolver = Optional.ofNullable(honoDeviceResolver)
                .orElseGet(() -> new DeviceInfoSupplier());

        final AdvancedPskStore store = Optional.ofNullable(pskStore)
                .orElseGet(() -> {
                    return new PskDeviceResolver(context, tracer,
                            getTypeName(), getConfig(),
                            getCredentialsClient(), getTenantClient());
                });

        final DtlsConnectorConfig.Builder dtlsConfig = new DtlsConnectorConfig.Builder();
        // prevent session resumption
        dtlsConfig.setNoServerSessionId(true);
        dtlsConfig.setServerOnly(true);
        dtlsConfig.setRecommendedCipherSuitesOnly(true);
        dtlsConfig.setClientAuthenticationRequired(true);
        dtlsConfig.setAddress(
                new InetSocketAddress(getConfig().getBindAddress(), getConfig().getPort(getPortDefaultValue())));
        dtlsConfig.setApplicationLevelInfoSupplier(deviceResolver);
        dtlsConfig.setAdvancedPskStore(store);
        dtlsConfig.setRetransmissionTimeout(getConfig().getDtlsRetransmissionTimeout());
        dtlsConfig.setMaxConnections(config.getInt(Keys.MAX_ACTIVE_PEERS));

        if (addIdentity(dtlsConfig)) {
            final NewAdvancedCertificateVerifier verifier = Optional.ofNullable(certificateVerifier)
                    .orElseGet(() -> {
                        return new CertificateDeviceResolver(
                                context, tracer,
                                getTypeName(), getTenantClient(),
                                new TenantServiceBasedX509Authentication(getTenantClient(), tracer),
                                Optional.ofNullable(clientCertAuthProvider).orElseGet(
                                        () -> new X509AuthProvider(getCredentialsClient(), tracer)));
                    });
            dtlsConfig.setAdvancedCertificateVerifier(verifier);
        }

        try {
            final DtlsConnectorConfig dtlsConnectorConfig = dtlsConfig.build();
            if (log.isInfoEnabled()) {
                final String ciphers = dtlsConnectorConfig.getSupportedCipherSuites()
                        .stream()
                        .map(cipher -> cipher.name())
                        .collect(Collectors.joining(", "));
                log.info("creating secure endpoint supporting ciphers: {}", ciphers);
            }
            final DTLSConnector dtlsConnector = new DTLSConnector(dtlsConnectorConfig);
            final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
            builder.setNetworkConfig(config);
            builder.setConnector(dtlsConnector);
            return Future.succeededFuture(builder.build());

        } catch (final IllegalStateException ex) {
            log.warn("failed to create secure endpoint", ex);
            return Future.failedFuture(ex);
        }
    }

    private Future<Endpoint> createInsecureEndpoint() {

        log.info("creating insecure endpoint");

        return getInsecureNetworkConfig()
                .map(config -> {
                    final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
                    builder.setNetworkConfig(config);
                    builder.setInetSocketAddress(new InetSocketAddress(
                            getConfig().getInsecurePortBindAddress(),
                            getConfig().getInsecurePort(getInsecurePortDefaultValue())));
                    return builder.build();
                });
    }

    /**
     * Invoked before the coap server is started.
     * <p>
     * May be overridden by sub-classes to provide additional startup handling.
     *
     * @return A future indicating the outcome of the operation. The start up process fails if the returned future
     *         fails.
     */
    protected Future<Void> preStartup() {

        return Future.succeededFuture();
    }

    /**
     * Invoked after this adapter has started up successfully.
     * <p>
     * May be overridden by sub-classes.
     */
    protected void onStartupSuccess() {
        // empty
    }

    private NetworkConfig newDefaultNetworkConfig() {
        final NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setInt(Keys.PROTOCOL_STAGE_THREAD_COUNT, getConfig().getCoapThreads());
        networkConfig.setInt(Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, getConfig().getConnectorThreads());
        networkConfig.setInt(Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, getConfig().getConnectorThreads());
        networkConfig.setInt(Keys.MAX_RESOURCE_BODY_SIZE, getConfig().getMaxPayloadSize());
        networkConfig.setInt(Keys.EXCHANGE_LIFETIME, getConfig().getExchangeLifetime());
        networkConfig.setBoolean(Keys.USE_MESSAGE_OFFLOADING, getConfig().isMessageOffloadingEnabled());
        networkConfig.setString(Keys.DEDUPLICATOR, Keys.DEDUPLICATOR_PEERS_MARK_AND_SWEEP);
        final int maxConnections = getConfig().getMaxConnections();
        if (maxConnections == 0) {
            final MemoryBasedConnectionLimitStrategy limits = new MemoryBasedConnectionLimitStrategy(MINIMAL_MEMORY, MEMORY_PER_CONNECTION);
            networkConfig.setInt(Keys.MAX_ACTIVE_PEERS, limits.getRecommendedLimit());
        } else {
            networkConfig.setInt(Keys.MAX_ACTIVE_PEERS, maxConnections);
        }
        return networkConfig;
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
        networkConfig.setInt(Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, getConfig().getDtlsThreads());
        return loadNetworkConfig(getConfig().getNetworkConfig(), networkConfig)
                .compose(c -> loadNetworkConfig(getConfig().getSecureNetworkConfig(), c));
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
        return loadNetworkConfig(getConfig().getNetworkConfig(), networkConfig)
                .compose(c -> loadNetworkConfig(getConfig().getInsecureNetworkConfig(), c));
    }

    /**
     * Loads Californium configuration properties from a file.
     *
     * @param fileName The absolute path to the properties file.
     * @param networkConfig The configuration to apply the properties to.
     * @return The updated configuration.
     */
    protected Future<NetworkConfig> loadNetworkConfig(final String fileName, final NetworkConfig networkConfig) {

        if (fileName != null && !fileName.isEmpty()) {
            getVertx().fileSystem().readFile(fileName, readAttempt -> {
                if (readAttempt.succeeded()) {
                    try (InputStream is = new ByteArrayInputStream(readAttempt.result().getBytes())) {
                        networkConfig.load(is);
                    } catch (final IOException e) {
                        log.warn("skipping malformed NetworkConfig properties [{}]", fileName);
                    }
                } else {
                    log.warn("error reading NetworkConfig file [{}]", fileName, readAttempt.cause());
                }
            });
        }
        return Future.succeededFuture(networkConfig);
    }

    @Override
    public final void doStop(final Promise<Void> stopPromise) {

        try {
            preShutdown();
        } catch (final Exception e) {
            log.error("error in preShutdown", e);
        }

        Futures.executeBlocking(vertx, () -> {
            if (server != null) {
                server.stop();
            }
            return (Void) null;
        })
        .compose(ok -> postShutdown())
        .onComplete(stopPromise);
    }

    /**
     * Invoked before the coap server is shut down. May be overridden by sub-classes.
     */
    protected void preShutdown() {
        // empty
    }

    /**
     * Invoked after the Adapter has been shutdown successfully. May be overridden by sub-classes to provide further
     * shutdown handling.
     *
     * @return A future that has to be completed when this operation is finished.
     */
    protected Future<Void> postShutdown() {
        return Future.succeededFuture();
    }
}
