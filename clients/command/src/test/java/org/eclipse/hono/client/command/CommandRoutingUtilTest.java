/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.util.Pair;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying behavior of {@link CommandRoutingUtil}.
 *
 */
public class CommandRoutingUtilTest {

    @Test
    void testGetK8sPodNameAndContainerIdFromAdapterInstanceId() {

        final String podName = "myPodName";
        final String containerId = "012345678901";
        final String newAdapterInstanceId = CommandRoutingUtil.getNewAdapterInstanceIdForK8sEnv(podName, containerId, 1);
        final Pair<String, String> podNameAndContainerId = CommandRoutingUtil.getK8sPodNameAndContainerIdFromAdapterInstanceId(
                newAdapterInstanceId);
        assertThat(podNameAndContainerId).isNotNull();
        assertThat(podNameAndContainerId.one()).isEqualTo(podName);
        assertThat(podNameAndContainerId.two()).isEqualTo(containerId);
    }

}
