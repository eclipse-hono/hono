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

package org.eclipse.hono.vertx.example.base;

/**
 * Class defines where to reach Hono's microservices that need to be accessed for sending and consuming data.
 * This is intentionally done as pure Java constants to provide an example with minimal dependencies (no Spring is
 * used e.g.).
 *
 * Please adopt the values to your needs - the defaults serve for a typical docker swarm setup.
 * TODO: check and (possibly) change to kubernetes values.
 */
public class HonoExampleConstants {
    /**
     Define the host where Hono's microservices can be reached.
     */
    public static final String HONO_CONTAINER_HOST = "127.0.0.1";

    public static final String HONO_AMQP_CONSUMER_HOST = HONO_CONTAINER_HOST;
    /**
     * Port of the AMQP network where consumers can receive data (in the standard setup this is the port of the qdrouter).
     */
    public static final int HONO_AMQP_CONSUMER_PORT = 15671;

    public static final String HONO_REGISTRY_HOST = HONO_CONTAINER_HOST;
    /**
     * Port of Hono's device registry microservice (used to register and enable devices).
     */
    public static final int HONO_REGISTRY_PORT = 25671;

    public static final String HONO_MESSAGING_HOST = HONO_CONTAINER_HOST;
    /**
     * Port of Hono's messaging microservice (used for sending data).
     */
    public static final int HONO_MESSAGING_PORT = 5671;

    public static final String TENANT_ID = "DEFAULT_TENANT";

    /**
     * Id of the device that is used inside these examples.
     * NB: you need to register the device before data can be sent.
     * E.g. like
     *    {@code http POST http://192.168.99.100:28080/registration/DEFAULT_TENANT device-id=4711}.
     * Please refer to Hono's "Getting started" guide for details.
     */
    public static final String DEVICE_ID = "4711"; // needs to be registered first

    private HonoExampleConstants () {
    }
}
