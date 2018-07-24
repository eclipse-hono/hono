/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
 * Constants &gt; utility methods used throughout the Event API.
 */
public final class EventConstants {

    /**
     * The name of the event endpoint.
     */
    public static final String EVENT_ENDPOINT = "event";

    /**
     * The short name of the event endpoint.
     */
    public static final String EVENT_ENDPOINT_SHORT = "e";

    /**
     * The content type of the <em>connection notification</em> event.
     */
    public static final String EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE = "application/vnd.eclipse-hono-dc-notification+json";

    private EventConstants() {
    }

    /**
     * The content type that is defined for empty events without any payload.
     */
    public static final String CONTENT_TYPE_EMPTY_NOTIFICATION = "application/vnd.eclipse-hono-empty-notification";

}
