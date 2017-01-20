/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import java.io.IOException;

abstract class AbstractCursorStream extends CursorStream {

  private boolean closed = false;
  protected long position = 0;

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
  public void close() throws IOException {
    if (!closed) {
      closed = true;
      onClosed();
    }
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
