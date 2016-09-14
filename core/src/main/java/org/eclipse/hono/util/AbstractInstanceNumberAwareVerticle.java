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

import io.vertx.core.AbstractVerticle;

/**
 * A base class for implementing vert.x {@code Verticle}s that are aware of their
 * instance number.
 *
 */
public class AbstractInstanceNumberAwareVerticle extends AbstractVerticle {

    protected final int                 instanceNo;
    protected final int                 totalNoOfInstances;

    protected AbstractInstanceNumberAwareVerticle(final int instanceNo, final int totalNoOfInstances) {
        this.instanceNo = instanceNo;
        this.totalNoOfInstances = totalNoOfInstances;
    }

    /**
     * Appends this verticle's instance number to a given base address.
     * 
     * @param baseAddress the base address.
     * @return the base address appended with a period and the instance number.
     */
    protected final String getAddressWithId(final String baseAddress) {
        return String.format("%s.%d", baseAddress, instanceNo);
    }

    /**
     * Gets this verticle's instance number.
     * 
     * @return the instance number.
     */
    final int getInstanceNo() {
        return instanceNo;
    }

    /**
     * Gets the total number of instances of this verticle that are deployed.
     * 
     * @return the total number.
     */
    final int getTotalNoOfInstances() {
        return totalNoOfInstances;
    }
}
