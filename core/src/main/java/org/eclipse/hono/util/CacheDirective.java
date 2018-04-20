/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A helper for parsing and creating <em>cache directives</em> compliant with
 * <a href="https://tools.ietf.org/html/rfc2616#section-14.9">RFC 2616, Section 14.9</a>.
 */
public final class CacheDirective {

    private static final Pattern PATTERN_MAX_AGE = Pattern.compile("^\\s*max-age\\s*=\\s*(\\d*)\\s*$");
    private static final Pattern PATTERN_NO_CACHE = Pattern.compile("^\\s*no-cache\\s*$");

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
     */
    public static CacheDirective maxAgeDirective(final long maxAge) {
        if (maxAge <= 0) {
            throw new IllegalArgumentException("max age must be > 0");
        }
        return new CacheDirective(false, maxAge);
    }

    /**
     * Creates a new <em>no-cache</em> directive.
     * 
     * @return The directive.
     */
    public static CacheDirective noCacheDirective() {
        return new CacheDirective(true, 0);
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
            Matcher matcher = PATTERN_MAX_AGE.matcher(directive);
            if (matcher.matches()) {
                return maxAgeDirective(Long.parseLong(matcher.group(1)));
            } else {
                matcher = PATTERN_NO_CACHE.matcher(directive);
                if (matcher.matches()) {
                    return noCacheDirective();
                } else {
                    return null;
                }
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
            return "no-cache";
        } else {
            return String.format("max-age = %d", maxAge);
        }
    }
}
