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

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ContainerStateBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;

/**
 * Unit tests for {@link KubernetesContainerInfoProvider}.
 */
@EnableKubernetesMockClient(https = false)
public class KubernetesContainerInfoProviderTest {

    KubernetesMockServer server;
    /**
     * This client uses the namespace "test" (see KubernetesMockServer#createClient()).
     */
    KubernetesClient client;

    /**
     * Verifies that getting the container id via the K8s API succeeds.
     */
    @Test
    public void testGetContainerIdViaK8sApi() {
        final String podName = "testPod0";
        final String containerId = getRandomContainerId();
        final String containerIdWithPrefix = "containerd://" + containerId;
        final String containerNameEnvVarValue = null; // should not be needed in this test (only one running container)

        // GIVEN a Kubernetes API service mock returning a pod with a running container
        final ContainerStatus containerStatus = createRunningContainerStatus(containerIdWithPrefix, "testContainer0");
        final Pod pod = createPod(podName, List.of(containerStatus));
        server.expect()
                .withPath("/api/v1/namespaces/test/pods/" + podName)
                .andReturn(200, pod)
                .once();
        // WHEN invoking getContainerIdViaK8sApi
        final String extractedContainerId = KubernetesContainerInfoProvider.getContainerIdViaK8sApi(client, podName,
                containerNameEnvVarValue);
        // THEN the expected id was returned
        assertThat(extractedContainerId).isEqualTo(containerId);
    }

    /**
     * Verifies that getting the container id via the K8s API succeeds when there are multiple running pods.
     */
    @Test
    public void testGetContainerIdViaK8sApiWithMultipleRunningContainers() {
        final String podName = "testPod0";
        final String containerId1WithPrefix = "containerd://" + getRandomContainerId();
        final String containerId2 = getRandomContainerId();
        final String containerId2WithPrefix = "containerd://" + containerId2;
        final String containerName1 = "testContainer1";
        final String containerName2 = "testContainer2";
        final ContainerStatus containerStatus1 = createRunningContainerStatus(containerId1WithPrefix, containerName1);
        final ContainerStatus containerStatus2 = createRunningContainerStatus(containerId2WithPrefix, containerName2);

        // GIVEN a Kubernetes API service mock returning a pod with multiple running containers
        final Pod pod = createPod(podName, List.of(containerStatus1, containerStatus2));
        server.expect()
                .withPath("/api/v1/namespaces/test/pods/" + podName)
                .andReturn(200, pod)
                .once();
        // WHEN invoking getContainerIdViaK8sApi, setting "testContainer2" as the KUBERNETES_CONTAINER_NAME env var value
        final String extractedContainerId = KubernetesContainerInfoProvider.getContainerIdViaK8sApi(client, podName,
                containerName2);
        // THEN the container id of "testContainer2" is returned
        assertThat(extractedContainerId).isEqualTo(containerId2);
    }

    /**
     * Verifies that getting the container id via the K8s API fails if there are multiple running pods, but no
     * environment variable is set to specify the container name.
     */
    @Test
    public void testGetContainerIdViaK8sApiWithMultipleRunningContainersButNoContainerNameEnvVar() {
        final String podName = "testPod0";
        final String containerId1 = getRandomContainerId();
        final String containerId2 = getRandomContainerId();
        final String containerName1 = "testContainer1";
        final String containerName2 = "testContainer2";
        final ContainerStatus containerStatus1 = createRunningContainerStatus(containerId1, containerName1);
        final ContainerStatus containerStatus2 = createRunningContainerStatus(containerId2, containerName2);
        final String containerNameEnvVarValue = null;

        // GIVEN a Kubernetes API service mock returning a pod with multiple running containers
        final Pod pod = createPod(podName, List.of(containerStatus1, containerStatus2));
        server.expect()
                .withPath("/api/v1/namespaces/test/pods/" + podName)
                .andReturn(200, pod)
                .once();

        // WHEN invoking getContainerIdViaK8sApi with a null KUBERNETES_CONTAINER_NAME env var value
        // THEN an IllegalStateException is thrown
        final IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> KubernetesContainerInfoProvider.getContainerIdViaK8sApi(client, podName, containerNameEnvVarValue));
        assertThat(thrown.getMessage()).contains("multiple running containers");
    }

    private static ContainerStatus createRunningContainerStatus(final String containerIdWithPrefix,
            final String containerName) {
        return new ContainerStatusBuilder()
                .withContainerID(containerIdWithPrefix)
                .withName(containerName)
                .withState(new ContainerStateBuilder().withNewRunning().endRunning().build())
                .build();
    }

    private Pod createPod(final String podName, final List<ContainerStatus> containerStatuses) {
        final PodStatus podStatus = new PodStatusBuilder()
                .withContainerStatuses(containerStatuses)
                .build();
        return new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .endMetadata()
                .withStatus(podStatus)
                .build();
    }

    private static String getRandomContainerId() {
        return UUID.randomUUID().toString().concat(UUID.randomUUID().toString()).replaceAll("-", "");
    }
}
