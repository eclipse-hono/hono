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

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.util.AdapterInstanceStatus;
import org.eclipse.hono.util.CommandConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.vertx.core.Future;

/**
 * A service for determining the status of adapter instances that run in a Kubernetes cluster along with the component
 * this service is running in.
 * <p>
 * Note that this component must be allowed to "list" and "watch" pods, requiring a corresponding RBAC configuration.
 */
public class KubernetesBasedAdapterInstanceStatusService implements AdapterInstanceStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBasedAdapterInstanceStatusService.class);
    private static final String ADAPTER_NAME_MATCH = "adapter";
    private static final long WATCH_RECREATION_DELAY_MILLIS = 100;
    private static final int MAX_LRU_MAP_ENTRIES = 200;

    private final KubernetesClient client;
    private final String namespace;
    /**
     * Values: 0: inactive, 1: active, -1: status service stopped.
     */
    private final AtomicInteger active = new AtomicInteger();
    private final Map<String, String> containerIdToPodNameMap = new ConcurrentHashMap<>();
    private final Map<String, String> podNameToContainerIdMap = new ConcurrentHashMap<>();
    private final Set<String> terminatedContainerIds = Collections
            .newSetFromMap(Collections.synchronizedMap(new LRUMap(MAX_LRU_MAP_ENTRIES)));
    private final Set<String> suspectedContainerIds = Collections
            .newSetFromMap(Collections.synchronizedMap(new LRUMap(MAX_LRU_MAP_ENTRIES)));
    private Watch watch;

    private KubernetesBasedAdapterInstanceStatusService() throws KubernetesClientException {
        this(new DefaultKubernetesClient());
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
        initAdaptersListAndWatch(namespace);
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

    private static boolean runningInKubernetes() {
        return System.getenv("KUBERNETES_SERVICE_HOST") != null;
    }

    private void initAdaptersListAndWatch(final String namespace) throws KubernetesClientException {
        if (active.get() == -1) {
            return; // already stopped
        }
        containerIdToPodNameMap.clear();
        podNameToContainerIdMap.clear();
        // get full pod list synchronously here first - to know that after it we have seen all current pods
        client.pods().inNamespace(namespace).list().getItems().forEach(pod -> {
            LOG.trace("handle pod list result entry: {}", pod.getMetadata().getName());
            applyPodStatus(Watcher.Action.ADDED, pod);
        });
        // now that we have the full pod list, use the created container list to evaluate the suspected containers
        for (final Iterator<String> iter = suspectedContainerIds.iterator(); iter.hasNext();) {
            final String suspectedContainerIdEntry = iter.next();
            if (!containerIdToPodNameMap.containsKey(suspectedContainerIdEntry)) {
                terminatedContainerIds.add(suspectedContainerIdEntry);
            }
            iter.remove();
        }
        watch = client.pods().inNamespace(namespace).watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(final Action watchAction, final Pod pod) {
                LOG.trace("event received: {}, pod: {}", watchAction, pod != null ? pod.getMetadata().getName() : null);
                applyPodStatus(watchAction, pod);
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

    private void applyPodStatus(final Watcher.Action watchAction, final Pod pod) {
        if (watchAction == Watcher.Action.DELETED) {
            final String podName = pod.getMetadata().getName();
            final String shortContainerId = podNameToContainerIdMap.remove(podName);
            if (shortContainerId != null && containerIdToPodNameMap.remove(shortContainerId) != null) {
                LOG.info("removed entry for deleted pod [{}], container [{}]; active adapter containers now: {}",
                        podName, shortContainerId, containerIdToPodNameMap.size());
                terminatedContainerIds.add(shortContainerId);
                onAdapterContainerRemoved(shortContainerId);
            }
        } else if (watchAction == Watcher.Action.ERROR) {
            LOG.error("got ERROR watch action event");
        } else {
            pod.getStatus().getContainerStatuses().forEach(containerStatus -> {
                applyContainerStatus(watchAction, pod, containerStatus);
            });
        }
    }

    private void applyContainerStatus(final Watcher.Action watchAction, final Pod pod, final ContainerStatus containerStatus) {
        if (!containerStatus.getName().contains(ADAPTER_NAME_MATCH) || containerStatus.getContainerID() == null) {
            // container ID may be null if state is ContainerStateWaiting when creating or terminating pod
            return;
        }
        final String podName = pod.getMetadata().getName();
        final String shortContainerId = getShortContainerId(containerStatus.getContainerID());
        if (shortContainerId == null) {
            LOG.warn("unexpected format of container id [{}] in pod [{}]", containerStatus.getContainerID(), podName);
        } else if (containerStatus.getState() != null && containerStatus.getState().getTerminated() != null) {
            podNameToContainerIdMap.remove(podName, shortContainerId);
            if (containerIdToPodNameMap.remove(shortContainerId) != null) {
                LOG.info("removed entry for pod [{}] and terminated container [{}] (reason: '{}'); active adapter containers now: {}",
                        podName, shortContainerId, containerStatus.getState().getTerminated().getReason(), containerIdToPodNameMap.size());
                terminatedContainerIds.add(shortContainerId);
                onAdapterContainerRemoved(shortContainerId);
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
                        onAdapterContainerRemoved(oldContainerId);
                    }
                }
            }
            podNameToContainerIdMap.put(podName, shortContainerId);
            if (containerIdToPodNameMap.put(shortContainerId, podName) == null) {
                LOG.info("added entry for pod [{}], container [{}]; active adapter containers now: {}", podName,
                        shortContainerId, containerIdToPodNameMap.size());
                suspectedContainerIds.remove(shortContainerId);
                onAdapterContainerAdded(shortContainerId);
            }
        }
    }

    /**
     * Invoked when an adapter container was added.
     * <p>
     * This method does nothing by default and may be overridden by subclasses.
     *
     * @param containerId The container identifier.
     */
    protected void onAdapterContainerAdded(final String containerId) {
       // nothing done by default
    }

    /**
     * Invoked when an adapter container was deleted or terminated.
     * <p>
     * This method does nothing by default and may be overridden by subclasses.
     *
     * @param containerId The container identifier.
     */
    protected void onAdapterContainerRemoved(final String containerId) {
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
                initAdaptersListAndWatch(namespace);
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
        final Matcher matcher = CommandConstants.KUBERNETES_ADAPTER_INSTANCE_ID_PATTERN.matcher(adapterInstanceId);
        if (!matcher.matches()) {
            return AdapterInstanceStatus.UNKNOWN;
        }
        final String shortContainerId = matcher.group(1);
        final String podName = containerIdToPodNameMap.get(shortContainerId);
        if (podName != null) {
            LOG.trace("found alive container in pod [{}] for adapter instance id [{}]", podName, adapterInstanceId);
            return AdapterInstanceStatus.ALIVE;
        } else {
            // container is not known to be alive
            if (terminatedContainerIds.contains(shortContainerId)) {
                LOG.debug("container already terminated for adapter instance id [{}]", adapterInstanceId);
                return AdapterInstanceStatus.DEAD;
            } else {
                // container hasn't explicitly been marked as terminated
                // so it could either have been terminated before this service got started
                // or, in an unlikely case, could be newly started and alive, and the watcher hasn't informed us about it yet
                // (watcher could be currently reconnecting internally)
                LOG.debug("no container found for adapter instance id [{}]", adapterInstanceId);
                suspectedContainerIds.add(shortContainerId);
                return AdapterInstanceStatus.SUSPECTED_DEAD;
            }
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
     */
    private static class LRUMap extends LinkedHashMap<String, Boolean> {

        private final int maxEntries;

        LRUMap(final int maxEntries) {
            super(16, 0.75f, true);
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
            return size() > maxEntries;
        }
    }
}
