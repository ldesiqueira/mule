/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.api.interception;

import org.mule.runtime.api.dsl.config.ComponentIdentifier;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.exception.MessagingException;

import java.util.Map;

/**
 * TODO
 */
public interface MessageProcessorInterceptorCallback {


  default boolean shouldExecuteProcessor(ComponentIdentifier componentIdentifier, Event event,
                                         Map<String, Object> parameters) {
    return true;
  }

  Event getResult(ComponentIdentifier componentIdentifier, Event event, Map<String, Object> parameters) throws MuleException;

  default Event before(ComponentIdentifier componentIdentifier, Event event, Map<String, Object> parameters) throws MuleException {
    return event;
  }

  default Event after(ComponentIdentifier componentIdentifier, Event resultEvent, Map<String, Object> parameters,
                      MessagingException e) throws MuleException {
    return resultEvent;
  }

}
