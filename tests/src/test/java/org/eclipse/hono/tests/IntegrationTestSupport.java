/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.tests;

import java.net.InetAddress;

import org.eclipse.hono.util.Constants;

/**
 * A helper class for integration tests.
 *
 */
public final class IntegrationTestSupport {

    public static final String DEFAULT_HOST = InetAddress.getLoopbackAddress().getHostAddress();
    public static final int    DEFAULT_DOWNSTREAM_PORT = 15672;
    public static final int    DEFAULT_DEVICEREGISTRY_PORT = 16672;

    public static final String PROPERTY_HONO_HOST = "hono.host";
    public static final String PROPERTY_HONO_PORT = "hono.amqp.port";
    public static final String PROPERTY_HONO_USERNAME = "hono.username";
    public static final String PROPERTY_HONO_PASSWORD = "hono.password";
    public static final String PROPERTY_DEVICEREGISTRY_HOST = "deviceregistry.host";
    public static final String PROPERTY_DEVICEREGISTRY_PORT = "deviceregistry.amqp.port";
    public static final String PROPERTY_DOWNSTREAM_HOST = "downstream.host";
    public static final String PROPERTY_DOWNSTREAM_PORT = "downstream.amqp.port";
    public static final String PROPERTY_DOWNSTREAM_USERNAME = "downstream.username";
    public static final String PROPERTY_DOWNSTREAM_PASSWORD = "downstream.password";
    public static final String PROPERTY_TENANT = "tenant";

    public static final String HONO_HOST = System.getProperty(PROPERTY_HONO_HOST, DEFAULT_HOST);
    public static final int    HONO_PORT = Integer.getInteger(PROPERTY_HONO_PORT, Constants.PORT_AMQP);
    public static final String HONO_USER = System.getProperty(PROPERTY_HONO_USERNAME);
    public static final String HONO_PWD = System.getProperty(PROPERTY_HONO_PASSWORD);

    public static final String HONO_DEVICEREGISTRY_HOST = System.getProperty(PROPERTY_DEVICEREGISTRY_HOST, DEFAULT_HOST);
    public static final int    HONO_DEVICEREGISTRY_PORT = Integer.getInteger(PROPERTY_DEVICEREGISTRY_PORT, DEFAULT_DEVICEREGISTRY_PORT);

    public static final String DOWNSTREAM_HOST = System.getProperty(PROPERTY_DOWNSTREAM_HOST, DEFAULT_HOST);
    public static final int    DOWNSTREAM_PORT = Integer.getInteger(PROPERTY_DOWNSTREAM_PORT, DEFAULT_DOWNSTREAM_PORT);
    public static final String DOWNSTREAM_USER = System.getProperty(PROPERTY_DOWNSTREAM_USERNAME);
    public static final String DOWNSTREAM_PWD = System.getProperty(PROPERTY_DOWNSTREAM_PASSWORD);

    public static final String PATH_SEPARATOR = System.getProperty("hono.pathSeparator", "/");
    public static final int    MSG_COUNT = Integer.getInteger("msg.count", 1000);

    private IntegrationTestSupport() {
        // prevent instantiation
    }
}
