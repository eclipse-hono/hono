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

package org.eclipse.hono.auth;

import java.util.Map;

/**
 * A collection of authorities granted on resources and/or operations.
 *
 */
public interface Authorities {

    boolean isAuthorized(String endpoint, String tenant, final Activity intent);
    boolean isAuthorized(String endpoint, String tenant, String operation);
    Map<String, Object> asMap();
}
