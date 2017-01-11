/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor;

import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.processor.Processor;

import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * Executes {@link Processor Processors} while coordinating the several moving parts that are affected by the execution process, so that such pieces
 * can remain decoupled.
 * <p/>
 * This mediator will coordinate {@link Processor processors}, {@link org.mule.runtime.core.processor.interceptor.MessageProcessorInterceptorCallback interceptor callbacks}.
 *
 * @since 4.0
 */
public interface MessageProcessorExecutionMediator {

  /**
   * Given the {@link }
   * @param processor
   * @return
   */
  Function<Flux<Event>, Publisher<Event>> apply(Processor processor);

}
