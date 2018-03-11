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

package org.eclipse.hono.service.registration;

/**
 * A utility for creating and validating JWT tokens asserting the registration status of devices.
 *
 */
public interface RegistrationAssertionHelper {

    /**
     * Creates a signed assertion.
     * 
     * @param tenantId The tenant to which the device belongs.
     * @param deviceId The device that is subject of the assertion.
     * @return The assertion.
     */
    String getAssertion(String tenantId, String deviceId);

    /**
     * Checks if a given token asserts a particular device's registration status.
     * 
     * @param token The token representing the asserted status.
     * @param tenantId The tenant that the device is expected to belong to.
     * @param deviceId The device that is expected to be the subject of the assertion.
     * @return {@code true} if the token has not expired and contains the following claims:
     * <ul>
     * <li>a <em>sub</em> claim with value device ID</li>
     * <li>a <em>ten</em> claim with value tenant ID</li>
     * </ul>
     */
    boolean isValid(String token, String tenantId, String deviceId);

    /**
     * Gets the lifetime of the assertions created by the <em>getAssertion</em>
     * method.
     * 
     * @return The lifetime in seconds.
     */
    long getAssertionLifetime();
}