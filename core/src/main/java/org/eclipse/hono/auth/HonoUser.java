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

import java.security.Principal;
import java.time.Instant;


/**
 * A principal representing a client of a Hono service API.
 *
 */
public interface HonoUser extends Principal {

    /**
     * Gets this user's granted authorities.
     * 
     * @return The authorities.
     */
    Authorities getAuthorities();

    /**
     * Gets a JSON Web Token representing this user's
     * claims.
     * 
     * @return The compact encoding of the token.
     */
    String getToken();

    /**
     * Checks if the validity period of this user's asserted claims has expired.
     * 
     * @return {@code true} if the claims have expired and this user's <em>authorities</em>
     *         should be considered invalid.
     */
    boolean isExpired();

    /**
     * Gets the point in time after which this user's authorities should be considered invalid.
     * 
     * @return The expiration time.
     */
    Instant getExpirationTime();
}
