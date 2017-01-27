/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes.factory;

import org.mule.runtime.core.api.functional.Either;
import org.mule.runtime.api.streaming.CursorStreamProvider;
import org.mule.runtime.core.api.stream.bytes.CursorStreamProviderFactory;

import java.io.InputStream;

public class NullCursorStreamProviderFactory implements CursorStreamProviderFactory {

  @Override
  public Either<CursorStreamProvider, InputStream> of(InputStream inputStream) {
    return Either.right(inputStream);
  }
}
