/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap;

import java.util.Optional;

import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.interceptors.MessageInterceptorAdapter;
import org.eclipse.californium.core.network.stack.BlockwiseLayer;
import org.eclipse.californium.elements.auth.AbstractExtensiblePrincipal;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Tracer;

/**
 * There are some errors in the CoAP protocol that occur on a lower level, for example {@link BlockwiseLayer}.
 * Californium returns an error response to the client but this is not propagated to the Hono CoAP adapter. This is
 * the purpose of this interceptor - to track if such errors occur and report a trace for them in Jaeger.
 */
public class InternalErrorTracer extends MessageInterceptorAdapter {

    private final String coapAdapterName;
    private final Tracer tracer;

    /**
     * @param coapAdapterName Hono CoAP adapter component name.
     * @param tracer Open Tracing tracer to use.
     */
    public InternalErrorTracer(final String coapAdapterName, final Tracer tracer) {
        this.coapAdapterName = coapAdapterName;
        this.tracer = tracer;
    }

    @Override
    public void sendResponse(final Response response) {
        if (response.isInternal() && response.isError()) {
            final var spanBuilder = TracingHelper
                    .buildServerChildSpan(tracer, null, "internal error", coapAdapterName)
                    .withTag(CoapConstants.TAG_COAP_MESSAGE_TYPE, response.getType().name())
                    .withTag(CoapConstants.TAG_COAP_RESPONSE_CODE, response.getCode().toString());

            Optional.ofNullable(response.getDestinationContext().getPeerIdentity())
                    .filter(AbstractExtensiblePrincipal.class::isInstance)
                    .map(AbstractExtensiblePrincipal.class::cast)
                    .map(AbstractExtensiblePrincipal::getExtendedInfo)
                    .ifPresent(info -> {
                        final String authId = info.get(DeviceInfoSupplier.EXT_INFO_KEY_HONO_AUTH_ID, String.class);
                        final var deviceInfo = info.get(DeviceInfoSupplier.EXT_INFO_KEY_HONO_DEVICE, DeviceUser.class);
                        spanBuilder
                            .withTag(TracingHelper.TAG_AUTH_ID, authId)
                            .withTag(TracingHelper.TAG_DEVICE_ID, deviceInfo.getDeviceId())
                            .withTag(TracingHelper.TAG_TENANT_ID, deviceInfo.getTenantId());
                    });
            final var span = spanBuilder.start();
            final String msg = Optional.ofNullable(response.getPayloadString())
                    .orElseGet(() -> {
                        switch (response.getCode()) {
                        case REQUEST_ENTITY_INCOMPLETE:
                            return "blockwise transfer of request body timed out";
                        case REQUEST_ENTITY_TOO_LARGE:
                            return "request body exceeds maximum size";
                        default:
                            return "error while processing request";
                        }
                    });

            TracingHelper.logError(span, msg);
            span.finish();
        }
    }
}
