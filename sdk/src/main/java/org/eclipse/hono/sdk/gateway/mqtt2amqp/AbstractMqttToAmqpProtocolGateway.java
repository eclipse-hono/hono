/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.CommandResponseMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.DownstreamMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.EventMessage;
import org.eclipse.hono.sdk.gateway.mqtt2amqp.downstream.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import io.vertx.proton.ProtonDelivery;

/**
 * Base class for implementing a protocol gateway that connects to a Hono AMQP adapter and provides a custom MQTT server
 * for devices to connect to.
 * <p>
 * This implementation does not support MQTT QoS 2; when a device requests QoS 2 in its <em>SUBSCRIBE</em> message, only
 * QoS 1 is granted.
 */
public abstract class AbstractMqttToAmqpProtocolGateway extends AbstractVerticle {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final ClientConfigProperties amqpClientConfig;
    private final MqttGatewayServerConfig mqttServerConfig;
    private final Map<String, AmqpAdapterClientFactory> clientFactoryPerTenant = new HashMap<>();
    private final int commandAckTimeout;

    private MqttServer server;

    /**
     * Creates an instance.
     * <p>
     * The AMQP client configuration needs to contain the properties that are required to connect to the Hono AMQP
     * adapter. If it contains a username and password, those are used to authenticate the amqp client with. Otherwise
     * {@link #provideGatewayCredentials(String)} needs to be overridden in order to dynamically resolve credentials for
     * the tenant of a device request ("multi-tenant mode").
     * <p>
     * The command acknowledgement timeout determines how long the protocol gateway waits for acknowledgement of a
     * command published with QoS 1. If there is no acknowledgement within this time limit, then the command is settled
     * with the <em>released</em> outcome.
     * 
     * @param amqpClientConfig The AMQP client configuration.
     * @param mqttServerConfig The MQTT server configuration.
     * @param commandAckTimeout The timeout in milliseconds.
     * @throws NullPointerException if amqpClientConfig or mqttServerConfig is {@code null}.
     * @throws IllegalArgumentException if the timeout is negative.
     * @see ClientConfigProperties#setTlsEnabled(boolean)
     * @see ClientConfigProperties#setTrustStorePath(String)
     */
    public AbstractMqttToAmqpProtocolGateway(final ClientConfigProperties amqpClientConfig,
            final MqttGatewayServerConfig mqttServerConfig, final int commandAckTimeout) {
        Objects.requireNonNull(amqpClientConfig);
        Objects.requireNonNull(mqttServerConfig);

        if (commandAckTimeout < 0) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        this.amqpClientConfig = amqpClientConfig;
        this.mqttServerConfig = mqttServerConfig;
        this.commandAckTimeout = commandAckTimeout;
    }

    /**
     * Authenticates a device that has provided the specified credentials in its CONNECT packet. This method is not
     * invoked if the client certificate-based authentication was already successful.
     * <p>
     * Implementations must return a (succeeded) future with the <em>authenticated</em> device if authentication was
     * successful or a failed future otherwise. {@code Null} must never be returned.
     * 
     * @param username The username.
     * @param password The password.
     * @param clientId The client id.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<Device> authenticateDevice(String username, String password, String clientId);

    /**
     * Validates the topic filter that a device sent in its subscription message. Additional information is provided
     * with the parameters that can be used for validation.
     *
     * @param topicFilter the topic filter provided by the device.
     * @param tenantId the tenant id of the authenticated device.
     * @param deviceId the device id of the authenticated device.
     * @param clientId the MQTT client id of the device.
     * @throws IllegalArgumentException if the given filter is invalid.
     * @return {@code true} if the topic filter is valid.
     */
    protected abstract boolean isTopicFilterValid(String topicFilter, String tenantId, String deviceId,
            String clientId);

    /**
     * This method is called when a message has been published by a device via MQTT. It prepares the data to be uploaded
     * to Hono.
     * <p>
     * Subclasses determine the message type by returning one of the subclasses of {@link DownstreamMessage}.
     *
     * @param ctx The context in which the MQTT message has been published.
     * @return A future indicating the outcome of the operation. If an error occurs, a failed future is returned, but
     *         never {@code null}. If the failure has been caused by the device that published the message, the (failed)
     *         future contains a {@link ClientErrorException}.
     */
    protected abstract Future<DownstreamMessage> onPublishedMessage(MqttDownstreamContext ctx);

    /**
     * This method is called when a command message that has been received from Hono. It prepares the data to be
     * published to the device via MQTT.
     * <p>
     * If the implementation throws an exception, the AMQP command message will be released.
     *
     * @param ctx The context in which the command has been received.
     * @return The command to be published to the device - must not be {@code null}.
     */
    protected abstract Command onCommandReceived(MqttCommandContext ctx);

    /**
     * Gets credentials for authentication against the AMQP adapter to which this protocol gateway connects. If username
     * and password are specified in the AMQP client configuration of this gateway, then these are used and this method
     * is not invoked.
     * <p>
     * Subclasses should overwrite this method to resolve the credentials for the given client.
     * <p>
     * This default implementation returns a failed future because it is only called if no configuration with username
     * and password is provided <em>and</em> it is not overwritten by an alternative implementation.
     * <p>
     * The method must never return {@code null}.
     *
     * @param tenantId The tenant for which a connection is required (from the device authentication).
     * @return A future indicating the outcome of the operation.
     * @see ClientConfigProperties#setUsername(String)
     * @see ClientConfigProperties#setPassword(String)
     */
    protected Future<Credentials> provideGatewayCredentials(final String tenantId) {
        return Future.failedFuture("credentials of the protocol gateway not found in the provided configuration.");
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
    protected void onMessageSent(final MqttDownstreamContext ctx) {
    }

    /**
     * Invoked when a message could not be forwarded downstream.
     * <p>
     * This method will only be invoked if the failure to forward the message has not been caused by the device that
     * published the message.
     * <p>
     * This default implementation does nothing.
     * <p>
     * Subclasses should override this method in order to e.g. update metrics counters.
     *
     * @param ctx The context in which the MQTT message has been published.
     */
    protected void onMessageUndeliverable(final MqttDownstreamContext ctx) {
    }

    /**
     * Invoked when a message has been sent to the device successfully.
     * <p>
     * This default implementation does nothing.
     * <p>
     * Subclasses should override this method in order to e.g. update metrics counters.
     *
     * @param command The received command message.
     * @param subscription The corresponding subscription.
     */
    protected void onCommandPublished(final Message command, final CommandSubscription subscription) {
    }

    /**
     * Invoked before the connection with a device is closed.
     * <p>
     * Subclasses should override this method in order to release any device specific resources.
     * <p>
     * This default implementation does nothing.
     *
     * @param endpoint The connection to be closed.
     */
    protected void onDeviceConnectionClose(final MqttEndpoint endpoint) {
    }

    /**
     * Authenticates a device using its TLS client certificate. This method is only invoked if the device establishes a
     * connection with TLS and presents a client certificate.
     * <p>
     * If authentication fails, the username/password based authentication
     * ({@link #authenticateDevice(String, String, String)}) will be invoked afterwards.
     * <p>
     * To authenticate devices using client certificates, subclasses must either (a) override methods
     * {@link #getTrustAnchors(List)} and {@link #authenticateClientCertificate(X509Certificate)} if only X.509
     * certificates are used, or (b) override this method if other certificate types are to be used.
     * <p>
     * This default implementation only validates X.509 certificates. It performs the following steps if the previous
     * steps were successful:
     * <ol>
     * <li>invoke {@link #getTrustAnchors(List)}</li>
     * <li>validate the given certificate chain against the trust anchors</li>
     * <li>invoke {@link #authenticateClientCertificate(X509Certificate)}</li>
     * </ol>
     * If one of the steps fails (which they do, unless the above methods are overridden in a subclass), this method
     * returns a failed future, which causes username/password based authentication to be invoked.
     *
     * @param path The certificate path from the TLS session with the client certificate first - not {@code null}.
     * @return A future indicating the outcome of the operation. The future will succeed with the device data belonging
     *         to the authentication or it will fail with a failure message indicating the cause of the failure.
     *         {@code Null} must never be returned.
     *
     * @see #getTrustAnchors(List)
     * @see #authenticateClientCertificate(X509Certificate)
     */
    protected Future<Device> authenticateDeviceCertificate(final Certificate[] path) {

        final List<X509Certificate> certificates = Arrays.stream(path)
                .filter(cert -> cert instanceof X509Certificate)
                .map(cert -> ((X509Certificate) cert))
                .collect(Collectors.toList());

        final X509CertificateValidator validator = new X509CertificateValidator();
        return getTrustAnchors(certificates)
                .compose(trustAnchors -> validator.validate(certificates, trustAnchors))
                .compose(ok -> authenticateClientCertificate(certificates.get(0)));
    }

    /**
     * Returns the trust anchors to be used to validate the X.509 client certificate of a device.
     * <p>
     * Subclasses should override this method to provide trust anchors against which the device certificate can be
     * validated.
     * <p>
     * To authenticate devices using client certificates, subclasses must either (a) override this method and
     * {@link #authenticateClientCertificate(X509Certificate)} if only X.509 certificates are used, or (b) override
     * method {@link #authenticateDeviceCertificate(Certificate[])} if other certificate types are to be used.
     * <p>
     * This default implementation always returns a failed future because there are no default trust anchors.
     *
     * @param certificates The certificate chain to be validated, which - depending on the actual implementation - may
     *            be necessary to select the relevant trust anchors.
     * @return A future indicating the outcome of the operation. The future will succeed with the trust anchors to be
     *         used for the validation or it will fail with a failure message indicating the cause of the failure.
     *         {@code Null} must never be returned.
     *
     * @see #authenticateDeviceCertificate(Certificate[])
     * @see #authenticateClientCertificate(X509Certificate)
     */
    protected Future<Set<TrustAnchor>> getTrustAnchors(final List<X509Certificate> certificates) {
        return Future.failedFuture("Client certificate can not be validated: no trust anchors provided");
    }

    /**
     * Authenticates the X.509 client certificate and returns the authenticated device.
     * <p>
     * Subclasses should override this method to check if the given certificate identifies is a known and authorized
     * device and to retrieve the tenant id and the device id for it.
     * <p>
     * To authenticate devices using client certificates, subclasses must either (a) override this method and
     * {@link #getTrustAnchors(List)} if only X.509 certificates are used, or (b) override method
     * {@link #authenticateDeviceCertificate(Certificate[])} if other certificate types are to be used.
     * <p>
     * This default implementation always returns a failed future.
     *
     * @param deviceCertificate The already validated client certificate.
     * @return A future indicating the outcome of the operation. The future will succeed with the authenticated device
     *         or it will fail with a failure message indicating the cause of the failure. {@code Null} must never be
     *         returned.
     *
     * @see #authenticateDeviceCertificate(Certificate[])
     * @see #getTrustAnchors(List)
     */
    protected Future<Device> authenticateClientCertificate(final X509Certificate deviceCertificate) {
        return Future.failedFuture("Cannot establish device identity");
    }

    /**
     * Invoked when a device sends its <em>CONNECT</em> packet.
     * <p>
     * Authenticates the device, connects the gateway to Hono's AMQP adapter and registers handlers for processing
     * messages published by the client.
     *
     * @param endpoint The MQTT endpoint representing the client.
     * @throws NullPointerException if the endpoint is {@code null}.
     */
    final void handleEndpointConnection(final MqttEndpoint endpoint) {

        Objects.requireNonNull(endpoint);

        log.debug("connection request from client [client-id: {}]", endpoint.clientIdentifier());

        if (!endpoint.isCleanSession()) {
            log.debug("ignoring client's intent to resume existing session");
        }
        if (endpoint.will() != null) {
            log.debug("ignoring client's last will");
        }

        final Future<Device> authAttempt = tryAuthenticationWithClientCertificate(endpoint)
                .recover(ex -> authenticateWithUsernameAndPassword(endpoint))
                .compose(authenticateDevice -> (authenticateDevice == null)
                        ? Future.failedFuture("device authentication failed")
                        : Future.succeededFuture(authenticateDevice));

        authAttempt
                .compose(this::connectGatewayToAmqpAdapter)
                .setHandler(result -> {
                    if (result.succeeded()) {
                        registerHandlers(endpoint, authAttempt.result());
                        log.debug("connection accepted from {}", authAttempt.result().toString());
                        endpoint.accept(false); // we do not maintain session state
                    } else {
                        final MqttConnectReturnCode returnCode;
                        if (authAttempt.failed()) {
                            log.debug("connection request from client [clientId: {}] rejected, authentication failed",
                                    endpoint.clientIdentifier(), authAttempt.cause());
                            returnCode = MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
                        } else {
                            log.debug(
                                    "connection request from client [clientId: {}] rejected, connection to backend failed",
                                    endpoint.clientIdentifier(), result.cause());
                            returnCode = MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
                        }

                        endpoint.reject(returnCode);
                    }
                });
    }

    private Future<Device> tryAuthenticationWithClientCertificate(final MqttEndpoint endpoint) {
        if (endpoint.isSsl()) {
            try {
                final Certificate[] path = endpoint.sslSession().getPeerCertificates();
                if (path != null && path.length > 0) {
                    final Future<Device> authAttempt = authenticateDeviceCertificate(path);
                    log.debug("authentication with client certificate: {}.",
                            (authAttempt.succeeded()) ? "succeeded" : "failed");
                    return authAttempt;
                }
            } catch (RuntimeException | SSLPeerUnverifiedException e) {
                log.debug("could not retrieve client certificate from device endpoint: {}", e.getMessage());
            }
        }
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED));
    }

    private Future<Device> authenticateWithUsernameAndPassword(final MqttEndpoint endpoint) {
        final MqttAuth auth = endpoint.auth();
        if (auth == null || auth.getUsername() == null || auth.getPassword() == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device did not provide credentials in CONNECT packet"));
        } else {
            final Future<Device> authenticatedDevice = authenticateDevice(auth.getUsername(), auth.getPassword(),
                    endpoint.clientIdentifier());
            if (authenticatedDevice == null) {
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
            } else {
                log.debug("authentication with username/password {}.",
                        (authenticatedDevice.succeeded()) ? "succeeded" : "failed");
                return authenticatedDevice;
            }
        }
    }

    private Future<Void> connectGatewayToAmqpAdapter(final Device authenticatedDevice) {

        final String tenantId = authenticatedDevice.getTenantId();

        if (amqpClientConfig.getUsername() != null && amqpClientConfig.getPassword() != null) {
            return connectGatewayToAmqpAdapter(tenantId, amqpClientConfig);
        } else {
            return provideGatewayCredentials(tenantId)
                    .compose(credentials -> {
                        final ClientConfigProperties tenantConfig = new ClientConfigProperties(amqpClientConfig);
                        tenantConfig.setUsername(credentials.getUsername());
                        tenantConfig.setPassword(credentials.getPassword());

                        return connectGatewayToAmqpAdapter(tenantId, tenantConfig);
                    });
        }
    }

    private Future<Void> connectGatewayToAmqpAdapter(final String tenantId, final ClientConfigProperties clientConfig) {

        final AmqpAdapterClientFactory factory = clientFactoryPerTenant
                .computeIfAbsent(tenantId, key -> createTenantClientFactory(key, clientConfig));

        return factory.connect() // returns successfully if already connected
                .map(con -> null);
    }

    /**
     * Returns a new {@link AmqpAdapterClientFactory} with a new AMQP connection for the given tenant.
     * <p>
     * This method is only visible for testing purposes.
     * 
     * @param tenantId The tenant to be connected.
     * @param clientConfig The client properties to use for the connection.
     * @return The factory. Note that the underlying AMQP connection will not be established until
     *         {@link AmqpAdapterClientFactory#connect()} is invoked.
     */
    AmqpAdapterClientFactory createTenantClientFactory(final String tenantId,
            final ClientConfigProperties clientConfig) {
        final HonoConnection connection = HonoConnection.newConnection(vertx, clientConfig);
        return AmqpAdapterClientFactory.create(connection, tenantId);
    }

    private void registerHandlers(final MqttEndpoint endpoint, final Device authenticatedDevice) {

        endpoint.publishHandler(
                message -> handlePublishedMessage(
                        MqttDownstreamContext.fromPublishPacket(message, endpoint, authenticatedDevice)));

        final CommandHandler cmdHandler = createCommandHandler(authenticatedDevice, vertx, commandAckTimeout);
        endpoint.publishAcknowledgeHandler(msgId -> cmdHandler.handlePubAck(msgId, this::onCommandPublished));
        endpoint.subscribeHandler(msg -> onSubscribe(endpoint, authenticatedDevice, msg, cmdHandler));
        endpoint.unsubscribeHandler(msg -> onUnsubscribe(endpoint, authenticatedDevice, msg, cmdHandler));

        endpoint.closeHandler(v -> {
            onDeviceConnectionClose(endpoint);
            cmdHandler.removeAllSubscriptions();
        });

    }

    /**
     * Invoked when a device connects, after authentication.
     * <p>
     * This method is only visible for testing purposes.
     * 
     * @param authenticatedDevice The device.
     * @param vertx The vert.x instance
     * @param commandAckTimeout The command acknowledgement timeout in milliseconds.
     * @return The command handler for the given device.
     */
    CommandHandler createCommandHandler(final Device authenticatedDevice, final Vertx vertx,
            final int commandAckTimeout) {
        return new CommandHandler(vertx, commandAckTimeout, authenticatedDevice);
    }

    /**
     * Invoked when a device publishes a message.
     * 
     * Invokes {@link #onPublishedMessage(MqttDownstreamContext)}, uploads the message to Hono's AMQP adapter.
     * Afterwards it invokes {@link #onMessageSent(MqttDownstreamContext)} if the message has been forwarded
     * successfully or if a the message could not be delivered, {@link #onMessageUndeliverable(MqttDownstreamContext)}.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @throws NullPointerException if the context is {@code null}.
     */
    private void handlePublishedMessage(final MqttDownstreamContext ctx) {

        Objects.requireNonNull(ctx);

        onPublishedMessage(ctx)
                .compose(downstreamMessage -> uploadMessage(downstreamMessage, ctx))
                .setHandler(processing -> {
                    if (processing.succeeded()) {
                        onUploadSuccess(ctx);
                        onMessageSent(ctx);
                    } else {
                        onUploadFailure(ctx, processing.cause());
                    }
                });
    }

    private Future<ProtonDelivery> uploadMessage(final DownstreamMessage downstreamMessage,
            final MqttDownstreamContext ctx) {

        final String tenantId = ctx.authenticatedDevice().getTenantId();
        final String deviceId = ctx.authenticatedDevice().getDeviceId();
        final Map<String, Object> properties = downstreamMessage.getApplicationProperties();
        final byte[] payload = downstreamMessage.getPayload();
        final String contentType = downstreamMessage.getContentType();

        if (downstreamMessage instanceof TelemetryMessage) {

            final TelemetryMessage telemetryMessage = (TelemetryMessage) downstreamMessage;
            return sendTelemetry(tenantId, deviceId, properties, payload, contentType,
                    telemetryMessage.shouldWaitForOutcome());

        } else if (downstreamMessage instanceof EventMessage) {

            return sendEvent(tenantId, deviceId, properties, payload, contentType);

        } else if (downstreamMessage instanceof CommandResponseMessage) {

            final CommandResponseMessage response = (CommandResponseMessage) downstreamMessage;
            return sendCommandResponse(tenantId, deviceId, response.getTargetAddress(tenantId, deviceId),
                    response.getCorrelationId(), response.getStatus(), payload, contentType, properties);

        } else {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("uploading message failed [topic: %s]. Unknown message type [%s]", ctx.topic(),
                            downstreamMessage.getClass().getSimpleName())));
        }
    }

    private void onUploadSuccess(final MqttDownstreamContext ctx) {
        log.debug("successfully processed message [topic: {}, QoS: {}] from device [tenantId: {}, deviceId: {}]",
                ctx.topic(), ctx.qosLevel(), ctx.authenticatedDevice().getTenantId(),
                ctx.authenticatedDevice().getDeviceId());
        // check that the remote MQTT client is still connected before sending PUBACK
        if (MqttQoS.AT_LEAST_ONCE.equals(ctx.qosLevel()) && ctx.deviceEndpoint().isConnected()) {
            log.debug("sending PUBACK");
            ctx.acknowledge();
        }
    }

    private void onUploadFailure(final MqttDownstreamContext ctx, final Throwable cause) {

        final int statusCode = ServiceInvocationException.extractStatusCode(cause);

        if (statusCode < 500) {
            log.debug("Publish message [topic: {}] from {} failed with client error: ", ctx.topic(),
                    ctx.authenticatedDevice(), cause);
        } else {
            log.info("Publish message [topic: {}] from {} failed: ", ctx.topic(), ctx.authenticatedDevice(), cause);
            onMessageUndeliverable(ctx);
        }

        if (ctx.deviceEndpoint().isConnected()) {
            log.info("closing connection to device {}", ctx.authenticatedDevice().toString());
            ctx.deviceEndpoint().close();
        }
    }

    private Future<ProtonDelivery> sendTelemetry(final String tenantId, final String deviceId,
            final Map<String, ?> properties, final byte[] payload, final String contentType,
            final boolean waitForOutcome) {

        return clientFactoryPerTenant.get(tenantId).getOrCreateTelemetrySender()
                .compose(sender -> {
                    if (waitForOutcome) {
                        log.trace(
                                "sending telemetry message and wait for outcome [tenantId: {}, deviceId: {}, contentType: {}, properties: {}]",
                                tenantId, deviceId, contentType, properties);
                        return sender.sendAndWaitForOutcome(deviceId, payload, contentType, properties);
                    } else {
                        log.trace(
                                "sending telemetry message [tenantId: {}, deviceId: {}, contentType: {}, properties: {}]",
                                tenantId, deviceId, contentType, properties);
                        return sender.send(deviceId, payload, contentType, properties);
                    }
                });
    }

    private Future<ProtonDelivery> sendEvent(final String tenantId, final String deviceId,
            final Map<String, ?> properties, final byte[] payload, final String contentType) {

        log.trace("sending event message [tenantId: {}, deviceId: {}, contentType: {}, properties: {}]",
                tenantId, deviceId, contentType, properties);

        return clientFactoryPerTenant.get(tenantId).getOrCreateEventSender()
                .compose(sender -> sender.send(deviceId, payload, contentType, properties));
    }

    private Future<ProtonDelivery> sendCommandResponse(final String tenantId, final String deviceId,
            final String targetAddress, final String correlationId, final int status, final byte[] payload,
            final String contentType, final Map<String, ?> properties) {

        log.trace(
                "sending command response [tenantId: {}, deviceId: {}, targetAddress: {}, correlationId: {}, status: {}, contentType: {}, properties: {}]",
                tenantId, deviceId, targetAddress, correlationId, status, contentType, properties);

        return clientFactoryPerTenant.get(tenantId).getOrCreateCommandResponseSender()
                .compose(sender -> sender.sendCommandResponse(deviceId, targetAddress, correlationId, status, payload,
                        contentType, properties));
    }

    /**
     * Invoked when a device sends an MQTT <em>SUBSCRIBE</em> packet.
     * 
     * It invokes {@link #isTopicFilterValid(String, String, String, String)} for each topic filter in the subscribe
     * packet. If there is a valid topic filter and no command consumer already exists for this device, this method
     * opens a device-specific command consumer for receiving commands from applications for the device.
     *
     * @param endpoint The endpoint representing the connection to the device.
     * @param authenticatedDevice The authenticated identity of the device.
     * @param subscribeMsg The subscribe request received from the device.
     * @param cmdHandler The CommandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    private void onSubscribe(final MqttEndpoint endpoint, final Device authenticatedDevice,
            final MqttSubscribeMessage subscribeMsg, final CommandHandler cmdHandler) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(authenticatedDevice);
        Objects.requireNonNull(subscribeMsg);
        Objects.requireNonNull(cmdHandler);

        @SuppressWarnings("rawtypes")
        final List<Future> subscriptionOutcome = new ArrayList<>(subscribeMsg.topicSubscriptions().size());

        subscribeMsg.topicSubscriptions().forEach(subscription -> {

            final Future<MqttQoS> result;

            if (isTopicFilterValid(subscription.topicName(), authenticatedDevice.getTenantId(),
                    authenticatedDevice.getDeviceId(), endpoint.clientIdentifier())) {

                // we do not support subscribing to commands using QoS 2
                final MqttQoS grantedQos = MqttQoS.EXACTLY_ONCE.equals(subscription.qualityOfService())
                        ? MqttQoS.AT_LEAST_ONCE
                        : subscription.qualityOfService();

                final CommandSubscription cmdSub = new CommandSubscription(subscription.topicName(), grantedQos,
                        endpoint.clientIdentifier());

                result = cmdHandler.addSubscription(cmdSub, () -> createCommandConsumer(endpoint, cmdHandler));
            } else {
                log.debug("cannot create subscription [filter: {}, requested QoS: {}]: unsupported topic filter",
                        subscription.topicName(), subscription.qualityOfService());
                result = Future.succeededFuture(MqttQoS.FAILURE);
            }
            subscriptionOutcome.add(result);
        });

        // wait for all futures to complete before sending SUBACK
        CompositeFuture.join(subscriptionOutcome).setHandler(v -> {

            // return a status code for each topic filter contained in the SUBSCRIBE packet
            final List<MqttQoS> grantedQosLevels = subscriptionOutcome.stream()
                    .map(Future::result)
                    .map(result -> (MqttQoS) result)
                    .collect(Collectors.toList());

            if (endpoint.isConnected()) {
                endpoint.subscribeAcknowledge(subscribeMsg.messageId(), grantedQosLevels);
            }
        });
    }

    /**
     * Invoked when a device sends an MQTT <em>UNSUBSCRIBE</em> packet.
     *
     * @param endpoint The endpoint representing the connection to the device.
     * @param authenticatedDevice The authenticated identity of the device.
     * @param unsubscribeMsg The unsubscribe request received from the device.
     * @param cmdHandler The CommandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    private void onUnsubscribe(final MqttEndpoint endpoint, final Device authenticatedDevice,
            final MqttUnsubscribeMessage unsubscribeMsg, final CommandHandler cmdHandler) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(authenticatedDevice);
        Objects.requireNonNull(unsubscribeMsg);
        Objects.requireNonNull(cmdHandler);

        unsubscribeMsg.topics().forEach(topic -> {
            if (!isTopicFilterValid(topic, authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId(),
                    endpoint.clientIdentifier())) {
                log.debug("ignoring unsubscribe request for unsupported topic filter [{}]", topic);
            } else {
                log.debug("unsubscribing device [tenant-id: {}, device-id: {}] from topic [{}]",
                        authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId(), topic);
                cmdHandler.removeSubscription(topic);
            }
        });
        if (endpoint.isConnected()) {
            endpoint.unsubscribeAcknowledge(unsubscribeMsg.messageId());
        }
    }

    private Future<MessageConsumer> createCommandConsumer(final MqttEndpoint endpoint,
            final CommandHandler cmdHandler) {
        return clientFactoryPerTenant.get(cmdHandler.getAuthenticatedDevice().getTenantId())
                .createDeviceSpecificCommandConsumer(cmdHandler.getAuthenticatedDevice().getDeviceId(),
                        cmd -> handleCommand(endpoint, cmd, cmdHandler));
    }

    private void handleCommand(final MqttEndpoint endpoint, final Message message, final CommandHandler cmdHandler) {

        if (message.getReplyTo() != null) {
            log.debug("Received request/response command [subject: {}, correlationID: {}, messageID: {}, reply-to: {}]",
                    message.getSubject(), message.getCorrelationId(), message.getMessageId(), message.getReplyTo());
        } else {
            log.debug("Received one-way command [subject: {}]", message.getSubject());
        }

        final MqttCommandContext ctx = MqttCommandContext.fromAmqpMessage(message, cmdHandler.getAuthenticatedDevice());
        final Command command = onCommandReceived(ctx);

        if (command == null) {
            throw new IllegalStateException("onCommandReceived returned null");
        }

        final CommandSubscription subscription = cmdHandler.getSubscriptions().get(command.getTopicFilter());
        if (subscription == null) {
            throw new IllegalStateException(
                    String.format("No subscription found for topic filter %s. Discarding message from %s",
                            command.getTopicFilter(), cmdHandler.getAuthenticatedDevice().toString()));
        }

        log.debug("Publishing command on topic [{}] to device {} [MQTT client-id: {}, QoS: {}]", command.getTopic(),
                cmdHandler.getAuthenticatedDevice().toString(), endpoint.clientIdentifier(), subscription.getQos());

        endpoint.publish(command.getTopic(), command.getPayload(), subscription.getQos(), false, false,
                ar -> {
                    // Vert.x only calls this handler after it successfully published the message, otherwise it throws
                    // an exception which causes the AMQP Command Consumer not to be settled (and the backend
                    // application to receive an error)
                    if (MqttQoS.AT_LEAST_ONCE.equals(subscription.getQos())) {
                        cmdHandler.addToWaitingForAcknowledgement(ar.result(), subscription,
                                message);
                    } else {
                        onCommandPublished(message, subscription);
                    }
                });

    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates and starts the MQTT server and invokes {@link #afterStartup(Promise)} afterwards.
     */
    @Override
    public final void start(final Promise<Void> startPromise) {

        if (mqttServerConfig.getKeyCertOptions() == null
                && mqttServerConfig.getPort() == MqttServerOptions.DEFAULT_TLS_PORT) {
            log.error("configuration must have key & certificate if port 8883 is configured");
            startPromise.fail("TLS configuration invalid");
        }

        MqttServer.create(vertx, getMqttServerOptions())
                .endpointHandler(this::handleEndpointConnection)
                .listen(asyncResult -> {
                    if (asyncResult.succeeded()) {
                        final MqttServer startedServer = asyncResult.result();
                        log.info("MQTT server running on {}:{}", mqttServerConfig.getBindAddress(),
                                startedServer.actualPort());
                        server = startedServer;
                        afterStartup(startPromise);
                    } else {
                        log.error("error while starting up MQTT server", asyncResult.cause());
                        startPromise.fail(asyncResult.cause());
                    }
                });
    }

    /**
     * Returns the options for the MQTT server.
     * <p>
     * This method is only visible for testing purposes.
     * 
     * @return The options configured with the values of the {@link MqttGatewayServerConfig}.
     */
    MqttServerOptions getMqttServerOptions() {
        final MqttServerOptions options = new MqttServerOptions()
                .setHost(mqttServerConfig.getBindAddress())
                .setPort(mqttServerConfig.getPort());

        addTlsKeyCertOptions(options);
        addTlsTrustOptions(options);
        return options;
    }

    private void addTlsKeyCertOptions(final NetServerOptions serverOptions) {

        final KeyCertOptions keyCertOptions = mqttServerConfig.getKeyCertOptions();

        if (keyCertOptions != null) {
            serverOptions.setSsl(true).setKeyCertOptions(keyCertOptions);
            log.info("Enabling TLS");

            final LinkedHashSet<String> enabledProtocols = new LinkedHashSet<>(mqttServerConfig.getSecureProtocols());
            serverOptions.setEnabledSecureTransportProtocols(enabledProtocols);
            log.info("Enabling secure protocols [{}]", enabledProtocols);

            serverOptions.setSni(mqttServerConfig.isSni());
            log.info("Supporting TLS ServerNameIndication: {}", mqttServerConfig.isSni());
        }
    }

    private void addTlsTrustOptions(final NetServerOptions serverOptions) {

        if (serverOptions.isSsl()) {

            final TrustOptions trustOptions = mqttServerConfig.getTrustOptions();
            if (trustOptions != null) {
                serverOptions.setTrustOptions(trustOptions).setClientAuth(ClientAuth.REQUEST);
                log.info("Enabling client authentication using certificates [{}]", trustOptions.getClass().getName());
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Invokes {@link #beforeShutdown(Promise)} and stops the MQTT server.
     */
    @Override
    public final void stop(final Promise<Void> stopPromise) {

        final Promise<Void> stopTracker = Promise.promise();
        beforeShutdown(stopTracker);
        stopTracker.future().onComplete(v -> {
            if (server != null) {
                server.close(stopPromise);
            } else {
                stopPromise.complete();
            }
        });

    }

    /**
     * Invoked directly before the gateway is shut down.
     * <p>
     * This default implementation always completes the promise.
     * <p>
     * Subclasses should override this method to perform any work required before shutting down this protocol gateway.
     *
     * @param stopPromise The promise to complete once all work is done and shut down should commence.
     */
    protected void beforeShutdown(final Promise<Void> stopPromise) {
        stopPromise.complete();
    }

    /**
     * Invoked after the gateway has started up.
     * <p>
     * This default implementation simply completes the promise.
     * <p>
     * Subclasses should override this method to perform any work required on start-up of this protocol gateway.
     *
     * @param startPromise The promise to complete once start up is complete.
     */
    protected void afterStartup(final Promise<Void> startPromise) {
        startPromise.complete();
    }

}
