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

package org.eclipse.hono.authentication;

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
     * @param the SASL mechanism used to authenticate the client.
     * @param response the raw bytes provided by the client.
     * @param resultHandler the handler to invoke with the issued authorization ID.
     */
    void validateResponse(String mechanism, byte[] response, Handler<AsyncResult<String>> resultHandler);
}
