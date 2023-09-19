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

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CgroupV1KubernetesContainerUtil}.
 */
public class CgroupV1KubernetesContainerUtilTest {

    /**
     * Tests extracting the container id from a container created by the containerd CRI plugin.
     */
    @Test
    public void testGetContainerIdForContainerdCriContainer() {
        final String containerId = "118cc69780e057ab94ed0526d2f05ad61cf208f1175bab24bba25c1d826aac82";
        final String cgroupLine = "6:cpuset:/kubepods.slice/kubepods-burstable.slice/kubepods-burstable-pod65b4c0a2_51e8_4d62_a015_1c096724b473.slice/"
                + "cri-containerd-118cc69780e057ab94ed0526d2f05ad61cf208f1175bab24bba25c1d826aac82.scope";
        final String extractedContainerId = CgroupV1KubernetesContainerUtil.getContainerId(cgroupLine);
        assertThat(extractedContainerId).isEqualTo(containerId);
    }

    /**
     * Tests extracting the container id from a docker container.
     */
    @Test
    public void testGetContainerIdForDockerContainer() {
        final String containerId = "3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b";
        final String cgroupLine = "8:cpuset:/kubepods.slice/kubepods-pod9c26dfb6_b9c9_11e7_bfb9_02c6c1fc4861.slice/"
                + "docker-3dd988081e7149463c043b5d9c57d7309e079c5e9290f91feba1cc45a04d6a5b.scope";
        final String extractedContainerId = CgroupV1KubernetesContainerUtil.getContainerId(cgroupLine);
        assertThat(extractedContainerId).isEqualTo(containerId);
    }
}
