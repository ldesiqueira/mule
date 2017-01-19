/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.resolver;

import org.mule.runtime.api.streaming.CursorStreamProvider;
import org.mule.runtime.extension.api.runtime.operation.ExecutionContext;

import java.io.InputStream;

public class ByParameterNameStreamArguementResolver extends ByParameterNameArgumentResolver<InputStream> {

  public ByParameterNameStreamArguementResolver(String parameterName) {
    super(parameterName);
  }

  @Override
  public InputStream resolve(ExecutionContext executionContext) {
    Object value = super.resolve(executionContext);

    if (value == null) {
      return null;
    } else if (value instanceof InputStream) {
      return (InputStream) value;
    } else if (value instanceof CursorStreamProvider) {
      return ((CursorStreamProvider) value).openCursor();
    } else {
      throw new IllegalArgumentException("value was not an InputStream. Value class is: " + value.getClass().getName());
    }
  }
}
