/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.limiting;

/**
 * Enforces an upper limit of concurrent connections.
 */
public interface ConnectionLimitManager {

    /**
     * Checks if the connection limit is exceeded.
     *
     * @return {@code true} if the current number of connections is equal or above the limit.
     */
    boolean isLimitExceeded();
}
