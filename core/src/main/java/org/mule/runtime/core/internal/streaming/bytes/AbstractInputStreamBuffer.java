/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.channels.Channels.newChannel;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.api.util.Preconditions.checkState;
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
 * Base class for implementations of {@link InputStreamBuffer}.
 * <p>
 * Contains the base algorithm and template methods so that implementations can be created easily
 *
 * @since 4.0
 */
public abstract class AbstractInputStreamBuffer implements InputStreamBuffer {

  private static Logger LOGGER = LoggerFactory.getLogger(AbstractInputStreamBuffer.class);

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
   * @param bufferSize the buffer size
   */
  public AbstractInputStreamBuffer(InputStream stream, int bufferSize) {
    this.bufferSize = bufferSize;
    this.stream = stream;
    this.buffer = allocateDirect(bufferSize);

    bufferRange = new Range(0, 0);
    streamChannel = newChannel(stream);
  }

  @Override
  public final void close() {
    closed = true;

    doClose();

    buffer.clear();
    safely(streamChannel::close);
    safely(stream::close);
  }

  protected abstract void doClose();

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if the buffer is closed
   */
  @Override
  public final int get(ByteBuffer destination, long position, int length) {
    checkState(!closed, "Buffer is closed");

    return doGet(destination, position, length, true);
  }

  private int doGet(ByteBuffer dest, long position, int length, boolean consumeStreamIfNecessary) {
    Range requiredRange = new Range(position, position + length);

    acquireBufferLock();

    try {

      if (streamFullyConsumed && requiredRange.startsAfter(bufferRange)) {
        return -1;
      }

      if (bufferRange.contains(requiredRange)) {
        return copy(dest, requiredRange);
      }

      if (bufferRange.isAhead(requiredRange)) {
        return getBackwardsData(dest, requiredRange, length);
      }

      if (consumeStreamIfNecessary) {
        while (!streamFullyConsumed && bufferRange.isBehind(requiredRange)) {
          try {
            if (reloadBuffer() > 0) {
              int overlap = handlePartialOverlap(dest, requiredRange);
              if (overlap > 0) {
                return overlap;
              }
            }
          } catch (IOException e) {
            throw new MuleRuntimeException(createStaticMessage("Could not read stream"), e);
          }
        }

        return doGet(dest, position, length, false);
      } else {
        return handlePartialOverlap(dest, requiredRange);
      }
    } finally {
      try {
        releaseBufferLock();
      } catch (IllegalMonitorStateException e) {
        // lock was released early to improve performance and somebody else took it. This is fine
      }
    }
  }

  protected void releaseBufferLock() {
    bufferLock.unlock();
  }

  protected void acquireBufferLock() {
    bufferLock.lock();
  }

  private int reloadBuffer() throws IOException {
    if (streamFullyConsumed) {
      return -1;
    }

    int result = consumeForwardData(buffer);

    if (result >= 0) {
      bufferRange = bufferRange.advance(result);
    } else {
      streamFullyConsumed();
    }

    return result;
  }
  protected int loadFromStream(ByteBuffer buffer) throws IOException {
    int result;
    try {
      result = streamChannel.read(buffer);
    } catch (ClosedChannelException e) {
      result = -1;
    }
    return result;
  }

  protected abstract int getBackwardsData(ByteBuffer dest, Range requiredRange, int length);

  protected abstract int consumeForwardData(ByteBuffer buffer) throws IOException;

  protected void streamFullyConsumed() {
    streamFullyConsumed = true;
  }

  private int handlePartialOverlap(ByteBuffer dest, Range requiredRange) {
    return bufferRange.overlap(requiredRange)
        .filter(r -> !r.isEmpty())
        .map(overlap -> copy(dest, overlap))
        .orElse(-1);
  }

  protected int copy(ByteBuffer dest, Range requiredRange) {
    ByteBuffer src = buffer.duplicate();
    final int newPosition = toIntExact(requiredRange.start - bufferRange.start);
    src.position(newPosition);
    src.limit(newPosition + min(dest.remaining(), min(requiredRange.length(), src.remaining())));
    if (src.hasRemaining()) {
      int remaining = src.remaining();
      dest.put(src);
      return remaining;
    } else {
      return -1;
    }
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

    protected Range(long start, long end) {
      checkArgument(end >= start, "end has to be greater than start");
      this.start = start;
      this.end = end;
    }

    protected Range advance(int offset) {
      return new Range(end, end + offset);
    }

    protected boolean contains(Range range) {
      return start <= range.start && end >= range.end;
    }

    protected boolean isAhead(Range range) {
      return start > range.start && end >= range.end;
    }

    protected boolean isBehind(Range range) {
      return end < range.end;
    }

    protected boolean startsAfter(Range range) {
      return start > range.end;
    }

    protected Optional<Range> overlap(Range range) {
      final long start = max(this.start, range.start);
      final long end = min(this.end, range.end);

      if (start <= end) {
        Range overlap = new Range(start, end);
        return contains(overlap) ? of(overlap) : empty();
      }

      return empty();
    }

    protected int length() {
      return toIntExact(end - start);
    }

    protected boolean isEmpty() {
      return start == end;
    }
  }
}
