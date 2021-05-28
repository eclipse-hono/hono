/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

/**
 * A strategy for determining the component's logical name.
 *
 */
public interface ComponentNameProvider {

    /**
     * Gets the name of the component to configure.
     *
     * @return The component name.
     */
    String getComponentName();
}
