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
    public static final String PROPERTY_DOWNSTREAM_HOST = "downstream.host";
    public static final String PROPERTY_DOWNSTREAM_PORT = "downstream.amqp.port";

    private IntegrationTestSupport() {
        // prevent instantiation
    }

    public static void doNothing() {
        
    }
}
