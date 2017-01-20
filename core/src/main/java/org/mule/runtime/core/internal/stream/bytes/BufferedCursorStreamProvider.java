/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import java.io.InputStream;
import java.util.function.Supplier;

public class BufferedCursorStreamProvider extends CursorStreamProvider {

  private final FileStoreBuffer buffer;
  private final Supplier<CursorStream> streamFactory;
  
  public BufferedCursorStreamProvider(InputStream wrappedStream, int bufferSize) {
    super(wrappedStream, bufferSize);
    byte[] prefetched = readUntil(wrappedStream, bufferSize);

    if (prefetched.length < bufferSize) {
      buffer = null;
      streamFactory = () -> new ByteArrayCursorStream(prefetched);
    } else {
      buffer = new FileStoreBuffer(wrappedStream, prefetched, bufferSize);
      streamFactory = () -> new BufferedCursorStream(buffer, bufferSize);
    }
  }

  @Override
  protected CursorStream doOpenCursor() {
    return streamFactory.get();
  }

  @Override
  protected void releaseResources() {
    if (buffer != null) {
      buffer.close();
    }
  }
}
