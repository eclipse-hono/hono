/*******************************************************************************
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.Strings;

/**
 * Utility methods used in connection with routing of Command and Control messages.
 */
public class CommandRoutingUtil {

    /**
     * Pattern of the adapter instance identifier, used when routing a command message to a protocol
     * adapter running in a Kubernetes cluster.
     * <p>
     * The first matcher group contains the pod name, the second matcher group contains the first 12 characters of the
     * docker container id of the adapter instance.
     */
    private static final Pattern KUBERNETES_ADAPTER_INSTANCE_ID_PATTERN = Pattern.compile("^(.*)_([0-9a-f]{12})_\\d+$");

    private CommandRoutingUtil() {
        // prevent instantiation
    }

    /**
     * Creates a new adapter instance identifier, used for identifying the protocol adapter to route a command to.
     * <p>
     * If this application is running in a Kubernetes cluster and the container id is supplied as parameter here,
     * the format of the returned id is <em>[prefix]_[container_id]_[counter]</em>, with prefix being the name of
     * the Kubernetes pod.
     * See also {@link #getK8sPodNameAndContainerIdFromAdapterInstanceId(String)}.
     * <p>
     * If not running in a Kubernetes cluster, a random id with the given adapter name as prefix is used.
     *
     * @param adapterName The adapter name.
     * @param k8sContainerId The container id or {@code null} if not running in Kubernetes.
     * @param counter The counter value to use.
     * @return The new adapter instance identifier.
     */
    public static String getNewAdapterInstanceId(final String adapterName, final String k8sContainerId,
            final int counter) {
        if (k8sContainerId == null || k8sContainerId.length() < 12) {
            return getNewAdapterInstanceIdForNonK8sEnv(adapterName);
        } else {
            // running in Kubernetes: use pod name as prefix
            String prefix = KubernetesContainerInfoProvider.getInstance().getPodName();
            if (Strings.isNullOrEmpty(prefix)) {
                prefix = adapterName;
            }
            return getNewAdapterInstanceIdForK8sEnv(prefix, k8sContainerId, counter);
        }
    }

    /**
     * Creates a new adapter instance identifier for use in a non-Kubernetes environment.
     * <p>
     * The format is <em>[adapterName]_[uuid]</em>.
     *
     * @param adapterName The adapter name to use.
     * @return The new adapter instance identifier.
     */
    public static String getNewAdapterInstanceIdForNonK8sEnv(final String adapterName) {
        final String prefix = Strings.isNullOrEmpty(adapterName) ? ""
                : adapterName.replaceAll("[^a-zA-Z0-9._-]", "") + "-";
        return prefix + UUID.randomUUID();
    }

    /**
     * Creates a new adapter instance identifier for use in a Kubernetes environment.
     * <p>
     * The format is <em>[pod_name]_[docker_container_id]_[counter]</em>.
     *
     * @param podName The pod name to use.
     * @param containerId The container identifier to use.
     * @param counter The counter value to use.
     * @return The new adapter instance identifier.
     * @throws NullPointerException If containerId is {@code null}.
     */
    public static String getNewAdapterInstanceIdForK8sEnv(final String podName, final String containerId, final int counter) {
        Objects.requireNonNull(containerId);
        // replace special characters so that the id can be used in a Kafka topic name
        final String podNameToUse = Optional.ofNullable(podName)
                .map(p -> p.replaceAll("[^a-zA-Z0-9._-]", "")).orElse("");
        return String.format("%s_%s_%d",
                podNameToUse,
                containerId.substring(0, 12),
                counter);
    }

    /**
     * Gets the pod name and container identifier from a given adapter instance identifier that was created via
     * {@link #getNewAdapterInstanceIdForK8sEnv(String, String, int)}.
     *
     * @param adapterInstanceId The adapter instance identifier.
     * @return The pod name and container identifier pair or {@code null} if the adapter instance identifier didn't
     *         match.
     * @throws NullPointerException If adapterInstanceId is {@code null}.
     */
    public static Pair<String, String> getK8sPodNameAndContainerIdFromAdapterInstanceId(final String adapterInstanceId) {
        Objects.requireNonNull(adapterInstanceId);
        final Matcher matcher = KUBERNETES_ADAPTER_INSTANCE_ID_PATTERN.matcher(adapterInstanceId);
        if (!matcher.matches()) {
            return null;
        }
        return Pair.of(matcher.group(1), matcher.group(2));
    }
}
