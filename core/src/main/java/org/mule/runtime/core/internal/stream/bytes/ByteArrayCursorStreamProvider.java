/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.InputStream;

public class ByteArrayCursorStreamProvider extends CursorStreamProvider {

  private byte[] array;

  public ByteArrayCursorStreamProvider(InputStream wrappedStream, int bufferSize) {
    super(wrappedStream, bufferSize);
    array = readUntil(wrappedStream, bufferSize);

    if (array.length < bufferSize) {
      closeQuietly(wrappedStream);
    } else {
      throw new StreamMaximumSizeExceededException(wrappedStream, bufferSize);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected CursorStream doOpenCursor() {
    return new ByteArrayCursorStream(array);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void releaseResources() {
    array = null;
  }
}
