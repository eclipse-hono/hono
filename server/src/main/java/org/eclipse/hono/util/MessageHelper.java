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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;

/**
 * @author wa20230
 *
 */
public final class MessageHelper {

    public static final String PREFIX_APPLICATION_PROPERTY = "app.";
    public static final String PROPERTY_CONTENT_TYPE = "content-type";
    public static final String PROPERTY_CORRELATION_ID = "correlation-id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the device that has reported the data
     * belongs to.
     */
    public static final String APP_PROPERTY_DEVICE_ID          = "device-id";
    public static final String APP_PROPERTY_DEVICE_ID_PREFIXED = PREFIX_APPLICATION_PROPERTY + APP_PROPERTY_DEVICE_ID;
    public static final String PROPERTY_MESSAGE_ID   = "message-id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the tenant the device that has
     * reported the data belongs to.
     */
    public static final String APP_PROPERTY_TENANT_ID          = "tenant-id";
    public static final String APP_PROPERTY_TENANT_ID_PREFIXED = PREFIX_APPLICATION_PROPERTY + APP_PROPERTY_TENANT_ID;

    private MessageHelper() {
    }

    public static String getContentType(final Message<? extends Object> msg) {
        Objects.requireNonNull(msg);
        return msg.headers().get(PROPERTY_CONTENT_TYPE);
    }

    public static String getCorrelationId(final Message<? extends Object> msg) {
        Objects.requireNonNull(msg);
        return msg.headers().get(PROPERTY_CORRELATION_ID);
    }

    public static String getDeviceId(final Message<? extends Object> msg) {
        Objects.requireNonNull(msg);
        return msg.headers().get(APP_PROPERTY_DEVICE_ID_PREFIXED);
    }

    public static String getDeviceId(final org.apache.qpid.proton.message.Message msg) {
        Objects.requireNonNull(msg);
        return (String) getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_DEVICE_ID);
    }
    public static String getMessageId(final Message<? extends Object> msg) {
        Objects.requireNonNull(msg);
        return msg.headers().get(PROPERTY_MESSAGE_ID);
    }

    public static String getMessageId(final org.apache.qpid.proton.message.Message msg) {
        Objects.requireNonNull(msg);
        return (String) msg.getMessageId();
    }

    public static String getTenantId(final Message<? extends Object> msg) {
        Objects.requireNonNull(msg);
        return msg.headers().get(APP_PROPERTY_TENANT_ID_PREFIXED);
    }

    public static String getTenantId(final org.apache.qpid.proton.message.Message msg) {
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
    public static void addTenantId(final org.apache.qpid.proton.message.Message msg, final String tenantId) {
        ApplicationProperties props = msg.getApplicationProperties();
        if (props == null) {
            props = new ApplicationProperties(new HashMap<String, Object>());
        }
        props.getValue().put(APP_PROPERTY_TENANT_ID, tenantId);
    }

    public static DeliveryOptions toVertxDeliveryOptions(final org.apache.qpid.proton.message.Message msg) {
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader(PROPERTY_CONTENT_TYPE, msg.getContentType());
        Optional.ofNullable(msg.getCorrelationId())
                .ifPresent(correlationId -> options.addHeader(PROPERTY_CORRELATION_ID, (String) correlationId));
        Optional.ofNullable(msg.getMessageId())
                .ifPresent(msgId -> options.addHeader(PROPERTY_MESSAGE_ID, (String) msgId));

        Optional.ofNullable(msg.getApplicationProperties())
                .ifPresent(appProps -> {
                    for (Object key : appProps.getValue().keySet()) {
                        if (key instanceof String) {
                            Object val = appProps.getValue().get(key);
                            if (val instanceof String) {
                                options.addHeader(PREFIX_APPLICATION_PROPERTY + (String) key,
                                        (String) appProps.getValue().get(key));
                            }
                        }
                    }
                });
        return options;
    }

    public static org.apache.qpid.proton.message.Message fromVertxMessage(final Message<Buffer> msg) {
        org.apache.qpid.proton.message.Message result = Proton.message();
        result.setBody(new Data(new Binary(msg.body().getBytes())));
        Map<String, Object> appProps = new HashMap<String, Object>();
        for (Entry<String, String> entry : msg.headers()) {
            if (entry.getKey().startsWith(PREFIX_APPLICATION_PROPERTY)) {
                appProps.put(entry.getKey().substring(PREFIX_APPLICATION_PROPERTY.length()), entry.getValue());
            } else if (entry.getKey().equals(PROPERTY_CONTENT_TYPE)) {
                result.setContentType(entry.getValue());
            } else if (entry.getKey().equals(PROPERTY_CORRELATION_ID)) {
                result.setCorrelationId(entry.getValue());
            } else if (entry.getKey().equals(PROPERTY_MESSAGE_ID)) {
                result.setMessageId(entry.getValue());
            }
        }
        if (!appProps.isEmpty()) {
            result.setApplicationProperties(new ApplicationProperties(appProps));
        }
        return result;
    }
}
