/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.tests;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;


/**
 * A condition that checks if DNS rebinding works for host names containing a particular domain.
 * <p>
 * This makes sure that a look up of a host name like {@code 127.0.0.1.nip.io} is successfully resolved to IPv4
 * address {@code 127.0.0.1}.
 * <p>
 * The domain name to check is taken from {@link EnabledIfDnsRebindingIsSupported#domain()}.
 */
public class EnabledIfDnsRebindingIsSupportedCondition implements ExecutionCondition {

    private static final Map<String, ConditionEvaluationResult> RESULTS = new HashMap<>();

    /**
     * Checks if {@code 127.0.0.1.nip.io} can be resolved to {@code 127.0.0.1}.
     *
     * @param context The context to evaluate in.
     */
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {

        final String domainName = AnnotationUtils.findAnnotation(
                context.getElement(),
                EnabledIfDnsRebindingIsSupported.class)
            .map(EnabledIfDnsRebindingIsSupported::domain)
            .orElse(EnabledIfDnsRebindingIsSupported.DEFAULT_DOMAIN);

        synchronized (RESULTS) {
            return RESULTS.computeIfAbsent(domainName, this::performLookup);
        }
    }

    private ConditionEvaluationResult performLookup(final String domainName) {


        final String hostname = "127.0.0.1.%s".formatted(domainName);
        try {
            final var address = InetAddress.getByName(hostname);
            if (address.isLoopbackAddress()) {
                return ConditionEvaluationResult.enabled("lookup of %s succeeded".formatted(hostname));
            } else {
                return ConditionEvaluationResult.disabled("lookup of %s yields non-loopback address: %s"
                        .formatted(hostname, address.getHostAddress()));
            }
        } catch (final UnknownHostException e) {
            // DNS rebinding protection seems to be in place
            return ConditionEvaluationResult.disabled("""
                    DNS rebinding protection prevents resolving of %s. You might want to configure your resolver 
                    to use a DNS server that allows rebinding for domain %s as described in 
                    https://github.com/IBM-Blockchain/blockchain-vscode-extension/issues/2878#issuecomment-890147917
                    """.formatted(hostname, domainName));
        }
    }
}
