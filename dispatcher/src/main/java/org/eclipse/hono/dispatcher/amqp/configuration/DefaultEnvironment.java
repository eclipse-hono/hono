/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.dispatcher.amqp.configuration;

/**
 * Default implementation, loads from System.getenv.
 */
public final class DefaultEnvironment implements Environment {
    @Override
    public String get(final String key) {
        return System.getenv(key);
    }

    @Override
    public String getOrDefault(final String key, final String def) {
        return System.getenv().getOrDefault(key, def);
    }
}
