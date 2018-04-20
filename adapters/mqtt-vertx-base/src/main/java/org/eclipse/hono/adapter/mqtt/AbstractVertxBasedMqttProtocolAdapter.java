/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EndpointType;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;

/**
 * A base class for implementing Vert.x based Hono protocol adapters
 * for publishing events &amp; telemetry data using MQTT.
 * 
 * @param <T> The type of configuration properties this adapter supports/requires.
 */
public abstract class AbstractVertxBasedMqttProtocolAdapter<T extends ProtocolAdapterProperties> extends AbstractProtocolAdapterBase<T> {

    private static final int IANA_MQTT_PORT = 1883;
    private static final int IANA_SECURE_MQTT_PORT = 8883;

    /**
     * A logger to be used by concrete subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    private MqttAdapterMetrics metrics;

    private MqttServer server;
    private MqttServer insecureServer;
    private HonoClientBasedAuthProvider usernamePasswordAuthProvider;

    /**
     * Sets the provider to use for authenticating devices based on
     * a username and password.
     * 
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public final void setUsernamePasswordAuthProvider(final HonoClientBasedAuthProvider provider) {
        this.usernamePasswordAuthProvider = Objects.requireNonNull(provider);
    }

    @Override
    public int getPortDefaultValue() {
        return IANA_SECURE_MQTT_PORT;
    }

    @Override
    public int getInsecurePortDefaultValue() {
        return IANA_MQTT_PORT;
    }

    @Override
    protected final int getActualPort() {
        return (server != null ? server.actualPort() : Constants.PORT_UNCONFIGURED);
    }

    @Override
    protected final int getActualInsecurePort() {
        return (insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED);
    }

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    @Autowired
    public final void setMetrics(final MqttAdapterMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Sets the MQTT server to use for handling secure MQTT connections.
     * 
     * @param server The server.
     * @throws NullPointerException if the server is {@code null}.
     */
    public void setMqttSecureServer(final MqttServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalArgumentException("MQTT server must not be started already");
        } else {
            this.server = server;
        }
    }

    /**
     * Sets the MQTT server to use for handling non-secure MQTT connections.
     * 
     * @param server The server.
     * @throws NullPointerException if the server is {@code null}.
     */
    public void setMqttInsecureServer(final MqttServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalArgumentException("MQTT server must not be started already");
        } else {
            this.insecureServer = server;
        }
    }

    private Future<Void> bindSecureMqttServer() {

        if (isSecurePortEnabled()) {
            MqttServerOptions options = new MqttServerOptions();
            options
                .setHost(getConfig().getBindAddress())
                .setPort(determineSecurePort())
                .setMaxMessageSize(getConfig().getMaxPayloadSize());
            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);

            return bindMqttServer(options, server).map(s -> {
                server = s;
                return (Void) null;
            }).recover(t -> {
                return Future.failedFuture(t);
            });
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<Void> bindInsecureMqttServer() {

        if (isInsecurePortEnabled()) {
            MqttServerOptions options = new MqttServerOptions();
            options
                .setHost(getConfig().getInsecurePortBindAddress())
                .setPort(determineInsecurePort())
                .setMaxMessageSize(getConfig().getMaxPayloadSize());

            return bindMqttServer(options, insecureServer).map(server -> {
                insecureServer = server;
                return (Void) null;
            }).recover(t -> {
                return Future.failedFuture(t);
            });
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<MqttServer> bindMqttServer(final MqttServerOptions options, final MqttServer mqttServer) {

        final Future<MqttServer> result = Future.future();
        final MqttServer createdMqttServer =
                (mqttServer == null ? MqttServer.create(this.vertx, options) : mqttServer);

        createdMqttServer
            .endpointHandler(this::handleEndpointConnection)
            .listen(done -> {

                if (done.succeeded()) {
                    LOG.info("MQTT server running on {}:{}", getConfig().getBindAddress(), createdMqttServer.actualPort());
                    result.complete(createdMqttServer);
                } else {
                    LOG.error("error while starting up MQTT server", done.cause());
                    result.fail(done.cause());
                }
            });
        return result;
    }

    @Override
    public void doStart(final Future<Void> startFuture) {

        LOG.info("limiting size of inbound message payload to {} bytes", getConfig().getMaxPayloadSize());
        if (!getConfig().isAuthenticationRequired()) {
            LOG.warn("authentication of devices turned off");
        }
        checkPortConfiguration()
        .compose(v -> bindSecureMqttServer())
        .compose(s -> bindInsecureMqttServer())
        .compose(t -> {
            if (usernamePasswordAuthProvider == null) {
                usernamePasswordAuthProvider = new UsernamePasswordAuthProvider(getCredentialsServiceClient(), getConfig());
            }
            startFuture.complete();
        }, startFuture);
    }

    @Override
    public void doStop(final Future<Void> stopFuture) {

        Future<Void> serverTracker = Future.future();
        if (this.server != null) {
            this.server.close(serverTracker.completer());
        } else {
            serverTracker.complete();
        }

        Future<Void> insecureServerTracker = Future.future();
        if (this.insecureServer != null) {
            this.insecureServer.close(insecureServerTracker.completer());
        } else {
            insecureServerTracker.complete();
        }

        CompositeFuture.all(serverTracker, insecureServerTracker)
            .compose(d -> stopFuture.complete(), stopFuture);
    }

    /**
     * Invoked when a client sends its <em>CONNECT</em> packet.
     * <p>
     * Authenticates the client (if required) and registers handlers for processing
     * messages published by the client.
     * 
     * @param endpoint The MQTT endpoint representing the client.
     */
    final void handleEndpointConnection(final MqttEndpoint endpoint) {

        LOG.debug("connection request from client [clientId: {}]", endpoint.clientIdentifier());

        isConnected().map(ok -> {
            if (getConfig().isAuthenticationRequired()) {
                handleEndpointConnectionWithAuthentication(endpoint);
            } else {
                handleEndpointConnectionWithoutAuthentication(endpoint);
            }
            return null;
        }).otherwise(t -> {
            LOG.debug("connection request from client [clientId: {}] rejected: {}",
                    endpoint.clientIdentifier(), MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            return null;
        });
    }

    /**
     * Invoked when a client sends its <em>CONNECT</em> packet and client authentication has
     * been disabled by setting the {@linkplain ProtocolAdapterProperties#isAuthenticationRequired()
     * authentication required} configuration property to {@code false}.
     * <p>
     * Registers a close handler on the endpoint which invokes {@link #close(MqttEndpoint)}.
     * Registers a publish handler on the endpoint which invokes {@link #onPublishedMessage(MqttContext)}
     * for each message being published by the client.
     * Accepts the connection request.
     * 
     * @param endpoint The MQTT endpoint representing the client.
     */
    private void handleEndpointConnectionWithoutAuthentication(final MqttEndpoint endpoint) {

        endpoint.closeHandler(v -> {
            close(endpoint);
            LOG.debug("connection to unauthenticated device [clientId: {}] closed", endpoint.clientIdentifier());
            metrics.decrementUnauthenticatedMqttConnections();
        });
        endpoint.publishHandler(message -> onPublishedMessage(new MqttContext(message, endpoint)));

        LOG.debug("unauthenticated device [clientId: {}] connected", endpoint.clientIdentifier());
        endpoint.accept(false);
        metrics.incrementUnauthenticatedMqttConnections();
    }

    private void handleEndpointConnectionWithAuthentication(final MqttEndpoint endpoint) {

        if (endpoint.auth() == null) {
            LOG.debug("connection request from device [clientId: {}] rejected: {}",
                    endpoint.clientIdentifier(), "device did not provide credentials in CONNECT packet");
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);

        } else {

            final DeviceCredentials credentials = getCredentials(endpoint.auth());

            if (credentials == null) {
                LOG.debug("connection request from device [clientId: {}] rejected: {}",
                        endpoint.clientIdentifier(), "device provided malformed credentials in CONNECT packet");
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);

            } else {

                getTenantConfiguration(credentials.getTenantId()).compose(tenantConfig -> {
                    if (tenantConfig.isAdapterEnabled(getTypeName())) {
                        LOG.debug("protocol adapter [{}] is enabled for tenant [{}]",
                                getTypeName(), credentials.getTenantId());
                        return Future.succeededFuture(tenantConfig);
                    } else {
                        LOG.debug("protocol adapter [{}] is disabled for tenant [{}]",
                                getTypeName(), credentials.getTenantId());
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, "adapter disabled for tenant"));
                    }
                }).compose(tenantConfig -> {
                    final Future<Device> result = Future.future();
                    usernamePasswordAuthProvider.authenticate(credentials, result.completer());
                    return result;
                }).map(authenticatedDevice -> {
                    LOG.debug("successfully authenticated device [tenant-id: {}, auth-id: {}, device-id: {}]",
                            authenticatedDevice.getTenantId(), credentials.getAuthId(), authenticatedDevice.getDeviceId());
                    onAuthenticationSuccess(endpoint, authenticatedDevice);
                    return null;
                }).otherwise(t -> {
                    LOG.debug("cannot authenticate device [tenant-id: {}, auth-id: {}]",
                            credentials.getTenantId(), credentials.getAuthId(), t);
                    if (ServerErrorException.class.isInstance(t)) {
                        // one of the services we depend on might not be available (yet)
                        endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                    } else {
                        // validation of credentials has failed
                        endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
                    }
                    return null;
                });
            }
        }
    }

    private void onAuthenticationSuccess(final MqttEndpoint endpoint, final Device authenticatedDevice) {

        endpoint.closeHandler(v -> {
            close(endpoint);
            LOG.debug("connection to device [tenant-id: {}, device-id: {}] closed",
                    authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
            metrics.decrementMqttConnections(authenticatedDevice.getTenantId());
        });

        endpoint.publishHandler(message -> onPublishedMessage(new MqttContext(message, endpoint, authenticatedDevice)));
        endpoint.accept(false);
        metrics.incrementMqttConnections(authenticatedDevice.getTenantId());
    }

    /**
     * Uploads a message to Hono Messaging.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param resource The resource that the message should be uploaded to.
     * @param payload The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, resource or payload is {@code null}.
     * @throws IllegalArgumentException if the payload is empty.
     */
    public final Future<Void> uploadMessage(
            final MqttContext ctx,
            final ResourceIdentifier resource,
            final Buffer payload) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(resource);
        Objects.requireNonNull(payload);

        switch (EndpointType.fromString(resource.getEndpoint())) {
            case TELEMETRY:
                return uploadTelemetryMessage(
                        ctx,
                        resource.getTenantId(),
                        resource.getResourceId(),
                        payload);
            case EVENT:
                return uploadEventMessage(
                        ctx,
                        resource.getTenantId(),
                        resource.getResourceId(),
                        payload);
            default:
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unsupported endpoint"));
        }

    }

    /**
     * Uploads a telemetry message to Hono Messaging.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, tenant, device ID or payload is {@code null}.
     * @throws IllegalArgumentException if the payload is empty.
     */
    public final Future<Void> uploadTelemetryMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload) {

        return uploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                Objects.requireNonNull(payload),
                getTelemetrySender(tenant),
                TelemetryConstants.TELEMETRY_ENDPOINT);
    }

    /**
     * Uploads an event message to Hono Messaging.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, tenant, device ID or payload is {@code null}.
     * @throws IllegalArgumentException if the payload is empty.
     */
    public final Future<Void> uploadEventMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload) {

        return uploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                Objects.requireNonNull(payload),
                getEventSender(tenant),
                EventConstants.EVENT_ENDPOINT);
    }

    private Future<Void> uploadMessage(final MqttContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final Future<MessageSender> senderTracker, final String endpointName) {

        if (payload.length() == 0) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "payload must not be empty"));
        } else {

            final Future<JsonObject> tokenTracker = getRegistrationAssertion(tenant, deviceId, ctx.authenticatedDevice());
            final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(tenant);

            return CompositeFuture.all(tokenTracker, tenantConfigTracker, senderTracker).compose(ok -> {

                if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {

                    final MessageSender sender = senderTracker.result();
                    final Message downstreamMessage = newMessage(
                            String.format("%s/%s", endpointName, tenant),
                            deviceId,
                            ctx.message().topicName(),
                            ctx.contentType(),
                            payload,
                            tokenTracker.result());

                    customizeDownstreamMessage(downstreamMessage, ctx);

                    if (ctx.message().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                        return sender.sendAndWaitForOutcome(downstreamMessage);
                    } else {
                        return sender.send(downstreamMessage);
                    }
                } else {
                    // this adapter is not enabled for the tenant
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN));
                }

            }).compose(delivery -> {

                LOG.trace("successfully processed message [topic: {}, QoS: {}] for device [tenantId: {}, deviceId: {}]",
                        ctx.message().topicName(), ctx.message().qosLevel(), tenant, deviceId);
                metrics.incrementProcessedMqttMessages(endpointName, tenant);
                onMessageSent(ctx);
                // check that the remote MQTT client is still connected before sending PUBACK
                if (ctx.deviceEndpoint().isConnected() && ctx.message().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                    ctx.deviceEndpoint().publishAcknowledge(ctx.message().messageId());
                }
                return Future.<Void> succeededFuture();

            }).recover(t -> {

                if (ClientErrorException.class.isInstance(t)) {
                    ClientErrorException e = (ClientErrorException) t;
                    LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]: {} - {}",
                            tenant, deviceId, endpointName, e.getErrorCode(), e.getMessage());
                } else {
                    LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                            tenant, deviceId, endpointName, t);
                    metrics.incrementUndeliverableMqttMessages(endpointName, tenant);
                    onMessageUndeliverable(ctx);
                }
                return Future.failedFuture(t);
            });
        }
    }

    /**
     * Closes a connection to a client.
     * 
     * @param endpoint The connection to close.
     */
    protected final void close(final MqttEndpoint endpoint) {
        onClose(endpoint);
        if (endpoint.isConnected()) {
            LOG.debug("closing connection with client [client ID: {}]", endpoint.clientIdentifier());
            endpoint.close();
        } else {
            LOG.trace("client has already closed connection");
        }
    }

    /**
     * Invoked before the connection with a device is closed.
     * <p>
     * Subclasses should override this method in order to release any device
     * specific resources.
     * <p>
     * This default implementation does nothing.
     * 
     * @param endpoint The connection to be closed.
     */
    protected void onClose(final MqttEndpoint endpoint) {
    }

    /**
     * Extracts credentials from a client's MQTT <em>CONNECT</em> packet.
     * <p>
     * This default implementation returns {@link UsernamePasswordCredentials} created
     * from the <em>username</em> and <em>password</em> fields of the <em>CONNECT</em> packet.
     * <p>
     * Subclasses should override this method if the device uses credentials that do not
     * comply with the format expected by {@link UsernamePasswordCredentials}.
     * 
     * @param authInfo The authentication info provided by the device.
     * @return The credentials or {@code null} if the information provided by the device
     *         can not be processed.
     */
    protected DeviceCredentials getCredentials(final MqttAuth authInfo) {
        if (authInfo.userName() == null || authInfo.password() == null) {
            return null;
        } else {
            return UsernamePasswordCredentials.create(authInfo.userName(), authInfo.password(),
                    getConfig().isSingleTenant());
        }
    }

    /**
     * Processes an MQTT message that has been published by a device.
     * <p>
     * Subclasses should determine
     * <ul>
     * <li>the tenant and identifier of the device that has published the message</li>
     * <li>the payload to send downstream</li>
     * <li>the content type of the payload</li>
     * </ul>
     * and then invoke one of the <em>upload*</em> methods to send the message
     * downstream.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been successfully uploaded.
     *         Otherwise, the future will fail with a {@link ServiceInvocationException}.
     */
    protected abstract Future<Void> onPublishedMessage(MqttContext ctx);

    /**
     * Invoked before the message is sent to the downstream peer.
     * <p>
     * This default implementation does nothing.
     * <p>
     * Subclasses may override this method in order to customize the message
     * before it is sent, e.g. adding custom properties.
     * 
     * @param downstreamMessage The message that will be sent downstream.
     * @param ctx The context in which the MQTT message has been published.
     */
    protected void customizeDownstreamMessage(final Message downstreamMessage, final MqttContext ctx) {
    }

    /**
     * Invoked when a message has been forwarded downstream successfully.
     * <p>
     * This default implementation does nothing.
     * <p>
     * Subclasses should override this method in order to e.g. update metrics counters.
     * 
     * @param ctx The context in which the MQTT message has been published.
     */
    protected void onMessageSent(final MqttContext ctx) {
    }

    /**
     * Invoked when a message could not be forwarded downstream.
     * <p>
     * This method will only be invoked if the failure to forward the
     * message has not been caused by the device that published the message.
     * In particular, this method will not be invoked for messages that cannot
     * be authorized or that are published to an unsupported/unrecognized topic.
     * Such messages will be silently discarded.
     * <p>
     * This default implementation does nothing.
     * <p>
     * Subclasses should override this method in order to e.g. update metrics counters.
     * 
     * @param ctx The context in which the MQTT message has been published.
     */
    protected void onMessageUndeliverable(final MqttContext ctx) {
    }
}
