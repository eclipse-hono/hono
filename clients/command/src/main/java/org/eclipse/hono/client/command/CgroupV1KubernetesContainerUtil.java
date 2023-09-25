/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.eclipse.hono.client.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locate the current docker container.
 * <p>
 * This class has been copied from <em>org.apache.logging.log4j.kubernetes.ContainerUtil</em> from the
 * <em>log4j-kubernetes</em> module of the <a href="https://github.com/apache/logging-log4j2">Apache Log4j 2</a>
 * project (commit a50abb9). Adaptations have been done concerning the used logger and the Hono code style.
 * Also, a fix regarding cri-containerd container ids has been applied.
 */
public class CgroupV1KubernetesContainerUtil {
    private static final int MAXLENGTH = 65;

    private static final Logger LOGGER = LoggerFactory.getLogger(CgroupV1KubernetesContainerUtil.class);

    private CgroupV1KubernetesContainerUtil() {
    }

    /**
     * Returns the container id when running in a Docker container.
     *
     * This inspects /proc/self/cgroup looking for a Kubernetes Control Group. Once it finds one it attempts
     * to isolate just the docker container id. There doesn't appear to be a standard way to do this, but
     * it seems to be the only way to determine what the current container is in a multi-container pod. It would have
     * been much nicer if Kubernetes would just put the container id in a standard environment variable.
     * <p>
     * See <a href="http://stackoverflow.com/a/25729598/12916">Stackoverflow</a> for a discussion on retrieving the
     * containerId.
     * See <a href="https://github.com/jenkinsci/docker-workflow-plugin/blob/master/src/main/java/org/jenkinsci/plugins/docker/workflow/client/ControlGroup.java">ControlGroup</a>
     * for the original version of this. Not much is actually left but it provided good inspiration.
     * @return The container id.
     */
    public static String getContainerId() {
        try {
            final File file = new File("/proc/self/cgroup");
            if (file.exists()) {
                try (Stream<String> lines = Files.lines(file.toPath())) {
                    final String id = lines.map(CgroupV1KubernetesContainerUtil::getContainerId).filter(Objects::nonNull)
                            .findFirst().orElse(null);
                    LOGGER.debug("Found container id via cgroup v1: {}", id);
                    return id;
                }
            } else {
                LOGGER.warn("Unable to access '/proc/self/cgroup' to get container information");
            }
        } catch (final IOException ioe) {
            LOGGER.warn("Error obtaining container id via cgroup v1: {}", ioe.getMessage());
        }
        return null;
    }

    static String getContainerId(final String cgroupLine) {
        String line = cgroupLine;
        // Every control group in Kubernetes will use
        if (line.contains("/kubepods")) {
            // Strip off everything up to the last slash.
            int i = line.lastIndexOf('/');
            if (i < 0) {
                return null;
            }
            // If the remainder has a period then take everything up to it.
            line = line.substring(i + 1);
            i = line.lastIndexOf('.');
            if (i > 0) {
                line = line.substring(0, i);
            }
            // Everything ending with a '/' has already been stripped but the remainder might start with "docker-", "cri-containerd-" or "cri-o-"
            i = line.lastIndexOf("-");
            if (i > -1) {
                line = line.substring(i + 1);
            }
            return line.length() <= MAXLENGTH ? line : line.substring(0, MAXLENGTH);
        }

        return null;
    }
}
