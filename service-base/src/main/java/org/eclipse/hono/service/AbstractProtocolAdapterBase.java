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

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonClientOptions;
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

    private HonoClient messaging;
    private HonoClient registration;
    private HonoClientBasedAuthProvider credentialsAuthProvider;

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
     * Sets the client to use for connecting to the Hono Messaging component.
     * 
     * @param honoClient The client.
     * @throws NullPointerException if hono client is {@code null}.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Autowired
    public final void setHonoMessagingClient(final HonoClient honoClient) {
        this.messaging = Objects.requireNonNull(honoClient);
    }

    /**
     * Gets the client used for connecting to the Hono Messaging component.
     * 
     * @return The client.
     */
    public final HonoClient getHonoMessagingClient() {
        return messaging;
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
        this.registration = Objects.requireNonNull(registrationServiceClient);
    }

    /**
     * Gets the client used for connecting to the Device Registration service.
     * 
     * @return The client.
     */
    public final HonoClient getRegistrationServiceClient() {
        return registration;
    }

    /**
     * Sets the authentication provider to use for verifying device credentials.
     *
     * @param credentialsAuthProvider The provider.
     * @throws NullPointerException if the provider is {@code null}.
     */
    @Autowired(required = false)
    public final void setCredentialsAuthProvider(final HonoClientBasedAuthProvider credentialsAuthProvider) {
        this.credentialsAuthProvider = Objects.requireNonNull(credentialsAuthProvider);
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

    /**
     * Gets the authentication provider used for verifying device credentials.
     *
     * @return The provider.
     */
    public final HonoClientBasedAuthProvider getCredentialsAuthProvider() {
        return credentialsAuthProvider;
    }

    @Override
    protected final Future<Void> startInternal() {
        Future<Void> result = Future.future();
        if (StringUtils.isEmpty(getTypeName())) {
            result.fail(new IllegalStateException("adapter does not define a typeName"));
        } else if (messaging == null) {
            result.fail(new IllegalStateException("Hono Messaging client must be set"));
        } else if (registration == null) {
            result.fail(new IllegalStateException("Device Registration client must be set"));
        } else if (credentialsAuthProvider == null) {
            result.fail(new IllegalStateException("Credentials Authentication Provider must be set"));
        } else {
            connectToMessaging();
            connectToDeviceRegistration();
            credentialsAuthProvider.start().compose(s -> {
                doStart(result);
            }, result);
        }
        return result;
    }

    /**
     * Invoked after the adapter has started up.
     * <p>
     * Subclasses should override this method to perform any work required on start-up of this protocol adapter.
     *
     * @param startFuture The future to complete once start up is complete.
     */
    protected void doStart(final Future<Void> startFuture) {
        // should be overridden by subclasses
        startFuture.complete();
    }

    @Override
    protected final Future<Void> stopInternal() {

        LOG.info("stopping protocol adapter");
        Future<Void> result = Future.future();
        Future<Void> doStopResult = Future.future();
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

        Future<Void> messagingTracker = Future.future();
        if (messaging == null) {
            messagingTracker.complete();
        } else {
            messaging.shutdown(messagingTracker.completer());
        }

        Future<Void> registrationTracker = Future.future();
        if (registration == null) {
            registrationTracker.complete();
        } else {
            registration.shutdown(registrationTracker.completer());
        }

        Future<Void> credentialsTracker = Future.future();
        if (credentialsAuthProvider == null) {
            credentialsTracker.complete();
        } else {
            credentialsTracker = credentialsAuthProvider.stop();
        }
        return CompositeFuture.all(messagingTracker, registrationTracker, credentialsTracker);
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
     * Connects to the Hono Messaging component using the configured client.
     * 
     * @return A future that will succeed once the connection has been established.
     *         The future will fail if the connection cannot be established.
     */
    protected final Future<HonoClient> connectToMessaging() {

        if (messaging == null) {
            return Future.failedFuture(new IllegalStateException("Hono Messaging client not set"));
        } else {
            return messaging.connect(createClientOptions(), this::onDisconnectMessaging).map(connectedClient -> {
                LOG.info("connected to Hono Messaging");
                return connectedClient;
            }).recover(t -> {
                LOG.warn("failed to connect to Hono Messaging", t);
                return Future.failedFuture(t);
            });
        }
    }

    /**
     * Attempts a reconnect for the Hono Messaging client after {@link Constants#DEFAULT_RECONNECT_INTERVAL_MILLIS} milliseconds.
     *
     * @param con The connection that was disonnected.
     */
    private void onDisconnectMessaging(final ProtonConnection con) {

        vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
            LOG.info("attempting to reconnect to Hono Messaging");
            messaging.connect(createClientOptions(), this::onDisconnectMessaging).setHandler(connectAttempt -> {
                if (connectAttempt.succeeded()) {
                    LOG.info("reconnected to Hono Messaging");
                } else {
                    LOG.debug("cannot reconnect to Hono Messaging: {}", connectAttempt.cause().getMessage());
                }
            });
        });
    }

    /**
     * Connects to the Device Registration service using the configured client.
     * 
     * @return A future that will succeed once the connection has been established.
     *         The future will fail if the connection cannot be established.
     */
    protected final Future<HonoClient> connectToDeviceRegistration() {

        if (registration == null) {
            return Future.failedFuture(new IllegalStateException("Device Registration client not set"));
        } else {
            return registration.connect(createClientOptions(), this::onDisconnectDeviceRegistry).map(connectedClient -> {
                LOG.info("connected to Device Registration service");
                return connectedClient;
            }).recover(t -> {
                LOG.warn("failed to connect to Device Registration service", t);
                return Future.failedFuture(t);
            });
        }
    }

    /**
     * Attempts a reconnect for the Hono Device Registration client after {@link Constants#DEFAULT_RECONNECT_INTERVAL_MILLIS} milliseconds.
     *
     * @param con The connection that was disonnected.
     */
    private void onDisconnectDeviceRegistry(final ProtonConnection con) {

        vertx.setTimer(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, reconnect -> {
            LOG.info("attempting to reconnect to Device Registration service");
            registration.connect(createClientOptions(), this::onDisconnectDeviceRegistry).setHandler(connectAttempt -> {
                if (connectAttempt.succeeded()) {
                    LOG.info("reconnected to Device Registration service");
                } else {
                    LOG.debug("cannot reconnect to Device Registration service: {}", connectAttempt.cause().getMessage());
                }
            });
        });
    }

    private ProtonClientOptions createClientOptions() {
        return new ProtonClientOptions()
                .setConnectTimeout(200)
                .setReconnectAttempts(1)
                .setReconnectInterval(Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS);
    }

    /**
     * Checks if this adapter is connected to <em>Hono Messaging</em>
     * and the <em>Device Registration</em> service.
     * 
     * @return A succeeded future containing {@code true} if and only if
     *         this adapter is connected to both services.
     */
    protected final Future<Boolean> isConnected() {
        final Future<Boolean> messagingCheck = Optional.ofNullable(messaging)
                .map(client -> client.isConnected()).orElse(Future.succeededFuture(Boolean.FALSE));
        final Future<Boolean> registrationCheck = Optional.ofNullable(registration)
                .map(client -> client.isConnected()).orElse(Future.succeededFuture(Boolean.FALSE));
        return CompositeFuture.all(messagingCheck, registrationCheck).compose(ok -> {
            return Future.succeededFuture(messagingCheck.result() && registrationCheck.result());
        });
    }

    /**
     * Gets a client for sending telemetry data for a tenant.
     * 
     * @param tenantId The tenant to send the telemetry data for.
     * @return The client.
     */
    protected final Future<MessageSender> getTelemetrySender(final String tenantId) {
        return messaging.getOrCreateTelemetrySender(tenantId);
    }

    /**
     * Gets a client for sending events for a tenant.
     * 
     * @param tenantId The tenant to send the events for.
     * @return The client.
     */
    protected final Future<MessageSender> getEventSender(final String tenantId) {
        return messaging.getOrCreateEventSender(tenantId);
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

        MessageHelper.addRegistrationAssertion(message, registrationInfo.getString(RegistrationConstants.FIELD_ASSERTION));
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_ORIG_ADAPTER, getTypeName());
        if (getConfig().isDefaultsEnabled()) {
            final JsonObject defaults = registrationInfo.getJsonObject(RegistrationConstants.FIELD_DEFAULTS);
            if (defaults != null) {
                addDefaults(message, defaults);
            }
        }
        if (StringUtils.isEmpty(message.getContentType())) {
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
                if (StringUtils.isEmpty(message.getContentType()) && String.class.isInstance(prop.getValue())) {
                    // set to default type registered for device or fall back to default content type
                    message.setContentType((String) prop.getValue());
                }
                break;
            case MessageHelper.SYS_PROPERTY_CONTENT_ENCODING:
                if (StringUtils.isEmpty(message.getContentEncoding()) && String.class.isInstance(prop.getValue())) {
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
     * the Device Registration and the Credentials service.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        handler.register("connection-to-services", status -> {
            isConnected().map(connected -> {
                if (connected) {
                    status.tryComplete(Status.OK());
                } else {
                    status.tryComplete(Status.KO());
                }
                return null;
            });
        });
        if (credentialsAuthProvider != null) {
            credentialsAuthProvider.registerReadinessChecks(handler);
        }
    }

    /**
     * Registers a check that always succeeds.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        if (credentialsAuthProvider != null) {
            credentialsAuthProvider.registerLivenessChecks(handler);
        }
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
        addProperties(msg, registrationInfo);
        return msg;
    }
}
