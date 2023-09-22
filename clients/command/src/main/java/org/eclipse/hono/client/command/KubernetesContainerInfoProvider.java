/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.PodStatusUtil;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * Provides information about the Kubernetes container if this application is running in Kubernetes.
 */
public class KubernetesContainerInfoProvider {

    /**
     * Name of the environment variable that contains the name of the container that this application is running in.
     * <br>
     * Such an environment variable needs to be set to determine the container id if the pod that this application
     * is running in contains multiple running containers.
     */
    public static final String KUBERNETES_CONTAINER_NAME_ENV_VAR = "KUBERNETES_CONTAINER_NAME";

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesContainerInfoProvider.class);

    private static final KubernetesContainerInfoProvider INSTANCE = new KubernetesContainerInfoProvider();

    private final AtomicReference<Promise<String>> containerIdPromiseRef = new AtomicReference<>();

    private String containerId;
    private final String podName;
    private final boolean isRunningInKubernetes;

    private KubernetesContainerInfoProvider() {
        isRunningInKubernetes = System.getenv("KUBERNETES_SERVICE_HOST") != null;
        podName = isRunningInKubernetes ? System.getenv("HOSTNAME") : null;
    }

    /**
     * Gets the KubernetesContainerInfoProvider instance.
     *
     * @return The KubernetesContainerInfoProvider.
     */
    public static KubernetesContainerInfoProvider getInstance() {
        return INSTANCE;
    }

    boolean isRunningInKubernetes() {
        return isRunningInKubernetes;
    }

    /**
     * Gets the Kubernetes pod name if running in Kubernetes.
     *
     * @return The pod name or {@code null} if not running in Kubernetes.
     */
    public String getPodName() {
        return podName;
    }

    /**
     * Determines the container id if running in a container in Kubernetes.
     * <p>
     * First an attempt is made to get the container id by inspecting <code>/proc/self/cgroup</code>
     * (via {@link CgroupV1KubernetesContainerUtil#getContainerId()}).
     * If not found there, the container id is queried via the Kubernetes API.
     * <p>
     * NOTE: The service account of the application pod must have an RBAC role allowing "get" on the "pods" resource.
     * If this application is running in a pod with multiple containers, the container that this application is running
     * in must have an environment variable with the name specified in {@link #KUBERNETES_CONTAINER_NAME_ENV_VAR} set
     * to the container name.
     *
     * @param context The vert.x context to run the code on to determine the container id via the K8s API.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded with the container id or {@code null} if not running in Kubernetes.
     *         In case of an error determining the container id, the future will be failed with either a
     *         {@link IllegalStateException} if a precondition for getting the id isn't fulfilled (because of missing
     *         permissions or because multiple pod containers exist and no KUBERNETES_CONTAINER_NAME env var is set),
     *         or otherwise a {@link ServerErrorException}.
     * @throws NullPointerException if context is {@code null}.
     */
    public Future<String> getContainerId(final Context context) {
        Objects.requireNonNull(context);
        if (containerId != null) {
            return Future.succeededFuture(containerId);
        }
        if (!isRunningInKubernetes()) {
            return Future.succeededFuture(null);
        }
        final String containerIdViaCgroup1 = CgroupV1KubernetesContainerUtil.getContainerId();
        if (containerIdViaCgroup1 != null) {
            containerId = containerIdViaCgroup1;
            return Future.succeededFuture(containerId);
        }
        final Promise<String> containerIdPromise = Promise.promise();
        if (!containerIdPromiseRef.compareAndSet(null, containerIdPromise)) {
            containerIdPromiseRef.get().future().onComplete(containerIdPromise);
            LOG.debug("getContainerId result future will be completed with the result of an already ongoing invocation");
            return containerIdPromise.future();
        }
        context.executeBlocking(codeHandler -> {
            try {
                containerId = getContainerIdViaK8sApi();
                codeHandler.complete(containerId);
            } catch (final Exception e) {
                codeHandler.fail(e);
            }
        }, containerIdPromise);
        containerIdPromise.future().onComplete(ar -> containerIdPromiseRef.set(null));
        return containerIdPromise.future();
    }

    private String getContainerIdViaK8sApi() throws ServerErrorException, IllegalStateException {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            // container name env var needs to be set if there are multiple running containers in the pod
            final String containerNameEnvVarValue = System.getenv(KUBERNETES_CONTAINER_NAME_ENV_VAR);
            return getContainerIdViaK8sApi(client, getPodName(), containerNameEnvVarValue);

        } catch (final KubernetesClientException e) {
            if (e.getCause() != null && e.getCause().getMessage() != null
                    && e.getCause().getMessage().contains("timed out")) {
                final String errorMsg = "Timed out getting container id via K8s API. Consider increasing " +
                        "the request timeout via the KUBERNETES_REQUEST_TIMEOUT env var (default is 10000[ms]).";
                LOG.error(errorMsg);
                throw new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, errorMsg);
            }
            throw new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR,
                    "Error getting container id via K8s API: " + e.getMessage());
        }
    }

    static String getContainerIdViaK8sApi(final KubernetesClient client, final String podName,
            final String containerNameEnvVarValue) throws KubernetesClientException, IllegalStateException {
        LOG.info("get container id via K8s API");
        try {
            final String namespace = Optional.ofNullable(client.getNamespace()).orElse("default");
            final Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                throw new KubernetesClientException("application pod not found in Kubernetes namespace " + namespace);
            }
            final List<ContainerStatus> containerStatuses = PodStatusUtil.getContainerStatus(pod).stream()
                    .filter(KubernetesContainerInfoProvider::isContainerRunning).toList();
            if (containerStatuses.isEmpty()) {
                LOG.debug("got empty container statuses list");
                throw new KubernetesClientException(
                        "no running container found in pod %s, namespace %s".formatted(podName, namespace));
            }
            final ContainerStatus foundContainerStatus;
            if (containerStatuses.size() > 1) {
                final String foundContainerNames = containerStatuses.stream().map(ContainerStatus::getName)
                        .collect(Collectors.joining(", "));
                if (containerNameEnvVarValue == null) {
                    LOG.error(
                            "can't get container id: found multiple running containers, but {} env var is not set " +
                                    "to specify which container to use; found containers [{}] in pod {}",
                            KUBERNETES_CONTAINER_NAME_ENV_VAR, foundContainerNames, podName);
                    throw new IllegalStateException(
                            ("can't get container id via K8s API: multiple running containers found; " +
                                    "the %s env variable needs to be set for the container this application is running in, " +
                                    "having the container name as value")
                                    .formatted(KUBERNETES_CONTAINER_NAME_ENV_VAR));
                }
                LOG.info("multiple running containers found: {}", foundContainerNames);
                LOG.info("using container name {} (derived from env var {}) to determine container id",
                        containerNameEnvVarValue, KUBERNETES_CONTAINER_NAME_ENV_VAR);
                foundContainerStatus = containerStatuses.stream()
                        .filter(status -> status.getName().equals(containerNameEnvVarValue))
                        .findFirst()
                        .orElseThrow(() -> new KubernetesClientException(
                                "no running container with name %s found in pod %s, namespace %s"
                                        .formatted(containerNameEnvVarValue, podName, namespace)));
            } else {
                foundContainerStatus = containerStatuses.get(0);
            }
            String containerId = foundContainerStatus.getContainerID();
            // remove container runtime prefix (e.g. "containerd://")
            final int delimIdx = containerId.lastIndexOf("://");
            if (delimIdx > -1) {
                containerId = containerId.substring(delimIdx + 3);
            }
            LOG.info("got container id via K8s API: {}", containerId);
            return containerId;

        } catch (final KubernetesClientException e) {
            // rethrow error concerning missing RBAC role assignment as IllegalStateException to skip retry
            // Error message looks like this:
            // Forbidden!Configured service account doesn't have access. Service account may have been revoked. pods "XXX" is forbidden:
            // User "XXX" cannot get resource "pods" in API group "" in the namespace "hono".
            if (e.getMessage().contains("orbidden")) {
                LOG.error("Error getting container id via K8s API: \n{}", e.getMessage());
                throw new IllegalStateException("error getting container id via K8s API: " +
                        "application pod needs service account with role binding allowing 'get' on 'pods' resource");
            }
            LOG.error("Error getting container id via K8s API", e);
            throw e;
        }
    }

    private static boolean isContainerRunning(final ContainerStatus containerStatus) {
        return containerStatus.getState() != null
                && containerStatus.getState().getRunning() != null;
    }
}
