/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream;

import org.mule.runtime.core.stream.bytes.CursorStreamSupplier;
import org.mule.runtime.core.util.ByteUnit;

import java.io.InputStream;

public class FileStoreRepeatableStreamFactory extends BufferedRepeatableStreamFactory {


  public FileStoreRepeatableStreamFactory(int maxInMemorySize, ByteUnit sizeUnit) {
    super(maxInMemorySize, sizeUnit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CursorStreamSupplier repeatable(InputStream inputStream) {
    return new CursorStreamSupplier(inputStream, getBufferSize());
  }
}
