/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.factories.stream;

import org.mule.runtime.core.internal.stream.FileStoreRepeatableStreamFactory;

public class FileStoreRepeatableStreamFactoryObjectFactory extends BufferedRepeatableStreamFactoryObjectFactory<FileStoreRepeatableStreamFactory> {

  @Override
  public FileStoreRepeatableStreamFactory getObject() throws Exception {
    return new FileStoreRepeatableStreamFactory(getMaxInMemorySize(), getSizeUnit());
  }

}
