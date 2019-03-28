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

import static org.eclipse.hono.jmeter.HonoSamplerUtils.getIntValueOrDefault;

import java.util.concurrent.CompletionException;

import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.ThreadListener;
import org.eclipse.hono.jmeter.client.HonoSender;
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
public class HonoSenderSampler extends HonoSampler implements ThreadListener {

    public static final int DEFAULT_SEND_TIMEOUT = 1000; // milliseconds

    private static final long serialVersionUID = -1386211797024120743L;

    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSenderSampler.class);

    private static final String DEVICE_ID = "deviceId";
    private static final String SET_SENDER_TIME = "setSenderTime";
    private static final String CONTENT_TYPE = "contentType";
    private static final String DATA = "data";
    private static final String WAIT_FOR_DELIVERY_RESULT = "waitForDeliveryResult";
    private static final String WAIT_FOR_RECEIVERS = "waitForReceivers";
    private static final String WAIT_FOR_RECEIVERS_TIMEOUT = "waitForReceiversTimeout";
    private static final String SEND_TIMEOUT = "sendTimeout";
    private static final String MESSAGE_COUNT_PER_SAMPLER_RUN = "messageCountPerSamplerRun";

    private HonoSender honoSender;

    /**
     * Gets the number of receivers as integer.
     * 
     * @return The number of receivers to wait for as integer or {@code 0}
     * if the value cannot be parsed as integer.
     */
    public int getWaitForReceiversAsInt() {
        return getIntValueOrDefault(getPropertyAsString(WAIT_FOR_RECEIVERS), 0);
    }

    public String getWaitForReceivers() {
        return getPropertyAsString(WAIT_FOR_RECEIVERS);
    }

    /**
     * Sets the number of receivers to wait for.
     * 
     * @param waitForReceivers Number of receivers to wait for (e.g. from other threads).
     */
    public void setWaitForReceivers(final String waitForReceivers) {
        setProperty(WAIT_FOR_RECEIVERS, waitForReceivers);
    }

    /**
     * Gets the timeout to wait for receivers as integer.
     * 
     * @return The timeout to wait for receivers in milliseconds or {@code 0}
     * if the value cannot be parsed as integer.
     */
    public int getWaitForReceiversTimeoutAsInt() {
        return getIntValueOrDefault(getPropertyAsString(WAIT_FOR_RECEIVERS_TIMEOUT), 0);
    }

    public String getWaitForReceiversTimeout() {
        return getPropertyAsString(WAIT_FOR_RECEIVERS_TIMEOUT);
    }

    /**
     * Sets the timeout to wait for receivers in milliseconds.
     * 
     * @param waitForReceiversTimeout The timeout in milliseconds encoded as string.
     */
    public void setWaitForReceiversTimeout(final String waitForReceiversTimeout) {
        setProperty(WAIT_FOR_RECEIVERS_TIMEOUT, waitForReceiversTimeout);
    }

    /**
     * Gets the timeout for sending a message in milliseconds as integer.
     *
     * @return The timeout for sending a message in milliseconds or the default timeout
     * if the value is empty or cannot be parsed as integer.
     */
    public int getSendTimeoutOrDefaultAsInt() {
        return getIntValueOrDefault(getPropertyAsString(SEND_TIMEOUT), DEFAULT_SEND_TIMEOUT);
    }

    /**
     * Gets the timeout for sending a message in milliseconds.
     * 
     * @return The timeout for sending a message in milliseconds or the default timeout
     * if the value is empty.
     */
    public String getSendTimeoutOrDefault() {
        final String value = getPropertyAsString(SEND_TIMEOUT);
        return value == null || value.isEmpty() ? Integer.toString(DEFAULT_SEND_TIMEOUT) : value;
    }

    /**
     * Sets the timeout for sending a message.
     * 
     * @param sendTimeout The timeout in milliseconds encoded as string.
     */
    public void setSendTimeout(final String sendTimeout) {
        setProperty(SEND_TIMEOUT, sendTimeout);
    }

    public String getDeviceId() {
        return getPropertyAsString(DEVICE_ID);
    }

    /**
     * Sets the device id.
     * 
     * @param deviceId The device ID to use.
     */
    public void setDeviceId(final String deviceId) {
        setProperty(DEVICE_ID, deviceId);
    }

    public boolean isSetSenderTime() {
        return getPropertyAsBoolean(SET_SENDER_TIME);
    }

    /**
     * Sets whether or not to add the current timestamp in the payload.
     * 
     * @param isSetSenderTime {@code true} to add the current timestamp at the point of sending.
     */
    public void setSetSenderTime(final boolean isSetSenderTime) {
        setProperty(SET_SENDER_TIME, isSetSenderTime);
    }

    /**
     * Gets whether to wait for the result of sending the message.
     * <p>
     * If this option has not been set, {@code true} is returned.
     * 
     * @return {@code true} in order to wait for the delivery result.
     */
    public boolean isWaitForDeliveryResult() {
        return getPropertyAsBoolean(WAIT_FOR_DELIVERY_RESULT, true);
    }

    /**
     * Sets whether to wait for the result of sending the message. The result contains the updated delivery state.
     *
     * @param isWaitForDeliveryResult {@code true} in order to wait for the result.
     */
    public void setWaitForDeliveryResult(final boolean isWaitForDeliveryResult) {
        setProperty(WAIT_FOR_DELIVERY_RESULT, isWaitForDeliveryResult);
    }

    /**
     * Gets the number of messages to send per sample run.
     * 
     * @return number of messages.
     */
    public String getMessageCountPerSamplerRun() {
        return getPropertyAsString(MESSAGE_COUNT_PER_SAMPLER_RUN, "1");
    }

    /**
     * Gets the number of messages to send per sample run as an integer.
     * <p>
     * If the property value is smaller than 1 or not a number, 1 is returned.
     *
     * @return number of messages as integer.
     */
    public int getMessageCountPerSamplerRunAsInt() {
        final int messageCount = getIntValueOrDefault(getPropertyAsString(MESSAGE_COUNT_PER_SAMPLER_RUN), 1);
        return messageCount > 0 ? messageCount : 1;
    }

    /**
     * Sets the number of messages to send per sample run.
     *
     * @param messageCountPerSamplerRun number of messages.
     */
    public void setMessageCountPerSamplerRun(final String messageCountPerSamplerRun) {
        final int parsedMessageCount = getIntValueOrDefault(messageCountPerSamplerRun, 1);
        setProperty(MESSAGE_COUNT_PER_SAMPLER_RUN, parsedMessageCount > 0 ? Integer.toString(parsedMessageCount) : "1");
    }

    public String getContentType() {
        return getPropertyAsString(CONTENT_TYPE);
    }

    /**
     * Sets the content type.
     * 
     * @param contentType The MIME type of the payload.
     */
    public void setContentType(final String contentType) {
        setProperty(CONTENT_TYPE, contentType);
    }

    public String getData() {
        return getPropertyAsString(DATA);
    }

    /**
     * Sets the payload data to send.
     * 
     * @param data The payload data.
     */
    public void setData(final String data) {
        setProperty(DATA, data);
    }

    @Override
    public SampleResult sample(final Entry entry) {

        final SampleResult res = new SampleResult();
        res.setDataType(SampleResult.TEXT);
        res.setResponseOK();
        res.setResponseCodeOK();
        res.setSampleLabel(getName());
        if (getMessageCountPerSamplerRunAsInt() == 1) {
            honoSender.send(res, getDeviceId(), isWaitForDeliveryResult());
        } else {
            honoSender.send(res, getMessageCountPerSamplerRunAsInt(), getDeviceId(),
                    isWaitForDeliveryResult());
        }
        return res;
    }

    @Override
    public void threadStarted() {

        int activeReceivers = getSemaphores();
        final int waitOn = getWaitForReceiversAsInt();
        final int waitOnTimeout = getWaitForReceiversTimeoutAsInt();

        honoSender = new HonoSender(this);

        if (activeReceivers < waitOn) {
            final int endCounter = waitOnTimeout / 100;
            for (int i = 0; i < endCounter && activeReceivers < waitOn; i++) {
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException e) {
                    LOGGER.error("wait on receiver", e);
                    Thread.currentThread().interrupt();
                }
                activeReceivers = getSemaphores();
                LOGGER.info("wait on receivers ({}/{}) for address: {} ({})", activeReceivers, waitOn, getAddress(),
                        Thread.currentThread().getName());
            }
        }

        try {
            honoSender.start().join();
        } catch (final CompletionException e) {
            LOGGER.error("error initializing sender: {}/{} ({})", getEndpoint(), getTenant(),
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public void threadFinished() {
        if (honoSender != null) {
            try {
                honoSender.close().join();
            } catch (final CompletionException e) {
                LOGGER.error("error during shut down of sender", e);
            }
        }
    }
}
