/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor;

import static org.mule.runtime.core.api.rx.Exceptions.checkedFunction;
import static org.mule.runtime.core.internal.util.rx.Operators.nullSafeMap;
import static org.mule.runtime.dsl.api.component.config.ComponentIdentifier.ANNOTATION_NAME;
import static org.mule.runtime.dsl.api.component.config.ComponentIdentifier.ANNOTATION_PARAMETERS;
import static reactor.core.publisher.Flux.from;
import org.mule.runtime.api.meta.AnnotatedObject;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorManager;
import org.mule.runtime.core.api.message.InternalMessage;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.rx.Exceptions;
import org.mule.runtime.dsl.api.component.config.ComponentIdentifier;

import java.util.Map;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Execution mediator for {@link Processor} that intercepts the processor execution with an {@link org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback interceptor callback}.
 *
 * @since 4.0
 */
public class InterceptorMessageProcessorExecutionMediator implements MessageProcessorExecutionMediator, MuleContextAware {

  private transient Logger logger = LoggerFactory.getLogger(InterceptorMessageProcessorExecutionMediator.class);

  private MuleContext muleContext;

  @Override
  public void setMuleContext(MuleContext muleContext) {
    this.muleContext = muleContext;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Publisher<Event> apply(Publisher<Event> publisher, Processor processor) {
    if (processor instanceof AnnotatedObject) {
      final AnnotatedObject annotatedObject = (AnnotatedObject) processor;
      ComponentIdentifier componentIdentifier = (ComponentIdentifier) annotatedObject.getAnnotation(ANNOTATION_NAME);
      if (componentIdentifier == null) {
        if (logger.isWarnEnabled()) {
          logger.warn("Processor '{}' is an '{}' but doesn't have a componentIdentifier", processor.getClass(),
                      AnnotatedObject.class);
          return processor.apply(publisher);
        }
      }
      MessageProcessorInterceptorManager interceptorManager = muleContext.getMessageProcessorInterceptorManager();
      Optional<MessageProcessorInterceptorCallback> callbackOptional =
          interceptorManager.retrieveInterceptorCallback(componentIdentifier);
      if (!callbackOptional.isPresent()) {
        return processor.apply(publisher);
      }

      Map<String, String> componentParameters = (Map<String, String>) annotatedObject.getAnnotation(ANNOTATION_PARAMETERS);
      if (logger.isDebugEnabled()) {
        logger.debug("Applying interceptor for Processor: '{}'", processor.getClass());
      }

      MessageProcessorInterceptorCallback interceptorCallback = callbackOptional.get();

      //TODO resolve parameters! (delegate to each processor)
      return applyInterceptor(publisher, interceptorCallback, componentParameters, processor);
    }
    return processor.apply(publisher);
  }

  /**
   * {@inheritDoc}
   */
  private Publisher<Event> applyInterceptor(Publisher<Event> publisher, MessageProcessorInterceptorCallback interceptorCallback,
                                            Map<String, String> parameters, Processor processor) {
    return stream -> from(publisher).flatMap(event -> {
      Mono<Event> mono = Mono.just(event).map(checkedFunction(eventToProcess -> Event.builder(event)
          .message(InternalMessage.builder(interceptorCallback.before(eventToProcess.getMessage(), parameters))
              .build())
          .build()));
      //TODO: how to get event obtained from before operation!
      if (interceptorCallback.shouldExecuteProcessor(event.getMessage(), parameters)) {
        mono = mono.transform(processor);
      } else {
        mono = mono.handle(nullSafeMap(checkedFunction(response -> Event.builder(event)
            .message(InternalMessage.builder(interceptorCallback.getResult(event.getMessage(), parameters))
                .build())
            .build())));
      }
      mono.doOnNext(response -> interceptorCallback.after(response.getMessage(), parameters))
          .otherwise(Exceptions.EventDroppedException.class, mde -> Mono.just(event));
      return mono;
    });
  }
}

