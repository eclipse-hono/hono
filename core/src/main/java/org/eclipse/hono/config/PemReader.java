/**
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial creation
 */

package org.eclipse.hono.config;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.nio.file.Files.newBufferedReader;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A reader for PEM files.
 */
public final class PemReader {

    private static final Pattern BEGIN_PATTERN = Pattern.compile("-+BEGIN (.*?)-+");
    private static final Pattern END_PATTERN = Pattern.compile("-+END (.*?)-+");

    private PemReader() {
    }

    /**
     * An entry in a PEM file.
     */
    public static class Entry {

        private String type;
        private byte[] payload;

        private Entry(final String type, final byte[] payload) {
            this.type = type;
            this.payload = payload;
        }

        /**
         * Gets the PEM entry payload.
         * 
         * @return the payload
         */
        public byte[] getPayload() {
            return payload;
        }

        /**
         * Gets the PEM entry type.
         * 
         * @return the type
         */
        public String getType() {
            return type;
        }

    }

    /**
     * Reads a PEM file and return its entries.
     * 
     * @param path The file to read.
     * @return The PEM entries.
     * 
     * @throws IOException I case of any error.
     */
    public static List<Entry> readAll(final Path path) throws IOException {
        requireNonNull(path);

        try (Reader reader = newBufferedReader(path, StandardCharsets.US_ASCII)) {
            return readAll(reader);
        }
    }

    /**
     * Reads a PEM file using vertx in blocking mode and return its entries.
     * 
     * @param vertx The vertx instance to use.
     * @param path The file to read.
     * @return The PEM entries.
     * 
     * @throws IOException I case of any error.
     */
    public static List<Entry> readAllBlocking(final Vertx vertx, final Path path) throws IOException {
        requireNonNull(vertx);
        requireNonNull(path);

        return readAllFromBuffer(
                vertx
                        .fileSystem()
                        .readFileBlocking(path.toString()));
    }

    private static List<Entry> readAllFromBuffer(final Buffer buffer) throws IOException {

        // read data as string

        final String string = buffer.toString(StandardCharsets.US_ASCII);

        // parse PEM

        return readAll(new StringReader(string));
    }

    /**
     * Asynchronously reads a PEM file using vertx and report its entries.
     * @param vertx The vertx instance to use.
     * @param path The file to read.
     * @param handler The handler to receive the result
     */
    public static void readAll(final Vertx vertx, final Path path, final Handler<AsyncResult<List<Entry>>> handler) {

        requireNonNull(vertx);
        requireNonNull(path);
        requireNonNull(handler);

        vertx.fileSystem().readFile(path.toString(), reader -> {

            if (reader.failed()) {

                // reading failed ... pass on failure
                handler.handle(failedFuture(reader.cause()));

            } else {

                try {

                    // pass on success

                    handler.handle(succeededFuture(readAllFromBuffer(reader.result())));

                } catch (final Exception e) {

                    // parsing the payload as PEM failed

                    handler.handle(failedFuture(e));
                }

            }
        });
    }

    /**
     * Reads a PEM file and return its entries.
     * 
     * @param reader The source to read from.
     * @return The list of entries.
     * @throws IOException In case of any error.
     */
    public static List<Entry> readAll(final Reader reader) throws IOException {

        final LineNumberReader lnr = new LineNumberReader(reader);
        final List<Entry> result = new LinkedList<>();

        String line;
        String type = null;
        StringBuffer buffer = null;

        while ((line = lnr.readLine()) != null) {

            if (line.isEmpty()) {
                // ignore empty lines
                continue;
            }

            final Matcher begin = BEGIN_PATTERN.matcher(line);
            if (begin.matches()) {

                if (buffer != null) {
                    // already inside block
                    throw new IOException("PEM: Duplicate BEGIN statement");
                }

                buffer = new StringBuffer();
                type = begin.group(1);
                continue;
            }

            final Matcher end = END_PATTERN.matcher(line);
            if (end.matches()) {

                if (buffer == null) {
                    throw new IOException("PEM: Encountered END without preceeding BEGIN statement");
                }

                final String endType = end.group(1);
                if (!type.equals(endType)) {
                    throw new IOException(String.format(
                            "PEM: END statement mismatches BEGIN statement type (BEGIN: '%s' - END: '%s')", type,
                            endType));
                }

                result.add(new Entry(type, Base64.getMimeDecoder().decode(buffer.toString())));

                // reset state

                buffer = null;
                type = null;
                continue;
            }

            if (buffer != null) {
                buffer.append(line).append('\n');
            } else {
                throw new IOException("PEM: Payload data outside of BEGIN/END block");
            }
        }

        if (buffer != null) {
            throw new IOException("PEM: Missing closing END block after BEGIN when reaching end of file");
        }

        return result;
    }
}
