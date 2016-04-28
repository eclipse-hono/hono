/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import java.util.HashMap;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;

/**
 * Utility methods for working with Proton {@code Message}s.
 *
 */
public final class MessageHelper {

    /**
     * The name of the AMQP 1.0 message application property containing the id of the device that has reported the data
     * belongs to.
     */
    public static final String APP_PROPERTY_DEVICE_ID          = "device-id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the tenant the device that has
     * reported the data belongs to.
     */
    public static final String APP_PROPERTY_TENANT_ID          = "tenant-id";

    private MessageHelper() {
    }

    public static String getDeviceId(final Message msg) {
        Objects.requireNonNull(msg);
        return (String) getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_DEVICE_ID);
    }

    public static String getTenantId(final Message msg) {
        Objects.requireNonNull(msg);
        return (String) getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_TENANT_ID);
    }

    private static Object getApplicationProperty(final ApplicationProperties props, final String name) {
        if (props == null) {
            return null;
        } else {
            return props.getValue().get(name);
        }
    }

    @SuppressWarnings("unchecked")
    public static void addTenantId(final Message msg, final String tenantId) {
        ApplicationProperties props = msg.getApplicationProperties();
        if (props == null) {
            props = new ApplicationProperties(new HashMap<String, Object>());
        }
        props.getValue().put(APP_PROPERTY_TENANT_ID, tenantId);
    }
}
