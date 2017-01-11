/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor.interceptor;

import org.mule.runtime.core.api.Event;

import java.util.Map;

/**
 * TODO
 */
public interface MessageProcessorInterceptorCallback {

  default Event before(Event event, Map<String, Object> parameters) { return event; }

  default boolean executeProcessor(Event event, Map<String, Object> parameters) { return true; }

  Event getResult(Event event, Map<String, Object> parameters);

  default Event after(Event resultEvent, Map<String, Object> parameters) { return resultEvent; }

}
