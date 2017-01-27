/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import org.mule.runtime.api.streaming.CursorStream;

import java.io.InputStream;

public class BufferedCursorStreamProvider extends AbstractCursorStreamProvider {

  private final FileStoreInputStreamBuffer buffer;

  public BufferedCursorStreamProvider(InputStream wrappedStream, int bufferSize, OffHeapMode offHeapMode) {
    super(wrappedStream, bufferSize);
    buffer = new FileStoreInputStreamBuffer(wrappedStream, bufferSize, offHeapMode);
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
