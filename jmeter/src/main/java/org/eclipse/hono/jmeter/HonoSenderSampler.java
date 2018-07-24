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

package org.eclipse.hono.jmeter;

import java.util.concurrent.CompletionException;

import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.ThreadListener;
import org.eclipse.hono.jmeter.client.HonoSender;
import org.eclipse.hono.jmeter.ui.ServerOptionsPanel;
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

    private static final long serialVersionUID = -1386211797024120743L;

    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSenderSampler.class);

    public static final int DEFAULT_SEND_TIMEOUT = 1000; // milliseconds

    private static final String REGISTRY_HOST = "registryHost";
    private static final String REGISTRY_USER = "registryUser";
    private static final String REGISTRY_PWD = "registryPwd";
    private static final String REGISTRY_PORT = "registryPort";
    private static final String REGISTRY_TRUSTSTORE_PATH = "registryTrustStorePath";

    private static final String DEVICE_ID = "deviceId";
    private static final String SET_SENDER_TIME = "setSenderTime";
    private static final String CONTENT_TYPE = "contentType";
    private static final String DATA = "data";
    private static final String WAIT_FOR_CREDITS = "waitForCredits";
    private static final String WAIT_FOR_DELIVERY_RESULT = "waitForDeliveryResult";
    private static final String WAIT_FOR_RECEIVERS = "waitForReceivers";
    private static final String WAIT_FOR_RECEIVERS_TIMEOUT = "waitForReceiversTimeout";
    private static final String SEND_TIMEOUT = "sendTimeout";
    private static final String MESSAGE_COUNT_PER_SAMPLER_RUN = "messageCountPerSamplerRun";
    private static final String PROPERTY_REGISTRATION_ASSERTION = "PROPERTY_REGISTRATION_ASSERTION";

    private HonoSender honoSender;

    /**
     * Applies the options to the local UI.
     * 
     * @param serverOptions The options to apply.
     */
    public void modifyRegistrationServiceOptions(final ServerOptionsPanel serverOptions) {
        setRegistryHost(serverOptions.getHost());
        setRegistryPort(serverOptions.getPort());
        setRegistryUser(serverOptions.getUser());
        setRegistryPwd(serverOptions.getPwd());
        setRegistryTrustStorePath(serverOptions.getTrustStorePath());
    }

    /**
     * Apply the local UI options to the provided object.
     * 
     * @param serverOptions The options to change.
     */
    public void configureRegistrationServiceOptions(final ServerOptionsPanel serverOptions) {
        serverOptions.setHost(getRegistryHost());
        serverOptions.setPort(getRegistryPort());
        serverOptions.setUser(getRegistryUser());
        serverOptions.setPwd(getRegistryPwd());
        serverOptions.setTrustStorePath(getRegistryTrustStorePath());
    }

    public String getRegistryTrustStorePath() {
        return getPropertyAsString(REGISTRY_TRUSTSTORE_PATH);
    }

    /**
     * Sets the path to the trust store of the registry.
     * 
     * @param trustStorePath The path to the registry trust store.
     */
    public void setRegistryTrustStorePath(final String trustStorePath) {
        setProperty(REGISTRY_TRUSTSTORE_PATH, trustStorePath);
    }

    public String getRegistryHost() {
        return getPropertyAsString(REGISTRY_HOST);
    }

    /**
     * Sets the host of the registry.
     * 
     * @param host The hostname of the registry.
     */
    public void setRegistryHost(final String host) {
        setProperty(REGISTRY_HOST, host);
    }

    public String getRegistryUser() {
        return getPropertyAsString(REGISTRY_USER);
    }

    /**
     * Get the user to use for the registry.
     * 
     * @param user The username to use.
     */
    public void setRegistryUser(final String user) {
        setProperty(REGISTRY_USER, user);
    }

    public String getRegistryPwd() {
        return getPropertyAsString(REGISTRY_PWD);
    }

    /**
     * Get the password to use for the registry.
     * 
     * @param pwd The password to use.
     */
    public void setRegistryPwd(final String pwd) {
        setProperty(REGISTRY_PWD, pwd);
    }

    /**
     * Gets the port of the registry as integer.
     * 
     * @return The registry port as integer, {@code 0} if the value cannot be parsed as an integer.
     */
    public int getRegistryPortAsInt() {
        return getIntValueOrDefault(getRegistryPort(), 0);
    }

    public String getRegistryPort() {
        return getPropertyAsString(REGISTRY_PORT);
    }

    /**
     * Sets the port of the registry.
     * 
     * @param port The port of the registry.
     */
    public void setRegistryPort(final String port) {
        setProperty(REGISTRY_PORT, port);
    }

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

    public boolean isWaitForCredits() {
        return getPropertyAsBoolean(WAIT_FOR_CREDITS);
    }

    /**
     * Sets whether to wait for credits.
     * 
     * @param isWaitForCredits {@code true} in order to wait for credits.
     */
    public void setWaitForCredits(final boolean isWaitForCredits) {
        setProperty(WAIT_FOR_CREDITS, isWaitForCredits);
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

    private static int getIntValueOrDefault(final String stringValue, final int defaultValue) {
        if (stringValue == null || stringValue.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(stringValue);
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
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

    public String getRegistrationAssertion() {
        return getPropertyAsString(PROPERTY_REGISTRATION_ASSERTION);
    }

    /**
     * Sets the registration assertion to use.
     * 
     * @param assertion The registration assertion.
     */
    public void setRegistrationAssertion(final String assertion) {
        setProperty(PROPERTY_REGISTRATION_ASSERTION, assertion);
    }

    @Override
    public SampleResult sample(final Entry entry) {

        final SampleResult res = new SampleResult();
        res.setDataType(SampleResult.TEXT);
        res.setResponseOK();
        res.setResponseCodeOK();
        res.setSampleLabel(getName());
        if (getMessageCountPerSamplerRunAsInt() == 1) {
            honoSender.send(res, getDeviceId(), isWaitForCredits(), isWaitForDeliveryResult());
        } else {
            honoSender.send(res, getMessageCountPerSamplerRunAsInt(), getDeviceId(), isWaitForCredits(),
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
