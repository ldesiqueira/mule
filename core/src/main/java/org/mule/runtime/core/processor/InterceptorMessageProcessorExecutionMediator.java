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
import static reactor.core.publisher.Flux.just;
import org.mule.runtime.api.meta.AnnotatedObject;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorManager;
import org.mule.runtime.core.api.message.InternalMessage;
import org.mule.runtime.core.api.processor.MessageProcessorChain;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.dsl.api.component.config.ComponentIdentifier;
import org.mule.runtime.core.construct.DynamicPipelineMessageProcessor;
import org.mule.runtime.core.exception.MessagingException;

import java.util.Map;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    if (processor instanceof DynamicPipeline) {
      ((MessageProcessorChain) ((DynamicPipelineMessageProcessor) processor).getListener()).getMessageProcessors()
          .stream().map(innerProcessor ->
                            apply(publisher, innerProcessor)
      ).reduce((first, second) -> second).get();
    }

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
    return from(publisher)
        .concatMap(request -> just(request)
            .map(checkedFunction(event -> Event.builder(event)
                .message(InternalMessage.builder(interceptorCallback.before(event.getMessage(), parameters))
                             .build()).build())))
        .transform(s -> doTransform(s, interceptorCallback, parameters, processor))
        .doOnNext(result -> interceptorCallback.after(result.getMessage(), parameters, null))
        .doOnError(MessagingException.class,
                           exception -> interceptorCallback.after(exception.getEvent().getMessage(), parameters, exception));
  }

  protected Publisher<Event> doTransform(Publisher<Event> publisher, MessageProcessorInterceptorCallback interceptorCallback,
                                         Map<String, String> parameters, Processor processor) {
    return from(publisher).concatMap(event -> {
        if (interceptorCallback.shouldExecuteProcessor(event.getMessage(), parameters)) {
          return processor.apply(publisher);
        } else {
          return from(publisher).handle(nullSafeMap(checkedFunction(request -> Event.builder(event)
                .message(InternalMessage.builder(interceptorCallback.getResult(request .getMessage(), parameters)).build()).build())));
        }
    });
  }

}

