/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.stream.bytes;

import static org.mule.runtime.core.internal.streaming.bytes.OffHeapMode.FILE_STORE;
import org.mule.runtime.api.streaming.CursorStreamProvider;
import org.mule.runtime.core.api.functional.Either;
import org.mule.runtime.core.internal.streaming.bytes.factory.DefaultCursorStreamProviderFactory;
import org.mule.runtime.extension.api.ExtensionConstants;

import java.io.InputStream;

public interface CursorStreamProviderFactory {

  static CursorStreamProviderFactory createDefault() {
    return new DefaultCursorStreamProviderFactory(ExtensionConstants.DEFAULT_STREAMING_BUFFER_SIZE,
                                                  ExtensionConstants.DEFAULT_STREAMING_BUFFER_SIZE_UNIT, FILE_STORE);
  }

  Either<CursorStreamProvider, InputStream> of(InputStream inputStream);
}
