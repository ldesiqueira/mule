/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.channels.Channels.newChannel;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.core.internal.stream.bytes.OffHeapMode.FILE_STORE;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.util.func.CheckedRunnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A buffer which provides concurrent random access to the entirety
 * of a dataset.
 * <p>
 * It works with the concept of a zero-base position. Each position
 * represents one byte in the stream. Although this buffer tracks the
 * position of each byte, it doesn't have a position itself. That means
 * that pulling data from this buffer does not make any current position
 * to be moved.
 * <p>
 * This buffer is capable of handling datasets larger than this buffer's size. It works by keeping
 * an in-memory buffer which holds as many information as possible. When information which is ahead
 * of the buffer's current position is requested then the following happens:
 * <p>
 * <ul>
 * <li>The contents of the buffer are written into a temporal file</li>
 * <li>The buffer is cleared</li>
 * <li>The stream is consumed until the buffer is full again or the stream reaches its end</li>
 * <li>If the required data is still ahead of the buffer, then the process is repeated until the data is reached or the stream
 * fully consumed</li>
 * </ul>
 * <p>
 * Another possible scenario, is one in which the data requested is behind the buffer's current position, in which case
 * the data is obtained by reading the temporal file.
 * <p>
 * In either case, what's really important to understand is that the buffer is <b>ALWAYS</b> moving forward. The buffer
 * will never go back and reload data from the temporal file. It only gets data from the stream.
 *
 * @since 4.0
 */
public final class StreamBuffer {

  private static final Logger LOGGER = LoggerFactory.getLogger(StreamBuffer.class);

  private final OffHeapMode offHeapMode;
  private final OffHeapMemory offHeapMemory;
  private final int bufferSize;
  private final InputStream stream;
  private final ByteBuffer buffer;

  private final ReadableByteChannel streamChannel;
  private final Lock bufferLock = new ReentrantLock();

  private boolean closed = false;
  private Range bufferRange;
  private boolean streamFullyConsumed = false;

  /**
   * Creates a new instance
   *
   * @param stream     The stream being buffered. This is the original data source
   * @param available  a piece of information which has already been pulled from the stream and is set as the buffer's original state
   * @param bufferSize the buffer size
   */
  public StreamBuffer(InputStream stream, int bufferSize, OffHeapMode offHeapMode) {
    this.bufferSize = bufferSize;
    this.stream = stream;
    this.buffer = allocateDirect(bufferSize);
    this.offHeapMode = offHeapMode;

    if (offHeapMode == FILE_STORE) {
      offHeapMemory = new FileStoreOffHeapMemory();
    } else {
      offHeapMemory = new NullOffHeapMemory();
    }

    bufferRange = new Range(0, 0);
    streamChannel = newChannel(stream);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if the buffer is closed
   */
  public int get(ByteBuffer destination, long position, int length) {
    if (closed) {
      throw new IllegalStateException("Buffer is closed");
    }

    return get(destination, position, length, true);
  }

  private int get(ByteBuffer dest, long position, int length, boolean consumeStreamIfNecessary) {
    Range requiredRange = new Range(position, position + length);

    bufferLock.lock();

    try {

      if (streamFullyConsumed && requiredRange.startsAfter(bufferRange)) {
        return -1;
      }

      if (bufferRange.contains(requiredRange)) {
        return copy(dest, requiredRange, length);
      }

      if (bufferRange.isAhead(requiredRange)) {
        bufferLock.unlock(); // we don't need to hold this lock anymore
        return offHeapMemory.get(dest, requiredRange, length);
      }

      if (consumeStreamIfNecessary) {
        while (!streamFullyConsumed && bufferRange.isBehind(requiredRange)) {
          try {
            if (reloadBuffer() > 0) {
              int overlap = handlePartialOverlap(dest, length, requiredRange);
              if (overlap > 0) {
                return overlap;
              }
            }
          } catch (IOException e) {
            throw new MuleRuntimeException(createStaticMessage("Could not read stream"), e);
          }
        }

        return get(dest, position, length, false);
      } else {
        return handlePartialOverlap(dest, length, requiredRange);
      }
    } finally {
      try {
        bufferLock.unlock();
      } catch (IllegalMonitorStateException e) {
        // lock was released early to improve performance and somebody else took it. This is fine
      }
    }
  }

  private int handlePartialOverlap(ByteBuffer dest, int length, Range requiredRange) {
    return bufferRange.overlap(requiredRange)
        .filter(r -> !r.isEmpty())
        .map(overlap -> copy(dest, overlap, length))
        .orElse(-1);
  }

  private int copy(ByteBuffer dest, Range requiredRange, int length) {
    ByteBuffer src = buffer.duplicate();
    final int newPosition = toIntExact(requiredRange.start - bufferRange.start);
    src.position(newPosition);
    src.limit(newPosition + min(dest.remaining(), min(length, toIntExact(bufferRange.end - newPosition))));
    if (src.hasRemaining()) {
      int remaining = src.remaining();
      dest.put(src);
      return remaining;
    } else {
      return -1;
    }
  }

  /**
   * {@inheritDoc}
   */
  protected void close() {
    closed = true;
    buffer.clear();
    safely(streamChannel::close);
    safely(stream::close);
    safely(offHeapMemory::release);
  }


  private int reloadBuffer() throws IOException {
    if (streamFullyConsumed) {
      return -1;
    }

    buffer.clear();

    int result = offHeapMemory.get(buffer);

    if (result > 0) {
      buffer.flip();
      return result;
    }

    try {
      result = streamChannel.read(buffer);
    } catch (ClosedChannelException e) {
      result = -1;
    }

    if (result >= 0) {
      buffer.flip();
      if (offHeapMemory.put(buffer)) {
        buffer.flip();
      }
      bufferRange = bufferRange.advance(result);
    } else {
      streamFullyConsumed = true;
    }

    return result;
  }

  private void safely(CheckedRunnable task) {
    try {
      task.run();
    } catch (Exception e) {
      LOGGER.debug("Found exception closing buffer", e);
    }
  }


  class Range {

    final long start;
    final long end;

    private Range(long start, long end) {
      checkArgument(end >= start, "end has to be greater than start");
      this.start = start;
      this.end = end;
    }

    private Range advance(int offset) {
      return new Range(end, end + offset);
    }

    private boolean contains(Range range) {
      return start <= range.start && end >= range.end;
    }

    private boolean isAhead(Range range) {
      return start > range.start && end >= range.end;
    }

    private boolean isBehind(Range range) {
      return end < range.end;
    }

    private boolean startsAfter(Range range) {
      return start > range.end;
    }

    private Optional<Range> overlap(Range range) {
      final long start = max(this.start, range.start);
      final long end = min(this.end, range.end);

      if (start <= end) {
        Range overlap = new Range(start, end);
        return contains(overlap) ? of(overlap) : empty();
      }

      return empty();
    }

    private boolean isEmpty() {
      return start == end;
    }
  }
}
