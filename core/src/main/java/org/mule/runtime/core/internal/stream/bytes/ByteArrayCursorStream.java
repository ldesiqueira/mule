/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import static java.lang.Math.toIntExact;
import static java.lang.System.arraycopy;

import java.io.IOException;

public class ByteArrayCursorStream extends AbstractCursorStream {

  private byte[] bytes;

  public ByteArrayCursorStream(byte[] bytes) {
    this.bytes = bytes;
  }

  @Override
  protected void onClosed() {
    bytes = null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read() throws IOException {
    assertNotClosed();

    if (position >= bytes.length) {
      return -1;
    }

    return unsigned(bytes[toIntExact(position++)]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    assertNotClosed();

    final long available = bytes.length - position;

    if (available <= 0) {
      return -1;
    } else if (available < len) {
      len = toIntExact(available);
    }

    arraycopy(bytes, toIntExact(position), b, off, len);
    position += len;
    return len;
  }
}
