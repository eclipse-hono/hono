/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RequestResponseResult;


/**
 * A result that contains a status code and a string payload.
 *
 */
public final class SimpleRequestResponseResult extends RequestResponseResult<String> {

    private SimpleRequestResponseResult(final int status, final String payload, final CacheDirective directive) {
        super(status, payload, directive);
    }

    /**
     * Creates a new instance for a status code and payload.
     * 
     * @param status The status code.
     * @param payload The payload.
     * @return The instance.
     */
    public static SimpleRequestResponseResult from(final int status, final String payload) {
        return new SimpleRequestResponseResult(status, payload, null);
    }

    /**
     * Creates a new instance for a status code and payload.
     * 
     * @param status The status code.
     * @param payload The payload.
     * @param cacheDirective Restrictions regarding the caching of the payload by
     *                       the receiver of the result (may be {@code null}).
     * @return The instance.
     */
    public static SimpleRequestResponseResult from(final int status, final String payload, final CacheDirective cacheDirective) {
        return new SimpleRequestResponseResult(status, payload, cacheDirective);
    }
}
