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
 * Constants &amp; utility methods that are common to APIs that follow the request response pattern.
 */
public abstract class RequestResponseApiConstants {

    /**
     * The name of the property which contains default properties that protocol adapters
     * should add to messages published by a device.
     */
    public static final String FIELD_PAYLOAD_DEFAULTS  = "defaults";
    /**
     * The name of the property that contains the identifier of a device.
     */
    public static final String FIELD_PAYLOAD_DEVICE_ID = Constants.JSON_FIELD_DEVICE_ID;
    /**
     * The name of the property that contains the <em>subject DN</em> of the CA certificate
     * that has been configured for a tenant. The subject DN is serialized as defined by
     * <a href="https://tools.ietf.org/html/rfc2253#section-2">RFC 2253, Section 2</a>.
     */
    public static final String FIELD_PAYLOAD_SUBJECT_DN = "subject-dn";
    /**
     * The name of the property that contains the <em>subject DN</em> of the CA certificate
     * in original ASN.1 DER encoded form.
     */
    public static final String FIELD_PAYLOAD_SUBJECT_DN_BYTES = "subject-dn-bytes";
    /**
     * The name of the property that contains the identifier of a tenant.
     */
    public static final String FIELD_PAYLOAD_TENANT_ID = Constants.JSON_FIELD_TENANT_ID;
    /**
     * The name of the property that defines the template for generating authentication identifier
     * to be used during authentication and auto-provisioning.
     */
    public static final String FIELD_PAYLOAD_AUTH_ID_TEMPLATE = "auth-id-template";

    /**
     * The name of the field that contains a boolean indicating the status of an entity.
     */
    public static final String FIELD_ENABLED   = "enabled";
    /**
     * The name of the field that contains a boolean indicating if an entity was auto-provisioned.
     */
    public static final String FIELD_AUTO_PROVISIONED   = "auto-provisioned";
    /**
     * The name of the field that contains a boolean indicating if a notification for an auto-provisioned device was sent.
     */
    public static final String FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT = "auto-provisioning-notification-sent";
    /**
     * The name of the field that contains additional information about an error
     * that has occurred while processing a request message.
     */
    public static final String FIELD_ERROR     = "error";
    /**
     * The name of the field that contains the payload of a request or response message.
     */
    public static final String FIELD_PAYLOAD   = "payload";
    /**
     * The name of the field that contains the identifier of the object.
     */
    public static final String FIELD_OBJECT_ID = "id";

    /**
     * Empty default constructor.
     */
    protected RequestResponseApiConstants() {
    }
}
