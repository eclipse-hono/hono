/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

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
