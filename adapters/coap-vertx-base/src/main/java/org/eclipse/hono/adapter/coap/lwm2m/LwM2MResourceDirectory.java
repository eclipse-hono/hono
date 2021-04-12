/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.coap.lwm2m;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.coap.CoapContext;
import org.eclipse.hono.adapter.coap.CoapProtocolAdapter;
import org.eclipse.hono.adapter.coap.TracingSupportingHonoResource;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.Strings;
import org.eclipse.leshan.core.Link;
import org.eclipse.leshan.core.californium.EndpointContextUtil;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * A CoAP resource implementing basic parts of the
 * <a href="https://tools.ietf.org/html/draft-ietf-core-resource-directory-23">
 * CoRE Resource Directory</a>.
 * <p>
 * The registration, update and removal requests of devices are mapped to <em>empty
 * notification</em> events containing the <em>lifetime</em> provided by the device
 * as TTD value.
 *
 */
public final class LwM2MResourceDirectory extends TracingSupportingHonoResource implements Lifecycle {

    private static final String QUERY_PARAM_BINDING_MODE = "b";
    private static final String QUERY_PARAM_ENDPOINT = "ep";
    private static final String QUERY_PARAM_LIFETIME = "lt";
    private static final String QUERY_PARAM_LWM2M_VERSION = "lwm2m";
    private static final Logger LOG = LoggerFactory.getLogger(LwM2MResourceDirectory.class);

    private final Set<BindingMode> supportedBindingModes = Set.of(BindingMode.U, BindingMode.UQ);
    private final LwM2MRegistrationStore registrationStore;

    /**
     * Creates a new resource directory resource.
     *
     * @param adapter The protocol adapter that this resource is part of.
     * @param registrationStore The component to use for managing registration information.
     * @param tracer Open Tracing tracer to use for tracking the processing of requests.
     */
    public LwM2MResourceDirectory(
            final CoapProtocolAdapter adapter,
            final LwM2MRegistrationStore registrationStore,
            final Tracer tracer) {
        super(adapter, tracer, "rd");
        getAttributes().addResourceType("core.rd");
        this.registrationStore = Objects.requireNonNull(registrationStore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> start() {
        if (registrationStore instanceof Lifecycle) {
            ((Lifecycle) registrationStore).start();
        }
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> stop() {
        if (registrationStore instanceof Lifecycle) {
            ((Lifecycle) registrationStore).stop();
        }
        return Future.succeededFuture();
    }

    private String getRegistrationId(final Device device) {
        return UUID.randomUUID().toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<CoapContext> createCoapContextForPost(final CoapExchange coapExchange, final Span span) {
        return getAuthenticatedDevice(coapExchange)
                .map(device -> CoapContext.fromRequest(coapExchange, device, device, null, span));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<CoapContext> createCoapContextForDelete(final CoapExchange coapExchange, final Span span) {
        return getAuthenticatedDevice(coapExchange)
                .map(device -> CoapContext.fromRequest(coapExchange, device, device, null, span));
    }

    /**
     * {@inheritDoc}
     *
     * Handles a request to create or update a device's registration.
     */
    @Override
    protected Future<?> handlePost(final CoapContext ctx) {

        final Request request = ctx.getExchange().advanced().getRequest();
        LOG.debug("processing POST request: {}", request);

        // we only support confirmable messages
        if (!ctx.isConfirmable()) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "LwM2M does not support CoAP NON requests"));
        }

        if (!ctx.isDeviceAuthenticated()) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                    "server does not support noSec security mode"));
        }

        final List<String> uri = ctx.getExchange().getRequestOptions().getUriPath();
        if (uri.size() > 2) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND));
        }

        final String lwm2mVersion = ctx.getQueryParameter(QUERY_PARAM_LWM2M_VERSION);
        final String endpoint = ctx.getQueryParameter(QUERY_PARAM_ENDPOINT);
        final Long lifetime = Optional.ofNullable(ctx.getQueryParameter(QUERY_PARAM_LIFETIME))
                .map(lt -> {
                    try {
                        return Long.valueOf(lt);
                    } catch (final NumberFormatException e) {
                        return -1L;
                    }
                })
                .orElse(null);
        final BindingMode bindingMode = Optional.ofNullable(ctx.getQueryParameter(QUERY_PARAM_BINDING_MODE))
                .map(BindingMode::valueOf)
                .orElse(BindingMode.U);
        final Link[] objectLinks = Link.parse(request.getPayload());

        final Promise<ResponseCode> result = Promise.promise();
        if (uri.size() == 1) {
            handleRegister(ctx, lwm2mVersion, endpoint, bindingMode, lifetime, objectLinks).onComplete(result);
        } else {
            // URI path can have at most 2 segments as already verified above
            handleUpdate(ctx, bindingMode, lifetime, objectLinks, uri.get(1)).onComplete(result);
        }
        return result.future().onSuccess(ctx::respondWithCode);
    }

    private Future<ResponseCode> handleRegister(
            final CoapContext ctx,
            final String lwm2mVersion,
            final String endpoint,
            final BindingMode bindingMode,
            final long lifetime,
            final Link[] objectLinks) {

        final Promise<ResponseCode> result = Promise.promise();

        final Span currentSpan = TracingHelper
                .buildChildSpan(getTracer(), ctx.getTracingContext(), "register LwM2M device", getAdapter().getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(TracingHelper.TAG_TENANT_ID, ctx.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, ctx.getOriginDevice().getDeviceId())
                .start();

        LOG.debug("processing LwM2M registration request for {}", ctx.getOriginDevice());
        currentSpan.log(Map.of(
                "lwm2m", lwm2mVersion,
                "endpoint", endpoint,
                "binding mode", bindingMode.name(),
                "lifetime (secs)", lifetime));

        if (Strings.isNullOrEmpty(lwm2mVersion)) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    QUERY_PARAM_LWM2M_VERSION + " query parameter must be non-empty"));
        } else if (!lwm2mVersion.startsWith("1.0")) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED,
                    "server supports LwM2M version 1.0.x only"));
        } else if (Strings.isNullOrEmpty(endpoint)) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    QUERY_PARAM_ENDPOINT + " query parameter must be non-empty"));
        } else if (lifetime <= 0) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    QUERY_PARAM_LIFETIME + " query parameter must be positive integer"));
        } else if (!supportedBindingModes.contains(bindingMode)) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED,
                    "server supports binding modes U and UQ only"));
        } else {
            final var identity = EndpointContextUtil.extractIdentity(ctx.getExchange().advanced().getRequest().getSourceContext());
            final var registration = new Registration.Builder(getRegistrationId(ctx.getOriginDevice()), endpoint, identity)
                    .lwM2mVersion(lwm2mVersion)
                    .lifeTimeInSec(lifetime)
                    .bindingMode(bindingMode)
                    .objectLinks(objectLinks)
                    .additionalRegistrationAttributes(Map.of(
                            TracingHelper.TAG_TENANT_ID.getKey(), ctx.getTenantId(),
                            TracingHelper.TAG_DEVICE_ID.getKey(), ctx.getAuthenticatedDevice().getDeviceId()))
                    .build();

            registrationStore.addRegistration(registration, currentSpan.context())
                .onSuccess(ok -> {
                    currentSpan.setTag(LwM2MUtils.TAG_LWM2M_REGISTRATION_ID, registration.getId());
                    ctx.getExchange().setLocationPath(String.format("%s/%s", getName(), registration.getId()));
                    result.complete(ResponseCode.CREATED);
                })
                .onFailure(t -> result.fail(t));
        }
        return result.future()
                .onFailure(t -> TracingHelper.logError(currentSpan, t))
                .onComplete(r -> currentSpan.finish());
    }

    private Future<ResponseCode> handleUpdate(
            final CoapContext ctx,
            final BindingMode bindingMode,
            final Long lifetime,
            final Link[] objectlinks,
            final String registrationId) {

        final Promise<ResponseCode> result = Promise.promise();

        final Span currentSpan = TracingHelper
                .buildChildSpan(getTracer(), ctx.getTracingContext(), "update LwM2M device registration", getAdapter().getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(TracingHelper.TAG_TENANT_ID, ctx.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, ctx.getOriginDevice().getDeviceId())
                .withTag(LwM2MUtils.TAG_LWM2M_REGISTRATION_ID, registrationId)
                .start();

        LOG.debug("processing LwM2M registration update request [tenant-id: {}, device-id: {}, registration ID: {}]",
                ctx.getTenantId(), ctx.getOriginDevice().getDeviceId(), registrationId);
        final Map<String, Object> items = new HashMap<>(2);
        Optional.ofNullable(bindingMode).ifPresent(bm -> items.put("binding mode", bm.name()));
        Optional.ofNullable(lifetime).ifPresent(lt -> items.put("lifetime (secs)", lt));
        currentSpan.log(items);

        if (lifetime != null && lifetime <= 0) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "lifetime query parameter must be positive integer"));
        } else if (bindingMode != null && !supportedBindingModes.contains(bindingMode)) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED,
                    "server supports binding modes U and UQ only"));
        } else {
            final var registrationUpdate = new RegistrationUpdate(
                    registrationId,
                    EndpointContextUtil.extractIdentity(ctx.getExchange().advanced().getRequest().getSourceContext()),
                    lifetime,
                    null,
                    bindingMode,
                    objectlinks,
                    null);

            registrationStore.updateRegistration(registrationUpdate, currentSpan.context())
                .onSuccess(ok -> result.complete(ResponseCode.CHANGED))
                .onFailure(t -> result.fail(t));
        }
        return result.future()
                .onFailure(t -> TracingHelper.logError(currentSpan, t))
                .onComplete(r -> currentSpan.finish());
    }

    /**
     * {@inheritDoc}
     *
     * Handles a request to delete a device's registration.
     */
    @Override
    protected Future<?> handleDelete(final CoapContext ctx) {

        final List<String> uri = ctx.getExchange().getRequestOptions().getUriPath();

        if (uri.size() == 2) {
            return handleDeregister(ctx, uri.get(1)).onSuccess(ctx::respondWithCode);
        } else {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_NOT_FOUND,
                    "request URI for deregistering is defined as /rd/${registrationId}"));
        }
    }

    private Future<ResponseCode> handleDeregister(final CoapContext ctx, final String registrationId) {

        final Device device = ctx.getAuthenticatedDevice();
        final Span currentSpan = TracingHelper
                .buildChildSpan(getTracer(), ctx.getTracingContext(), "deregister LwM2M device", getAdapter().getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(TracingHelper.TAG_TENANT_ID, device.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, device.getDeviceId())
                .withTag(LwM2MUtils.TAG_LWM2M_REGISTRATION_ID, registrationId)
                .start();

        LOG.debug("processing request for deregistration of {} using registration ID {}", device, registrationId);

        return registrationStore.removeRegistration(registrationId, currentSpan.context())
            .map(ResponseCode.DELETED)
            .onFailure(t -> TracingHelper.logError(currentSpan, t))
            .onComplete(r -> currentSpan.finish());
    }
}
