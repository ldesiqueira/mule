/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import org.mule.runtime.api.streaming.CursorStream;
import org.mule.runtime.api.streaming.CursorStreamProvider;

import java.io.InputStream;

public class BufferedCursorStreamProvider extends CursorStreamProvider {

  private final StreamBuffer buffer;

  public BufferedCursorStreamProvider(InputStream wrappedStream, int bufferSize, OffHeapMode offHeapMode) {
    super(wrappedStream, bufferSize);
    buffer = new StreamBuffer(wrappedStream, bufferSize, offHeapMode);
  }

  @Override
  protected CursorStream doOpenCursor() {
    return new BufferedCursorStream(buffer, bufferSize);
  }

  @Override
  protected void releaseResources() {
    if (buffer != null) {
      buffer.close();
    }
  }
}
