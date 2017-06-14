/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

/**
 * A helper class for integration tests.
 *
 */
public final class IntegrationTestSupport {

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

    private IntegrationTestSupport() {
        // prevent instantiation
    }

    public static void doNothing() {
        
    }
}
