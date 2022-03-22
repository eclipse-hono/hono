/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.registration;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AbstractDelegatingRequestResponseEndpoint;
import org.eclipse.hono.service.amqp.AbstractRequestResponseEndpoint;
import org.eclipse.hono.service.amqp.GenericRequestMessageFilter;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * An {@code AmqpEndpoint} for managing device registration information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/device-registration/">Device Registration API</a>.
 * It receives AMQP 1.0 messages representing requests and and forwards them to the registration service implementation.
 * The outcome is then returned to the peer in a response message.
 *
 * @param <S> The type of service this endpoint delegates to.
 */
public class DelegatingRegistrationAmqpEndpoint<S extends RegistrationService> extends AbstractDelegatingRequestResponseEndpoint<S, ServiceConfigProperties> {

    private static final String SPAN_NAME_ASSERT_DEVICE_REGISTRATION = "assert Device Registration";

    /**
     * Creates a new registration endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public DelegatingRegistrationAmqpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
    }

    @Override
    public final String getName() {
        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    protected Future<Message> handleRequestMessage(final Message requestMessage, final ResourceIdentifier targetAddress,
            final SpanContext spanContext) {

        Objects.requireNonNull(requestMessage);
        Objects.requireNonNull(targetAddress);

        final String operation = requestMessage.getSubject();
        if (operation == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }

        switch (operation) {
            case RegistrationConstants.ACTION_ASSERT:
                return processAssertRequest(requestMessage, targetAddress, spanContext);
            default:
                return processCustomRegistrationMessage(requestMessage, spanContext);
        }
    }

    private Future<Message> processAssertRequest(final Message request, final ResourceIdentifier targetAddress,
            final SpanContext spanContext) {

        final String tenantId = targetAddress.getTenantId();
        final String deviceId = AmqpUtils.getDeviceId(request);
        final String gatewayId = AmqpUtils.getGatewayId(request);

        final Span span = TracingHelper.buildServerChildSpan(tracer,
                spanContext,
                SPAN_NAME_ASSERT_DEVICE_REGISTRATION,
                getClass().getSimpleName()
        ).start();

        TracingHelper.setDeviceTags(span, tenantId, deviceId);

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null) {
            TracingHelper.logError(span, "missing tenant and/or device");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {

            final Future<RegistrationResult> result;
            if (gatewayId == null) {
                logger.debug("asserting registration of device [tenant: {}, device-id: {}]", tenantId, deviceId);
                result = getService().assertRegistration(tenantId, deviceId, span);
            } else {
                TracingHelper.TAG_GATEWAY_ID.set(span, gatewayId);
                logger.debug("asserting registration of device [tenant: {}, device-id: {}] for gateway [{}]",
                        tenantId, deviceId, gatewayId);
                result = getService().assertRegistration(tenantId, deviceId, gatewayId, span);
            }
            resultFuture = result.map(res -> AbstractRequestResponseEndpoint.getAmqpReply(
                    RegistrationConstants.REGISTRATION_ENDPOINT,
                    tenantId,
                    request,
                    res
            ));
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Device Registration API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @param spanContext The span context representing the request to be processed.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<Message> processCustomRegistrationMessage(final Message request, final SpanContext spanContext) {
        logger.debug("invalid operation in request message [{}]", request.getSubject());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return GenericRequestMessageFilter.isValidRequestMessage(msg);
    }

}
