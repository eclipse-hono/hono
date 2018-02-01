/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */
package org.eclipse.hono.util;

/**
 * Constants used by protocol adapters of Hono.
 *
 */
public final class ProtocolAdapterConstants {

    /**
     * The type of the mqtt protocol adapter.
     */
    public static final String TYPE_MQTT = "hono-mqtt";

    /**
     * The type of the http protocol adapter.
     */
    public static final String TYPE_HTTP = "hono-http";

    /**
     * The type of the kura protocol adapter.
     */
    public static final String TYPE_KURA = "hono-kura";
}
