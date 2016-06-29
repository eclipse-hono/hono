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

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;

import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.impl.ProtonReceiverImpl;
import io.vertx.proton.impl.ProtonSenderImpl;

/**
 * Utility methods for working with Proton {@code Message}s.
 *
 */
public final class MessageHelper {

    /**
     * The name of the AMQP 1.0 message application property containing the id of the device that has reported the data
     * belongs to.
     */
    public static final String APP_PROPERTY_DEVICE_ID          = "device_id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the tenant the device that has
     * reported the data belongs to.
     */
    public static final String APP_PROPERTY_TENANT_ID          = "tenant_id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the resource a message is addressed at.
     */
    public static final String APP_PROPERTY_RESOURCE_ID          = "resource_id";

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

    public static String getDeviceIdAnnotation(final Message msg) {
        Objects.requireNonNull(msg);
        return getAnnotation(msg, APP_PROPERTY_DEVICE_ID);
    }

    public static String getTenantIdAnnotation(final Message msg) {
        Objects.requireNonNull(msg);
        return getAnnotation(msg, APP_PROPERTY_TENANT_ID);
    }

    public static Object getApplicationProperty(final ApplicationProperties props, final String name) {
        if (props == null) {
            return null;
        } else {
            return props.getValue().get(name);
        }
    }

    public static void addTenantId(final Message msg, final String tenantId) {
        addProperty(msg, APP_PROPERTY_TENANT_ID, tenantId);
    }

    public static void addDeviceId(final Message msg, final String deviceId) {
        addProperty(msg, APP_PROPERTY_DEVICE_ID, deviceId);
    }

    @SuppressWarnings("unchecked")
    public static void addProperty(final Message msg, final String key, final String value) {
        ApplicationProperties props = msg.getApplicationProperties();
        if (props == null) {
            props = new ApplicationProperties(new HashMap<String, Object>());
            msg.setApplicationProperties(props);
        }
        props.getValue().put(key, value);
    }

    public static void rejected(final ProtonDelivery delivery, final String error, final String description) {
        final ErrorCondition errorCondition = ProtonHelper.condition(error, description);
        final Rejected rejected = new Rejected();
        rejected.setError(errorCondition);
        delivery.disposition(rejected, true);
    }

    /**
     * Adds several AMQP 1.0 message <em>annotations</em> to the given message that are used to process/route the message.
     *
     * @param msg the message to add the message annotations to.
     * @param resourceIdentifier the resource identifier that will be added as annotation.
     */
    public static void annotate(final Message msg, final ResourceIdentifier resourceIdentifier) {
        MessageHelper.addAnnotation(msg, APP_PROPERTY_TENANT_ID, resourceIdentifier.getTenantId());
        MessageHelper.addAnnotation(msg, APP_PROPERTY_DEVICE_ID, resourceIdentifier.getDeviceId());
        MessageHelper.addAnnotation(msg, APP_PROPERTY_RESOURCE_ID, resourceIdentifier.toString());
    }

    /**
     * Adds a value for a symbol to an AMQP 1.0 message's <em>annotations</em>.
     * 
     * @param msg the message to add the symbol to.
     * @param key the name of the symbol to add a value for.
     * @param value the value to add.
     */
    public static void addAnnotation(final Message msg, final String key, final String value) {
        MessageAnnotations annotations = msg.getMessageAnnotations();
        if (annotations == null) {
            annotations = new MessageAnnotations(new HashMap<>());
            msg.setMessageAnnotations(annotations);
        }
        annotations.getValue().put(Symbol.getSymbol(key), value);
    }

    /**
     * Returns the value to which the specified key is mapped in the message annotations,
     * or {@code null} if the message annotations contain no mapping for the key.
     *
     * @param msg the message that contains the annotations.
     * @param key the name of the symbol to return a value for.
     */
    public static String getAnnotation(final Message msg, final String key) {
        MessageAnnotations annotations = msg.getMessageAnnotations();
        if (annotations == null) {
            return null;
        }
        return (String) annotations.getValue().get(Symbol.getSymbol(key));
    }

    public static String getLinkName(final ProtonLink<?> link) {
        if (link instanceof ProtonReceiverImpl) {
            return ((ProtonReceiverImpl) link).getName();
        } else if (link instanceof ProtonSenderImpl) {
            return ((ProtonSenderImpl) link).getName();
        } else {
            return "unknown";
        }
    }
}
