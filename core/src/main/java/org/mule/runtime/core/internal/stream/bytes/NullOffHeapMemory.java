/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import java.nio.ByteBuffer;

public class NullOffHeapMemory implements OffHeapMemory {

  @Override
  public int get(ByteBuffer dest, StreamBuffer.Range requiredRange, int length) {
    return -1;
  }

  @Override
  public boolean put(ByteBuffer buffer) {
    return false;
  }

  @Override
  public int get(ByteBuffer dest) {
    return -1;
  }

  @Override
  public void release() {
    // no op
  }
}
