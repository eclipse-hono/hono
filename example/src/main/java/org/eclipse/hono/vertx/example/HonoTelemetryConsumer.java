/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.vertx.example;

import org.eclipse.hono.vertx.example.base.HonoConsumerBase;

/**
 * Example class with minimal dependencies for consuming telemetry data from Hono.
 * <p>
 * Please refer to {@link org.eclipse.hono.vertx.example.base.HonoExampleConstants} to configure where Hono's
 * microservices are reachable.
 */
public class HonoTelemetryConsumer extends HonoConsumerBase {

    public static void main(final String[] args) throws Exception {

        System.out.println("Starting telemetry consumer...");
        HonoTelemetryConsumer honoDownstreamEventConsumer = new HonoTelemetryConsumer();
        honoDownstreamEventConsumer.consumeData();
        System.out.println("Finishing telemetry consumer.");
    }

}
