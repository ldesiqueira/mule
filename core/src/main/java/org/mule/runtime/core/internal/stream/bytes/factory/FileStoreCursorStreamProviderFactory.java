/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes.factory;

import org.mule.runtime.core.internal.stream.bytes.BufferedCursorStreamProvider;
import org.mule.runtime.core.internal.stream.bytes.CursorStreamProvider;
import org.mule.runtime.core.util.ByteUnit;

import java.io.InputStream;

public class FileStoreCursorStreamProviderFactory extends CursorStreamProviderFactory {


  public FileStoreCursorStreamProviderFactory(int maxInMemorySize, ByteUnit sizeUnit) {
    super(maxInMemorySize, sizeUnit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CursorStreamProvider of(InputStream inputStream) {
    return new BufferedCursorStreamProvider(inputStream, getBufferSize());
  }
}
