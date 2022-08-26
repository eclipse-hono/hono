/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.amqp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.util.DownstreamMessageProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonHelper;

/**
 * A factory for creating downstream AMQP 1.0 messages.
 */
public final class DownstreamAmqpMessageFactory {

    /**
     * The {@code JMS_AMQP_CONTENT_ENCODING} vendor property name.
     */
    public static final String JMS_VENDOR_PROPERTY_CONTENT_ENCODING = "JMS_AMQP_CONTENT_ENCODING";
    /**
     * The {@code JMS_AMQP_CONTENT_TYPE} vendor property name.
     */
    public static final String JMS_VENDOR_PROPERTY_CONTENT_TYPE = "JMS_AMQP_CONTENT_TYPE";

    private static final Logger LOG = LoggerFactory.getLogger(DownstreamAmqpMessageFactory.class);

    private DownstreamAmqpMessageFactory() {
        // prevent instantiation
    }

    /**
     * Creates a new AMQP 1.0 message.
     * <p>
     * This method creates a new {@code Message} and sets
     * <ul>
     * <li>its <em>to</em> property to the address consisting of the target's endpoint and tenant</li>
     * <li>its <em>creation-time</em> to the current system time,</li>
     * <li>its <em>content-type</em> to the given value,</li>
     * <li>its payload as an AMQP <em>Data</em> section</li>
     * </ul>
     *
     * In addition, this method
     * <ul>
     * <li>augments the message with the given message properties.</li>
     * <li>augments the message with missing (application) properties corresponding to the default properties
     * registered at the tenant and device level. Default properties defined at the device level take precedence
     * over properties with the same name defined at the tenant level.</li>
     * <li>optionally adds JMS vendor properties.</li>
     * <li>sets the message's <em>content-type</em> to the {@linkplain MessageHelper#CONTENT_TYPE_OCTET_STREAM fall
     * back content type}, if the payload is not {@code null} but the given content type is {@code null} and the
     * default properties do not contain a content type property.</li>
     * <li>sets the message's <em>ttl</em> header field based on the (device provided) <em>time-to-live</em> duration
     * as specified by the <a href="https://www.eclipse.org/hono/docs/api/tenant/#resource-limits-configuration-format">
     * Tenant API</a>.</li>
     * </ul>
     *
     * @param target The target address of the message. The target address is used to determine if the message
     *               represents an event or not.
     * @param contentType The content type describing the message's payload. If {@code null}, the message's
     *                    <em>content-type</em> property will either be derived from the given properties or
     *                    deviceDefaultProperties (if set there) or it will be set to the
     *                    {@linkplain MessageHelper#CONTENT_TYPE_OCTET_STREAM default content type} if the
     *                    given payload isn't {@code null}.
     * @param payload The message payload or {@code null} if the message has no payload.
     * @param tenant The information registered for the tenant that the device belongs to or {@code null}
     *               if no information about the tenant is available.
     * @param tenantLevelDefaults The default properties registered at the tenant level or {@code null}
     *                            if no default properties are registered for the tenant.
     * @param deviceLevelDefaults The device's default properties registered at the device level or {@code null}
     *                            if no default properties are registered for the device.
     * @param messageProperties Additional message properties or {@code null} if the message has no additional properties.
     * @param addJmsVendorProps {@code true} if
     *                          <a href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf">
     *                          JMS Vendor Properties</a> should be added to the message.

     * @return The newly created message.
     * @throws NullPointerException if target is {@code null}.
     */
    public static Message newMessage(
            final ResourceIdentifier target,
            final String contentType,
            final Buffer payload,
            final TenantObject tenant,
            final Map<String, Object> tenantLevelDefaults,
            final Map<String, Object> deviceLevelDefaults,
            final Map<String, Object> messageProperties,
            final boolean addJmsVendorProps) {

        Objects.requireNonNull(target);

        final Message message = ProtonHelper.message();
        AmqpUtils.setCreationTime(message);
        message.setAddress(target.getBasePath());
        AmqpUtils.annotate(message, target);

        Optional.ofNullable(payload)
            .map(Buffer::getBytes)
            .map(Binary::new)
            .map(Data::new)
            .ifPresent(message::setBody);

        final Map<String, Object> effectiveMessageProperties = Optional.ofNullable(messageProperties)
                .map(HashMap::new)
                .orElseGet(HashMap::new);
        Optional.ofNullable(contentType)
            .ifPresent(ct -> effectiveMessageProperties.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, ct));

        final DownstreamMessageProperties props = new DownstreamMessageProperties(
                target.getEndpoint(),
                tenantLevelDefaults,
                deviceLevelDefaults,
                effectiveMessageProperties,
                Optional.ofNullable(tenant)
                    .map(TenantObject::getResourceLimits)
                    .orElse(null));

        addDefaults(message, props.asMap());

        // set default content type if none has been set yet (also after applying properties and defaults)
        if (Strings.isNullOrEmpty(message.getContentType())) {
            if (message.getBody() != null) {
                message.setContentType(MessageHelper.CONTENT_TYPE_OCTET_STREAM);
            }
        }
        if (addJmsVendorProps) {
            addJmsVendorProperties(message);
        }
        return message;
    }

    /**
     * Adds JMS vendor properties defined by
     * <a href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf"> AMQP JMS
     * Mapping 1.0</a> as AMQP 1.0 application properties to a given message.
     * <p>
     * The following vendor properties are added (if the message has a corresponding non-null value set):
     * <ul>
     * <li>{@link #JMS_VENDOR_PROPERTY_CONTENT_TYPE}</li>
     * <li>{@link #JMS_VENDOR_PROPERTY_CONTENT_ENCODING}</li>
     * </ul>
     *
     * @param msg the message to add the vendor properties to.
     */
    public static void addJmsVendorProperties(final Message msg) {
        if (!Strings.isNullOrEmpty(msg.getContentType())) {
            AmqpUtils.addProperty(msg, JMS_VENDOR_PROPERTY_CONTENT_TYPE, msg.getContentType());
        }
        if (!Strings.isNullOrEmpty(msg.getContentEncoding())) {
            AmqpUtils.addProperty(msg, JMS_VENDOR_PROPERTY_CONTENT_ENCODING, msg.getContentEncoding());
        }
    }

    /**
     * Adds default properties to an AMQP message.
     * <p>
     * This method identifies AMQP 1.0 standard *properties* based on their names. All other properties are being
     * added to the message's *application-properties*.
     *
     * @param message The message to add the properties to.
     * @param properties The properties to set or {@code null} if no properties should be added.
     * @return The message with the properties set.
     * @throws NullPointerException if message is {@code null}.
     * @throws IllegalArgumentException if target is {@code null} and the message does not have an address set.
     */
    public static Message addDefaults(
            final Message message,
            final Map<String, Object> properties) {

        Objects.requireNonNull(message);

        if (properties == null) {
            return message;
        }

        properties.entrySet().forEach(prop -> {

            switch (prop.getKey()) {
            case MessageHelper.ANNOTATION_X_OPT_RETAIN:
                AmqpUtils.addAnnotation(message, prop.getKey(), prop.getValue());
                break;
            case MessageHelper.SYS_HEADER_PROPERTY_TTL:
                if (Number.class.isInstance(prop.getValue())) {
                    message.setTtl(((Number) prop.getValue()).longValue());
                }
                break;
            case MessageHelper.SYS_PROPERTY_CONTENT_TYPE:
                if (String.class.isInstance(prop.getValue())) {
                    message.setContentType((String) prop.getValue());
                }
                break;
            case MessageHelper.SYS_PROPERTY_CONTENT_ENCODING:
                if (String.class.isInstance(prop.getValue())) {
                    message.setContentEncoding((String) prop.getValue());
                }
                break;
            case MessageHelper.SYS_HEADER_PROPERTY_DELIVERY_COUNT:
            case MessageHelper.SYS_HEADER_PROPERTY_DURABLE:
            case MessageHelper.SYS_HEADER_PROPERTY_FIRST_ACQUIRER:
            case MessageHelper.SYS_HEADER_PROPERTY_PRIORITY:
            case MessageHelper.SYS_PROPERTY_ABSOLUTE_EXPIRY_TIME:
            case MessageHelper.SYS_PROPERTY_CORRELATION_ID:
            case MessageHelper.SYS_PROPERTY_CREATION_TIME:
            case MessageHelper.SYS_PROPERTY_GROUP_ID:
            case MessageHelper.SYS_PROPERTY_GROUP_SEQUENCE:
            case MessageHelper.SYS_PROPERTY_MESSAGE_ID:
            case MessageHelper.SYS_PROPERTY_REPLY_TO:
            case MessageHelper.SYS_PROPERTY_REPLY_TO_GROUP_ID:
            case MessageHelper.SYS_PROPERTY_SUBJECT:
            case MessageHelper.SYS_PROPERTY_TO:
            case MessageHelper.SYS_PROPERTY_USER_ID:
                // these standard properties cannot be set using defaults
                LOG.debug("ignoring default property [{}] registered for device", prop.getKey());
                break;
            default:
                // add all other defaults as application properties
                AmqpUtils.addProperty(message, prop.getKey(), prop.getValue());
            }
        });

        return message;
    }
}
