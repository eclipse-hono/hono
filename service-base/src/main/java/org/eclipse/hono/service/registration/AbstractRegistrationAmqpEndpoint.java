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
package org.eclipse.hono.service.registration;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AbstractRequestResponseEndpoint;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * An {@code AmqpEndpoint} for managing device registration information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/device-registration/">Device Registration API</a>.
 * It receives AMQP 1.0 messages representing requests and and forward them to the registration service implementation.
 * The outcome is then returned to the peer in a response message.
 */
public abstract class AbstractRegistrationAmqpEndpoint extends AbstractRequestResponseEndpoint<ServiceConfigProperties> {

    private static final String SPAN_NAME_ASSERT_DEVICE_REGISTRATION = "assert Device Registration";

    /**
     * Creates a new registration endpoint for a vertx instance.
     *
     * @param vertx The vertx instance to use.
     */
    public AbstractRegistrationAmqpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    @Override
    public final String getName() {
        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    /**
     * The service to forward requests to.
     *
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract RegistrationService getService();

    @Override
    protected Future<Message> handleRequestMessage(final Message requestMessage, final ResourceIdentifier targetAddress) {

        Objects.requireNonNull(requestMessage);
        final String operation = requestMessage.getSubject();

        switch (operation) {
            case RegistrationConstants.ACTION_ASSERT:
                return processAssertRequest(requestMessage, targetAddress);
            default:
                return processCustomRegistrationMessage(requestMessage);
        }
    }

    private Future<Message> processAssertRequest(final Message request, final ResourceIdentifier targetAddress) {

        final String tenantId = targetAddress.getTenantId();
        final String deviceId = MessageHelper.getDeviceId(request);
        final String gatewayId = MessageHelper.getGatewayId(request);

        final Span span = TracingHelper.buildServerChildSpan(tracer,
                TracingHelper.extractSpanContext(tracer, request),
                SPAN_NAME_ASSERT_DEVICE_REGISTRATION,
                getClass().getSimpleName()
        ).start();

        final Future<Message> resultFuture;
        if (tenantId == null || deviceId == null) {
            TracingHelper.logError(span, "missing tenant and/or device");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            TracingHelper.TAG_TENANT_ID.set(span, tenantId);
            TracingHelper.TAG_DEVICE_ID.set(span, deviceId);

            final Future<RegistrationResult> result;
            if (gatewayId == null) {
                log.debug("asserting registration of device [{}] with tenant [{}]", deviceId, tenantId);
                result = getService().assertRegistration(tenantId, deviceId, span);
            } else {
                TracingHelper.TAG_GATEWAY_ID.set(span, gatewayId);
                log.debug("asserting registration of device [{}] with tenant [{}] for gateway [{}]",
                        deviceId, tenantId, gatewayId);
                result = getService().assertRegistration(tenantId, deviceId, gatewayId, span);
            }
            resultFuture = result.map(res -> RegistrationConstants.getAmqpReply(
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
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<Message> processCustomRegistrationMessage(final Message request) {
        log.debug("invalid operation in request message [{}]", request.getSubject());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return RegistrationMessageFilter.verify(linkTarget, msg);
    }

}
