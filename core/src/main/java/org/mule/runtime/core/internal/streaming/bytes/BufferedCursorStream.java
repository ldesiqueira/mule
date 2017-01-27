/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import static java.nio.ByteBuffer.allocateDirect;

import org.mule.runtime.api.streaming.CursorStream;
import org.mule.runtime.api.streaming.CursorStreamProvider;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link CursorStream} which pulls its data from a {@link FileStoreInputStreamBuffer}.
 * <p>
 * To reduce contention on the {@link FileStoreInputStreamBuffer}, this class also uses a local intermediate
 * memory buffer which size must be configured
 *
 * @see FileStoreInputStreamBuffer
 * @since 1.0
 */
public final class BufferedCursorStream extends BaseCursorStream {

  private final FileStoreInputStreamBuffer streamBuffer;
  private final int localBufferSize;

  /**
   * Intermediate buffer between this cursor and the {@code traversableBuffer}. This reduces contention
   * on the {@code traversableBuffer}
   */
  private final ByteBuffer memoryBuffer;

  /**
   * Creates a new instance
   *
   * @param streamBuffer the buffer which provides data
   * @param localBufferSize   The size of the intermediate buffer
   */
  public BufferedCursorStream(FileStoreInputStreamBuffer streamBuffer, int localBufferSize, CursorStreamProvider provider) {
    super(provider);
    this.streamBuffer = streamBuffer;
    this.localBufferSize = localBufferSize;
    memoryBuffer = allocateDirect(localBufferSize);
    memoryBuffer.flip();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void seek(long position) throws IOException {
    super.seek(position);
    memoryBuffer.clear();
    memoryBuffer.flip();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read() throws IOException {
    assertNotClosed();
    if (reloadLocalBufferIfEmpty() > 0) {
      int read = unsigned((int) memoryBuffer.get());
      position++;
      return read;
    }

    return -1;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    assertNotClosed();
    return readInto(b, off, len);
  }

  private int readInto(byte[] b, int off, int len) throws IOException {
    int read = 0;

    while (true) {
      int remaining = reloadLocalBufferIfEmpty();
      if (remaining == -1) {
        return read == 0 ? -1 : read;
      }

      if (len <= remaining) {
        memoryBuffer.get(b, off, len);
        position += len;
        return read + len;
      } else {
        memoryBuffer.get(b, off, remaining);
        position += remaining;
        read += remaining;
        off += remaining;
        len -= remaining;
      }
    }
  }

  private int reloadLocalBufferIfEmpty() {
    if (!memoryBuffer.hasRemaining()) {
      memoryBuffer.clear();
      int read = streamBuffer.get(memoryBuffer, position, localBufferSize);
      if (read > 0) {
        memoryBuffer.flip();
        return read;
      } else {
        memoryBuffer.limit(0);
        return -1;
      }
    }

    return memoryBuffer.remaining();
  }

  @Override
  protected void onClosed() {
    memoryBuffer.clear();
  }
}