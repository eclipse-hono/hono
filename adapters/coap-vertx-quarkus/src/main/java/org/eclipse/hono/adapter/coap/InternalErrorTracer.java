/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.auth.AbstractExtensiblePrincipal;
import org.eclipse.californium.elements.auth.AdditionalInfo;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;

/**
 * There are some errors in the CoAP protocol that occur on a lower level, for example {@link BlockwiseLayer}.
 * Californium returns an error response to the client but this is not propagated to the Hono CoAP adapter. This is
 * the purpose of this interceptor - to track if such errors occur and report a trace for them in Jaeger.
 */
public class InternalErrorTracer extends MessageInterceptorAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(InternalErrorTracer.class);

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
            final EndpointContext context = response.getDestinationContext();
            final AdditionalInfo honoInfo = Optional.ofNullable(context.getPeerIdentity())
                    .filter(AbstractExtensiblePrincipal.class::isInstance)
                    .map(AbstractExtensiblePrincipal.class::cast)
                    .map(AbstractExtensiblePrincipal::getExtendedInfo)
                    .orElse(null);
            if (honoInfo == null) {
                LOG.debug("CoAP internal error has occured, but Hono context info is not set in the response [{}]",
                        response.getCode().name());
                return;
            }

            final String authId = honoInfo.get(DeviceInfoSupplier.EXT_INFO_KEY_HONO_AUTH_ID, String.class);
            final Device deviceInfo = honoInfo.get(DeviceInfoSupplier.EXT_INFO_KEY_HONO_DEVICE, Device.class);
            final String deviceId = deviceInfo.getDeviceId();
            final String tenantId = deviceInfo.getTenantId();
            final Span span = TracingHelper
                    .buildServerChildSpan(tracer, null, response.getCode().toString(), coapAdapterName)
                    .withTag(TracingHelper.TAG_AUTH_ID, authId)
                    .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                    .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                    .withTag(CoapConstants.TAG_COAP_MESSAGE_TYPE, response.getType().name())
                    .start();
            TracingHelper.logError(span, "CoAP Internal error");
            span.finish();
        }
    }
}
