/**
 * Copyright (c) 2016,2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.jmeter;

import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.ThreadListener;
import org.eclipse.hono.jmeter.client.HonoReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JMeter creates an instance of a sampler class for every occurrence of the element in every thread. [some additional
 * copies may be created before the test run starts]
 *
 * Thus each sampler is guaranteed to be called by a single thread - there is no need to synchronize access to instance
 * variables.
 *
 * However, access to class fields must be synchronized.
 */
public class HonoReceiverSampler extends HonoSampler implements TestBean, ThreadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(HonoReceiverSampler.class);

    private static final String USE_SENDER_TIME = "useSenderTime";

    private HonoReceiver honoReceiver;

    public Boolean isUseSenderTime() {
        return getPropertyAsBoolean(USE_SENDER_TIME);
    }

    public void setUseSenderTime(final Boolean useSenderTime) {
        setProperty(USE_SENDER_TIME, useSenderTime);
    }

    @Override
    public SampleResult sample(final Entry entry) {
        SampleResult res = new SampleResult();
        res.setResponseOK();
        res.setDataType(SampleResult.TEXT);
        res.setSampleLabel(getName());
        res.sampleStart();
        honoReceiver.sample(res, isUseSenderTime());
        return res;
    }

    @Override
    public void threadStarted() {
        try {
            honoReceiver = new HonoReceiver(this);
        } catch (InterruptedException e) {
            LOGGER.error("thread start", e);
        }
        addSemaphore();
    }

    @Override
    public void threadFinished() {
        if (honoReceiver != null) {
            honoReceiver.close();
        }
        removeSemaphores();
    }
}
