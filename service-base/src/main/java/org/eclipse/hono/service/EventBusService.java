/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.EventBusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * A base class for services implementing any of the Hono APIs.
 * <p>
 * In particular, this base class provides support for receiving request messages via vert.x' event bus
 * and route them to specific methods corresponding to the operation indicated in the message.
 *
 * @param <T> The type of configuration this service supports.
 */
public abstract class EventBusService<T> extends ConfigurationSupportingVerticle<T> {

    /**
     * A logger to be shared by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private MessageConsumer<JsonObject> requestConsumer;

    /**
     * Starts up this service.
     * <ol>
     * <li>Registers an event bus consumer for {@linkplain #getEventBusAddress()
     * the service's event bus request address}.</li>
     * <li>Invokes {@link #doStart(Future)}.</li>
     * </ol>
     *
     * @param startFuture The future to complete on successful startup.
     */
    public final void start(final Future<Void> startFuture) {
        registerConsumer();
        doStart(startFuture);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this service.
     * <p>
     * This default implementation performs nothing except for completing the Future.
     * </p>
     * <p>
     * This method is invoked by {@link #start()} as part of the verticle deployment process.
     * </p>
     *
     * @param startFuture future to invoke once start up is complete.
     */
    protected void doStart(final Future<Void> startFuture) {
        // should be overridden by subclasses
        startFuture.complete();
    }

    /**
     * Gets the event bus address that this service listens on for incoming requests.
     * 
     * @return The address.
     */
    protected abstract String getEventBusAddress();

    /**
     * Unregisters the registration message consumer from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    public final void stop(final Future<Void> stopFuture) {
        if (requestConsumer != null) {
            requestConsumer.unregister();
            log.info("unregistered Tenant API request consumer from event bus");
        }
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this service.
     * <p>
     * This default implementation performs nothing except for completing the Future.
     * </p>
     * <p>
     * This method is invoked by {@link #stop()} as part of the verticle deployment process.
     * </p>
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    private void registerConsumer() {

        requestConsumer = vertx.eventBus().consumer(getEventBusAddress());
        requestConsumer.handler(this::processRequestMessage);
        log.info("listening on event bus [address: {}] for requests", getEventBusAddress());
    }

    private void processRequestMessage(final Message<JsonObject> msg) {

        if (log.isTraceEnabled()) {
            log.trace("received request message: {}", msg.body().encodePrettily());
        }

        final EventBusMessage request = EventBusMessage.fromJson(msg.body());
        processRequest(request).map(response -> {
            msg.reply(response.toJson());
            return null;
        }).otherwise(t -> {
            log.debug("cannot process request [operation: {}]", request.getOperation(), t);
            if (t instanceof ServiceInvocationException) {
                msg.fail(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        String.format("cannot process request [operation: %s]", request.getOperation()));
            } else {
                msg.fail(
                        ((ServiceInvocationException) t).getErrorCode(),
                        String.format("cannot process request [operation: %s]", request.getOperation()));
            }
            return null;
        });
    }

    /**
     * Processes a service invocation request.
     * 
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    protected abstract Future<? extends EventBusMessage> processRequest(final EventBusMessage request);

    /**
     * Gets a property value of a given type from a JSON object.
     * 
     * @param payload The object to get the property from.
     * @param field The name of the property.
     * @param <T> The type of the field.
     * @return The property value or {@code null} if no such property exists or is not of the expected type.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @SuppressWarnings({ "unchecked", "hiding" })
    protected final <T> T getTypesafeValueForField(final JsonObject payload, final String field) {

        Objects.requireNonNull(payload);
        Objects.requireNonNull(field);

        try {
            return (T) payload.getValue(field);
        } catch (ClassCastException e) {
            return null;
        }
    }
}