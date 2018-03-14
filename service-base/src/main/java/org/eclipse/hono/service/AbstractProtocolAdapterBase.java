/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */
package org.eclipse.hono.service;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import io.vertx.core.Handler;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.AbstractConfig;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.TenantApiTrustOptions;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;

/**
 * A base class for implementing protocol adapters.
 * <p>
 * Provides connections to device registration and telemetry and event service endpoints.
 * 
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractProtocolAdapterBase<T extends ProtocolAdapterProperties> extends AbstractServiceBase<T> {

    /**
     * The <em>application/octet-stream</em> content type.
     */
    protected static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";

    private HonoClient messagingClient;
    private HonoClient registrationClient;
    private HonoClient tenantClient;
    private HonoClient credentialsServiceClient;

    /**
     * Sets the configuration by means of Spring dependency injection.
     * <p>
     * Most protocol adapters will support a single transport protocol to communicate with
     * devices only. For those adapters there will only be a single bean instance available
     * in the application context of type <em>T</em>.
     */
    @Autowired
    @Override
    public void setConfig(final T configuration) {
        setSpecificConfig(configuration);
    }

    /**
     * Sets the client to use for connecting to the Tenant service.
     *
     * @param tenantClient The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Autowired
    public final void setTenantServiceClient(final HonoClient tenantClient) {
        this.tenantClient = Objects.requireNonNull(tenantClient);
    }

    /**
     * Gets the client used for connecting to the Tenant service.
     *
     * @return The client.
     */
    public final HonoClient getTenantServiceClient() {
        return tenantClient;
    }

    /**
     * Gets a client for interacting with the Tenant service.
     *
     * @return The client.
     */
    protected final Future<TenantClient> getTenantClient() {
        return getTenantServiceClient().getOrCreateTenantClient();
    }

    /**
     * Sets the client to use for connecting to the Hono Messaging component.
     * 
     * @param honoClient The client.
     * @throws NullPointerException if hono client is {@code null}.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Autowired
    public final void setHonoMessagingClient(final HonoClient honoClient) {
        this.messagingClient = Objects.requireNonNull(honoClient);
    }

    /**
     * Gets the client used for connecting to the Hono Messaging component.
     * 
     * @return The client.
     */
    public final HonoClient getHonoMessagingClient() {
        return messagingClient;
    }

    /**
     * Sets the client to use for connecting to the Device Registration service.
     * 
     * @param registrationServiceClient The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Autowired
    public final void setRegistrationServiceClient(final HonoClient registrationServiceClient) {
        this.registrationClient = Objects.requireNonNull(registrationServiceClient);
    }

    /**
     * Gets the client used for connecting to the Device Registration service.
     * 
     * @return The client.
     */
    public final HonoClient getRegistrationServiceClient() {
        return registrationClient;
    }

    /**
     * Sets the client to use for connecting to the Credentials service.
     * 
     * @param credentialsServiceClient The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Autowired
    public final void setCredentialsServiceClient(final HonoClient credentialsServiceClient) {
        this.credentialsServiceClient = Objects.requireNonNull(credentialsServiceClient);
    }

    /**
     * Gets the client used for connecting to the Credentials service.
     * 
     * @return The client.
     */
    public final HonoClient getCredentialsServiceClient() {
        return credentialsServiceClient;
    }

    /**
     * Gets this adapter's type name.
     * <p>
     * The name should be unique among all protocol adapters that are part of a Hono installation.
     * There is no specific scheme to follow but it is recommended to include the adapter's origin
     * and the protocol that the adapter supports in the name and to use lower case letters only.
     * <p>
     * Based on this recommendation, Hono's standard HTTP adapter for instance might report
     * <em>hono-http</em> as its type name.
     * <p>
     * The name returned by this method is added to a downstream message by the
     * {@link #addProperties(Message, JsonObject)} method.
     * 
     * @return The adapter's name.
     */
    protected abstract String getTypeName();

    @Override
    protected final Future<Void> startInternal() {

        final Future<Void> result = Future.future();

        if (Strings.isNullOrEmpty(getTypeName())) {
            result.fail(new IllegalStateException("adapter does not define a typeName"));
        } else if (tenantClient == null) {
            result.fail(new IllegalStateException("Tenant service client must be set"));
        } else if (messagingClient == null) {
            result.fail(new IllegalStateException("Hono Messaging client must be set"));
        } else if (registrationClient == null) {
            result.fail(new IllegalStateException("Device Registration service client must be set"));
        } else if (credentialsServiceClient == null) {
            result.fail(new IllegalStateException("Credentials service client must be set"));
        } else {
            connectToService(tenantClient, "Tenant service");
            connectToService(messagingClient, "Messaging");
            connectToService(registrationClient, "Device Registration service");
            connectToService(credentialsServiceClient, "Credentials service");
            doStart(result);
        }
        return result;
    }

    /**
     * Invoked after the adapter has started up.
     * <p>
     * This default implementation simply completes the future.
     * <p>
     * Subclasses should override this method to perform any work required
     * on start-up of this protocol adapter.
     *
     * @param startFuture The future to complete once start up is complete.
     */
    protected void doStart(final Future<Void> startFuture) {
        startFuture.complete();
    }

    @Override
    protected final Future<Void> stopInternal() {

        LOG.info("stopping protocol adapter");
        final Future<Void> result = Future.future();
        final Future<Void> doStopResult = Future.future();
        doStop(doStopResult);
        doStopResult
            .compose(s -> closeServiceClients())
            .recover(t -> {
                LOG.info("error while stopping protocol adapter", t);
                return Future.failedFuture(t);
            }).compose(s -> {
                result.complete();
                LOG.info("successfully stopped protocol adapter");
            }, result);
        return result;
    }

    private CompositeFuture closeServiceClients() {

        return CompositeFuture.all(
                closeServiceClient(tenantClient),
                closeServiceClient(messagingClient),
                closeServiceClient(registrationClient),
                closeServiceClient(credentialsServiceClient));
    }

    private Future<Void> closeServiceClient(final HonoClient client) {

        final Future<Void> shutdownTracker = Future.future();
        if (client == null) {
            shutdownTracker.complete();
        } else {
            client.shutdown(shutdownTracker.completer());
        }

        return shutdownTracker;
    }

    /**
     * Invoked directly before the adapter is shut down.
     * <p>
     * Subclasses should override this method to perform any work required before
     * shutting down this protocol adapter.
     *
     * @param stopFuture The future to complete once all work is done and shut down
     *                   should commence.
     */
    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    /**
     * Gets the options for configuring the server side trust anchor.
     * <p>
     * This implementation returns the options returned by
     * {@link AbstractConfig#getTrustOptions()} if not {@code null}.
     * Otherwise, it returns trust options for using the configured
     * Tenant service client for retrieving tenant specific trust anchor
     * configuration.
     * 
     * @return The trust options.
     */
    protected TrustOptions getServerTrustOptions() {

        return Optional.ofNullable(getConfig().getTrustOptions())
                .orElse(new TenantApiTrustOptions(getTenantServiceClient()));
    }

    /**
     * Connects to a Hono Service component using the configured client.
     *
     * @param client The Hono client for the service that is to be connected.
     * @param serviceName The name of the service that is to be connected (used for logging).
     * @return A future that will succeed once the connection has been established.
     *         The future will fail if the connection cannot be established.
     * @throws NullPointerException if serviceName is {@code null}.
     * @throws IllegalArgumentException if client is {@code null}.
     */
    protected final Future<HonoClient> connectToService(final HonoClient client, final String serviceName) {

        Objects.requireNonNull(serviceName);

        if (client == null) {
            return Future.failedFuture(new IllegalStateException(String.format("Hono %s client not set", serviceName)));
        } else {
            final Handler<ProtonConnection> disconnectHandler = getHandlerForDisconnectHonoService(client, serviceName);

            return client.connect(disconnectHandler).map(connectedClient -> {
                LOG.info("connected to {}", serviceName);
                return connectedClient;
            }).recover(t -> {
                LOG.warn("failed to connect to {}", serviceName, t);
                return Future.failedFuture(t);
            });
        }
    }

    /**
     * Gets a handler that attempts a reconnect for a Hono service client after {@link Constants#DEFAULT_RECONNECT_INTERVAL_MILLIS} milliseconds.
     *
     * @param client The Hono client for the service that is to be connected.
     * @param serviceName The name of the service that is to be connected (used for logging).
     * @return A handler that attempts the reconnect.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    private Handler<ProtonConnection> getHandlerForDisconnectHonoService(final HonoClient client, final String serviceName) {

        return (connection) -> {
            vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
                LOG.info("attempting to reconnect to {}", serviceName);
                client.connect(getHandlerForDisconnectHonoService(client, serviceName)).setHandler(connectAttempt -> {
                    if (connectAttempt.succeeded()) {
                        LOG.info("reconnected to {}", serviceName);
                    } else {
                        LOG.debug("cannot reconnect to {}: {}", serviceName, connectAttempt.cause().getMessage());
                    }
                });
            });
        };
    }

    /**
     * Checks if this adapter is connected to the services it depends on.
     * 
     * <em>Hono Messaging</em>
     * and the <em>Device Registration</em> service.
     * 
     * @return A future indicating the outcome of the check.
     *         The future will succeed if this adapter is currently connected to
     *         <ul>
     *         <li>a Tenant service</li>
     *         <li>a Device Registration service</li>
     *         <li>a Credentials service</li>
     *         <li>a service implementing the Telemetry &amp; Event APIs</li>
     *         </ul>
     *         Otherwise, the future will fail.
     */
    protected final Future<Void> isConnected() {

        final Future<Void> tenantCheck = Optional.ofNullable(tenantClient)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new IllegalStateException("Tenant service client is not set")));
        final Future<Void> messagingCheck = Optional.ofNullable(messagingClient)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new IllegalStateException("Messaging client is not set")));
        final Future<Void> registrationCheck = Optional.ofNullable(registrationClient)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new IllegalStateException("Device Registration service client is not set")));
        final Future<Void> credentialsCheck = Optional.ofNullable(credentialsServiceClient)
                .map(client -> client.isConnected())
                .orElse(Future.failedFuture(new IllegalStateException("Credentials service client is not set")));
        return CompositeFuture.all(tenantCheck, messagingCheck, registrationCheck, credentialsCheck).compose(ok -> {
            return Future.succeededFuture();
        });
    }

    /**
     * Gets a client for sending telemetry data for a tenant.
     * 
     * @param tenantId The tenant to send the telemetry data for.
     * @return The client.
     */
    protected final Future<MessageSender> getTelemetrySender(final String tenantId) {
        return getHonoMessagingClient().getOrCreateTelemetrySender(tenantId);
    }

    /**
     * Gets a client for sending events for a tenant.
     * 
     * @param tenantId The tenant to send the events for.
     * @return The client.
     */
    protected final Future<MessageSender> getEventSender(final String tenantId) {
        return getHonoMessagingClient().getOrCreateEventSender(tenantId);
    }

    /**
     * Gets a client for interacting with the Device Registration service.
     * 
     * @param tenantId The tenant that the client is scoped to.
     * @return The client.
     */
    protected final Future<RegistrationClient> getRegistrationClient(final String tenantId) {
        return getRegistrationServiceClient().getOrCreateRegistrationClient(tenantId);
    }

    /**
     * Gets an assertion for a device's registration status.
     * <p>
     * The returned JSON object contains the assertion for the device
     * under property {@link RegistrationConstants#FIELD_ASSERTION}.
     * <p>
     * In addition to the assertion the returned object may include <em>default</em>
     * values for properties to set on messages published by the device under
     * property {@link RegistrationConstants#FIELD_DEFAULTS}.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to get the assertion for.
     * @param authenticatedDevice The device that has authenticated to this protocol adapter.
     *                  <p>
     *                  If not {@code null} then the authenticated device is compared to the
     *                  given tenant and device ID. If they differ in the device identifier,
     *                  then the authenticated device is considered to be a gateway acting on
     *                  behalf of the device.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if the assertion cannot be retrieved. The cause will be
     *         a {@link ServiceInvocationException} containing a corresponding error code.
     *         <p>
     *         Otherwise the future will contain the assertion.
     * @throws NullPointerException if tenant ID or device ID are {@code null}.
     */
    protected final Future<JsonObject> getRegistrationAssertion(final String tenantId, final String deviceId, final Device authenticatedDevice) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Future<String> gatewayId = getGatewayId(tenantId, deviceId, authenticatedDevice);

        return gatewayId
                .compose(gwId -> getRegistrationClient(tenantId))
                .compose(client -> client.assertRegistration(deviceId, gatewayId.result()));
    }

    private Future<String> getGatewayId(final String tenantId, final String deviceId, final Device authenticatedDevice) {

        final Future<String> result = Future.future();
        if (authenticatedDevice == null) {
            result.complete(null);
        } else if (tenantId.equals(authenticatedDevice.getTenantId())) {
            if (deviceId.equals(authenticatedDevice.getDeviceId())) {
                result.complete(null);
            } else {
                result.complete(authenticatedDevice.getDeviceId());
            }
        } else {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                    "cannot publish data for device of other tenant"));
        }
        return result;
    }

    /**
     * Gets configuration information for a tenant.
     * <p>
     * The returned JSON object contains information as defined by Hono's
     * <a href="https://www.eclipse.org/hono/api/tenant-api/#response-payload">Tenant API</a>.
     * 
     * @param tenantId The tenant to retrieve information for.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if the information cannot be retrieved. The cause will be
     *         a {@link ServiceInvocationException} containing a corresponding error code.
     *         <p>
     *         Otherwise the future will contain the configuration information.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    protected final Future<TenantObject> getTenantConfiguration(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return getTenantClient().compose(client -> client.get(tenantId));
    }

    /**
     * Adds message properties based on a device's registration information.
     * <p>
     * Sets the following properties on the message:
     * <ul>
     * <li>Adds the registration assertion found in the
     * {@link RegistrationConstants#FIELD_ASSERTION} property of the given registration information.</li>
     * <li>Adds {@linkplain #getTypeName() the adapter's name} to the message in application property
     * {@link MessageHelper#APP_PROPERTY_ORIG_ADAPTER}</li>
     * <li>Augments the message with missing (application) properties corresponding to the
     * {@link RegistrationConstants#FIELD_DEFAULTS} contained in the registration information.</li>
     * <li>Adds JMS vendor properties if configuration property <em>jmsVendorPropertiesEnabled</em> is set
     * to {@code true}.</li>
     * </ul>
     * 
     * @param message The message to set the properties on.
     * @param registrationInfo The values to set.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void addProperties(final Message message, final JsonObject registrationInfo) {

        final String registrationAssertion = registrationInfo.getString(RegistrationConstants.FIELD_ASSERTION);
        MessageHelper.addRegistrationAssertion(message, registrationAssertion);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_ORIG_ADAPTER, getTypeName());
        if (getConfig().isDefaultsEnabled()) {
            final JsonObject defaults = registrationInfo.getJsonObject(RegistrationConstants.FIELD_DEFAULTS);
            if (defaults != null) {
                addDefaults(message, defaults);
            }
        }
        if (Strings.isNullOrEmpty(message.getContentType())) {
            // set default content type if none has been specified when creating the
            // message nor a default content type is available
            message.setContentType(CONTENT_TYPE_OCTET_STREAM);
        }
        if (getConfig().isJmsVendorPropsEnabled()) {
            MessageHelper.addJmsVendorProperties(message);
        }
    }

    private void addDefaults(final Message message, final JsonObject defaults) {

        defaults.forEach(prop -> {

            switch(prop.getKey()) {
            case MessageHelper.SYS_PROPERTY_CONTENT_TYPE:
                if (Strings.isNullOrEmpty(message.getContentType()) && String.class.isInstance(prop.getValue())) {
                    // set to default type registered for device or fall back to default content type
                    message.setContentType((String) prop.getValue());
                }
                break;
            case MessageHelper.SYS_PROPERTY_CONTENT_ENCODING:
                if (Strings.isNullOrEmpty(message.getContentEncoding()) && String.class.isInstance(prop.getValue())) {
                    message.setContentEncoding((String) prop.getValue());
                }
                break;
            case MessageHelper.SYS_PROPERTY_ABSOLUTE_EXPIRY_TIME:
            case MessageHelper.SYS_PROPERTY_CORRELATION_ID:
            case MessageHelper.SYS_PROPERTY_CREATION_TIME:
            case MessageHelper.SYS_PROPERTY_GROUP_ID:
            case MessageHelper.SYS_PROPERTY_GROUP_SEQUENCE:
            case MessageHelper.SYS_PROPERTY_MESSAGE_ID:
            case MessageHelper.SYS_PROPERTY_REPLY_TO:
            case MessageHelper.SYS_PROPERTY_REPLY_TO_GROUP_ID:
            case MessageHelper.SYS_PROPERTY_SUBJECT:
            case MessageHelper.SYS_PROPERTY_TO:
            case MessageHelper.SYS_PROPERTY_USER_ID:
                // these standard properties cannot be set using defaults
                LOG.debug("ignoring default property [{}] registered for device", prop.getKey());
                break;
            default:
                // add all other defaults as application properties
                MessageHelper.addProperty(message, prop.getKey(), prop.getValue());
            }
        });
    }

    /**
     * Registers a check that succeeds if this component is connected to Hono Messaging,
     * the Tenant Service, the Device Registration and the Credentials service.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        handler.register("connection-to-services", status -> {
            isConnected().map(connected -> {
                status.tryComplete(Status.OK());
                return null;
            }).otherwise(t -> {
                status.tryComplete(Status.KO());
                return null;
            });
        });
    }

    /**
     * Does not register any checks.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
    }

    /**
     * Creates a new AMQP 1.0 message.
     * <p>
     * Subclasses are encouraged to use this method for creating {@code Message} instances to
     * be sent downstream in order to have the following properties set on the message automatically:
     * <ul>
     * <li><em>to</em> will be set to address</li>
     * <li>application property <em>device_id</em> will be set to device ID</li>
     * <li>application property <em>orig_address</em> will be set to publish address</li>
     * <li><em>content-type</em> will be set to content type</li>
     * <li><em>creation-time</em> will be set to the current number of milliseconds from the epoch of 1970-01-01T00:00:00Z.
     * <li>additional properties set by {@link #addProperties(Message, JsonObject)}</li>
     * </ul>
     * This method also sets the message's payload.
     * 
     * @param address The receiver of the message.
     * @param deviceId The identifier of the device that the message originates from.
     * @param publishAddress The address that the message has been published to originally by the device.
     *                       (may be {@code null}).
     *                       <p>
     *                       This address will be transport protocol specific, e.g. an HTTP based adapter
     *                       will probably use URIs here whereas an MQTT based adapter might use the
     *                       MQTT message's topic.
     * @param contentType The content type describing the message's payload (may be {@code null}).
     * @param payload The message payload.
     * @param registrationInfo The device's registration information as retrieved by the <em>Device
     * Registration</em> service's <em>assert Device Registration</em> operation.
     * @return The message.
     * @throws NullPointerException if address, device ID or registration info are {@code null}.
     */
    protected final Message newMessage(
            final String address,
            final String deviceId,
            final String publishAddress,
            final String contentType,
            final Buffer payload,
            final JsonObject registrationInfo) {

        Objects.requireNonNull(address);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(registrationInfo);

        final Message msg = ProtonHelper.message();
        msg.setAddress(address);
        MessageHelper.addDeviceId(msg, deviceId);
        if (publishAddress != null) {
            MessageHelper.addProperty(msg, MessageHelper.APP_PROPERTY_ORIG_ADDRESS, publishAddress);
        }
        if (contentType != null) {
            msg.setContentType(contentType);
        }
        if (payload != null) {
            msg.setBody(new Data(new Binary(payload.getBytes())));
        }

        MessageHelper.setCreationTime(msg);

        addProperties(msg, registrationInfo);
        return msg;
    }
}
