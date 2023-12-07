/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * A helper for parsing and creating <em>cache directives</em> compliant with
 * <a href="https://tools.ietf.org/html/rfc2616#section-14.9">RFC 2616, Section 14.9</a>.
 */
public final class CacheDirective {

    private static final Pattern PATTERN_MAX_AGE = Pattern.compile("^max-age\\s*=\\s*(\\d*)$");
    private static final String NO_CACHE = "no-cache";

    private static final CacheDirective NO_CACHE_DIRECTIVE = new CacheDirective(true, 0L);

    private final boolean noCache;
    private final long maxAge;

    private CacheDirective(final boolean noCacheFlag, final long maxAge) {
        this.noCache = noCacheFlag;
        this.maxAge = maxAge;
    }

    /**
     * Creates a new <em>max-age</em> directive.
     *
     * @param maxAge The maximum age in number of seconds.
     * @return The directive.
     * @throws IllegalArgumentException if the given value is less or equal to zero.
     */
    public static CacheDirective maxAgeDirective(final long maxAge) {
        if (maxAge <= 0) {
            throw new IllegalArgumentException("max age must be > 0");
        }
        return new CacheDirective(false, maxAge);
    }

    /**
     * Creates a new <em>max-age</em> directive.
     *
     * @param maxAge The maximum age.
     * @return The directive.
     * @throws IllegalArgumentException if the given value is less or equal to zero seconds.
     */
    public static CacheDirective maxAgeDirective(final Duration maxAge) {
        return maxAgeDirective(maxAge.toSeconds());
    }

    /**
     * Creates a new <em>no-cache</em> directive.
     *
     * @return The directive.
     */
    public static CacheDirective noCacheDirective() {
        return NO_CACHE_DIRECTIVE;
    }

    /**
     * Parses a cache directive.
     *
     * @param directive The directive to parse.
     * @return The cache directive or {@code null} if the directive cannot be parsed.
     */
    public static CacheDirective from(final String directive) {

        if (directive == null) {
            return null;
        } else {
            final var strippedDirective = directive.strip().toLowerCase();
            final var matcher = PATTERN_MAX_AGE.matcher(strippedDirective);
            if (matcher.matches()) {
                return maxAgeDirective(Long.parseLong(matcher.group(1)));
            } else if (NO_CACHE.equals(strippedDirective)) {
                    return noCacheDirective();
            } else {
                return null;
            }
        }
    }

    /**
     * Checks if this directive allows caching.
     *
     * @return {@code false} if caching is not allowed.
     */
    public boolean isCachingAllowed() {
        return !noCache;
    }

    /**
     * Gets the maximum period of time for which a resource
     * may be cached.
     *
     * @return The maximum age in seconds.
     */
    public long getMaxAge() {
        return maxAge;
    }

    /**
     * Creates a string representation of this directive.
     * <p>
     * The format follows the <em>cache-directive</em> defined
     * in <a href="https://tools.ietf.org/html/rfc2616#section-14.9">
     * RFC 2616, Section 14.9</a>.
     *
     * @return The cache directive which can be included, e.g. in a
     *         <em>Cache-Control</em> header.
     */
    @Override
    public String toString() {
        if (noCache) {
            return NO_CACHE;
        } else {
            return String.format("max-age = %d", maxAge);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (maxAge ^ (maxAge >>> 32));
        result = prime * result + (noCache ? 1231 : 1237);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CacheDirective other = (CacheDirective) obj;
        if (maxAge != other.maxAge) {
            return false;
        }
        if (noCache != other.noCache) {
            return false;
        }
        return true;
    }
}
