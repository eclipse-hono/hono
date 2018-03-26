/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

import java.util.concurrent.CompletionException;

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
    private static final String SENDER_TIME_IN_PAYLOAD = "senderTimeInPayload";
    private static final String SENDER_TIME_VARIABLE_NAME = "senderTimeVariableName";
    private static final String RECONNECT_ATTEMPTS = "reconnectAttempts";
    private static final String DEFAULT_SENDER_TIME_VARIABLE_NAME = "timeStamp";
    private static final String PREFETCH = "prefetch";

    private HonoReceiver honoReceiver;

    public boolean isUseSenderTime() {
        return getPropertyAsBoolean(USE_SENDER_TIME);
    }

    /**
     * Sets whether or not to use the sender time.
     * 
     * @param useSenderTime {@code true} in order to use the sender time. 
     */
    public void setUseSenderTime(final boolean useSenderTime) {
        setProperty(USE_SENDER_TIME, useSenderTime);
    }

    public String getPrefetch() {
        return getPropertyAsString(PREFETCH);
    }

    /**
     * Sets the number of messages to prefetch.
     * 
     * @param prefetch The number of messages to prefetch, encoded as String.
     */
    public void setPrefetch(final String prefetch) {
        setProperty(PREFETCH, prefetch);
    }

    public String getSenderTimeVariableName() {
        return getPropertyAsString(SENDER_TIME_VARIABLE_NAME, DEFAULT_SENDER_TIME_VARIABLE_NAME);
    }

    /**
     * Sets the name of the sender time in the payload.
     * 
     * @param variableName The name of the variable the sender time uses in the payload.
     */
    public void setSenderTimeVariableName(final String variableName) {
        setProperty(SENDER_TIME_VARIABLE_NAME, variableName);
    }

    public boolean isSenderTimeInPayload() {
        return getPropertyAsBoolean(SENDER_TIME_IN_PAYLOAD);
    }

    /**
     * Sets if the payload contains the sender time.
     * 
     * @param senderTimeInPayload {@code true} if the payload contains the sender time.
     */
    public void setSenderTimeInPayload(final boolean senderTimeInPayload) {
        setProperty(SENDER_TIME_IN_PAYLOAD, senderTimeInPayload);
    }

    public String getReconnectAttempts() {
        return getPropertyAsString(RECONNECT_ATTEMPTS, "1");
    }

    /**
     * Sets the number of re-connect attempts.
     * 
     * @param reconnectAttempts The number of attempts as string. 
     */
    public void setReconnectAttempts(final String reconnectAttempts) {
        setProperty(RECONNECT_ATTEMPTS, reconnectAttempts);
    }

    @Override
    public SampleResult sample(final Entry entry) {
        SampleResult res = new SampleResult();
        res.setResponseOK();
        res.setDataType(SampleResult.TEXT);
        res.setSampleLabel(getName());
        honoReceiver.sample(res);
        return res;
    }

    @Override
    public void threadStarted() {

        try {
            honoReceiver = new HonoReceiver(this);
            honoReceiver.start().join();
            addSemaphore();
        } catch (CompletionException e) {
            LOGGER.error("error starting receiver: {}/{} ({)}", getEndpoint(), getTenant(),
                    Thread.currentThread().getName(), e.getCause());
        }
    }

    @Override
    public void threadFinished() {
        if (honoReceiver != null) {
            try {
                honoReceiver.close().join();
            } catch (CompletionException e) {
                LOGGER.error("error during shut down of receiver", e);
            }
        }
        removeSemaphores();
    }
}
