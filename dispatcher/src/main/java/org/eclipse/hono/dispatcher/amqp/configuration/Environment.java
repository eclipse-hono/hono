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
 * Environment interface. Implementations can provide variables from different sources e.g. a mock in unit tests.
 */
public interface Environment {
    /**
     * @param key key of the requested property
     * @return value of the requested property (may be null).
     */
    String get(String key);

    /**
     * @param key key of the requested property
     * @param def default value if property value is null
     * @return value of the requested property or the default value if property is null.
     */
    String getOrDefault(String key, String def);
}
