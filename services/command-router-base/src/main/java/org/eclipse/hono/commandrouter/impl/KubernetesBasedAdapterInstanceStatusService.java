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

import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.hono.client.command.CommandRoutingUtil;
import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.util.AdapterInstanceStatus;
import org.eclipse.hono.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A service for determining the status of adapter instances that run in a Kubernetes cluster along with the component
 * this service is running in.
 * <p>
 * Note that this component must be allowed to "list" and "watch" pods, requiring a corresponding RBAC configuration.
 */
public class KubernetesBasedAdapterInstanceStatusService implements AdapterInstanceStatusService {

    /**
     * The minimum duration a container, not contained in the list of current adapter containers, is kept in the
     * "suspected" state before it is considered dead. This accounts for cases when there is a significant delay in
     * the current cluster state being represented by the K8s API.
     */
    static final Duration MIN_TIME_IN_SUSPECTED_STATE = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBasedAdapterInstanceStatusService.class);
    private static final String ADAPTER_NAME_MATCH = "adapter";
    private static final long WATCH_RECREATION_DELAY_MILLIS = 100;
    private static final int MAX_LRU_MAP_ENTRIES = 200;
    private static final String STARTING_POD_UNKNOWN_CONTAINER_ID_PLACEHOLDER = "";

    private final KubernetesClient client;
    private final String namespace;
    /**
     * Values: 0: inactive, 1: active, -1: status service stopped.
     */
    private final AtomicInteger active = new AtomicInteger();
    private final Map<String, String> containerIdToPodNameMap = new ConcurrentHashMap<>();
    private final Map<String, String> podNameToContainerIdMap = new ConcurrentHashMap<>();
    private final Set<String> terminatedContainerIds = Collections
            .newSetFromMap(Collections.synchronizedMap(new LRUMap<>(MAX_LRU_MAP_ENTRIES)));
    /**
     * Value is the Instant the entry got added, i.e. the container was first marked as "suspected".
     */
    private final Map<String, Instant> suspectedContainerIds = Collections
            .synchronizedMap(new LRUMap<>(MAX_LRU_MAP_ENTRIES));
    private Watch watch;
    private Clock clock = Clock.systemUTC();

    private KubernetesBasedAdapterInstanceStatusService() throws KubernetesClientException {
        this(new KubernetesClientBuilder().build());
    }

    /**
     * To be used by unit tests.
     *
     * @param client The Kubernetes client to use.
     * @throws KubernetesClientException If listing adapter pod resources or creating the Watch failed.
     * @throws NullPointerException If any of the parameters is {@code null}.
     */
    KubernetesBasedAdapterInstanceStatusService(final KubernetesClient client) throws KubernetesClientException {
        this.client = Objects.requireNonNull(client);
        namespace = Optional.ofNullable(client.getNamespace()).orElse("default");
        initAdaptersListAndWatch();
    }

    /**
     * Creates a new KubernetesBasedAdapterInstanceStatusServiceImpl, if the Kubernetes client can connect
     * and can establish a Watch on adapter pod resources. If one or the other fails, {@code null} is returned.
     *
     * @return A new service instance or {@code null} if creation failed.
     */
    public static KubernetesBasedAdapterInstanceStatusService create() {
        if (runningInKubernetes()) {
            try {
                return new KubernetesBasedAdapterInstanceStatusService();
            } catch (final Exception e) {
                LOG.error("error creating KubernetesClient or pod watch: {}", e.toString());
            }
        }
        return null;
    }

    /**
     * Sets a clock to use for determining the current system time.
     * <p>
     * The default value of this property is {@link Clock#systemUTC()}.
     * <p>
     * This property should only be set for running tests expecting the current
     * time to be a certain value, e.g. by using {@link Clock#fixed(Instant, java.time.ZoneId)}.
     *
     * @param clock The clock to use.
     * @throws NullPointerException if clock is {@code null}.
     */
    void setClock(final Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    private static boolean runningInKubernetes() {
        return System.getenv("KUBERNETES_SERVICE_HOST") != null;
    }

    private void initAdaptersListAndWatch() throws KubernetesClientException {
        if (active.get() == -1) {
            return; // already stopped
        }
        final var podListResource = client.pods().inNamespace(namespace);
        // get full pod list synchronously here first - to know that after it we have seen all current pods
        refreshContainerLists(podListResource.list().getItems());
        watch = podListResource.watch(new Watcher<>() {
            @Override
            public void eventReceived(final Action watchAction, final Pod pod) {
                LOG.trace("event received: {}, pod: {}", watchAction, pod != null ? pod.getMetadata().getName() : null);
                if (pod != null && isPodNameMatch(pod)) {
                    applyPodStatus(pod, watchAction);
                }
            }

            @Override
            public void onClose(final WatcherException e) {
                onWatcherClosed(e);
            }
        });
        active.compareAndExchange(0, 1);
        LOG.info("initialized list of active adapter containers: {}",
                containerIdToPodNameMap.size() <= 20 ? containerIdToPodNameMap : containerIdToPodNameMap.size() + " containers");
    }

    private static boolean isPodNameMatch(final Pod pod) {
        return pod.getMetadata().getName().contains(ADAPTER_NAME_MATCH);
    }

    private synchronized void refreshContainerLists(final List<Pod> podList) {
        containerIdToPodNameMap.clear();
        podNameToContainerIdMap.clear();
        LOG.info("refresh container status list");
        podList.forEach(pod -> {
            if (isPodNameMatch(pod)) {
                LOG.trace("handle pod list result entry: {}", pod.getMetadata().getName());
                applyPodStatus(pod, null);
            }
        });
        // now that we have the full pod list, use the created container list to evaluate the suspected containers
        final Map<String, Instant> oldSuspectedIds = new HashMap<>(suspectedContainerIds);
        suspectedContainerIds.clear();
        oldSuspectedIds.forEach((suspectedId, timeAdded) -> {
            if (!containerIdToPodNameMap.containsKey(suspectedId)) {
                // container not marked as active; only consider it terminated if it is suspected for long enough already
                // (for cases where a newly started container is returned in the pod list only after a considerable delay,
                // preventing this container to be considered dead here)
                if (timeAdded.plus(MIN_TIME_IN_SUSPECTED_STATE).isBefore(Instant.now(clock))) {
                    terminatedContainerIds.add(suspectedId);
                } else {
                    suspectedContainerIds.put(suspectedId, timeAdded);
                }
            }
        });
    }

    private synchronized void applyPodStatus(final Pod pod, final Watcher.Action watchAction) {
        if (watchAction == Watcher.Action.DELETED) {
            final String podName = pod.getMetadata().getName();
            final String shortContainerId = podNameToContainerIdMap.remove(podName);
            if (STARTING_POD_UNKNOWN_CONTAINER_ID_PLACEHOLDER.equals(shortContainerId)) {
                onAdapterContainerRemoved(podName, null);
            } else if (shortContainerId != null && containerIdToPodNameMap.remove(shortContainerId) != null) {
                LOG.info("removed entry for deleted pod [{}], container [{}]; active adapter containers now: {}",
                        podName, shortContainerId, containerIdToPodNameMap.size());
                terminatedContainerIds.add(shortContainerId);
                onAdapterContainerRemoved(podName, shortContainerId);
            }
        } else if (watchAction == Watcher.Action.ERROR) {
            LOG.error("got ERROR watch action event");
        } else {
            pod.getStatus().getContainerStatuses().forEach(containerStatus -> {
                if (containerStatus.getName().contains(ADAPTER_NAME_MATCH)) {
                    applyContainerStatus(pod, containerStatus, watchAction);
                }
            });
            if (pod.getStatus().getContainerStatuses().isEmpty() && watchAction == Watcher.Action.ADDED) {
                registerAddedPodWithoutStartedContainer(pod.getMetadata().getName(), "pod ADDED");
            }
        }
    }

    private void applyContainerStatus(final Pod pod, final ContainerStatus containerStatus, final Watcher.Action watchAction) {
        final String podName = pod.getMetadata().getName();
        if (containerStatus.getContainerID() == null) {
            // container ID may be null if state is ContainerStateWaiting when creating or terminating pod
            if (containerStatus.getState().getWaiting() != null
                    && containerStatus.getLastState().getRunning() == null
                    && containerStatus.getLastState().getTerminated() == null) {
                registerAddedPodWithoutStartedContainer(podName, containerStatus.getState().getWaiting().getReason());
            } else {
                podNameToContainerIdMap.remove(podName, STARTING_POD_UNKNOWN_CONTAINER_ID_PLACEHOLDER);
            }
            return;
        }
        podNameToContainerIdMap.remove(podName, STARTING_POD_UNKNOWN_CONTAINER_ID_PLACEHOLDER);
        final String shortContainerId = getShortContainerId(containerStatus.getContainerID());
        if (shortContainerId == null) {
            LOG.warn("unexpected format of container id [{}] in pod [{}]", containerStatus.getContainerID(), podName);
        } else if (containerStatus.getState() != null && containerStatus.getState().getTerminated() != null) {
            podNameToContainerIdMap.remove(podName, shortContainerId);
            if (containerIdToPodNameMap.remove(shortContainerId) != null) {
                LOG.info("removed entry for pod [{}] and terminated container [{}] (reason: '{}'); active adapter containers now: {}",
                        podName, shortContainerId, containerStatus.getState().getTerminated().getReason(), containerIdToPodNameMap.size());
                terminatedContainerIds.add(shortContainerId);
                onAdapterContainerRemoved(podName, shortContainerId);
            }
        } else {
            if (watchAction == Watcher.Action.MODIFIED) {
                final String oldContainerId = podNameToContainerIdMap.get(podName);
                // perform check to make sure that we didn't miss a "terminated container" event
                if (oldContainerId != null && !oldContainerId.equals(shortContainerId)) {
                    podNameToContainerIdMap.remove(podName, oldContainerId);
                    if (containerIdToPodNameMap.remove(oldContainerId) != null) {
                        LOG.info("removed obsolete entry for pod [{}], container [{}]; active adapter containers now: {}",
                                podName, oldContainerId, containerIdToPodNameMap.size());
                        terminatedContainerIds.add(oldContainerId);
                        onAdapterContainerRemoved(podName, oldContainerId);
                    }
                }
            }
            podNameToContainerIdMap.put(podName, shortContainerId);
            if (containerIdToPodNameMap.put(shortContainerId, podName) == null) {
                LOG.info("added entry for pod [{}], container [{}]; active adapter containers now: {}", podName,
                        shortContainerId, containerIdToPodNameMap.size());
                suspectedContainerIds.remove(shortContainerId);
                onAdapterContainerAdded(podName, shortContainerId);
            }
        }
    }

    private void registerAddedPodWithoutStartedContainer(final String podName, final String statusInfo) {
        if (!podNameToContainerIdMap.containsKey(podName)) {
            // keep track of the pod name here
            // for handling scenarios where the subsequent MODIFIED event with a container (with non-null id) gets delayed
            LOG.debug("new pod [{}] found [state: {}]", podName, statusInfo);
            podNameToContainerIdMap.put(podName, STARTING_POD_UNKNOWN_CONTAINER_ID_PLACEHOLDER);
            onAdapterContainerAdded(podName, null);
        }
    }

    /**
     * Invoked when an adapter container was added.
     * <p>
     * During container startup, this method may first be invoked with the containerId being {@code null} if
     * the container has no id yet.
     * <p>
     * This method does nothing by default and may be overridden by subclasses.
     *
     * @param podName The pod name of the added container.
     * @param containerId The container identifier or {@code null} if no identifier is set yet.
     */
    protected void onAdapterContainerAdded(final String podName, final String containerId) {
       // nothing done by default
    }

    /**
     * Invoked when an adapter container was deleted or terminated.
     * <p>
     * This method does nothing by default and may be overridden by subclasses.
     *
     * @param podName The pod name of the removed container.
     * @param containerId The container identifier, may be {@code null} if container had no id yet.
     */
    protected void onAdapterContainerRemoved(final String podName, final String containerId) {
        // nothing done by default
    }

    private void onWatcherClosed(final WatcherException cause) {
        if (active.compareAndExchange(1, 0) != 1) {
            return; // watcher already closed or status service already stopped
        }
        containerIdToPodNameMap.clear();
        podNameToContainerIdMap.clear();
        LOG.error("Watcher closed with error", cause);
        while (active.get() == 0) {
            try {
                Thread.sleep(WATCH_RECREATION_DELAY_MILLIS);
                LOG.info("Recreating watch");
                initAdaptersListAndWatch();
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (final Exception ex) {
                LOG.error("error re-initializing adapter list and pod watch", cause);
            }
        }
    }

    @Override
    public Future<Void> start() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        LOG.trace("stopping status service");
        if (active.getAndSet(-1) == -1) {
            return Future.succeededFuture(); // already stopped
        }
        final Watch w = watch;
        if (w != null) {
            w.close();
        }
        client.close();
        return Future.succeededFuture();
    }

    @Override
    public AdapterInstanceStatus getStatus(final String adapterInstanceId) {
        if (active.get() != 1) {
            LOG.debug("no status info available for adapter instance id [{}]; service not active", adapterInstanceId);
            return AdapterInstanceStatus.UNKNOWN;
        }
        final Pair<String, String> matchedPodNameAndContainerIdPair = CommandRoutingUtil
                .getK8sPodNameAndContainerIdFromAdapterInstanceId(adapterInstanceId);
        if (matchedPodNameAndContainerIdPair == null) {
            return AdapterInstanceStatus.UNKNOWN;
        }
        final String shortContainerId = matchedPodNameAndContainerIdPair.two();
        final String registeredPodName = containerIdToPodNameMap.get(shortContainerId);
        if (registeredPodName != null) {
            LOG.trace("found alive container in pod [{}] for adapter instance id [{}]", registeredPodName, adapterInstanceId);
            return AdapterInstanceStatus.ALIVE;
        } else {
            // container derived from adapterInstanceId is not known to be alive
            if (STARTING_POD_UNKNOWN_CONTAINER_ID_PLACEHOLDER
                    .equals(podNameToContainerIdMap.get(matchedPodNameAndContainerIdPair.one()))) {
                // pod derived from adapterInstanceId is new pod with container reported to be starting up (no id yet)
                // => it may be that the adapterInstanceId container is alive but info wasn't delivered to us yet via K8s API
                LOG.debug("""
                        returning status UNKNOWN for adapter instance id [{}] - \
                        container id not known but found corresponding pod (with no known started container there yet)\
                        """, adapterInstanceId);
                return AdapterInstanceStatus.UNKNOWN;
            } else if (terminatedContainerIds.contains(shortContainerId)) {
                LOG.debug("container already terminated for adapter instance id [{}]", adapterInstanceId);
                return AdapterInstanceStatus.DEAD;
            } else {
                // container hasn't explicitly been marked as terminated
                // so it could either have been terminated before this service got started
                // or, in an unlikely case, could be newly started and alive, and the watcher hasn't informed us about it yet
                // (watcher could be currently reconnecting internally)
                LOG.debug("no container found for adapter instance id [{}]", adapterInstanceId);
                suspectedContainerIds.putIfAbsent(shortContainerId, Instant.now(clock));
                return AdapterInstanceStatus.SUSPECTED_DEAD;
            }
        }
    }

    @Override
    public Future<Set<String>> getDeadAdapterInstances(final Collection<String> adapterInstanceIds) {
        Objects.requireNonNull(adapterInstanceIds);
        if (active.get() != 1) {
            return Future.failedFuture("service not active");
        }
        boolean needPodListRefresh = false;
        final Set<String> resultSet = new HashSet<>();
        for (final String adapterInstanceId : adapterInstanceIds) {
            final AdapterInstanceStatus status = getStatus(adapterInstanceId);
            if (status == AdapterInstanceStatus.DEAD) {
                resultSet.add(adapterInstanceId);
            } else if (status == AdapterInstanceStatus.SUSPECTED_DEAD) {
                needPodListRefresh = true;
                // don't break out of loop here - let getStatus() be invoked for all, filling suspectedContainerIds if needed
            }
        }
        if (needPodListRefresh) {
            final Promise<Set<String>> resultPromise = Promise.promise();
            final Handler<Promise<Set<String>>> resultProvider = promise -> {
                try {
                    // get current pod list so that SUSPECTED_DEAD entries can be resolved
                    refreshContainerLists(client.pods().inNamespace(namespace).list().getItems());
                    final Set<String> deadAdapterInstances = adapterInstanceIds.stream()
                            .filter(id -> getStatus(id) == AdapterInstanceStatus.DEAD)
                            .collect(Collectors.toSet());
                    promise.complete(deadAdapterInstances);
                } catch (final Exception e) {
                    promise.fail(e);
                }
            };
            Optional.ofNullable(Vertx.currentContext()).ifPresentOrElse(
                    ctx -> ctx.executeBlocking(resultProvider, false, resultPromise),
                    () -> resultProvider.handle(resultPromise)
            );
            return resultPromise.future();
        } else {
            return Future.succeededFuture(resultSet);
        }
    }

    /**
     * Gets the identifiers of the currently active adapter instance containers.
     * If this service is currently not active, an empty Optional is returned.
     *
     * @return The container ids or an empty Optional.
     */
    Optional<Set<String>> getActiveAdapterInstanceContainerIds() {
        if (active.get() != 1) {
            return Optional.empty();
        }
        return Optional.of(new HashSet<>(containerIdToPodNameMap.keySet()));
    }

    /**
     * Gets the first 12 characters of the given container identifier, excluding any prefix like <em>docker://</em>.
     *
     * @param containerId The container identifier.
     * @return The short container ID or {@code null} if the given ID has an unexpected format.
     */
    static String getShortContainerId(final String containerId) {
        // containerId is e.g. docker://d0c7e6c2b80afd3b3f696465af3875b56ff98545167a29408fe196f1b50b764a
        final int lastSlashIndex = containerId.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return null;
        }
        final String fullId = containerId.substring(lastSlashIndex + 1);
        if (fullId.length() < 12) {
            return null;
        }
        return fullId.substring(0, 12);
    }

    /**
     * Map with limited size.
     *
     * @param <T> The map value type.
     */
    private static class LRUMap<T> extends LinkedHashMap<String, T> {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int maxEntries;

        LRUMap(final int maxEntries) {
            super(16, 0.75f, true);
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, T> eldest) {
            return size() > maxEntries;
        }
    }
}
