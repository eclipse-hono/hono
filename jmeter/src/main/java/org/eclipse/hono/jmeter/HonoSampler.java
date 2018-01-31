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

import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.util.JMeterUtils;
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
public abstract class HonoSampler extends AbstractSampler {

    private static final long serialVersionUID = 1L;
    private static final Object semaphoreLock = new Object();

    public enum Endpoint {
        telemetry, event
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(HonoSampler.class);

    private static final String HONO_PREFIX              = "__hono_";

    private static final String HOST                     = "host";
    private static final String USER                     = "user";
    private static final String PWD                      = "pwd";
    private static final String PORT                     = "port";
    private static final String TRUSTSTORE_PATH          = "trustStorePath";

    private static final String CONTAINER                = "container";
    private static final String TENANT                   = "tenant";
    private static final String ENDPOINT                 = "endpoint";

    public String getTrustStorePath() {
        return getPropertyAsString(TRUSTSTORE_PATH);
    }

    public void setTrustStorePath(final String trustStorePath) {
        setProperty(TRUSTSTORE_PATH, trustStorePath);
    }

    public String getHost() {
        return getPropertyAsString(HOST);
    }

    public void setHost(final String host) {
        setProperty(HOST, host);
    }

    public String getUser() {
        return getPropertyAsString(USER);
    }

    public void setUser(final String user) {
        setProperty(USER, user);
    }

    public String getPwd() {
        return getPropertyAsString(PWD);
    }

    public void setPwd(final String pwd) {
        setProperty(PWD, pwd);
    }

    public String getPort() {
        return getPropertyAsString(PORT);
    }

    public void setPort(final String port) {
        setProperty(PORT, port);
    }

    public String getContainer() {
        return getPropertyAsString(CONTAINER);
    }

    public void setContainer(final String container) {
        setProperty(CONTAINER, container);
    }

    public String getTenant() {
        return getPropertyAsString(TENANT);
    }
    public void setTenant(final String tenant) {
        setProperty(TENANT,tenant);
    }

    public String getEndpoint() {
        return getPropertyAsString(ENDPOINT);
    }
    public void setEndpoint(final Endpoint endpoint) {
        setProperty(ENDPOINT,endpoint.toString());
    }

    protected String getAddress() {
        return getEndpoint()+"/"+getTenant();
    }

    void addSemaphore() {

        synchronized (semaphoreLock) {
            final String receivers = (getSemaphores() + 1) + "";
            JMeterUtils.setProperty(HONO_PREFIX + getAddress(), receivers);
            LOGGER.info("addSemaphore - receivers: {}",receivers);
        }
    }

    int getSemaphores() {
        synchronized (semaphoreLock) {
            final String semaphores = JMeterUtils.getProperty(HONO_PREFIX + getAddress());
            if (semaphores == null) {
                return 0;
            } else {
                return Integer.parseInt(semaphores);
            }
        }
    }

    void removeSemaphores() {
        synchronized (semaphoreLock) {
            JMeterUtils.setProperty(HONO_PREFIX + getAddress(), "0");
        }
    }
}
