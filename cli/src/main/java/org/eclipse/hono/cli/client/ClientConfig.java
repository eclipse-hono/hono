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

/**
 * Configuration class. Holds all the parameters to execute a command.
 * <p>
 * A default instance will be created at startup and it will be cloned and modified with custom
 * parameter for the execution of the command.
 */
public class ClientConfig implements  Cloneable{
    public static final String TYPE_TELEMETRY = "telemetry";
    public static final String TYPE_EVENT = "event";
    public static final String TYPE_ALL = "all";

    public ClientConfigProperties honoClientConfig;

    public String tenantId;
    public String deviceId;
    public String messageType;
    public int connectionRetryInterval;
    public int requestTimeoutInSecs;
    public String messageAddress;
    public String payload;

    /**
     * Constructor.
     *
     * @param tenantId Parameter for command execution
     * @param deviceId Parameter for command execution
     * @param messageType Parameter for command execution
     * @param connectionRetryInterval Parameter for command execution
     * @param requestTimeoutInSecs Parameter for command execution
     * @param messageAddress Parameter for command execution
     * @param payload Parameter for command execution
     */
    public ClientConfig(final String tenantId, final String deviceId, final String messageType, final int connectionRetryInterval, final int requestTimeoutInSecs, final String messageAddress, final String payload) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.messageType = messageType;
        this.connectionRetryInterval = connectionRetryInterval;
        this.requestTimeoutInSecs = requestTimeoutInSecs;
        this.payload = payload;
        this.messageAddress = messageAddress;
    }

    /**
     * MessageTypes class container.
     */
    @Component
    public static class MessageTypeProvider extends ValueProviderSupport {

        private final String[] VALUES = new String[] {
                TYPE_TELEMETRY,
                TYPE_EVENT,
                TYPE_ALL
        };

        @Override
        public List<CompletionProposal> complete(final MethodParameter parameter, final CompletionContext completionContext, final String[] hints) {
            return Arrays.stream(VALUES).map(CompletionProposal::new).collect(Collectors.toList());
        }

    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        final ClientConfigProperties cfp = new ClientConfigProperties(this.honoClientConfig);
        final ClientConfig clone = new ClientConfig(this.tenantId, this.deviceId, this.messageType, this.connectionRetryInterval, this.requestTimeoutInSecs, this.messageAddress, this.payload);
        clone.honoClientConfig = cfp;
        return clone;
    }
}
