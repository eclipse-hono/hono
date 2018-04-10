/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.util;

import io.vertx.core.buffer.Buffer;

/**
 * A container for the result returned by Hono's registration API.
 *
 */
public final class CommandResult extends RequestResponseResult<byte[]> {

    private CommandResult(final int status, final Buffer payload) {
        super(status, payload.getBytes(), CacheDirective.noCacheDirective());
    }

    /**
     * Creates a new result for a status code.
     * 
     * @param status The status code.
     * @return The result.
     */
    public static CommandResult from(final int status) {
        return new CommandResult(status, null);
    }

    /**
     * Creates a new result for a status code and a payload.
     * 
     * @param status The status code.
     * @param payload The payload to include in the result.
     * @return The result.
     */
    public static CommandResult from(final int status, Buffer payload) {
        return new CommandResult(status, payload);
    }
}
