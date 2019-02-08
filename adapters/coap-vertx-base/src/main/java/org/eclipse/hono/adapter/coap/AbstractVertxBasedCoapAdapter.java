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

import java.io.File;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for a Vert.x based Hono protocol adapter that uses CoAP. It provides access to the Telemetry and Event
 * API.
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
     * Called during {@link #doStart(Future)}.
     * 
     * Note: The call is in the scope of "executeBlocking" and therefore special care accessing this instance must be
     * obeyed.
     * 
     * @param adapterContext context of this adapter. Intended for schedule calls back into this vertical.
     * @param server coap server to add resources.
     */
    protected abstract void addResources(Context adapterContext, CoapServer server);

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
        checkPortConfiguration().compose(s -> preStartup()).compose(s -> {
            final Future<Void> deployFuture = Future.future();
            // access environment using the context of this vertx and
            // load it into finals to access them within the executeBlocking
            final CoapAdapterProperties config = getConfig();
            final CoapPreSharedKeyHandler coapPreSharedKeyProvider = new CoapPreSharedKeyHandler(getVertx(), config,
                    getCredentialsServiceClient());
            // add handler to authentication handler map.
            authenticationHandlerMap.put(coapPreSharedKeyProvider.getType(), coapPreSharedKeyProvider);
            final Context adapterContext = this.context;
            final CoapServer server = this.server;

            // delegate for blocking execution
            getVertx().executeBlocking(future -> {
                // Call some potential blocking API
                final NetworkConfig secureNetworkConfig = getSecureNetworkConfig();
                final NetworkConfig insecureNetworkConfig = getInsecureNetworkConfig();
                final CoapServer startingServer = server == null ? new CoapServer(insecureNetworkConfig) : server;
                addResources(adapterContext, startingServer);
                final DtlsConnectorConfig.Builder dtlsConfig = new DtlsConnectorConfig.Builder();
                dtlsConfig.setClientAuthenticationRequired(config.isAuthenticationRequired());
                dtlsConfig.setConnectionThreadCount(config.getConnectorThreads());
                dtlsConfig.setAddress(
                        new InetSocketAddress(config.getBindAddress(), config.getPort(getPortDefaultValue())));
                if (coapPreSharedKeyProvider != null) {
                    dtlsConfig.setPskStore(coapPreSharedKeyProvider);
                }
                Endpoint secureEndpoint = null;
                try {
                    final CoapEndpoint.CoapEndpointBuilder builder = new CoapEndpoint.CoapEndpointBuilder();
                    builder.setNetworkConfig(secureNetworkConfig);
                    builder.setConnector(new DTLSConnector(dtlsConfig.build()));
                    secureEndpoint = builder.build();
                    startingServer.addEndpoint(secureEndpoint);
                } catch (final IllegalStateException ex) {
                    LOG.warn("Failed to create secure endpoint!", ex);
                }
                Endpoint insecureEndpoint = null;
                if (config.isInsecurePortEnabled()) {
                    if (config.isAuthenticationRequired()) {
                        LOG.warn("ambig configuration! authenticationRequired is not supported for insecure endpoint!");
                    } else {
                        final CoapEndpoint.CoapEndpointBuilder builder = new CoapEndpoint.CoapEndpointBuilder();
                        builder.setNetworkConfig(insecureNetworkConfig);
                        builder.setInetSocketAddress(new InetSocketAddress(config.getInsecurePortBindAddress(),
                                config.getInsecurePort(getInsecurePortDefaultValue())));
                        insecureEndpoint = builder.build();
                        startingServer.addEndpoint(insecureEndpoint);
                    }
                }
                startingServer.start();
                this.secureEndpoint = secureEndpoint;
                this.insecureEndpoint = insecureEndpoint;
                future.complete(startingServer);
            }, res -> {
                if (res.succeeded()) {
                    this.server = (CoapServer) res.result();
                    deployFuture.complete();
                } else {
                    deployFuture.fail(res.cause());
                }
            });
            return deployFuture;
        }).compose(s -> {
            try {
                onStartupSuccess();
                startFuture.complete();
            } catch (final Exception e) {
                LOG.error("error in onStartupSuccess", e);
                startFuture.fail(e);
            }
        }, startFuture);
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
     * Creates a coap network configuration setup with defaults. If the {@link CoapAdapterProperties} provides a
     * filename in "networkConfig", load that available values from that file overwriting existing values. If the
     * {@link CoapAdapterProperties} provides a filename in "secureNetworkConfig", load that available values from that
     * file also overwriting existing values.
     * 
     * @return The network configuration for the secure endpoint.
     */
    protected NetworkConfig getSecureNetworkConfig() {
        final CoapAdapterProperties config = getConfig();
        final NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setInt(NetworkConfig.Keys.PROTOCOL_STAGE_THREAD_COUNT, config.getCoapThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, config.getConnectorThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, config.getConnectorThreads());
        loadNetworkConfig(config.getNetworkConfig(), networkConfig);
        loadNetworkConfig(config.getSecureNetworkConfig(), networkConfig);
        return networkConfig;
    }

    /**
     * Gets the CoAP network configuration for the insecure endpoint.
     * 
     * Creates a coap network configuration setup with defaults. If the {@link CoapAdapterProperties} provides a
     * filename in "networkConfig", load that available values from that file overwriting existing values. If the
     * {@link CoapAdapterProperties} provides a filename in "insecureNetworkConfig", load that available values from
     * that file also overwriting existing values.
     * 
     * @return The network configuration for the insecure endpoint.
     */
    protected NetworkConfig getInsecureNetworkConfig() {
        final CoapAdapterProperties config = getConfig();
        final NetworkConfig networkConfig = new NetworkConfig();
        networkConfig.setInt(NetworkConfig.Keys.PROTOCOL_STAGE_THREAD_COUNT, config.getCoapThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_RECEIVER_THREAD_COUNT, config.getConnectorThreads());
        networkConfig.setInt(NetworkConfig.Keys.NETWORK_STAGE_SENDER_THREAD_COUNT, config.getConnectorThreads());
        loadNetworkConfig(config.getNetworkConfig(), networkConfig);
        loadNetworkConfig(config.getInsecureNetworkConfig(), networkConfig);
        return networkConfig;
    }

    /**
     * Loads Californium configuration properties from a file.
     * 
     * @param fileName The absolute path to the properties file.
     * @param networkConfig The configuration to apply the properties to.
     */
    protected void loadNetworkConfig(final String fileName, final NetworkConfig networkConfig) {
        if (fileName != null && !fileName.isEmpty()) {
            final File file = new File(fileName);
            String cause = null;
            if (!file.exists()) {
                cause = "File doesn't exists!";
            } else if (!file.isFile()) {
                cause = "Isn't a file!";
            } else if (!file.canRead()) {
                cause = "Can't read file!";
            } else {
                networkConfig.load(file);
            }
            if (cause != null) {
                LOG.warn("Can't read NetworkConfig from {}! {}", fileName, cause);
            }
        }
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
    private void doUploadMessage(final CoapContext context, final Device authenticatedDevice, final Device device,
            final boolean waitForOutcome, final Buffer payload, final String contentType,
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
                        device.getTenantId(), device.getDeviceId(), endpoint);
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
                LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]: {}",
                        device.getTenantId(), device.getDeviceId(), endpoint, t.getMessage());
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
