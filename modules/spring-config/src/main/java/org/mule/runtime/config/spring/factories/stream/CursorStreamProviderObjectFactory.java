/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.factories.stream;

import org.mule.runtime.core.internal.stream.bytes.OffHeapMode;
import org.mule.runtime.core.api.stream.bytes.CursorStreamProviderFactory;
import org.mule.runtime.core.internal.stream.bytes.factory.DefaultCursorStreamProviderFactory;
import org.mule.runtime.api.util.ByteUnit;
import org.mule.runtime.dsl.api.component.ObjectFactory;

public class CursorStreamProviderObjectFactory implements ObjectFactory<CursorStreamProviderFactory> {

  private final int maxInMemorySize;
  private final ByteUnit sizeUnit;
  private final OffHeapMode offHeapMode;

  public CursorStreamProviderObjectFactory(int maxInMemorySize, ByteUnit sizeUnit, OffHeapMode offHeapMode) {
    this.maxInMemorySize = maxInMemorySize;
    this.sizeUnit = sizeUnit;
    this.offHeapMode = offHeapMode;
  }

  @Override
  public CursorStreamProviderFactory getObject() throws Exception {
    return new DefaultCursorStreamProviderFactory(maxInMemorySize, sizeUnit, offHeapMode);
  }

}
