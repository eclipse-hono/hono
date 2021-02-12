/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.limiting;

/**
 * A strategy to calculate a recommended connection limit for {@link DefaultConnectionLimitManager} based on the available
 * resources and the demand of a protocol adapter has to serve a connection.
 */
public interface ConnectionLimitStrategy {

    /**
     * Returns the calculated connection limit recommendation based on the available resources.
     *
     * @return The recommended limit.
     */
    int getRecommendedLimit();

    /**
     * Gives a descriptive text of the resources and their amount that the calculation took into account. It is intended
     * to be logged by {@link DefaultConnectionLimitManager}.
     *
     * @return The description of the resources on which the strategy calculated the limit.
     */
    String getResourcesDescription();
}
