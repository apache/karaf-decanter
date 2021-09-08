/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.decanter.collector.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class BoundedInputStream extends InputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketCollector.class);

    private final InputStream in;

    private final long max;

    private long pos;

    private long mark = -1;

    private boolean propagateClose = true;

    public BoundedInputStream(final InputStream in, final long size) {
        this.max = size;
        this.in = in;
    }

    public BoundedInputStream(final InputStream in) {
        this(in, -1);
    }

    @Override
    public int read() throws IOException {
        if (max >= 0 && pos >= max) {
            LOGGER.warn("Reach socket read input stream limit");
            return -1;
        }
        final int result = in.read();
        pos++;
        return result;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (max >= 0 && pos >= max) {
            LOGGER.warn("Reach socket read input stream limit");
            return -1;
        }
        final long maxRead = max >= 0 ? Math.min(len, max - pos) : len;
        final int bytesRead = in.read(b, off, (int) maxRead);
        if (bytesRead == -1) {
            return -1;
        }
        pos += bytesRead;
        return bytesRead;
    }

    @Override
    public long skip(final long n) throws IOException {
        final long toSkip = max >= 0 ? Math.min(n, max - pos) : n;
        final long skippedBytes = in.skip(toSkip);
        pos += skippedBytes;
        return skippedBytes;
    }

    @Override
    public int available() throws IOException {
        if (max >= 0 && pos >= max) {
            return 0;
        }
        return in.available();
    }

    @Override
    public String toString() {
        return in.toString();
    }

    @Override
    public void close() throws IOException {
        if (propagateClose) {
            in.close();
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        in.reset();
        pos = mark;
    }

    @Override
    public synchronized void mark(final int readLimit) {
        in.mark(readLimit);
        mark = pos;
    }

    @Override
    public boolean markSupported() {
        return in.markSupported();
    }

    public boolean isPropagateClose() {
        return propagateClose;
    }

    public void setPropagateClose(boolean propagateClose) {
        this.propagateClose = propagateClose;
    }

}
