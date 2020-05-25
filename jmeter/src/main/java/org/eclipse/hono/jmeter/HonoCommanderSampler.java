/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.jmeter;

import java.util.concurrent.CompletionException;

import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.ThreadListener;
import org.eclipse.hono.jmeter.client.HonoCommander;
import org.eclipse.hono.jmeter.sampleresult.HonoCommanderSampleResult;
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
public class HonoCommanderSampler extends HonoSampler implements ThreadListener {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HonoCommanderSampler.class);
    private static final String RECONNECT_ATTEMPTS = "reconnectAttempts";
    private static final String COMMAND = "command";
    private static final String COMMAND_PAYLOAD = "commandPayload";
    private static final String COMMAND_TIMEOUT = "commandTimeOut";
    private static final String TRIGGER_TYPE = "triggerType";
    private static final int DEFAULT_COMMAND_REQUEST_TIMEOUT = 1000;
    private HonoCommander honoCommander;

    @Override
    public SampleResult sample(final Entry entry) {
        final HonoCommanderSampleResult sampleResult = new HonoCommanderSampleResult();
        sampleResult.setDataType(SampleResult.TEXT);
        sampleResult.setSampleLabel(getName());
        honoCommander.sample(sampleResult);
        return sampleResult;
    }

    @Override
    public void threadStarted() {
        LOG.info("Sampler thread started");
        try {
            honoCommander = new HonoCommander(this);
            honoCommander.start().join();
        } catch (final CompletionException e) {
            throw new RuntimeException(String.format("Error starting commander sampler for tenant %s", getTenant()),
                    e.getCause());
        }
    }

    @Override
    public void threadFinished() {
        LOG.info("Sampler thread finished");
        if (honoCommander != null) {
            try {
                honoCommander.close().join();
            } catch (final CompletionException e) {
                LOG.error("error during shut down of command application", e);
            }
        }

    }

    public int getReconnectAttemptsAsInt() {
        return HonoSamplerUtils.getIntValueOrDefault(getPropertyAsString(RECONNECT_ATTEMPTS), 0);
    }

    public String getReconnectAttempts() {
        return getPropertyAsString(RECONNECT_ATTEMPTS);
    }

    /**
     * Sets the number of re-connect attempts.
     *
     * @param reconnectAttempts The number of attempts as string.
     */
    public void setReconnectAttempts(final String reconnectAttempts) {
        setProperty(RECONNECT_ATTEMPTS, reconnectAttempts);
    }

    public String getCommand() {
        return getPropertyAsString(COMMAND, "ArbitraryCommand");
    }

    /**
     * Sets the command to send to device.
     *
     * @param command Command to send to device
     */
    public void setCommand(final String command) {
        setProperty(COMMAND, command);
    }

    public String getCommandPayload() {
        return getPropertyAsString(COMMAND_PAYLOAD, "{\"set\": \"arbitrary value\"}");
    }

    /**
     * Sets the command payload to send to device.
     *
     * @param commandPayload Command payload to send to device or the default timeout if the value is empty or cannot be
     *            parsed as integer.
     */
    public void setCommandPayload(final String commandPayload) {
        setProperty(COMMAND_PAYLOAD, commandPayload);
    }

    public int getCommandTimeoutAsInt() {
        return HonoSamplerUtils.getIntValueOrDefault(getPropertyAsString(COMMAND_TIMEOUT), DEFAULT_COMMAND_REQUEST_TIMEOUT);
    }

    public String getCommandTimeout() {
        return getPropertyAsString(COMMAND_TIMEOUT);
    }

    /**
     * Sets the timeout for waiting for a command response by the application.
     *
     * @param commandTimeout The timeout in milliseconds encoded as string.
     */
    public void setCommandTimeout(final String commandTimeout) {
        setProperty(COMMAND_TIMEOUT, commandTimeout);
    }

    public String getTriggerType() {
        return getPropertyAsString(TRIGGER_TYPE);
    }

    /**
     * Sets the type of trigger for the command sampler.
     *
     * @param triggerType The trigger type (device or sampler)
     */
    public void setTriggerType(final String triggerType) {
        setProperty(TRIGGER_TYPE, triggerType);
    }
}
