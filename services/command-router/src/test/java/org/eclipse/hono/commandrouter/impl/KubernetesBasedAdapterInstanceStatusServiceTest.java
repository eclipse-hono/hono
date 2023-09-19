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

package org.eclipse.hono.commandrouter.impl;

import static org.junit.jupiter.api.Assertions.fail;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.command.CommandRoutingUtil;
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
import io.vertx.core.Future;

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

        final Pod pod0 = createAdapterPodWithRunningContainer("adapterTestPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addToItems(pod0).build())
                .once();

        final Pod pod1 = createAdapterPodWithRunningContainer("adapterTestPod1");
        final String pod1ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod1.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod1ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10).andEmit(new WatchEvent(pod1, "MODIFIED")) // preceding ADDED event (with pod without containers) omitted here
                .done()
                .once();

        statusService = new KubernetesBasedAdapterInstanceStatusService(client) {
            @Override
            protected void onAdapterContainerAdded(final String podName, final String containerId) {
                LOG.debug("onAdapterContainerAdded; podName: '{}', containerId: '{}'", podName, containerId);
                if (containerId != null) {
                    eventLatch.countDown();
                }
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
     * Verifies that a starting adapter pod with either no container or only a container without an id yet is detected
     * by the status service and given an UNKNOWN status.
     *
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testServiceHandlesStartingContainerWithNoIDYet() throws InterruptedException {
        final CountDownLatch eventLatch = new CountDownLatch(2);

        final Pod pod0 = createAdapterPodWithStartingContainer("adapterTestPod0");
        final Pod pod1 = createAdapterPodWithoutContainer("adapterTestPod1");

        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addToItems(pod0).build())
                .once();

        server.expect().withPath("/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(10).andEmit(new WatchEvent(pod1, "ADDED"))
                .done()
                .once();

        statusService = new KubernetesBasedAdapterInstanceStatusService(client) {
            @Override
            protected void onAdapterContainerAdded(final String podName, final String containerId) {
                LOG.debug("onAdapterContainerAdded; podName: '{}', containerId: '{}'", podName, containerId);
                if (containerId == null) {
                    eventLatch.countDown();
                }
            }
        };
        assertThat(statusService).isNotNull();

        if (eventLatch.await(10, TimeUnit.SECONDS)) {
            assertThat(statusService.getActiveAdapterInstanceContainerIds().isPresent()).isTrue();
            assertThat(statusService.getActiveAdapterInstanceContainerIds().get()).isEmpty();

            final String someContainerId0 = "012345678901";
            final String adapterInstanceId0 = pod0.getMetadata().getName() + "_" + someContainerId0 + "_1";
            assertThat(statusService.getStatus(adapterInstanceId0)).isEqualTo(AdapterInstanceStatus.UNKNOWN);

            final String someContainerId1 = "123456789012";
            final String adapterInstanceId1 = pod1.getMetadata().getName() + "_" + someContainerId1 + "_1";
            assertThat(statusService.getStatus(adapterInstanceId1)).isEqualTo(AdapterInstanceStatus.UNKNOWN);
        } else {
            fail("added pod not detected");
        }
    }

    /**
     * Verifies that the status service identifies a given adapter instance as dead, if no container with that id
     * exists and the minimum time period in the suspected state has elapsed.
     */
    @Test
    public void testServiceReportsStateOfUnknownAdapterInstanceAsDeadAfterDelay() {

        final String nonExistingInstanceId = "old-adapter-pod_000000000000_0";
        assertThat(CommandRoutingUtil.getK8sPodNameAndContainerIdFromAdapterInstanceId(nonExistingInstanceId))
                .isNotNull();

        final Pod pod0 = createAdapterPodWithRunningContainer("adapterTestPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addToItems(pod0).build())
                .times(3);

        server.expect().withPath("/api/v1/namespaces/test/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(100).andEmit(new WatchEvent(pod0, "MODIFIED")) // event irrelevant here, watcher just has to be kept active
                .done()
                .always();

        // GIVEN a status service working on a cluster with one running adapter pod
        statusService = new KubernetesBasedAdapterInstanceStatusService(client);
        assertThat(statusService).isNotNull();

        // WHEN getting the status of a non-existing adapter instance
        assertThat(statusService.getStatus(nonExistingInstanceId)).isEqualTo(AdapterInstanceStatus.SUSPECTED_DEAD);
        // THEN the invocation of "getDeadAdapterInstances" (doing the extra checks concerning already suspected entries)
        //  will first return a SUSPECTED_DEAD state
        Future<Set<String>> deadAdapterInstances = statusService.getDeadAdapterInstances(Set.of(nonExistingInstanceId));
        assertThat(deadAdapterInstances.succeeded()).isTrue();
        assertThat(deadAdapterInstances.result()).isEmpty();
        // WHEN the MIN_TIME_IN_SUSPECTED_STATE has elapsed
        statusService.setClock(Clock.offset(Clock.systemUTC(),
                KubernetesBasedAdapterInstanceStatusService.MIN_TIME_IN_SUSPECTED_STATE));

        // THEN the state is returned as DEAD
        deadAdapterInstances = statusService.getDeadAdapterInstances(Set.of(nonExistingInstanceId));
        assertThat(deadAdapterInstances.succeeded()).isTrue();
        assertThat(deadAdapterInstances.result()).containsExactly(nonExistingInstanceId);
    }

    /**
     * Verifies that a deleted adapter pod is detected by the status service.
     *
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testServiceDetectsDeletedPod() throws InterruptedException {
        final CountDownLatch eventLatch = new CountDownLatch(3);

        final Pod pod0 = createAdapterPodWithRunningContainer("adapterTestPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();
        final Pod pod1 = createAdapterPodWithRunningContainer("adapterTestPod1");
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
            protected void onAdapterContainerAdded(final String podName, final String containerId) {
                LOG.debug("onAdapterContainerAdded; podName: '{}', containerId: '{}'", podName, containerId);
                if (containerId != null) {
                    eventLatch.countDown();
                }
            }

            @Override
            protected void onAdapterContainerRemoved(final String podName, final String containerId) {
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

        final Pod pod0 = createAdapterPodWithRunningContainer("adapterTestPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();
        final Pod pod1WithFirstContainer = createAdapterPodWithRunningContainer("adapterTestPod1");
        final String pod1FirstContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod1WithFirstContainer.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod1FirstContainerId).isNotNull();

        final Pod pod1WithFirstContainerTerminated = createCopyWithTerminatedContainer(pod1WithFirstContainer);

        final Pod pod1WithSecondContainer = createAdapterPodWithRunningContainer("adapterTestPod1");
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
            protected void onAdapterContainerAdded(final String podName, final String containerId) {
                LOG.debug("onAdapterContainerAdded; podName: '{}', containerId: '{}'", podName, containerId);
                if (containerId != null) {
                    eventLatch.countDown();
                }
            }

            @Override
            protected void onAdapterContainerRemoved(final String podName, final String containerId) {
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

        final Pod pod0 = createAdapterPodWithRunningContainer("adapterTestPod0");
        final String pod0ContainerId = KubernetesBasedAdapterInstanceStatusService
                .getShortContainerId(pod0.getStatus().getContainerStatuses().get(0).getContainerID());
        assertThat(pod0ContainerId).isNotNull();

        server.expect().withPath("/api/v1/namespaces/test/pods")
                .andReturn(200, new PodListBuilder().addToItems(pod0).build())
                .times(2);

        final Pod pod1 = createAdapterPodWithRunningContainer("adapterTestPod1");
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
            protected void onAdapterContainerAdded(final String podName, final String containerId) {
                LOG.debug("onAdapterContainerAdded; podName: '{}', containerId: '{}'", podName, containerId);
                if (containerId != null) {
                    eventLatch.countDown();
                }
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
        final ContainerState containerState = new ContainerStateBuilder()
                .withNewTerminated().withContainerID(containerID).endTerminated()
                .build();
        final ContainerState containerLastState = null; // value not set (not needed for the tests)
        return createAdapterPod(podName, containerID, containerState, containerLastState);
    }

    private Pod createAdapterPodWithRunningContainer(final String podName) {
        final String containerId = getRandomContainerId();
        final ContainerState containerState = new ContainerStateBuilder().withNewRunning().endRunning().build();
        final ContainerState containerLastState = null; // value not set (not needed for the tests)
        return createAdapterPod(podName, containerId, containerState, containerLastState);
    }

    private Pod createAdapterPodWithStartingContainer(final String podName) {
        final String containerId = null;
        final ContainerState containerState = new ContainerStateBuilder()
                .withNewWaiting(null, "ContainerCreating")
                .build();
        final ContainerState containerLastState = new ContainerStateBuilder().build();
        return createAdapterPod(podName, containerId, containerState, containerLastState);
    }

    private Pod createAdapterPodWithoutContainer(final String podName) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .endMetadata()
                .withStatus(new PodStatusBuilder().build())
                .build();
    }

    private Pod createAdapterPod(final String podName, final String containerId, final ContainerState podContainerState,
            final ContainerState podContainerLastState) {
        final PodStatus podStatus = new PodStatusBuilder()
                .addNewContainerStatus()
                .withContainerID(containerId)
                .withName("test_adapter")
                .withState(podContainerState)
                .withLastState(podContainerLastState)
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
