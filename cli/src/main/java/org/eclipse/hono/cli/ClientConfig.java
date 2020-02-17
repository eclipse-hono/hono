package org.eclipse.hono.cli;

import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.beans.factory.annotation.Value;
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

    @Value(value = "${tenant.id}")
    public String tenantId;
    @Value(value = "${device.id}")
    public String deviceId;
    @Value(value = "${message.type}")
    public String messageType;
    @Value(value = "${connection.retryInterval}")
    public int connectionRetryInterval;
    @Value(value = "${command.timeoutInSeconds}")
    public int requestTimeoutInSecs;

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
        return super.clone();
    }
    //TODO solve and test clone
    //start hono.eclipseprojects.io 15672 consumer@HONO verysecret
    //TODO check that the annotation value be got by the variables
}
