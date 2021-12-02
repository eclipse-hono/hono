/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.vertx.example;

import org.eclipse.hono.vertx.example.base.HonoExampleApplicationBase;

/**
 * Example class with minimal dependencies for consuming data from Hono and sending commands
 * to connected devices.
 * <p>
 * Please refer to {@link org.eclipse.hono.vertx.example.base.HonoExampleConstants} to configure the host/port
 * to which to connect.
 */
public class HonoExampleApplication extends HonoExampleApplicationBase {

    public static void main(final String[] args) {
        new HonoExampleApplication().consumeData();
    }
}
