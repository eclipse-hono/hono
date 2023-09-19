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
package org.eclipse.hono.commandrouter.impl.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.hono.client.command.CommandRoutingUtil;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.kafka.admin.KafkaAdminClient;

/**
 * Verifies behavior of {@link InternalKafkaTopicCleanupService}.
 */
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class InternalKafkaTopicCleanupServiceTest {

    private InternalKafkaTopicCleanupService internalKafkaTopicCleanupService;
    private AdapterInstanceStatusService adapterInstanceStatusService;
    private KafkaAdminClient kafkaAdminClient;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        final Vertx vertx = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertx);
        adapterInstanceStatusService = mock(AdapterInstanceStatusService.class);
        kafkaAdminClient = mock(KafkaAdminClient.class);
        internalKafkaTopicCleanupService = new InternalKafkaTopicCleanupService(
                adapterInstanceStatusService,
                kafkaAdminClient);
        internalKafkaTopicCleanupService.init(vertx, context);
        internalKafkaTopicCleanupService.start();
    }

    /**
     * Verifies that the service deletes topics identified as obsolete.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testPerformCleanup() {

        final AtomicInteger counter = new AtomicInteger();
        final String podName = "myAdapter";
        final String aliveContainerId = "0ad9864b08bf";
        final String deadContainerId = "000000000000";

        final Set<String> toBeDeletedCmdInternalTopics = new HashSet<>();
        toBeDeletedCmdInternalTopics.add(getCmdInternalTopic(podName, deadContainerId, counter.getAndIncrement()));
        toBeDeletedCmdInternalTopics.add(getCmdInternalTopic(podName, deadContainerId, counter.getAndIncrement()));
        toBeDeletedCmdInternalTopics.add(getCmdInternalTopic(podName, deadContainerId, counter.getAndIncrement()));

        // GIVEN a number of topics
        final Set<String> allTopics = new HashSet<>(toBeDeletedCmdInternalTopics);
        allTopics.add("other");
        allTopics.add(getCmdInternalTopic(podName, aliveContainerId, counter.getAndIncrement()));
        allTopics.add(getCmdInternalTopic(podName, aliveContainerId, counter.getAndIncrement()));
        allTopics.add(getCmdInternalTopic(podName, aliveContainerId, counter.getAndIncrement()));

        // all adapter instances whose identifier contains the "deadContainerId" shall be identified as dead
        when(adapterInstanceStatusService.getDeadAdapterInstances(any()))
                .thenAnswer(invocation -> {
                    final Collection<String> adapterInstanceIdsParam = invocation.getArgument(0);
                    final Set<String> deadIds = adapterInstanceIdsParam.stream()
                            .filter(id -> id.contains(deadContainerId)).collect(Collectors.toSet());
                    return Future.succeededFuture(deadIds);
                });
        when(kafkaAdminClient.deleteTopics(any()))
                .thenAnswer(invocation -> {
                    // remove deleted from allTopics
                    final List<String> topicsToDeleteParam = invocation.getArgument(0);
                    topicsToDeleteParam.forEach(allTopics::remove);
                    return Future.succeededFuture();
                });
        when(kafkaAdminClient.listTopics()).thenReturn(Future.succeededFuture(allTopics));

        // WHEN the cleanup gets performed
        internalKafkaTopicCleanupService.performCleanup();
        verify(kafkaAdminClient, never()).deleteTopics(any());
        // THEN the next invocation ...
        internalKafkaTopicCleanupService.performCleanup();

        // ... will cause the matching topics to be deleted
        final var deletedTopicsCaptor = ArgumentCaptor.forClass(List.class);
        verify(kafkaAdminClient).deleteTopics(deletedTopicsCaptor.capture());
        assertThat(deletedTopicsCaptor.getValue()).isEqualTo(new ArrayList<>(toBeDeletedCmdInternalTopics));
    }

    private String getCmdInternalTopic(final String podName, final String containerId, final int counter) {
        final String adapterInstanceId = CommandRoutingUtil.getNewAdapterInstanceIdForK8sEnv(
                podName, containerId, counter);
        return new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId).toString();
    }
}
