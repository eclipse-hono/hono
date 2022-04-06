/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl;

import static org.junit.jupiter.api.Assertions.fail;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.util.AdapterInstanceStatus;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.api.model.WatchEventBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;

/**
 * Tests verifying behavior of {@link KubernetesBasedAdapterInstanceStatusService}.
 *
 */
@EnableKubernetesMockClient(https = false)
public class KubernetesBasedAdapterInstanceStatusServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBasedAdapterInstanceStatusServiceTest.class);

    KubernetesMockServer server;
    /**
     * This client uses the namespace "test" (see KubernetesMockServer#createClient()).
     */
    KubernetesClient client;

    private KubernetesBasedAdapterInstanceStatusService statusService;

    /**
     * Verifies that an added adapter pod is detected by the status service.
     *
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testServiceDetectsAddedPod() throws InterruptedException {
        final CountDownLatch eventLatch = new CountDownLatch(2);

        final Pod pod0 = createAdapterPodWithRunningContainer("testPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addToItems(pod0).build())
                .once();

        final Pod pod1 = createAdapterPodWithRunningContainer("testPod1");
        final String pod1ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod1.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod1ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10).andEmit(new WatchEvent(pod1, "ADDED"))
                .done()
                .once();

        statusService = new KubernetesBasedAdapterInstanceStatusService(client) {
            @Override
            protected void onAdapterContainerAdded(final String containerId) {
                LOG.debug("onAdapterContainerAdded; containerId: '{}'", containerId);
                eventLatch.countDown();
            }
        };
        assertThat(statusService).isNotNull();

        if (eventLatch.await(10, TimeUnit.SECONDS)) {
            assertThat(statusService.getActiveAdapterInstanceContainerIds().isPresent()).isTrue();
            assertThat(statusService.getActiveAdapterInstanceContainerIds().get()).containsExactly(pod0ContainerId, pod1ContainerId);
            final String adapterInstanceId0 = pod0.getMetadata().getName() + "_" + pod0ContainerId + "_1";
            assertThat(statusService.getStatus(adapterInstanceId0)).isEqualTo(AdapterInstanceStatus.ALIVE);
            final String adapterInstanceId1 = pod1.getMetadata().getName() + "_" + pod1ContainerId + "_1";
            assertThat(statusService.getStatus(adapterInstanceId1)).isEqualTo(AdapterInstanceStatus.ALIVE);
        } else {
            fail("added pod not detected");
        }
    }

    /**
     * Verifies that a deleted adapter pod is detected by the status service.
     *
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testServiceDetectsDeletedPod() throws InterruptedException {
        final CountDownLatch eventLatch = new CountDownLatch(3);

        final Pod pod0 = createAdapterPodWithRunningContainer("testPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();
        final Pod pod1 = createAdapterPodWithRunningContainer("testPod1");
        final String pod1ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod1.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod1ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addToItems(pod0, pod1).build())
                .once();

        server.expect().withPath("/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10).andEmit(new WatchEvent(pod1, "DELETED"))
                .done()
                .once();

        statusService = new KubernetesBasedAdapterInstanceStatusService(client) {
            @Override
            protected void onAdapterContainerAdded(final String containerId) {
                LOG.debug("onAdapterContainerAdded; containerId: '{}'", containerId);
                eventLatch.countDown();
            }

            @Override
            protected void onAdapterContainerRemoved(final String containerId) {
                LOG.debug("onAdapterContainerRemoved; containerId: '{}'", containerId);
                eventLatch.countDown();
            }
        };
        assertThat(statusService).isNotNull();

        if (eventLatch.await(10, TimeUnit.SECONDS)) {
            assertThat(statusService.getActiveAdapterInstanceContainerIds().isPresent()).isTrue();
            assertThat(statusService.getActiveAdapterInstanceContainerIds().get()).containsExactly(pod0ContainerId);
            final String adapterInstanceId0 = pod0.getMetadata().getName() + "_" + pod0ContainerId + "_1";
            assertThat(statusService.getStatus(adapterInstanceId0)).isEqualTo(AdapterInstanceStatus.ALIVE);
            final String adapterInstanceId1 = pod1.getMetadata().getName() + "_" + pod1ContainerId + "_1";
            assertThat(statusService.getStatus(adapterInstanceId1)).isEqualTo(AdapterInstanceStatus.DEAD);
        } else {
            fail("added/removed pod not detected");
        }
    }

    /**
     * Verifies that a terminated container in an adapter pod is detected by the status service.
     *
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testServiceDetectsTerminatedContainer() throws InterruptedException {
        final CountDownLatch eventLatch = new CountDownLatch(4);

        final Pod pod0 = createAdapterPodWithRunningContainer("testPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();
        final Pod pod1WithFirstContainer = createAdapterPodWithRunningContainer("testPod1");
        final String pod1FirstContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod1WithFirstContainer.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod1FirstContainerId).isNotNull();

        final Pod pod1WithFirstContainerTerminated = createCopyWithTerminatedContainer(pod1WithFirstContainer);

        final Pod pod1WithSecondContainer = createAdapterPodWithRunningContainer("testPod1");
        final String pod1SecondContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod1WithSecondContainer.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod1SecondContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addToItems(pod0, pod1WithFirstContainer).build())
                .once();

        server.expect().withPath("/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10).andEmit(new WatchEvent(pod1WithFirstContainerTerminated, "MODIFIED"))
                .waitFor(20).andEmit(new WatchEvent(pod1WithSecondContainer, "MODIFIED"))
                .done()
                .once();

        statusService = new KubernetesBasedAdapterInstanceStatusService(client) {
            @Override
            protected void onAdapterContainerAdded(final String containerId) {
                LOG.debug("onAdapterContainerAdded; containerId: '{}'", containerId);
                eventLatch.countDown();
            }

            @Override
            protected void onAdapterContainerRemoved(final String containerId) {
                LOG.debug("onAdapterContainerRemoved; containerId: '{}'", containerId);
                eventLatch.countDown();
            }
        };
        assertThat(statusService).isNotNull();

        if (eventLatch.await(10, TimeUnit.SECONDS)) {
            assertThat(statusService.getActiveAdapterInstanceContainerIds().isPresent()).isTrue();
            assertThat(statusService.getActiveAdapterInstanceContainerIds().get()).containsExactly(pod0ContainerId, pod1SecondContainerId);
            final String adapterInstanceId0 = pod0.getMetadata().getName() + "_" + pod0ContainerId + "_1";
            assertThat(statusService.getStatus(adapterInstanceId0)).isEqualTo(AdapterInstanceStatus.ALIVE);
            final String adapterInstanceId1 = pod1WithFirstContainer.getMetadata().getName() + "_" + pod1FirstContainerId + "_1";
            assertThat(statusService.getStatus(adapterInstanceId1)).isEqualTo(AdapterInstanceStatus.DEAD);
        } else {
            fail("added/removed pod not detected");
        }
    }

    /**
     * Verifies that when the pod watcher of the status service is closed, a new watcher gets created
     * and subsequently added pods get detected.
     *
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testServiceReactivatesAfterWatchClosed() throws InterruptedException {
        // 3 events: 2x on initAdaptersListAndWatch() + 1x on WatchEvent
        final CountDownLatch eventLatch = new CountDownLatch(3);

        final Pod pod0 = createAdapterPodWithRunningContainer("testPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addToItems(pod0).build())
                .times(2);

        final Pod pod1 = createAdapterPodWithRunningContainer("testPod1");
        final String pod1ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod1.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod1ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10).andEmit(outdatedEvent())
                .done()
                .once();

        server.expect().withPath("/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10).andEmit(new WatchEvent(pod1, "MODIFIED"))
                .done()
                .once();

        statusService = new KubernetesBasedAdapterInstanceStatusService(client) {
            @Override
            protected void onAdapterContainerAdded(final String containerId) {
                LOG.debug("onAdapterContainerAdded; containerId: '{}'", containerId);
                eventLatch.countDown();
            }
        };
        assertThat(statusService).isNotNull();

        if (eventLatch.await(10, TimeUnit.SECONDS)) {
            assertThat(statusService.getActiveAdapterInstanceContainerIds().isPresent()).isTrue();
            assertThat(statusService.getActiveAdapterInstanceContainerIds().get()).containsExactly(pod0ContainerId, pod1ContainerId);
            final String adapterInstanceId0 = pod0.getMetadata().getName() + "_" + pod0ContainerId + "_1";
            assertThat(statusService.getStatus(adapterInstanceId0)).isEqualTo(AdapterInstanceStatus.ALIVE);
            final String adapterInstanceId1 = pod1.getMetadata().getName() + "_" + pod1ContainerId + "_1";
            assertThat(statusService.getStatus(adapterInstanceId1)).isEqualTo(AdapterInstanceStatus.ALIVE);
        } else {
            fail("added pod not detected");
        }
    }

    private Pod createCopyWithTerminatedContainer(final Pod pod) {
        final String containerID = pod.getStatus().getContainerStatuses().get(0).getContainerID();
        final String podName = pod.getMetadata().getName();
        return createAdapterPod(podName, containerID, new ContainerStateBuilder().withNewTerminated()
                .withContainerID(containerID).endTerminated().build());
    }

    private Pod createAdapterPodWithRunningContainer(final String podName) {
        final String containerId = getRandomContainerId();
        return createAdapterPod(podName, containerId, new ContainerStateBuilder().withNewRunning().endRunning().build());
    }

    private Pod createAdapterPod(final String podName, final String containerId, final ContainerState podState) {
        final PodStatus podStatus = new PodStatusBuilder()
                .addNewContainerStatus()
                .withContainerID(containerId)
                .withName("test_adapter")
                .withState(podState)
                .endContainerStatus()
                .build();
        return new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .endMetadata()
                .withStatus(podStatus)
                .build();
    }

    private static String getRandomContainerId() {
        return "docker://" + UUID.randomUUID().toString().concat(UUID.randomUUID().toString())
                .replaceAll("-", "");
    }

    private static WatchEvent outdatedEvent() {
        return new WatchEventBuilder()
                .withType("ERROR")
                .withStatusObject(new StatusBuilder()
                        .withCode(HttpURLConnection.HTTP_GONE)
                        .withMessage("410: The event in requested index is outdated and cleared (the requested history has been cleared [3/1]) [2]")
                        .build())
                .build();
    }
}
