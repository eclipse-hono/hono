/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

/**
 * A pair of two values of arbitrary type.
 *
 * @param <A> The type of the first value.
 * @param <B> The type of the second value.
 */
public final class Pair<A, B> {

    private final A one;
    private final B two;

    private String stringRep;

    private Pair(final A one, final B two) {

        if (one == null && two == null) {
            throw new IllegalArgumentException("at least one argument must be non-null");
        }
        this.one = one;
        this.two = two;
    }

    /**
     * Creates a new pair for two values of arbitrary type.
     *
     * @param one First value.
     * @param two Second value.
     * @param <A> The type of the first value.
     * @param <B> The type of the second value.
     * @return The pair.
     * @throws IllegalArgumentException if all values are {@code null}.
     */
    public static <A, B> Pair<A, B> of(final A one, final B two) {
        return new Pair<>(one, two);
    }

    /**
     * Gets this pair's first value.
     *
     * @return The value or {@code null} if not set.
     */
    public A one() {
        return one;
    }

    /**
     * Gets this pair's second value.
     *
     * @return The value or {@code null} if not set.
     */
    public B two() {
        return two;
    }

    @Override
    public String toString() {
        if (stringRep == null) {
            stringRep = String.format("Pair[one: %s, two: %s]", one, two);
        }
        return stringRep;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (one == null ? 0 : one.hashCode());
        result = prime * result + (two == null ? 0 : two.hashCode());
        return result;
    }

    @SuppressWarnings("rawtypes")
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
        final Pair other = (Pair) obj;
        if (one == null) {
            if (other.one != null) {
                return false;
            }
        } else if (!one.equals(other.one)) {
            return false;
        }
        if (two == null) {
            if (other.two != null) {
                return false;
            }
        } else if (!two.equals(other.two)) {
            return false;
        }
        return true;
    }
}
