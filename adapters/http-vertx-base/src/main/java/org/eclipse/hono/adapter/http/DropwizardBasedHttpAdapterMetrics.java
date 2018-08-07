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

package org.eclipse.hono.adapter.http;

import org.eclipse.hono.service.metric.DropwizardBasedMetrics;
import org.springframework.stereotype.Component;

/**
 * Metrics for the HTTP based adapters.
 */
@Component
public class DropwizardBasedHttpAdapterMetrics extends DropwizardBasedMetrics implements HttpAdapterMetrics {

    private static final String SERVICE_PREFIX = "hono.http";

    @Override
    protected String getScope() {
        return SERVICE_PREFIX;
    }
}
