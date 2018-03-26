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
    private static final String WAIT_FOR_RECEIVERS = "waitForReceivers";
    private static final String WAIT_FOR_RECEIVERS_TIMEOUT = "waitForReceiversTimeout";
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
        final String portString = getRegistryPort();
        try {
            return Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            return 0;
        }
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
        final String value = getPropertyAsString(WAIT_FOR_RECEIVERS);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
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
        final String value = getPropertyAsString(WAIT_FOR_RECEIVERS_TIMEOUT);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
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

        SampleResult res = new SampleResult();
        res.setDataType(SampleResult.TEXT);
        res.setResponseOK();
        res.setResponseCodeOK();
        res.setSampleLabel(getName());
        honoSender.send(res, getDeviceId(), isWaitForCredits());
        return res;
    }

    @Override
    public void threadStarted() {

        int activeReceivers = getSemaphores();
        int waitOn = getWaitForReceiversAsInt();
        int waitOnTimeout = getWaitForReceiversTimeoutAsInt();

        honoSender = new HonoSender(this);

        if (activeReceivers < waitOn) {
            int endCounter = waitOnTimeout / 100;
            for (int i = 0; i < endCounter && activeReceivers < waitOn; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    LOGGER.error("wait on receiver", e);
                }
                activeReceivers = getSemaphores();
                LOGGER.info("wait on receivers ({}/{}) for address: {} ({})", activeReceivers, waitOn, getAddress(),
                        Thread.currentThread().getName());
            }
        }

        try {
            honoSender.start().join();
        } catch (CompletionException e) {
            LOGGER.error("error initializing sender: {}/{} ({})", getEndpoint(), getTenant(),
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public void threadFinished() {
        if (honoSender != null) {
            try {
                honoSender.close().join();
            } catch (CompletionException e) {
                LOGGER.error("error during shut down of sender", e);
            }
        }
    }
}
