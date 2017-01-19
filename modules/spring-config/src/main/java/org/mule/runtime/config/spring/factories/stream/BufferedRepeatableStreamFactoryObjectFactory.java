/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.factories.stream;

import org.mule.runtime.core.internal.stream.RepeatableStreamFactory;
import org.mule.runtime.core.util.ByteUnit;
import org.mule.runtime.dsl.api.component.ObjectFactory;

abstract class BufferedRepeatableStreamFactoryObjectFactory<T extends RepeatableStreamFactory> implements ObjectFactory<T> {

  public static final int DEFAULT_IN_MEMORY_SIZE = 1572864;
  public static final ByteUnit DEFAULT_SIZE_UNIT = ByteUnit.BYTE;

  private int maxInMemorySize = DEFAULT_IN_MEMORY_SIZE;
  private ByteUnit sizeUnit = DEFAULT_SIZE_UNIT;

  protected int getMaxInMemorySize() {
    return maxInMemorySize;
  }

  protected ByteUnit getSizeUnit() {
    return sizeUnit;
  }

  public void setMaxInMemorySize(int maxInMemorySize) {
    this.maxInMemorySize = maxInMemorySize;
  }

  public void setSizeUnit(ByteUnit sizeUnit) {
    this.sizeUnit = sizeUnit;
  }
}
