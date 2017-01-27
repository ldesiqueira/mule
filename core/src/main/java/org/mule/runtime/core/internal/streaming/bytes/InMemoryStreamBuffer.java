/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class InMemoryStreamBuffer extends AbstractInputStreamBuffer {

  public InMemoryStreamBuffer(InputStream stream, int bufferSize) {
    super(stream, bufferSize);
  }

  @Override
  protected void doClose() {

  }

  @Override
  protected int getBackwardsData(ByteBuffer dest, Range requiredRange, int length) {
    return copy(dest, requiredRange);
  }

  @Override
  protected int consumeForwardData(ByteBuffer buffer) throws IOException {
    if (!buffer.hasRemaining()) {
      //buffer = expand()
    }

    int result = loadFromStream(buffer);

    if (result >= 0) {
      buffer.flip();

    }

    return result;
  }
}
