/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.kura;

import org.eclipse.hono.config.ProtocolAdapterProperties;


/**
 * Properties for configuring a the Kura adapter.
 *
 */
public class KuraAdapterProperties extends ProtocolAdapterProperties {

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
