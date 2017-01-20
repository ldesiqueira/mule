/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.runtime.api.exception.MuleRuntimeException;

import java.io.InputStream;

public final class StreamMaximumSizeExceededException extends MuleRuntimeException {

  private final InputStream inputStream;
  private final int bufferSize;

  public StreamMaximumSizeExceededException(InputStream inputStream, int bufferSize) {
    super(createStaticMessage(String.format("Buffer of %d bytes was defined but stream contains more information than that", bufferSize)));
    this.bufferSize = bufferSize;
    this.inputStream = inputStream;
  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public int getBufferSize() {
    return bufferSize;
  }
}
