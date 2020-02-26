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

package org.eclipse.hono.cli.client;

import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.core.MethodParameter;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.ValueProviderSupport;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ClientConfig implements  Cloneable{
    public static final String TYPE_TELEMETRY = "telemetry";
    public static final String TYPE_EVENT = "event";
    public static final String TYPE_ALL = "all";

    public String tenantId;
    public String deviceId;
    public String messageType;
    public int connectionRetryInterval;
    public int requestTimeoutInSecs;
    public String messageAddress;
    public String payload;

    public ClientConfig(String tenantId, String deviceId, String messageType, int connectionRetryInterval, int requestTimeoutInSecs, String messageAddress, String payload) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.messageType = messageType;
        this.connectionRetryInterval = connectionRetryInterval;
        this.requestTimeoutInSecs = requestTimeoutInSecs;
        this.payload = payload;
        this.messageAddress = messageAddress;
    }

    @Component
    public static class MessageTypeProvider extends ValueProviderSupport {

        private final String[] VALUES = new String[] {
                TYPE_TELEMETRY,
                TYPE_EVENT,
                TYPE_ALL
        };

        @Override
        public List<CompletionProposal> complete(MethodParameter parameter, CompletionContext completionContext, String[] hints) {
            return Arrays.stream(VALUES).map(CompletionProposal::new).collect(Collectors.toList());
        }
    }

    public ClientConfigProperties honoClientConfig;

    @Override
    public Object clone() throws CloneNotSupportedException {
        ClientConfigProperties cfp = new ClientConfigProperties(this.honoClientConfig);
        ClientConfig clone = new ClientConfig(this.tenantId, this.deviceId, this.messageType, this.connectionRetryInterval, this.requestTimeoutInSecs, this.messageAddress, this.payload);
        clone.honoClientConfig = cfp;
        return clone;
    }
}
