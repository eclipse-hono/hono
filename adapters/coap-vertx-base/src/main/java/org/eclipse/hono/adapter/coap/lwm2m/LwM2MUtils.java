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

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.leshan.server.registration.Registration;

/**
 * Helper methods for LwM2M.
 *
 */
final class LwM2MUtils {

    static final String TAG_LWM2M_REGISTRATION_ID = "lwm2m_registration_id";
    static final String TAG_LWM2M_RESOURCE = "path";

    private LwM2MUtils() {
        // prevent instantiation
    }

    static Device getDevice(final Registration registration) {
        return new Device(getTenantId(registration), getDeviceId(registration));
    }

    static String getTenantId(final Registration registration) {
        return registration.getAdditionalRegistrationAttributes().get(TracingHelper.TAG_TENANT_ID.getKey());
    }

    static String getDeviceId(final Registration registration) {
        return registration.getAdditionalRegistrationAttributes().get(TracingHelper.TAG_DEVICE_ID.getKey());
    }


}
