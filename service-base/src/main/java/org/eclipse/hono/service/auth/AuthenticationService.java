/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;

/**
 * A service for validating credentials provided by a client in a SASL exchange.
 *
 */
public interface AuthenticationService extends Verticle {

    /**
     * Validates the SASL response provided by the client.
     * 
     * @param mechanism the SASL mechanism used to authenticate the client.
     * @param response the raw bytes provided by the client in its SASL <em>response</em>.
     * @param resultHandler the handler to invoke with the issued authorization ID.
     * @throws IllegalArgumentException if the given mechanism is not supported.
     */
    void validateResponse(String mechanism, byte[] response, Handler<AsyncResult<String>> resultHandler) throws IllegalArgumentException;

    /**
     * Checks whether this authentication service supports a particular SASL mechanism.
     * 
     * @param mechanism The SASL mechanism to check.
     * @return {@code true} if the mechanism is supported.
     */
    boolean isSupported(String mechanism);
}
