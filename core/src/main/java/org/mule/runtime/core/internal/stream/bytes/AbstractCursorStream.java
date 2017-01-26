/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import org.mule.runtime.api.streaming.CursorStream;

import java.io.IOException;

abstract class AbstractCursorStream extends CursorStream {

  private boolean closed = false;
  protected long position = 0;
  private long mark = 0;

  /**
   * {@inheritDoc}
   */
  @Override
  public long getPosition() {
    return position;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void seek(long position) throws IOException {
    assertNotClosed();
    this.position = position;
  }

  /**
   * Closes this stream and invokes the closing callback received in the constructor.
   */
  @Override
  public final void close() throws IOException {
    if (!closed) {
      closed = true;
      onClosed();
    }
  }

  /**
   * {@inheritDoc}
   * Equivalent to {@code this.seek(this.getPosition() + n)}
   */
  @Override
  public final long skip(long n) throws IOException {
    seek(position + n);
    return n;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void mark(int readlimit) {
    mark = readlimit;
  }

  @Override
  public synchronized void reset() throws IOException {
    seek(mark);
  }

  /**
   * {@inheritDoc}
   * @return {@code true}
   */
  @Override
  public boolean markSupported() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isClosed() {
    return closed;
  }

  protected abstract void onClosed();

  protected void assertNotClosed() {
    if (closed) {
      throw new IllegalStateException("Stream is closed");
    }
  }

  protected int unsigned(int value) {
    return value & 0xff;
  }
}
