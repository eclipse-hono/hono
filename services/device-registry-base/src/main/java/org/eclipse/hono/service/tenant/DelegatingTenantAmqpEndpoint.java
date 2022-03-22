/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.tenant;

import java.net.HttpURLConnection;
import java.util.Objects;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AbstractDelegatingRequestResponseEndpoint;
import org.eclipse.hono.service.amqp.AbstractRequestResponseEndpoint;
import org.eclipse.hono.service.amqp.GenericRequestMessageFilter;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * An {@code AmqpEndpoint} for managing tenant information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/tenant/">Tenant API</a>. It receives AMQP 1.0
 * messages representing requests and sends them to an address on the vertx event bus for processing. The outcome is
 * then returned to the peer in a response message.
 *
 * @param <S> The type of service this endpoint delegates to.
 */
public class DelegatingTenantAmqpEndpoint<S extends TenantService>
    extends AbstractDelegatingRequestResponseEndpoint<S, ServiceConfigProperties> {

    private static final String SPAN_NAME_GET_TENANT = "get Tenant";

    private static final String TAG_SUBJECT_DN_NAME = "subject_dn_name";

    /**
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public DelegatingTenantAmqpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
    }

    @Override
    public final String getName() {
        return TenantConstants.TENANT_ENDPOINT;
    }

    @Override
    protected Future<Message> handleRequestMessage(final Message requestMessage, final ResourceIdentifier targetAddress,
            final SpanContext spanContext) {

        Objects.requireNonNull(requestMessage);
        Objects.requireNonNull(targetAddress);

        switch (TenantConstants.TenantAction.from(requestMessage.getSubject())) {
            case get:
                return processGetRequest(requestMessage, spanContext);
            default:
                return processCustomTenantMessage(requestMessage, spanContext);
        }
    }

    private Future<Message> processGetRequest(final Message request, final SpanContext spanContext) {

        final String tenantId = AmqpUtils.getTenantId(request);

        final Span span = TracingHelper.buildServerChildSpan(tracer,
                spanContext,
                SPAN_NAME_GET_TENANT,
                getClass().getSimpleName())
                .start();

        final JsonObject payload;
        try {
            payload = AmqpUtils.getJsonPayload(request);
        } catch (final DecodeException e) {
            logger.debug("failed to decode AMQP request message", e);
            return finishSpanOnFutureCompletion(span, Future.failedFuture(
                    new ClientErrorException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "request message body contains malformed JSON")));
        }

        final Future<Message> resultFuture;
        if (tenantId == null && payload == null) {
            TracingHelper.logError(span, "request does not contain any query parameters");
            logger.debug("request does not contain any query parameters");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));

        } else if (tenantId != null) {

            // deprecated API
            logger.debug("retrieving tenant [{}] using deprecated variant of get tenant request", tenantId);
            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            span.log("using deprecated variant of get tenant request");
            // span will be finished in processGetByIdRequest
            resultFuture = processGetByIdRequest(request, tenantId, span);

        } else {

            final String tenantIdFromPayload = getTypesafeValueForField(String.class, payload,
                    TenantConstants.FIELD_PAYLOAD_TENANT_ID);
            final String subjectDn = getTypesafeValueForField(String.class, payload,
                    TenantConstants.FIELD_PAYLOAD_SUBJECT_DN);

            if (tenantIdFromPayload == null && subjectDn == null) {
                TracingHelper.logError(span, "request does not contain any query parameters");
                logger.debug("payload does not contain any query parameters");
                resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            } else if (tenantIdFromPayload != null) {
                logger.debug("retrieving tenant [id: {}]", tenantIdFromPayload);
                TracingHelper.TAG_TENANT_ID.set(span, tenantIdFromPayload);
                resultFuture = processGetByIdRequest(request, tenantIdFromPayload, span);
            } else {
                span.setTag(TAG_SUBJECT_DN_NAME, subjectDn);
                resultFuture = processGetByCaRequest(request, subjectDn, span);
            }
        }

        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    private Future<Message> processGetByIdRequest(final Message request, final String tenantId,
            final Span span) {

        return getService().get(tenantId, span)
                .map(tr -> AbstractRequestResponseEndpoint.getAmqpReply(
                        TenantConstants.TENANT_ENDPOINT,
                        tenantId,
                        request,
                        tr));
    }

    private Future<Message> processGetByCaRequest(final Message request, final String subjectDn,
            final Span span) {

        try {
            final X500Principal dn = new X500Principal(subjectDn);
            logger.debug("retrieving tenant [subject DN: {}]", subjectDn);
            return getService().get(dn, span).map(tr -> {
                String tenantId = null;
                if (tr.isOk() && tr.getPayload() != null) {
                    tenantId = getTypesafeValueForField(String.class, tr.getPayload(),
                            TenantConstants.FIELD_PAYLOAD_TENANT_ID);
                    TracingHelper.TAG_TENANT_ID.set(span, tenantId);
                }
                return AbstractRequestResponseEndpoint.getAmqpReply(
                        TenantConstants.TENANT_ENDPOINT,
                        tenantId,
                        request,
                        tr);
            });
        } catch (final IllegalArgumentException e) {
            TracingHelper.logError(span, "illegal subject DN provided by client: " + subjectDn);
            // the given subject DN is invalid
            logger.debug("cannot parse subject DN [{}] provided by client", subjectDn);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Tenant API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @param spanContext The span context representing the request to be processed.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<Message> processCustomTenantMessage(final Message request, final SpanContext spanContext) {
        logger.debug("invalid operation in request message [{}]", request.getSubject());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    /**
     * Verifies that a response only contains tenant information that the
     * client is authorized to retrieve.
     * <p>
     * If the response does not contain a tenant ID nor a payload, then the
     * returned future will succeed with the response <em>as-is</em>.
     * Otherwise the tenant ID is used together with the endpoint and operation
     * name to check the client's authority to retrieve the data. If the client
     * is authorized, the returned future will succeed with the response as-is,
     * otherwise the future will fail with a {@link ClientErrorException} containing a
     * <em>403 Forbidden</em> status.
     */
    @Override
    protected Future<Message> filterResponse(
            final HonoUser clientPrincipal,
            final Message request, final Message response) {

        Objects.requireNonNull(clientPrincipal);
        Objects.requireNonNull(response);

        final String tenantId = AmqpUtils.getTenantId(response);
        final JsonObject payload = AmqpUtils.getJsonPayload(response);

        if (tenantId == null || payload == null) {
            return Future.succeededFuture(response);
        } else {
            // verify that payload contains tenant that the client is authorized for
            final var resourceId = ResourceIdentifier.from(TenantConstants.TENANT_ENDPOINT, tenantId, null);
            return getAuthorizationService().isAuthorized(clientPrincipal, resourceId, request.getSubject())
                    .map(isAuthorized -> {
                        if (isAuthorized) {
                            return response;
                        } else {
                            throw new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN);
                        }
                    });
        }
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return GenericRequestMessageFilter.isValidRequestMessage(msg);
    }

    /**
     * Checks if a resource identifier constitutes a valid reply-to address
     * for the Tenant service.
     *
     * @param replyToAddress The address to check.
     * @return {@code true} if the address contains two segments.
     */
    @Override
    protected boolean isValidReplyToAddress(final ResourceIdentifier replyToAddress) {

        if (replyToAddress == null) {
            return false;
        } else {
            return replyToAddress.length() >= 2;
        }
    }

    /**
     * Checks if the client is authorized to invoke an operation.
     * <p>
     * If the request does not include a <em>tenant_id</em> application property
     * then the request is authorized by default. This behavior allows clients to
     * invoke operations that do not require a tenant ID as a parameter.
     * <p>
     * If the request does contain a tenant ID parameter in its application properties
     * then this tenant ID is used for the authorization check together with the
     * endpoint and operation name.
     *
     * @param clientPrincipal The client.
     * @param resource The resource the operation belongs to.
     * @param request The message for which the authorization shall be checked.
     * @return The outcome of the check.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @Override
    protected Future<Boolean> isAuthorized(
            final HonoUser clientPrincipal,
            final ResourceIdentifier resource,
            final Message request) {

        Objects.requireNonNull(request);

        final String tenantId = AmqpUtils.getTenantId(request);
        if (tenantId == null) {
            // delegate authorization check to filterResource operation
            return Future.succeededFuture(Boolean.TRUE);
        } else {
            final var specificTenantAddress = ResourceIdentifier.fromPath(resource.getEndpoint(), tenantId);
            return getAuthorizationService().isAuthorized(clientPrincipal, specificTenantAddress, request.getSubject());
        }
    }

}
