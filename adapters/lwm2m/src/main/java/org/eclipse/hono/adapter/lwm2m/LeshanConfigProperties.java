/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.lwm2m;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Leshan Demo Server.
 */
@Component
@ConfigurationProperties(prefix = "leshan.server")
public class LeshanConfigProperties {

    private String coapBindAddress = "0.0.0.0";
    private int coapPort = 5683;
    private String coapsBindAddress = "0.0.0.0";
    private int coapsPort = 5684;
    private String httpBindAddress = "0.0.0.0";
    private int httpPort = 8080;
    private String objectDir;

    /**
     * @return the coapBindAddress
     */
    public String getCoapBindAddress() {
        return coapBindAddress;
    }
    /**
     * @param coapBindAddress the coapBindAddress to set
     */
    public void setCoapBindAddress(String coapBindAddress) {
        this.coapBindAddress = coapBindAddress;
    }
    /**
     * @return the coapPort
     */
    public int getCoapPort() {
        return coapPort;
    }
    /**
     * @param coapPort the coapPort to set
     */
    public void setCoapPort(int coapPort) {
        this.coapPort = coapPort;
    }
    /**
     * @return the coapsBindAddress
     */
    public String getCoapsBindAddress() {
        return coapsBindAddress;
    }
    /**
     * @param coapsBindAddress the coapsBindAddress to set
     */
    public void setCoapsBindAddress(String coapsBindAddress) {
        this.coapsBindAddress = coapsBindAddress;
    }
    /**
     * @return the coapsPort
     */
    public int getCoapsPort() {
        return coapsPort;
    }
    /**
     * @param coapsPort the coapsPort to set
     */
    public void setCoapsPort(int coapsPort) {
        this.coapsPort = coapsPort;
    }
    /**
     * @return the httpBindAddress
     */
    public String getHttpBindAddress() {
        return httpBindAddress;
    }
    /**
     * @param httpBindAddress the httpBindAddress to set
     */
    public void setHttpBindAddress(String httpBindAddress) {
        this.httpBindAddress = httpBindAddress;
    }
    /**
     * @return the httpPort
     */
    public int getHttpPort() {
        return httpPort;
    }
    /**
     * @param httpPort the httpPort to set
     */
    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }
    /**
     * @return the objectDir
     */
    public String getObjectDir() {
        return objectDir;
    }
    /**
     * @param objectDir the objectDir to set
     */
    public void setObjectDir(String objectDir) {
        this.objectDir = objectDir;
    }

}
