/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes.factory;

import org.mule.runtime.core.api.functional.Either;
import org.mule.runtime.core.api.stream.bytes.CursorStreamProviderFactory;
import org.mule.runtime.core.internal.streaming.bytes.BufferedCursorStreamProvider;
import org.mule.runtime.api.streaming.CursorStreamProvider;
import org.mule.runtime.core.internal.streaming.bytes.OffHeapMode;
import org.mule.runtime.api.util.ByteUnit;

import java.io.InputStream;

public class DefaultCursorStreamProviderFactory implements CursorStreamProviderFactory {

  private final int bufferSize;
  private final OffHeapMode offHeapMode;

  public DefaultCursorStreamProviderFactory(int maxInMemorySize, ByteUnit sizeUnit, OffHeapMode offHeapMode) {
    bufferSize = sizeUnit.toBytes(maxInMemorySize);
    this.offHeapMode = offHeapMode;
  }

  @Override
  public Either<CursorStreamProvider, InputStream> of(InputStream inputStream) {
    return Either.left(new BufferedCursorStreamProvider(inputStream, bufferSize, offHeapMode));
  }
}
