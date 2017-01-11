/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor.interceptor;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.runtime.api.util.Preconditions.checkNotNull;
import org.mule.runtime.api.dsl.config.ComponentIdentifier;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * TODO
 */
public class DefaultMessageProcessorInterceptorManager implements MessageProcessorInterceptorManager {

  private Map<ComponentIdentifier, MessageProcessorInterceptorCallback> processorInterceptorCallbacks = new HashMap<>();

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasInterceptionCallbacksRegistered() {
    return processorInterceptorCallbacks.size() > 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void registerInterceptionCallback(ComponentIdentifier componentIdentifier,
                                           MessageProcessorInterceptorCallback processorInterceptorCallback) {
    checkNotNull(componentIdentifier, "componentIdentifier not null");
    checkNotNull(processorInterceptorCallback, "processorInterceptorCallback not null");

    if (this.processorInterceptorCallbacks.containsKey(componentIdentifier)) {
      throw new IllegalStateException("There is already registered an " + MessageProcessorInterceptorCallback.class.getName()
          + " for componentIdentifier: " + componentIdentifier);
    }
    this.processorInterceptorCallbacks.put(componentIdentifier, processorInterceptorCallback);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<MessageProcessorInterceptorCallback> retrieveInterceptorCallback(ComponentIdentifier componentIdentifier) {
    checkNotNull(componentIdentifier, "componentIdentifier not null");

    if (!this.processorInterceptorCallbacks.containsKey(componentIdentifier)) {
      return empty();
    }
    return of(this.processorInterceptorCallbacks.get(componentIdentifier));
  }

}
