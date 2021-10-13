/*
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

package org.eclipse.hono.client.notification;

import java.time.Instant;

import org.eclipse.hono.annotation.HonoTimestamp;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A Hono internal notification that is published by one component to inform other components about events.
 *
 * Notifications are always sent as JSON.
 *
 * Implementing classes need to declare a {@code com.fasterxml.jackson.databind.jsontype.TypeIdResolver} for automatic
 * handling of the type by Jackson.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
public interface Notification {

    String CONTENT_TYPE = "application/json";
    String FIELD_SOURCE = "source";
    String FIELD_TIMESTAMP = "timestamp";

    /**
     * Gets the type of the notification.
     *
     * @return The type name.
     */
    @JsonIgnore
    String getType();

    /**
     * Gets the canonical name of the component that publishes the notification.
     *
     * @return The name of the component.
     */
    @JsonGetter(FIELD_SOURCE)
    String getSource();

    /**
     * Gets the timestamp of the notification.
     *
     * @return The point in time.
     */
    @JsonGetter(FIELD_TIMESTAMP)
    @HonoTimestamp
    Instant getTimestamp();

}
