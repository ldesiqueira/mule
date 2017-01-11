/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.api.interception;

import org.mule.runtime.dsl.api.component.config.ComponentIdentifier;

import java.util.Optional;

/**
 * TODO
 */
public interface MessageProcessorInterceptorManager {

  boolean hasInterceptionCallbacksRegistered();

  void registerInterceptionCallback(ComponentIdentifier componentIdentifier,
                                    MessageProcessorInterceptorCallback processorInterceptorCallback);

  Optional<MessageProcessorInterceptorCallback> retrieveInterceptorCallback(ComponentIdentifier componentIdentifier);
}
