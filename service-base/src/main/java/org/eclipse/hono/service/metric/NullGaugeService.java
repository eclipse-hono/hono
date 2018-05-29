/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.metric;

import org.springframework.boot.actuate.metrics.GaugeService;

/**
 * A singleton gauge service that does not do anything with submitted values.
 */
public final class NullGaugeService implements GaugeService {

    private NullGaugeService() {
    }

    /**
     * A no-op gauge metrics service.
     */
    private static class NullGaugeServiceSingleton {

        private static final NullGaugeService INSTANCE = new NullGaugeService();
    }

    /**
     * Gets the singleton instance.
     * 
     * @return The instance.
     */
    public static NullGaugeService getInstance() {
        return NullGaugeService.NullGaugeServiceSingleton.INSTANCE;
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void submit(final String s, final double v) {
        // do nothing
    }
}
