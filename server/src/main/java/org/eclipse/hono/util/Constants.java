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
package org.eclipse.hono.util;

/**
 * Constants used throughout Hono.
 *
 */
public final class Constants {

    public static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    private Constants() {
    }

    public static boolean isDefaultTenant(final String tenantId) {
        return DEFAULT_TENANT.equals(tenantId);
    }
}
