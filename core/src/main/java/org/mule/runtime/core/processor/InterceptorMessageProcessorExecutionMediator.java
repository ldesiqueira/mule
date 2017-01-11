/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.mule.runtime.api.dsl.config.ComponentConfiguration.ANNOTATION_PARAMETERS;
import static org.mule.runtime.api.dsl.config.ComponentIdentifier.ANNOTATION_NAME;
import static org.mule.runtime.api.util.Preconditions.checkState;
import static org.mule.runtime.core.api.rx.Exceptions.checkedFunction;
import static org.mule.runtime.core.internal.util.rx.Operators.nullSafeMap;
import static reactor.core.publisher.Flux.from;
import org.mule.runtime.api.dsl.config.ComponentIdentifier;
import org.mule.runtime.api.meta.AnnotatedObject;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.rx.Exceptions;
import org.mule.runtime.core.processor.interceptor.MessageProcessorInterceptorCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Execution mediator for {@link Processor} that intercepts the processor execution with an {@link org.mule.runtime.core.processor.interceptor.MessageProcessorInterceptorCallback interceptor callback}.
 *
 * @since 4.0
 */
public class InterceptorMessageProcessorExecutionMediator implements MessageProcessorExecutionMediator {

  private transient Logger logger = LoggerFactory.getLogger(InterceptorMessageProcessorExecutionMediator.class);

  private Map<ComponentIdentifier, MessageProcessorInterceptorCallback> interceptorCallbacks = new HashMap<>();

  /**
   * Registers an {@link MessageProcessorInterceptorCallback interceptor callback} for {@link Processor} identified by a namespace and name.
   * <p/>
   * Only one {@link MessageProcessorInterceptorCallback interceptor callback} can be registered by {@link Processor}.
   *
   * @param namespace namespace of the component to be intercepted. Not null.
   * @param name name of the component to be intercepted. Not null.
   * @param interceptorCallback {@link MessageProcessorInterceptorCallback interceptor callback} to be called when the processor is intercepted. Not null.
   * @throws IllegalStateException if there is already registered an interceptor callback.
   */
  public void registerMessageProcessorInterceptor(String namespace, String name, MessageProcessorInterceptorCallback interceptorCallback) throws IllegalStateException {
    checkNotNull(namespace, "namespace cannot be null");
    checkNotNull(name, "name cannot be null");
    checkNotNull(interceptorCallback, "interceptorCallback cannot be null");

    ComponentIdentifier componentIdentifier = ComponentIdentifier.builder()
        .withNamespace(namespace)
        .withName(name)
        .build();

    checkState(interceptorCallbacks.containsKey(componentIdentifier), "Cannot register more than one interceptor callback for processor: " + componentIdentifier);
    interceptorCallbacks.put(componentIdentifier, interceptorCallback);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Function<Flux<Event>, Publisher<Event>> apply(Processor processor) {
    if (processor instanceof AnnotatedObject) {
      ComponentIdentifier componentIdentifier = (ComponentIdentifier) ((AnnotatedObject) processor).getAnnotation(ANNOTATION_NAME);
      if (componentIdentifier == null) {
        if (logger.isWarnEnabled()) {
          logger.warn("Processor '{}' is an '{}' but doesn't have a componentIdentifier", processor.getClass(),
                       AnnotatedObject.class);
          return applyNext(processor);
        }
      }
      if (!interceptorCallbacks.containsKey(componentIdentifier)) {
        return applyNext(processor);
      }

      MessageProcessorInterceptorCallback interceptorCallback = interceptorCallbacks.get(componentIdentifier);
      Map<String, String> componentParameters = (Map<String, String>) ((AnnotatedObject) processor).getAnnotation(ANNOTATION_PARAMETERS);
      if (logger.isDebugEnabled()) {
        logger.debug("Applying interceptor for Processor: '{}'", processor.getClass());
      }
      //TODO resolve parameters!
      return applyInterceptor(interceptorCallback, componentParameters, processor);
    }
    return applyNext(processor);
  }

  private Function<Flux<Event>,Publisher<Event>> applyInterceptor(MessageProcessorInterceptorCallback interceptorCallback, Map<String, String> parameters, Processor processor) {
    return stream -> from(stream).flatMap(event -> {
      Mono<Event> mono = Mono.just(event)
          .map(checkedFunction(eventToProcess -> interceptorCallback.before(eventToProcess, parameters)));
      if (interceptorCallback.executeProcessor(event, parameters)) {
            mono = mono.transform(processor);
      } else {
            mono = mono.handle(nullSafeMap(checkedFunction(response -> interceptorCallback.getResult(event, parameters))));
      }
      mono.map(checkedFunction(response -> interceptorCallback.after(response, parameters)))
          .otherwise(Exceptions.EventDroppedException.class, mde -> Mono.just(event));
      return mono;
    });
  }

  private Function<Flux<Event>, Publisher<Event>> applyNext(Processor processor) {
    return stream -> from(stream.transform(processor));
  }

}
