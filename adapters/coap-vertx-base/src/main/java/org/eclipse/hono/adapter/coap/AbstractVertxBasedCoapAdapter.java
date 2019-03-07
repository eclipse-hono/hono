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
import java.net.InetSocketAddress;
import java.security.Principal;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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
    /**
     * Map for authorization handler.
     */
    protected final Map<Class<? extends Principal>, CoapAuthenticationHandler> authenticationHandlerMap = new HashMap<>();

    private final Set<Resource> resourcesToAdd = new HashSet<>();

    /**
     * COAP server. Created from blocking execution, therefore use volatile.
     */
    private CoapServer server;
    private CoapAdapterMetrics metrics = CoapAdapterMetrics.NOOP;
    private volatile Endpoint secureEndpoint;
    private volatile Endpoint insecureEndpoint;

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
    protected CoapAdapterMetrics getMetrics() {
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
     * Get authentication handler for the type of the provided principal.
     * 
     * @param principal principal to be authenticated
     * @return authentication handler, or {@code null}, if no handler is available for this principal or no principal was provided.
     */
    protected CoapAuthenticationHandler getAuthenticationHandler(final Principal principal) {
        return principal == null ? null : authenticationHandlerMap.get(principal.getClass());
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
    public final void doStart(final Future<Void> startFuture) {

        checkPortConfiguration()
        .compose(s -> preStartup())
        .compose(s -> {
            final Future<NetworkConfig> secureConfig = getSecureNetworkConfig();
            final Future<NetworkConfig> insecureConfig = getInsecureNetworkConfig();

            return CompositeFuture.all(secureConfig, insecureConfig)
                .map(ok -> {
                    final CoapServer startingServer = server == null ? new CoapServer(insecureConfig.result()) : server;
                    addResources(startingServer);
                    bindSecureEndpoint(startingServer, secureConfig.result());
                    bindInsecureEndpoint(startingServer, insecureConfig.result());
                    startingServer.start();
                    if (secureEndpoint != null) {
                        LOG.info("coaps/udp endpoint running on {}", secureEndpoint.getAddress());
                    }
                    if (insecureEndpoint != null) {
                        LOG.info("coap/udp endpoint running on {}", insecureEndpoint.getAddress());
                    }
                    return ok;
                });
        }).compose(ok -> {
            try {
                onStartupSuccess();
                startFuture.complete();
            } catch (final Exception e) {
                LOG.error("error in onStartupSuccess", e);
                startFuture.fail(e);
            }
        }, startFuture);
    }

    private void addResources(final CoapServer startingServer) {
        resourcesToAdd.forEach(resource -> startingServer.add(new VertxCoapResource(resource, context)));
        resourcesToAdd.clear();
    }

    private void bindSecureEndpoint(final CoapServer startingServer, final NetworkConfig config) {

        final CoapPreSharedKeyHandler pskHandler = new CoapPreSharedKeyHandler(context, getConfig(),
                getCredentialsServiceClient());
        authenticationHandlerMap.put(pskHandler.getType(), pskHandler);

        final DtlsConnectorConfig.Builder dtlsConfig = new DtlsConnectorConfig.Builder();
        dtlsConfig.setClientAuthenticationRequired(getConfig().isAuthenticationRequired());
        dtlsConfig.setConnectionThreadCount(getConfig().getConnectorThreads());
        dtlsConfig.setAddress(
                new InetSocketAddress(getConfig().getBindAddress(), getConfig().getPort(getPortDefaultValue())));
        dtlsConfig.setPskStore(pskHandler);
        final KeyLoader keyLoader = KeyLoader.fromFiles(vertx, getConfig().getKeyPath(), getConfig().getCertPath());
        final PrivateKey pk = keyLoader.getPrivateKey();
        if (pk != null && keyLoader.getCertificateChain() != null) {
            if (pk.getAlgorithm().equals("EC")) {
                // Californium's cipher suites support ECC based keys only
                dtlsConfig.setIdentity(keyLoader.getPrivateKey(), keyLoader.getCertificateChain());
            } else {
                LOG.warn("configured key is not ECC based, certificate based cipher suites will be disabled");
            }
        }

        try {
            final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
            builder.setNetworkConfig(config);
            builder.setConnector(new DTLSConnector(dtlsConfig.build()));
            this.secureEndpoint = builder.build();
            startingServer.addEndpoint(this.secureEndpoint);

        } catch (final IllegalStateException ex) {
            LOG.warn("failed to create secure endpoint", ex);
        }
    }

    private void bindInsecureEndpoint(final CoapServer startingServer, final NetworkConfig config) {

        if (getConfig().isInsecurePortEnabled()) {
            if (getConfig().isAuthenticationRequired()) {
                LOG.warn("skipping start up of insecure endpoint, configuration requires authentication of devices");
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

    /**
     * Gets the CoAP network configuration for the secure endpoint.
     * <p>
     * Creates a CoAP network configuration setup with defaults. If the {@link CoapAdapterProperties} provides a
     * filename in "networkConfig", load that available values from that file overwriting existing values. If the
     * {@link CoapAdapterProperties} provides a filename in "secureNetworkConfig", load that available values from that
     * file also overwriting existing values.
     * 
     * @return The network configuration for the secure endpoint.
     */
    protected Future<NetworkConfig> getSecureNetworkConfig() {

        final Future<NetworkConfig> result = Future.future();
        final CoapAdapterProperties config = getConfig();
        final NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setInt(NetworkConfig.Keys.PROTOCOL_STAGE_THREAD_COUNT, config.getCoapThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, config.getConnectorThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, config.getConnectorThreads());
        loadNetworkConfig(config.getNetworkConfig(), networkConfig)
        .compose(c -> loadNetworkConfig(config.getSecureNetworkConfig(), c))
        .setHandler(result);
        return result;
    }

    /**
     * Gets the CoAP network configuration for the insecure endpoint.
     * 
     * Creates a CoAP network configuration setup with defaults. If the {@link CoapAdapterProperties} provides a
     * filename in "networkConfig", load that available values from that file overwriting existing values. If the
     * {@link CoapAdapterProperties} provides a filename in "insecureNetworkConfig", load that available values from
     * that file also overwriting existing values.
     * 
     * @return The network configuration for the insecure endpoint.
     */
    protected Future<NetworkConfig> getInsecureNetworkConfig() {

        final Future<NetworkConfig> result = Future.future();
        final CoapAdapterProperties config = getConfig();
        final NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setInt(NetworkConfig.Keys.PROTOCOL_STAGE_THREAD_COUNT, config.getCoapThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, config.getConnectorThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, config.getConnectorThreads());
        loadNetworkConfig(config.getNetworkConfig(), networkConfig)
        .compose(c -> loadNetworkConfig(config.getInsecureNetworkConfig(), c))
        .setHandler(result);
        return result;
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
                        LOG.warn("skipping malformed NetworkConfig properties [{}]", fileName);
                    }
                } else {
                    LOG.warn("error reading NetworkConfig file [{}]", fileName, readAttempt.cause());
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
    public final void doStop(final Future<Void> stopFuture) {

        try {
            preShutdown();
        } catch (final Exception e) {
            LOG.error("error in preShutdown", e);
        }

        final Future<Void> serverStopTracker = Future.future();
        if (server != null) {
            getVertx().executeBlocking(future -> {
                // Call some blocking API
                server.stop();
                future.complete();
            }, serverStopTracker);
        } else {
            serverStopTracker.complete();
        }

        serverStopTracker.compose(v -> postShutdown()).compose(s -> stopFuture.complete(), stopFuture);
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
     * Forwards the body of a CoAP request to the south bound Telemetry API of the AMQP 1.0 Messaging Network.
     * 
     * @param context The context representing the request to be processed.
     * @param authenticatedDevice authenticated device
     * @param originDevice message's origin device
     * @param waitForOutcome {@code true} to send the message waiting for the outcome, {@code false}, to wait just for
     *            the sent.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadTelemetryMessage(final CoapContext context, final Device authenticatedDevice,
            final Device originDevice, final boolean waitForOutcome) {

        doUploadMessage(
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
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadEventMessage(final CoapContext context, final Device authenticatedDevice,
            final Device originDevice) {

        doUploadMessage(
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
     */
    private void doUploadMessage(
            final CoapContext context,
            final Device authenticatedDevice,
            final Device device,
            final boolean waitForOutcome,
            final Buffer payload,
            final String contentType,
            final Future<MessageSender> senderTracker,
            final MetricsTags.EndpointType endpoint) {

        if (contentType == null) {
            context.respondWithCode(ResponseCode.NOT_ACCEPTABLE);
        } else if (payload == null || payload.length() == 0) {
            context.respondWithCode(ResponseCode.NOT_ACCEPTABLE);
        } else {

            final Future<JsonObject> tokenTracker = getRegistrationAssertion(
                    device.getTenantId(), device.getDeviceId(),
                    authenticatedDevice,
                    null);
            final Future<TenantObject> tenantEnabledTracker = getTenantConfiguration(device.getTenantId(), null)
                    .compose(tenantObject -> isAdapterEnabled(tenantObject));
            CompositeFuture.all(tokenTracker, senderTracker, tenantEnabledTracker).compose(ok -> {
                    final MessageSender sender = senderTracker.result();
                    final Message downstreamMessage = newMessage(
                            ResourceIdentifier.from(endpoint.getCanonicalName(), device.getTenantId(), device.getDeviceId()),
                            sender.isRegistrationAssertionRequired(),
                            "/" + context.getExchange().getRequestOptions().getUriPathString(),
                            contentType,
                            payload,
                            tokenTracker.result(),
                            null);
                    customizeDownstreamMessage(downstreamMessage, context);
                    if (waitForOutcome) {
                        // wait for outcome, ensure message order, if CoAP NSTART-1 is used.
                        return sender.sendAndWaitForOutcome(downstreamMessage);
                    } else {
                        return sender.send(downstreamMessage);
                    }
            }).map(delivery -> {
                LOG.trace("successfully processed message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                        device.getTenantId(), device.getDeviceId(), endpoint.getCanonicalName());
                metrics.reportTelemetry(
                        endpoint,
                        device.getTenantId(),
                        MetricsTags.ProcessingOutcome.FORWARDED,
                        waitForOutcome ? MetricsTags.QoS.AT_LEAST_ONCE : MetricsTags.QoS.AT_MOST_ONCE,
                        payload.length(),
                        context.getTimer());
                context.respondWithCode(ResponseCode.CHANGED);
                return delivery;
            }).recover(t -> {
                LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                        device.getTenantId(), device.getDeviceId(), endpoint.getCanonicalName(), t);
                metrics.reportTelemetry(
                        endpoint,
                        device.getTenantId(),
                        ClientErrorException.class.isInstance(t) ? MetricsTags.ProcessingOutcome.UNPROCESSABLE : MetricsTags.ProcessingOutcome.UNDELIVERABLE,
                        waitForOutcome ? MetricsTags.QoS.AT_LEAST_ONCE : MetricsTags.QoS.AT_MOST_ONCE,
                        payload.length(),
                        context.getTimer());
                CoapErrorResponse.respond(context.getExchange(), t);
                return Future.failedFuture(t);
            });
        }
    }
}
