/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor.interceptor;

import static org.mule.runtime.api.util.Preconditions.checkNotNull;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorManager;

/**
 * TODO
 */
public class DefaultMessageProcessorInterceptorManager implements MessageProcessorInterceptorManager {

  private MessageProcessorInterceptorCallback processorInterceptorCallback;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isInterceptionEnabled() {
    return processorInterceptorCallback != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setInterceptionCallback(MessageProcessorInterceptorCallback processorInterceptorCallback) {
    checkNotNull(processorInterceptorCallback, "processorInterceptorCallback cannot be null");

    this.processorInterceptorCallback = processorInterceptorCallback;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MessageProcessorInterceptorCallback retrieveInterceptorCallback() {
    return processorInterceptorCallback;
  }
}
