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

package org.eclipse.hono.adapter.kura.impl;

import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.config.ClientConfigProperties;


/**
 * Properties for configuring a the Kura adapter.
 *
 */
public class KuraAdapterProperties extends MqttProtocolAdapterProperties {

    /**
     * The content type used for Kura <em>control</em> messages.
     */
    public static final String DEFAULT_CONTENT_TYPE_KURA_CONTROL_MSG = "application/vnd.eclipse.kura-control";
    /**
     * The content type used for Kura <em>data</em> messages.
     */
    public static final String DEFAULT_CONTENT_TYPE_KURA_DATA_MSG = "application/vnd.eclipse.kura-data";
    /**
     * The default control prefix.
     */
    public static final String DEFAULT_CONTROL_PREFIX = "$EDC";

    private String controlPrefix = DEFAULT_CONTROL_PREFIX;
    private String ctrlMsgContentType = DEFAULT_CONTENT_TYPE_KURA_CONTROL_MSG;
    private String dataMsgContentType = DEFAULT_CONTENT_TYPE_KURA_DATA_MSG;

    /**
     * Create new Kura adapter properties with default values based on the given client configuration properties where
     * applicable.
     *
     * @param clientConfigProperties Client configuration properties whose values shall be set as default.
     */
    public KuraAdapterProperties(final ClientConfigProperties clientConfigProperties) {
        super(clientConfigProperties);
    }

    /**
     * Gets the <em>topic.control-prefix</em> to use for determining if a message published
     * by a Kura gateway is a <em>control</em> message.
     * <p>
     * The default value of this property is {@link #DEFAULT_CONTROL_PREFIX}.
     *
     * @return The prefix.
     */
    public final String getControlPrefix() {
        return controlPrefix;
    }

    /**
     * Sets the <em>topic.control-prefix</em> to use for determining if a message published
     * by a Kura gateway is a <em>control</em> message.
     * <p>
     * The default value of this property is {@link #DEFAULT_CONTROL_PREFIX}.
     *
     * @param prefix The prefix to set.
     */
    public final void setControlPrefix(final String prefix) {
        this.controlPrefix = prefix;
    }

    /**
     * Gets the content type to use for Kura <em>control</em> messages being
     * forwarded downstream.
     * <p>
     * The default value of this property is {@link #DEFAULT_CONTENT_TYPE_KURA_CONTROL_MSG}.
     *
     * @return The content type.
     */
    public final String getCtrlMsgContentType() {
        return ctrlMsgContentType;
    }

    /**
     * Sets the content type to use for Kura <em>control</em> messages being
     * forwarded downstream.
     * <p>
     * The default value of this property is {@link #DEFAULT_CONTENT_TYPE_KURA_CONTROL_MSG}.
     *
     * @param contentType The content type to set.
     */
    public final void setCtrlMsgContentType(final String contentType) {
        this.ctrlMsgContentType = contentType;
    }

    /**
     * Gets the content type to use for Kura <em>data</em> messages being
     * forwarded downstream.
     * <p>
     * The default value of this property is {@link #DEFAULT_CONTENT_TYPE_KURA_DATA_MSG}.
     *
     * @return The content type.
     */
    public final String getDataMsgContentType() {
        return dataMsgContentType;
    }

    /**
     * Sets the content type to use for Kura <em>data</em> messages being
     * forwarded downstream.
     * <p>
     * The default value of this property is {@link #DEFAULT_CONTENT_TYPE_KURA_DATA_MSG}.
     *
     * @param contentType The content type to set.
     */
    public final void setDataMsgContentType(final String contentType) {
        this.dataMsgContentType = contentType;
    }

}
