/*******************************************************************************
 * Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.http;

import org.eclipse.hono.service.Endpoint;

import io.vertx.ext.web.Router;

/**
 * An endpoint that handles HTTP requests.
 *
 */
public interface HttpEndpoint extends Endpoint {

    /**
     * Adds custom routes for handling requests that this endpoint can handle.
     *
     * @param router The router to add the routes to.
     */
    void addRoutes(Router router);
}
