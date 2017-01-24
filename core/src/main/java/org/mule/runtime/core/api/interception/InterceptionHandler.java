/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.api.interception;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.Event.Builder;
import org.mule.runtime.dsl.api.component.config.ComponentIdentifier;

import java.util.Map;

/**
 * TODO
 */
public interface InterceptionHandler {

  //TODO change Event and Builder to other things (view of Event and Builder)
  InterceptionCallbackResult before(ComponentIdentifier componentIdentifier, Event event, Builder eventBuilder, Map<String, Object> parameters, InterceptionCallback callback) throws MuleException;


  default void after(Event event) {} //throws MuleException {}
}
