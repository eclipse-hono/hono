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

    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSenderSampler.class);

    private HonoSender honoSender;

    private static final String REGISTRY_HOST              = "registryHost";
    private static final String REGISTRY_USER              = "registryUser";
    private static final String REGISTRY_PWD               = "registryPwd";
    private static final String REGISTRY_PORT              = "registryPort";
    private static final String REGISTRY_TRUSTSTORE_PATH   = "registryTrustStorePath";

    private static final String DEVICE_ID                  = "deviceId";
    private static final String SET_SENDER_TIME            = "setSenderTime";
    private static final String CONTENT_TYPE               = "contentType";
    private static final String DATA                       = "data";
    private static final String WAIT_FOR_CREDITS           = "waitForCredits";
    private static final String WAIT_FOR_RECEIVERS         = "waitForReceivers";
    private static final String WAIT_FOR_RECEIVERS_TIMEOUT = "waitForReceiversTimeout";

    public String getRegistryTrustStorePath() {
        return getPropertyAsString(REGISTRY_TRUSTSTORE_PATH);
    }

    public void setRegistryTrustStorePath(final String trustStorePath) {
        setProperty(REGISTRY_TRUSTSTORE_PATH, trustStorePath);
    }

    public String getRegistryHost() {
        return getPropertyAsString(REGISTRY_HOST);
    }

    public void setRegistryHost(final String host) {
        setProperty(REGISTRY_HOST, host);
    }

    public String getRegistryUser() {
        return getPropertyAsString(REGISTRY_USER);
    }

    public void setRegistryUser(final String user) {
        setProperty(REGISTRY_USER, user);
    }

    public String getRegistryPwd() {
        return getPropertyAsString(REGISTRY_PWD);
    }

    public void setRegistryPwd(final String pwd) {
        setProperty(REGISTRY_PWD, pwd);
    }

    public String getRegistryPort() {
        return getPropertyAsString(REGISTRY_PORT);
    }

    public void setRegistryPort(final String port) {
        setProperty(REGISTRY_PORT, port);
    }

    public String getWaitForReceivers() {
        return getPropertyAsString(WAIT_FOR_RECEIVERS);
    }

    public void setWaitForReceivers(final String waitForReceivers) {
        setProperty(WAIT_FOR_RECEIVERS, waitForReceivers);
    }

    public String getWaitForReceiversTimeout() {
        return getPropertyAsString(WAIT_FOR_RECEIVERS_TIMEOUT);
    }

    public void setWaitForReceiversTimeout(final String waitForReceiversTimeout) {
        setProperty(WAIT_FOR_RECEIVERS_TIMEOUT, waitForReceiversTimeout);
    }

    public String getDeviceId() {
        return getPropertyAsString(DEVICE_ID);
    }

    public void setDeviceId(final String deviceId) {
        setProperty(DEVICE_ID, deviceId);
    }

    public boolean isSetSenderTime() {
        return getPropertyAsBoolean(SET_SENDER_TIME);
    }

    public void setSetSenderTime(final boolean isSetSenderTime) {
        setProperty(SET_SENDER_TIME, isSetSenderTime);
    }

    public boolean isWaitForCredits() {
        return getPropertyAsBoolean(WAIT_FOR_CREDITS);
    }

    public void setWaitForCredits(final boolean isWaitForCredits) {
        setProperty(WAIT_FOR_CREDITS, isWaitForCredits);
    }

    public String getContentType() {
        return getPropertyAsString(CONTENT_TYPE);
    }

    public void setContentType(final String contentType) {
        setProperty(CONTENT_TYPE, contentType);
    }

    public String getData() {
        return getPropertyAsString(DATA);
    }

    public void setData(final String data) {
        setProperty(DATA, data);
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
        int waitOn = 0;
        try {
            waitOn = Integer.parseInt(getWaitForReceivers());
        } catch (NumberFormatException t) {
            LOGGER.error("wait on receivers value is not an integer - using default (0)");
        }

        int waitOnTimeout = 0;
        try {
            waitOnTimeout = Integer.parseInt(getWaitForReceiversTimeout());
        } catch (NumberFormatException t) {
            LOGGER.error("wait on receivers timeout value is not an integer - using default (0)");
        }

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
            LOGGER.info("deviceId on threadStart: {}", getDeviceId());
            honoSender.start(getDeviceId()).join();
        } catch (CompletionException e) {
            LOGGER.error("error initializing sender: {}/{} ({})", getEndpoint(), getTenant(),
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public void threadFinished() {
        if (honoSender != null) {
            try {
                honoSender.close(getDeviceId()).join();
            } catch (CompletionException e) {
                LOGGER.error("error during shut down of sender", e);
            }
        }
    }
}
