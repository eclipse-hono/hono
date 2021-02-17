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

package org.eclipse.hono.adapter;

/**
 * A base class that provides helper methods for configuring protocol adapters.
 *
 */
public abstract class AdapterConfigurationSupport {

    /**
     * Gets the name of the protocol adapter to configure.
     * <p>
     * This name will be used as part of the <em>container-id</em> in the AMQP <em>Open</em> frame sent by the
     * clients configured by this class.
     *
     * @return The protocol adapter name.
     */
    protected abstract String getAdapterName();
}
