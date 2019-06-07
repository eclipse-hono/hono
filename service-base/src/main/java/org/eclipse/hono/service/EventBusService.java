/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
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
 * @param <C> The type of configuration this service supports.
 */
public abstract class EventBusService<C> extends ConfigurationSupportingVerticle<C> {

    /**
     * A logger to be shared by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The OpenTracing {@code Tracer} for tracking processing of requests.
     */
    protected Tracer tracer = NoopTracerFactory.create();

    private MessageConsumer<JsonObject> requestConsumer;

    /**
     * Sets the OpenTracing {@code Tracer} to use for tracking the processing
     * of requests.
     * <p>
     * If not set explicitly, the {@code NoopTracer} from OpenTracing will
     * be used.
     *
     * @param opentracingTracer The tracer.
     */
    @Autowired(required = false)
    public final void setTracer(final Tracer opentracingTracer) {
        log.info("using OpenTracing Tracer implementation [{}]", opentracingTracer.getClass().getName());
        this.tracer = Objects.requireNonNull(opentracingTracer);
    }

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
    @Override
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
    @Override
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
        final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, msg.headers());
        request.setSpanContext(spanContext);
        processRequest(request).recover(t -> {
            log.debug("cannot process request [operation: {}]: {}", request.getOperation(), t.getMessage(), t);
            final int status = ServiceInvocationException.extractStatusCode(t);
            return Future.succeededFuture(request.getResponse(status));
        }).map(response -> {
            if (response.getReplyToAddress() == null) {
                log.debug("sending response as direct reply to request [operation: {}]", request.getOperation());
                msg.reply(response.toJson());
            } else if (response.hasResponseProperties()) {
                log.debug("sending response [operation: {}, reply-to: {}]",
                        request.getOperation(), request.getReplyToAddress());
                vertx.eventBus().send(request.getReplyToAddress(), response.toJson());
            } else {
                log.warn("discarding response lacking correlation ID or operation");
            }
            return null;
        });
    }

    /**
     * Processes a service invocation request.
     * <p>
     * The response message returned in the future will be sent over the vert.x
     * event bus to the address given in the response's <em>replyToAddress</em>
     * property.
     * <p>
     * Implementations should therefore use {@link EventBusMessage#getResponse(int)}
     * for creating the response message based on the request (which contains the
     * reply-to address).
     * 
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     *         The future will succeed with the response to be sent to the
     *         client if the invocation of the operation was successful.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}
     *         indicating the cause of the problem in its error code.
     * @throws NullPointerException If the request message is {@code null}.
     */
    protected abstract Future<EventBusMessage> processRequest(EventBusMessage request);

    /**
     * Gets a property value of a given type from a JSON object.
     * 
     * @param clazz Type class of the type
     * @param payload The object to get the property from.
     * @param field The name of the property.
     * @param <T> The type of the field.
     * @return The property value or {@code null} if no such property exists or is not of the expected type.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected static final <T> T getTypesafeValueForField(final Class<T> clazz, final JsonObject payload,
            final String field) {

        Objects.requireNonNull(clazz);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(field);

        final Object result = payload.getValue(field);

        if (clazz.isInstance(result)) {
            return clazz.cast(result);
        }

        return null;
    }

    /**
     * Removes a property value of a given type from a JSON object.
     *
     * @param clazz Type class of the type
     * @param payload The object to get the property from.
     * @param field The name of the property.
     * @param <T> The type of the field.
     * @return The property value or {@code null} if no such property exists or is not of the expected type.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected static final <T> T removeTypesafeValueForField(final Class<T> clazz, final JsonObject payload,
            final String field) {

        Objects.requireNonNull(clazz);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(field);

        final Object result = payload.remove(field);

        if (clazz.isInstance(result)) {
            return clazz.cast(result);
        }

        return null;
    }

    /**
     * Gets the payload from a request message.
     * <p>
     * The returned JSON object contains the given payload (if not {@code null}).
     * If the given payload does not contain an <em>enabled</em> property, then
     * it is added with value {@code true} to the returned object.
     * 
     * @param payload The payload from the request message.
     * @return The payload (never {@code null}).
     */
    protected final JsonObject getRequestPayload(final JsonObject payload) {

        return Optional.ofNullable(payload).map(pl -> {
            final Object obj = pl.getValue(RequestResponseApiConstants.FIELD_ENABLED);
            if (obj instanceof Boolean) {
                return pl;
            } else {
                log.trace("adding 'enabled=true' property to request payload");
                return pl.copy().put(RequestResponseApiConstants.FIELD_ENABLED, Boolean.TRUE);
            }
        }).orElse(new JsonObject().put(RequestResponseApiConstants.FIELD_ENABLED, Boolean.TRUE));
    }

    /**
     * Composes the given future so that the given <em>OpenTracing</em> span is finished when the future completes.
     * <p>
     * The result or exception of the given future will be used to set a {@link Tags#HTTP_STATUS} tag on the span
     * and to set a {@link Tags#ERROR} tag in case of an exception or a result with error status.
     * 
     * @param span The span to finish.
     * @param resultFuture The future to be composed.
     * @return The composed future.
     */
    protected Future<EventBusMessage> finishSpanOnFutureCompletion(final Span span, final Future<EventBusMessage> resultFuture) {
        return resultFuture.compose(eventBusMessage -> {
            Tags.HTTP_STATUS.set(span, eventBusMessage.getStatus());
            if (eventBusMessage.hasErrorStatus()) {
                Tags.ERROR.set(span, true);
            }
            span.finish();
            return Future.succeededFuture(eventBusMessage);
        }).recover(t -> {
            Tags.HTTP_STATUS.set(span, ServiceInvocationException.extractStatusCode(t));
            TracingHelper.logError(span, t);
            span.finish();
            return Future.failedFuture(t);
        });
    }
}
