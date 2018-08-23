/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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
import java.net.HttpURLConnection;
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
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
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

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * COAP server. Created from blocking execution, therefore use volatile.
     */
    private CoapServer server;
    private volatile Endpoint secureEndpoint;
    private volatile Endpoint insecureEndpoint;
    private CoapAdapterMetrics metrics;
    /**
     * Map for authorization handler.
     */
    protected final Map<Class<? extends Principal>, CoapAuthenticationHandler> authenticationHandlerMap = new HashMap<Class<? extends Principal>, CoapAuthenticationHandler>();

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
            final NetworkConfig secureNetworkConfig = getSecureNetworkConfig();
            final NetworkConfig insecureNetworkConfig = getInsecureNetworkConfig();
            final Context adapterContext = this.context;
            final CoapServer server = this.server;

            // delegate for blocking execution
            getVertx().executeBlocking(future -> {
                // Call some potential blocking API
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
                } catch (IllegalStateException ex) {
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
            } catch (Exception e) {
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
     * Get the coap network configuration for the secure endpoint.
     * 
     * Creates a coap network configuration setup with defaults. If the {@link CoapAdapterProperties} provides a
     * filename in "networkConfig", load that available values from that file overwriting existing values. If the
     * {@link CoapAdapterProperties} provides a filename in "secureNetworkConfig", load that available values from that
     * file also overwriting existing values.
     * 
     * @return coap network configuration for the secure endpoint.
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
     * Get the coap network configuration for the insecure endpoint.
     * 
     * Creates a coap network configuration setup with defaults. If the {@link CoapAdapterProperties} provides a
     * filename in "networkConfig", load that available values from that file overwriting existing values. If the
     * {@link CoapAdapterProperties} provides a filename in "insecureNetworkConfig", load that available values from
     * that file also overwriting existing values.
     * 
     * @return coap network configuration for the insecure endpoint.
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
     * 
     * @param downstreamMessage The message that will be sent downstream.
     * @param exchange coap message exchange.
     */
    protected void customizeDownstreamMessage(final Message downstreamMessage, final CoapExchange exchange) {
        // this default implementation does nothing
    }

    @Override
    public final void doStop(final Future<Void> stopFuture) {

        try {
            preShutdown();
        } catch (Exception e) {
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
     * Uploads the body of an COAP request as a telemetry message to the Hono server.
     * 
     * @param exchange coap message exchange
     * @param authenticatedDevice authenticated device
     * @param originDevice message's origin device
     * @param waitForOutcome {@code true} to send the message waiting for the outcome, {@code false}, to wait just for
     *            the sent.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadTelemetryMessage(final CoapExchange exchange, final Device authenticatedDevice,
            final Device originDevice, final boolean waitForOutcome) {

        doUploadMessage(Objects.requireNonNull(exchange), Objects.requireNonNull(authenticatedDevice),
                Objects.requireNonNull(originDevice), waitForOutcome,
                Buffer.buffer(exchange.getRequestPayload()),
                MediaTypeRegistry.toString(exchange.getRequestOptions().getContentFormat()),
                getTelemetrySender(authenticatedDevice.getTenantId()),
                TelemetryConstants.TELEMETRY_ENDPOINT);
    }

    /**
     * Uploads the body of an COAP request as a event message to the Hono server.
     * 
     * @param exchange coap message exchange
     * @param authenticatedDevice authenticated device
     * @param originDevice message's origin device
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadEventMessage(final CoapExchange exchange, final Device authenticatedDevice,
            final Device originDevice) {

        doUploadMessage(Objects.requireNonNull(exchange), Objects.requireNonNull(authenticatedDevice),
                Objects.requireNonNull(originDevice), true,
                Buffer.buffer(exchange.getRequestPayload()),
                MediaTypeRegistry.toString(exchange.getRequestOptions().getContentFormat()),
                getEventSender(authenticatedDevice.getTenantId()), EventConstants.EVENT_ENDPOINT);
    }

    /**
     * Uploads a telemetry or event message to the Hono server.
     * <p>
     * Depending on the outcome of the attempt to upload the message to Hono, the COAP response's code is set as
     * follows:
     * <ul>
     * <li>2.04 (Changed) - if the message has been sent to the Hono server.</li>
     * <li>4.01 (Unauthorized) - if the device could not be authorized.</li>
     * <li>4.03 (Forbidden) - if the tenant/device is not authorized to send messages.</li>
     * <li>4.06 (Not Acceptable) - if the message is misformed.</li>
     * <li>5.00 (Internal Server Error) - if the message could not be sent caused by an unspecific processing
     * error.</li>
     * <li>5.03 (Service Unavailable) - if the message could not be sent to the Hono server, e.g. due to lack of
     * connection or credit.</li>
     * <li>?.?? (Generic mapped HTTP error) - if the message could not be sent caused by an specific processing error.
     * See {@link CoapErrorResponse}.</li>
     * </ul>
     * 
     * @param exchange coap message exchange
     * @param authenticatedDevice authenticated device
     * @param device message's origin device
     * @param waitForOutcome {@code true} to send the message waiting for the outcome, {@code false}, to wait just for
     *            the sent.
     * @param payload message payload
     * @param contentType content type of message payload
     * @param senderTracker hono message sender tracker
     * @param endpointName message destination endpoint name
     */
    private void doUploadMessage(final CoapExchange exchange, final Device authenticatedDevice, final Device device,
            final boolean waitForOutcome, final Buffer payload, final String contentType,
            final Future<MessageSender> senderTracker,
            final String endpointName) {

        if (contentType == null) {
            exchange.respond(ResponseCode.NOT_ACCEPTABLE);
        } else if (payload == null || payload.length() == 0) {
            exchange.respond(ResponseCode.NOT_ACCEPTABLE);
        } else {

            final Future<JsonObject> tokenTracker = getRegistrationAssertion(
                    device.getTenantId(), device.getDeviceId(),
                    authenticatedDevice,
                    null);
            final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(device.getTenantId(), null);
            CompositeFuture.all(tokenTracker, senderTracker, tenantConfigTracker).compose(ok -> {
                if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {
                    final MessageSender sender = senderTracker.result();
                    final Message downstreamMessage = newMessage(
                            ResourceIdentifier.from(endpointName, device.getTenantId(), device.getDeviceId()),
                            sender.isRegistrationAssertionRequired(),
                            "/" + exchange.getRequestOptions().getUriPathString(),
                            contentType,
                            payload,
                            tokenTracker.result(),
                            null);
                    customizeDownstreamMessage(downstreamMessage, exchange);
                    if (waitForOutcome) {
                        // wait for outcome, ensure message order, if CoAP NSTART-1 is used.
                        return sender.sendAndWaitForOutcome(downstreamMessage);
                    } else {
                        return sender.send(downstreamMessage);
                    }
                } else {
                    // this adapter is not enabled for the tenant
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN));
                }
            }).compose(delivery -> {
                LOG.trace("successfully processed message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                        device.getTenantId(), device.getDeviceId(), endpointName);
                metrics.incrementProcessedMessages(endpointName, device.getTenantId());
                exchange.respond(ResponseCode.CHANGED);
                return Future.succeededFuture();
            }).recover(t -> {
                LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]: {}",
                        device.getTenantId(), device.getDeviceId(), endpointName, t.getMessage());
                if (!(ClientErrorException.class.isInstance(t))) {
                    metrics.incrementUndeliverableMessages(endpointName, device.getTenantId());
                }
                CoapErrorResponse.respond(exchange, t);
                return Future.failedFuture(t);
            });
        }
    }
}
