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
public abstract class HonoSampler extends AbstractSampler {

    private static final long serialVersionUID = 1L;
    private static final Object semaphoreLock = new Object();

    /**
     * Endpoint type.
     */
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

    /**
     * Applies the options to the local UI.
     * 
     * @param serverOptions The options to apply.
     */
    public void modifyServerOptions(final ServerOptionsPanel serverOptions) {
        setHost(serverOptions.getHost());
        setPort(serverOptions.getPort());
        setUser(serverOptions.getUser());
        setPwd(serverOptions.getPwd());
        setTrustStorePath(serverOptions.getTrustStorePath());
    }

    /**
     * Apply the local UI options to the provided object.
     * 
     * @param serverOptions The options to change.
     */
    public void configureServerOptions(final ServerOptionsPanel serverOptions) {
        serverOptions.setHost(getHost());
        serverOptions.setPort(getPort());
        serverOptions.setUser(getUser());
        serverOptions.setPwd(getPwd());
        serverOptions.setTrustStorePath(getTrustStorePath());
    }

    public String getTrustStorePath() {
        return getPropertyAsString(TRUSTSTORE_PATH);
    }

    /**
     * Sets the path of the trust store.
     * 
     * @param trustStorePath The path to the trust store.
     */
    public void setTrustStorePath(final String trustStorePath) {
        setProperty(TRUSTSTORE_PATH, trustStorePath);
    }

    public String getHost() {
        return getPropertyAsString(HOST);
    }

    /**
     * Sets the host to use.
     * 
     * @param host The hostname to use.
     */
    public void setHost(final String host) {
        setProperty(HOST, host);
    }

    public String getUser() {
        return getPropertyAsString(USER);
    }

    /**
     * Sets the user to use.
     * 
     * @param user The user name to use.
     */
    public void setUser(final String user) {
        setProperty(USER, user);
    }

    public String getPwd() {
        return getPropertyAsString(PWD);
    }

    /**
     * Sets the password to use.
     * 
     * @param pwd The password to use.
     */
    public void setPwd(final String pwd) {
        setProperty(PWD, pwd);
    }

    /**
     * Returns the port number as int.
     * 
     * @return The port number as int.
     */
    public int getPortAsInt() {
        final String portString = getPort();
        try {
            return Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getPort() {
        return getPropertyAsString(PORT);
    }

    /**
     * Sets the port to use.
     * 
     * @param port The port to use.
     */
    public void setPort(final String port) {
        setProperty(PORT, port);
    }

    public String getContainer() {
        return getPropertyAsString(CONTAINER);
    }

    /**
     * Sets the AMQP container name to use.
     * 
     * @param container The container name to use.
     */
    public void setContainer(final String container) {
        setProperty(CONTAINER, container);
    }

    public String getTenant() {
        return getPropertyAsString(TENANT);
    }

    /**
     * Sets the tenant name to use.
     * 
     * @param tenant The tenant name to use.
     */
    public void setTenant(final String tenant) {
        setProperty(TENANT, tenant);
    }

    public String getEndpoint() {
        return getPropertyAsString(ENDPOINT);
    }

    /**
     * Sets the endpoint type to use.
     * 
     * @param endpoint The endpoint type to use.
     */
    public void setEndpoint(final Endpoint endpoint) {
        setProperty(ENDPOINT, endpoint.toString());
    }

    protected String getAddress() {
        return getEndpoint() + "/" + getTenant();
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
