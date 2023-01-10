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
package org.eclipse.hono.util;

/**
 * Constants for working with Hono API messages.
 *
 */
public final class MessageHelper {

    /**
     * The name of the AMQP 1.0 message annotation that is used to indicate that the sender of the
     * message intended the message to be <em>retained</em> in the MQTT sense. The value of the
     * property is a boolean. If a message does not contain this annotation, then a consumer must process
     * the message as if the annotation exists and has a value of {@code false}.
     */
    public static final String ANNOTATION_X_OPT_RETAIN = "x-opt-retain";

    /**
     * The name of the AMQP 1.0 message application property containing the caching directive to follow for the body of
     * the message.
     */
    public static final String APP_PROPERTY_CACHE_CONTROL = "cache_control";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the device that has reported the data
     * belongs to.
     */
    public static final String APP_PROPERTY_DEVICE_ID = "device_id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the gateway that wants to report data
     * on behalf of another device.
     */
    public static final String APP_PROPERTY_GATEWAY_ID = "gateway_id";
    /**
     * The name of the AMQP 1.0 message application property containing the flag denoting an event sent whenever
     * a device was auto-provisioned.
     */
    public static final String APP_PROPERTY_REGISTRATION_STATUS = "hono_registration_status";
    /**
     * The name of the AMQP 1.0 message application property containing a lifespan value in seconds.
     */
    public static final String APP_PROPERTY_LIFESPAN = "lifespan";
    /**
     * The name of the AMQP 1.0 application property that is used to convey the address that a message has been
     * originally published to by a device.
     */
    public static final String APP_PROPERTY_ORIG_ADDRESS = "orig_address";
    /**
     * The name of the AMQP 1.0 message application property containing the name of the protocol adapter over which an
     * uploaded message has originally been received.
     */
    public static final String APP_PROPERTY_ORIG_ADAPTER = "orig_adapter";
    /**
     * The name of the AMQP 1.0 message application property containing the QoS level of the message as set by the
     * device.
     */
    public static final String APP_PROPERTY_QOS = "qos";
    /**
     * The name of the AMQP 1.0 message application property containing the resource a message is addressed at.
     */
    public static final String APP_PROPERTY_RESOURCE = "resource";
    /**
     * The name of the AMQP 1.0 message application property containing the status code indicating the outcome of
     * processing a request.
     */
    public static final String APP_PROPERTY_STATUS = "status";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the tenant the device that has
     * reported the data belongs to.
     */
    public static final String APP_PROPERTY_TENANT_ID = "tenant_id";
    /**
     * The name of the AMQP 1.0 message application property containing the resource version expected by the client.
     */
    public static final String APP_PROPERTY_RESOURCE_VERSION = "resource_version";
    /**
     * The name of the AMQP 1.0 message application property containing the gateway through which a command is sent.
     */
    public static final String APP_PROPERTY_CMD_VIA = "via";

    /**
     * The name of the AMQP 1.0 message application property containing flag if connection event should be sent.
     */
    public static final String APP_PROPERTY_SEND_EVENT = "send_event";

    /**
     * The AMQP 1.0 <em>delivery-count</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_DELIVERY_COUNT = "delivery-count";
    /**
     * The AMQP 1.0 <em>durable</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_DURABLE = "durable";
    /**
     * The AMQP 1.0 <em>first-acquirer</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_FIRST_ACQUIRER = "first-acquirer";
    /**
     * The AMQP 1.0 <em>priority</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_PRIORITY = "priority";
    /**
     * The AMQP 1.0 <em>ttl</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_TTL = "ttl";

    /**
     * The AMQP 1.0 <em>absolute-expiry-time</em> message property.
     */
    public static final String SYS_PROPERTY_ABSOLUTE_EXPIRY_TIME = "absolute-expiry-time";
    /**
     * The AMQP 1.0 <em>content-encoding</em> message property.
     */
    public static final String SYS_PROPERTY_CONTENT_ENCODING = "content-encoding";
    /**
     * The AMQP 1.0 <em>content-type</em> message property.
     */
    public static final String SYS_PROPERTY_CONTENT_TYPE = "content-type";
    /**
     * The AMQP 1.0 <em>correlation-id</em> message property.
     */
    public static final String SYS_PROPERTY_CORRELATION_ID = "correlation-id";
    /**
     * The AMQP 1.0 <em>creation-time</em> message property.
     */
    public static final String SYS_PROPERTY_CREATION_TIME = "creation-time";
    /**
     * The AMQP 1.0 <em>group-id</em> message property.
     */
    public static final String SYS_PROPERTY_GROUP_ID = "group-id";
    /**
     * The AMQP 1.0 <em>group-sequence</em> message property.
     */
    public static final String SYS_PROPERTY_GROUP_SEQUENCE = "group-sequence";
    /**
     * The AMQP 1.0 <em>message-id</em> message property.
     */
    public static final String SYS_PROPERTY_MESSAGE_ID = "message-id";
    /**
     * The AMQP 1.0 <em>reply-to</em> message property.
     */
    public static final String SYS_PROPERTY_REPLY_TO = "reply-to";
    /**
     * The AMQP 1.0 <em>reply-to-group-id</em> message property.
     */
    public static final String SYS_PROPERTY_REPLY_TO_GROUP_ID = "reply-to-group-id";
    /**
     * The AMQP 1.0 <em>subject</em> message property.
     */
    public static final String SYS_PROPERTY_SUBJECT = "subject";
    /**
     * The AMQP 1.0 <em>user-id</em> message property.
     */
    public static final String SYS_PROPERTY_USER_ID = "user-id";
    /**
     * The AMQP 1.0 <em>to</em> message property.
     */
    public static final String SYS_PROPERTY_TO = "to";

    /**
     * The time-til-disconnect value to use for indicating that a device will remain connected until further notice.
     */
    public static final int TTD_VALUE_UNLIMITED = -1;

    /**
     * The MIME type representing the String representation of a JSON Object.
     */
    public static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    /**
     * The MIME type representing an opaque array of bytes.
     */
    public static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    /**
     * The MIME type representing plain text.
     */
    public static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain";

    private MessageHelper() {
    }
}
