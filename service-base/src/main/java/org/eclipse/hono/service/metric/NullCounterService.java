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

import org.springframework.boot.actuate.metrics.CounterService;

/**
 * A singleton counter service that does not do anything with submitted values.
 */
public final class NullCounterService implements CounterService {

    private NullCounterService() {
    }

    /**
     * A no-op metrics counter service.
     */
    private static class NullCounterServiceSingleton {

        private static final NullCounterService INSTANCE = new NullCounterService();
    }

    /**
     * Gets the singleton instance.
     * 
     * @return The instance.
     */
    public static NullCounterService getInstance() {
        return NullCounterServiceSingleton.INSTANCE;
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void increment(final String s) {
        // do nothing
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void decrement(final String s) {
        // do nothing
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void reset(final String s) {
        // do nothing
    }
}
