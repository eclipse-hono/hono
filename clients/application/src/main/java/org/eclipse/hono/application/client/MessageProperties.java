/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.application.client;

import java.util.Map;

/**
 * The metadata of a {@link Message}.
 * <p>
 * Implementations for a specific messaging system may add different kinds of specific properties.
 */
public interface MessageProperties {

    /**
     * Gets the metadata properties.
     *
     * @return returns the message properties or an empty map if no properties set.
     */
    Map<String, Object> getPropertiesMap();

}
