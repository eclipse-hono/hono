/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.service.authorization;

/**
 * Defines permissions that are required to access Hono service resources.
 */
public enum Permission
{
    /**
     * Permission required for receiving message from a node.
     */
    READ,
    /**
     * Permission required for sending messages to a node.
     */
    WRITE
}
