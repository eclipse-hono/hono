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
package org.eclipse.hono.server;

/**
 * A factory for creating {@code Endpoint} instances.
 * 
 * @param T the type of endpoint this factory creates instances of.
 */
public interface EndpointFactory<T extends Endpoint> {

    /**
     * Creates a new instance based on this factory's configuration.
     * 
     * @return the new instance.
     */
    T newInstance();

    /**
     * Creates an instance with a particular number based on this factory's configuration.
     * <p>
     * The given instance number can be used to distinguish between multiple instances created by this
     * factory.
     * </p>
     * 
     * @param instanceNo the number to assign to the the newly created instance.
     * @param totalNoOfInstances the total number of instances the client will (eventually) create.
     *                           This may be important for the created instance to know e.g.
     *                           when using the instance number as a sharding key for distributing
     *                           the data to be processed to the individual instances.
     * @return the new instance.
     */
    T newInstance(int instanceNo, int totalNoOfInstances);
}
