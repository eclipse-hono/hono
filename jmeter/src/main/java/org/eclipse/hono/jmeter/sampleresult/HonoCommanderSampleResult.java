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

package org.eclipse.hono.jmeter.sampleresult;

import org.apache.jmeter.samplers.SampleResult;

/**
 * This is extended SampleResult class for the HonoCommanderSampler. Method setErrorCount is ignored in the SampleResult
 * class from Jmeter. Here this method is overridden so that the error count set during sampling is available in the
 * summary report.
 *
 */
public class HonoCommanderSampleResult extends SampleResult {

    private int errorCount = 0;

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(final int errorCount) {
        this.errorCount = errorCount;
    }

}
