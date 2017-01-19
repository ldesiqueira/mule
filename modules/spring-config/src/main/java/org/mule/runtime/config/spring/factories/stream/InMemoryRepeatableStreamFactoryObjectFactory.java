/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.factories.stream;

import org.mule.runtime.core.internal.stream.FileStoreRepeatableStreamFactory;
import org.mule.runtime.core.internal.stream.InMemoryRepeatableStreamFactory;
import org.mule.runtime.core.util.ByteUnit;
import org.mule.runtime.dsl.api.component.ObjectFactory;

public class InMemoryRepeatableStreamFactoryObjectFactory extends BufferedRepeatableStreamFactoryObjectFactory<InMemoryRepeatableStreamFactory> {

  @Override
  public InMemoryRepeatableStreamFactory getObject() throws Exception {
    return new InMemoryRepeatableStreamFactory(getMaxInMemorySize(), getSizeUnit());
  }
}
