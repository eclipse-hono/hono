/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.tracing;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Common URI path resolution.
 * <p>
 * This class has been copied from the <a href="https://github.com/quarkusio/quarkus">Quarkus</a> project.
 * It has been slightly adapted to Hono's code style guide and the relativize() method has been removed.
 */
public class UriNormalizationUtil {
    private UriNormalizationUtil() {
    }

    /**
     * Create a URI path from a string. The specified path can not contain
     * relative {@literal ..} segments or {@literal %} characters.
     * <p>
     * Examples:
     * <ul>
     * <li>{@code toUri("/", true)} will return a URI with path {@literal /}</li>
     * <li>{@code toUri("/", false)} will return a URI with an empty path {@literal /}</li>
     * <li>{@code toUri("./", true)} will return a URI with path {@literal /}</li>
     * <li>{@code toUri("./", false)} will return a URI with an empty path {@literal /}</li>
     * <li>{@code toUri("foo/", true)} will return a URI with path {@literal foo/}</li>
     * <li>{@code toUri("foo/", false)} will return a URI with an empty path {@literal foo}</li>
     * </ul>
     *
     *
     * @param path String to convert into a URI
     * @param trailingSlash true if resulting URI must end with a '/'
     * @return URI path.
     * @throws IllegalArgumentException if the path contains invalid characters or path segments.
     */
    public static URI toURI(final String path, final boolean trailingSlash) {
        try {
            // replace inbound // with /
            String tmpPath = path.replaceAll("//", "/");
            // remove trailing slash if result shouldn't have one
            if (!trailingSlash && tmpPath.endsWith("/")) {
                tmpPath = tmpPath.substring(0, tmpPath.length() - 1);
            }

            if (tmpPath.contains("..") || tmpPath.contains("%")) {
                throw new IllegalArgumentException("Specified path can not contain '..' or '%'. Path was " + tmpPath);
            }
            URI uri = new URI(tmpPath).normalize();
            if (uri.getPath().equals("")) {
                return trailingSlash ? new URI("/") : new URI("");
            } else if (trailingSlash && !tmpPath.endsWith("/")) {
                uri = new URI(uri.getPath() + "/");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Specified path is an invalid URI. Path was " + path, e);
        }
    }

    /**
     * Resolve a string path against a URI base. The specified path can not contain
     * relative {@literal ..} segments or {@literal %} characters.
     *
     * Relative paths will be resolved against the specified base URI.
     * Absolute paths will be normalized and returned.
     * <p>
     * Examples:
     * <ul>
     * <li>{@code normalizeWithBase(new URI("/"), "example", true)}
     * will return a URI with path {@literal /example/}</li>
     * <li>{@code normalizeWithBase(new URI("/"), "example", false)}
     * will return a URI with an empty path {@literal /example}</li>
     * <li>{@code normalizeWithBase(new URI("/"), "/example", true)}
     * will return a URI with path {@literal /example/}</li>
     * <li>{@code normalizeWithBase(new URI("/"), "/example", false)}
     * will return a URI with an empty {@literal /example}</li>
     *
     * <li>{@code normalizeWithBase(new URI("/prefix/"), "example", true)}
     * will return a URI with path {@literal /prefix/example/}</li>
     * <li>{@code normalizeWithBase(new URI("/prefix/"), "example", false)}
     * will return a URI with an empty path {@literal /prefix/example}</li>
     * <li>{@code normalizeWithBase(new URI("/prefix/"), "/example", true)}
     * will return a URI with path {@literal /example/}</li>
     * <li>{@code normalizeWithBase(new URI("/prefix/"), "/example", false)}
     * will return a URI with an empty path {@literal /example}</li>
     *
     * <li>{@code normalizeWithBase(new URI("foo/"), "example", true)}
     * will return a URI with path {@literal foo/example/}</li>
     * <li>{@code normalizeWithBase(new URI("foo/"), "example", false)}
     * will return a URI with an empty path {@literal foo/example}</li>
     * <li>{@code normalizeWithBase(new URI("foo/"), "/example", true)}
     * will return a URI with path {@literal /example/}</li>
     * <li>{@code normalizeWithBase(new URI("foo/"), "/example", false)}
     * will return a URI with an empty path {@literal /example}</li>
     * </ul>
     *
     * @param base URI to resolve relative paths. Use {@link #toURI(String, boolean)} to construct this parameter.
     * @param segment Relative or absolute path
     * @param trailingSlash true if resulting URI must end with a '/'
     * @return the resolved URI.
     * @throws IllegalArgumentException if the path contains invalid characters or path segments.
     */
    public static URI normalizeWithBase(final URI base, final String segment, final boolean trailingSlash) {
        if (segment == null || segment.trim().isEmpty()) {
            if ("/".equals(base.getPath())) {
                return base;
            }
            // otherwise, make sure trailingSlash is honored
            return toURI(base.getPath(), trailingSlash);
        }
        final URI segmentUri = toURI(segment, trailingSlash);
        final URI resolvedUri = base.resolve(segmentUri);
        return resolvedUri;
    }
}
