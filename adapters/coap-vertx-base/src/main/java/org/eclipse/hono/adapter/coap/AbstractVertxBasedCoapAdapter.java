/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.auth.ExtensiblePrincipal;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.auth.ApplicationLevelInfoSupplier;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for a vert.x based Hono protocol adapter that uses CoAP.
 * <p>
 * Provides support for exposing Hono's southbound Telemetry &amp; Event
 * API by means of CoAP resources.
 * 
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractVertxBasedCoapAdapter<T extends CoapAdapterProperties>
        extends AbstractProtocolAdapterBase<T> {

    /**
     * A logger shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Set<Resource> resourcesToAdd = new HashSet<>();

    /**
     * COAP server. Created from blocking execution, therefore use volatile.
     */
    private CoapServer server;
    private CoapAdapterMetrics metrics = CoapAdapterMetrics.NOOP;
    private ApplicationLevelInfoSupplier honoDeviceResolver;
    private PskStore pskStore;

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
    public final void setPskStore(final PskStore pskStore) {
        this.pskStore = Objects.requireNonNull(pskStore);
    }

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    @Autowired
    public final void setMetrics(final CoapAdapterMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Gets the object for reporting this adapter's metrics.
     * 
     * @return The metrics.
     */
    protected final CoapAdapterMetrics getMetrics() {
        return metrics;
    }

    /**
     * Sets the CoAP resources that should be added to the CoAP server
     * managed by this class.
     * 
     * @param resources The resources.
     * @throws NullPointerException if resources is {@code null}.
     */
    @Autowired(required = false)
    public final void setResources(final Set<Resource> resources) {
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
    @Autowired(required = false)
    public final void setCoapServer(final CoapServer server) {
        Objects.requireNonNull(server);
        this.server = server;
    }

    @Override
    public final void doStart(final Promise<Void> startPromise) {

        checkPortConfiguration()
        .compose(s -> preStartup())
        .compose(s -> {
            final Future<NetworkConfig> secureConfig = getSecureNetworkConfig();
            final Future<NetworkConfig> insecureConfig = getInsecureNetworkConfig();

            return CompositeFuture.all(secureConfig, insecureConfig)
                    .map(ok -> {
                        final CoapServer startingServer = server == null ? new CoapServer(insecureConfig.result()) : server;
                        addResources(startingServer);
                        return startingServer;
                    })
                    .compose(server -> bindSecureEndpoint(server, secureConfig.result()))
                    .compose(server -> bindInsecureEndpoint(server, insecureConfig.result()))
                    .map(server -> {
                        server.start();
                        if (secureEndpoint != null) {
                            log.info("coaps/udp endpoint running on {}", secureEndpoint.getAddress());
                        }
                        if (insecureEndpoint != null) {
                            log.info("coap/udp endpoint running on {}", insecureEndpoint.getAddress());
                        }
                        return server;
                    });
        })
        .compose(ok -> {
            final Promise<Void> result = Promise.promise();
            try {
                onStartupSuccess();
                result.complete();
            } catch (final Exception e) {
                log.error("error in onStartupSuccess", e);
                result.fail(e);
            }
            return result.future();
        })
        .setHandler(startPromise);
    }

    private void addResources(final CoapServer startingServer) {
        resourcesToAdd.forEach(resource -> startingServer.add(new VertxCoapResource(resource, context)));
        resourcesToAdd.clear();
    }

    private void addIdentity(final DtlsConnectorConfig.Builder dtlsConfig) {

        final KeyLoader keyLoader = KeyLoader.fromFiles(vertx, getConfig().getKeyPath(), getConfig().getCertPath());
        final PrivateKey pk = keyLoader.getPrivateKey();
        final Certificate[] certChain = keyLoader.getCertificateChain();
        if (pk != null && certChain != null) {
            if (pk.getAlgorithm().equals("EC")) {
                // Californium's cipher suites support ECC based keys only
                log.info("using private key [{}] and certificate [{}] as server identity",
                        getConfig().getKeyPath(), getConfig().getCertPath());
                dtlsConfig.setIdentity(pk, certChain);
            } else {
                log.warn("configured key is not ECC based, certificate based cipher suites will be disabled");
            }
        }
    }

    private Future<CoapServer> bindSecureEndpoint(final CoapServer startingServer, final NetworkConfig config) {

        final ApplicationLevelInfoSupplier deviceResolver = Optional.ofNullable(honoDeviceResolver)
                .orElse(new DefaultDeviceResolver(context, getConfig(), getCredentialsClientFactory()));
        final PskStore store = Optional.ofNullable(pskStore)
                .orElseGet(() -> {
                    if (deviceResolver instanceof PskStore) {
                        return (PskStore) deviceResolver;
                    } else {
                        return new DefaultDeviceResolver(context, getConfig(), getCredentialsClientFactory());
                    }
                });

        final DtlsConnectorConfig.Builder dtlsConfig = new DtlsConnectorConfig.Builder();
        dtlsConfig.setServerOnly(true);
        dtlsConfig.setRecommendedCipherSuitesOnly(true);
        dtlsConfig.setClientAuthenticationRequired(getConfig().isAuthenticationRequired());
        dtlsConfig.setAddress(
                new InetSocketAddress(getConfig().getBindAddress(), getConfig().getPort(getPortDefaultValue())));
        dtlsConfig.setApplicationLevelInfoSupplier(deviceResolver);
        dtlsConfig.setPskStore(store);
        if (getConfig().getMaxConnections() > 0) {
            dtlsConfig.setMaxConnections(getConfig().getMaxConnections());
        }
        addIdentity(dtlsConfig);

        try {
            final DtlsConnectorConfig dtlsConnectorConfig = dtlsConfig.build();
            if (log.isInfoEnabled()) {
                final String ciphers = dtlsConnectorConfig.getSupportedCipherSuites()
                        .stream()
                        .map(cipher -> cipher.name())
                        .collect(Collectors.joining(", "));
                log.info("adding secure endpoint supporting ciphers: {}", ciphers);
            }
            final DTLSConnector dtlsConnector = new DTLSConnector(dtlsConnectorConfig);
            final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
            builder.setNetworkConfig(config);
            builder.setConnector(dtlsConnector);
            this.secureEndpoint = builder.build();
            startingServer.addEndpoint(this.secureEndpoint);
            return Future.succeededFuture(startingServer);

        } catch (final IllegalStateException ex) {
            log.warn("failed to create secure endpoint", ex);
            return Future.failedFuture(ex);
        }

    }

    private Future<CoapServer> bindInsecureEndpoint(final CoapServer startingServer, final NetworkConfig config) {

        if (getConfig().isInsecurePortEnabled()) {
            if (getConfig().isAuthenticationRequired()) {
                log.warn("skipping start up of insecure endpoint, configuration requires authentication of devices");
            } else {
                final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
                builder.setNetworkConfig(config);
                builder.setInetSocketAddress(new InetSocketAddress(
                        getConfig().getInsecurePortBindAddress(),
                        getConfig().getInsecurePort(getInsecurePortDefaultValue())));
                this.insecureEndpoint = builder.build();
                startingServer.addEndpoint(this.insecureEndpoint);
            }
        }
        return Future.succeededFuture(startingServer);
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
        networkConfig.setInt(NetworkConfig.Keys.PROTOCOL_STAGE_THREAD_COUNT, getConfig().getCoapThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, getConfig().getConnectorThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, getConfig().getConnectorThreads());
        networkConfig.setInt(NetworkConfig.Keys.MAX_RESOURCE_BODY_SIZE, getConfig().getMaxPayloadSize());
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

    /**
     * Invoked before the message is sent to the downstream peer.
     * <p>
     * Subclasses may override this method in order to customize the message before it is sent, e.g. adding custom
     * properties.
     * <p>
     * This default implementation does nothing.
     * 
     * @param downstreamMessage The message that will be sent downstream.
     * @param context The context representing the request to be processed.
     */
    protected void customizeDownstreamMessage(final Message downstreamMessage, final CoapContext context) {
    }

    @Override
    public final void doStop(final Promise<Void> stopPromise) {

        try {
            preShutdown();
        } catch (final Exception e) {
            log.error("error in preShutdown", e);
        }

        final Promise<Void> serverStopTracker = Promise.promise();
        if (server != null) {
            getVertx().executeBlocking(future -> {
                // Call some blocking API
                server.stop();
                future.complete();
            }, serverStopTracker);
        } else {
            serverStopTracker.complete();
        }

        serverStopTracker.future()
        .compose(v -> postShutdown())
        .setHandler(stopPromise);
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

    /**
     * Gets an authenticated device's identity for a CoAP request.
     * 
     * @param device The device that the data in the request payload originates from.
     *               If {@code null}, the origin of the data is assumed to be the authenticated device.
     * @param exchange The CoAP exchange with the authenticated device's principal.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the authenticated device can be determined from the CoAP exchange,
     *         otherwise the future will be failed with a {@link ClientErrorException}.
     */
    protected final Future<ExtendedDevice> getAuthenticatedExtendedDevice(
            final Device device,
            final CoapExchange exchange) {

        final Promise<ExtendedDevice> result = Promise.promise();
        final Principal peerIdentity = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
        if (peerIdentity instanceof ExtensiblePrincipal) {
            @SuppressWarnings("unchecked")
            final ExtensiblePrincipal<? extends Principal> extPrincipal = (ExtensiblePrincipal<? extends Principal>) peerIdentity;
            final Device authenticatedDevice = extPrincipal.getExtendedInfo().get("hono-device", Device.class);
            if (authenticatedDevice != null) {
                final Device originDevice = Optional.ofNullable(device).orElse(authenticatedDevice);
                result.complete(new ExtendedDevice(authenticatedDevice, originDevice));
            } else {
                result.fail(new ClientErrorException(
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        "DTLS session does not contain authenticated Device"));
            }
        } else {
            result.fail(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "DTLS session does not contain ExtensiblePrincipal"));

        }

        return result.future();
    }


    /**
     * Forwards the body of a CoAP request to the south bound Telemetry API of the AMQP 1.0 Messaging Network.
     * 
     * @param context The context representing the request to be processed.
     * @param authenticatedDevice authenticated device
     * @param originDevice message's origin device
     * @param waitForOutcome {@code true} to send the message waiting for the outcome, {@code false}, to wait just for
     *            the sent.
     * @return A future containing the response code that has been returned to
     *         the device.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final Future<ResponseCode> uploadTelemetryMessage(final CoapContext context, final Device authenticatedDevice,
            final Device originDevice, final boolean waitForOutcome) {

        return doUploadMessage(
                Objects.requireNonNull(context),
                Objects.requireNonNull(authenticatedDevice),
                Objects.requireNonNull(originDevice),
                waitForOutcome,
                Buffer.buffer(context.getExchange().getRequestPayload()),
                MediaTypeRegistry.toString(context.getExchange().getRequestOptions().getContentFormat()),
                getTelemetrySender(authenticatedDevice.getTenantId()),
                MetricsTags.EndpointType.TELEMETRY);
    }

    /**
     * Forwards the body of a CoAP request to the south bound Event API of the AMQP 1.0 Messaging Network.
     * 
     * @param context The context representing the request to be processed.
     * @param authenticatedDevice authenticated device
     * @param originDevice message's origin device
     * @return A future containing the response code that has been returned to
     *         the device.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final Future<ResponseCode> uploadEventMessage(final CoapContext context, final Device authenticatedDevice,
            final Device originDevice) {

        return doUploadMessage(
                Objects.requireNonNull(context),
                Objects.requireNonNull(authenticatedDevice),
                Objects.requireNonNull(originDevice),
                true,
                Buffer.buffer(context.getExchange().getRequestPayload()),
                MediaTypeRegistry.toString(context.getExchange().getRequestOptions().getContentFormat()),
                getEventSender(authenticatedDevice.getTenantId()),
                MetricsTags.EndpointType.EVENT);
    }

    /**
     * Forwards a message to the south bound Telemetry or Event API of the AMQP 1.0 Messaging Network.
     * <p>
     * Depending on the outcome of the attempt to upload the message, the CaAP response's code is set as
     * follows:
     * <ul>
     * <li>2.04 (Changed) - if the message has been forwarded downstream.</li>
     * <li>4.01 (Unauthorized) - if the device could not be authorized.</li>
     * <li>4.03 (Forbidden) - if the tenant/device is not authorized to send messages.</li>
     * <li>4.06 (Not Acceptable) - if the message is malformed.</li>
     * <li>5.00 (Internal Server Error) - if the message could not be processed due to an unknown processing error.</li>
     * <li>5.03 (Service Unavailable) - if the message could not be forwarded, e.g. due to lack of
     * connection or credit.</li>
     * <li>?.?? (Generic mapped HTTP error) - if the message could not be sent caused by an specific processing error.
     * See {@link CoapErrorResponse}.</li>
     * </ul>
     * 
     * @param context The context representing the request to be processed.
     * @param authenticatedDevice authenticated device
     * @param device message's origin device
     * @param waitForOutcome {@code true} to send the message waiting for the outcome, {@code false}, to wait just for
     *            the sent.
     * @param payload message payload
     * @param contentType content type of message payload
     * @param senderTracker hono message sender tracker
     * @param endpoint message destination endpoint
     * @return A succeeded future containing the CoAP status code that has been returned to the device.
     */
    private Future<ResponseCode> doUploadMessage(
            final CoapContext context,
            final Device authenticatedDevice,
            final Device device,
            final boolean waitForOutcome,
            final Buffer payload,
            final String contentType,
            final Future<DownstreamSender> senderTracker,
            final MetricsTags.EndpointType endpoint) {

        if (contentType == null) {
            context.respondWithCode(ResponseCode.NOT_ACCEPTABLE);
            return Future.succeededFuture(ResponseCode.NOT_ACCEPTABLE);
        } else if (payload == null || payload.length() == 0) {
            context.respondWithCode(ResponseCode.NOT_ACCEPTABLE);
            return Future.succeededFuture(ResponseCode.NOT_ACCEPTABLE);
        } else {

            final Span currentSpan = TracingHelper
                    .buildChildSpan(tracer, context.getTracingContext(),
                            "upload " + endpoint.getCanonicalName())
                    .ignoreActiveSpan()
                    .withTag(Tags.COMPONENT.getKey(), getTypeName())
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, device.getTenantId())
                    .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, device.getDeviceId())
                    .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                    .start();

            final Future<JsonObject> tokenTracker = getRegistrationAssertion(
                    device.getTenantId(), device.getDeviceId(),
                    authenticatedDevice,
                    currentSpan.context());
            final Future<TenantObject> tenantTracker = getTenantConfiguration(device.getTenantId(), currentSpan.context());
            final Future<TenantObject> tenantValidationTracker = tenantTracker
                    .compose(tenantObject -> CompositeFuture
                            .all(isAdapterEnabled(tenantObject),
                                    checkMessageLimit(tenantObject, payload.length(), currentSpan.context()))
                            .map(success -> tenantObject));
            return CompositeFuture.all(tokenTracker, senderTracker, tenantValidationTracker).compose(ok -> {
                    final DownstreamSender sender = senderTracker.result();
                    final Message downstreamMessage = newMessage(
                            ResourceIdentifier.from(endpoint.getCanonicalName(), device.getTenantId(), device.getDeviceId()),
                            "/" + context.getExchange().getRequestOptions().getUriPathString(),
                            contentType,
                            payload,
                            tenantValidationTracker.result(),
                            tokenTracker.result(),
                            null);
                    customizeDownstreamMessage(downstreamMessage, context);
                    if (waitForOutcome) {
                        // wait for outcome, ensure message order, if CoAP NSTART-1 is used.
                        return sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context());
                    } else {
                        return sender.send(downstreamMessage, currentSpan.context());
                    }
            }).map(delivery -> {
                log.trace("successfully processed message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                        device.getTenantId(), device.getDeviceId(), endpoint.getCanonicalName());
                metrics.reportTelemetry(
                        endpoint,
                        device.getTenantId(),
                        tenantTracker.result(),
                        MetricsTags.ProcessingOutcome.FORWARDED,
                        waitForOutcome ? MetricsTags.QoS.AT_LEAST_ONCE : MetricsTags.QoS.AT_MOST_ONCE,
                        payload.length(),
                        context.getTimer());
                context.respondWithCode(ResponseCode.CHANGED);
                currentSpan.finish();
                return ResponseCode.CHANGED;
            }).otherwise(t -> {
                log.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                        device.getTenantId(), device.getDeviceId(), endpoint.getCanonicalName(), t);
                metrics.reportTelemetry(
                        endpoint,
                        device.getTenantId(),
                        tenantTracker.result(),
                        ClientErrorException.class.isInstance(t) ? MetricsTags.ProcessingOutcome.UNPROCESSABLE : MetricsTags.ProcessingOutcome.UNDELIVERABLE,
                        waitForOutcome ? MetricsTags.QoS.AT_LEAST_ONCE : MetricsTags.QoS.AT_MOST_ONCE,
                        payload.length(),
                        context.getTimer());
                TracingHelper.logError(currentSpan, t);
                currentSpan.finish();
                return CoapErrorResponse.respond(context.getExchange(), t);
            });
        }
    }

    /**
     * {@inheritDoc}
     *
     * Marked as final since overriding this method doesn't make sense here (command &amp; control is not
     * supported for now).
     */
    @Override
    protected final Future<Boolean> isGatewayMappingEnabled(final String tenantId, final String deviceId,
            final Device authenticatedDevice) {
        return Future.succeededFuture(true);
    }
}
