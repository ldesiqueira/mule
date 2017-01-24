/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor;

import static java.lang.String.valueOf;
import static org.mule.runtime.core.api.rx.Exceptions.checkedFunction;
import static org.mule.runtime.dsl.api.component.config.ComponentIdentifier.ANNOTATION_NAME;
import static org.mule.runtime.dsl.api.component.config.ComponentIdentifier.ANNOTATION_PARAMETERS;
import static reactor.core.publisher.Flux.from;
import static reactor.core.publisher.Flux.just;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.meta.AnnotatedObject;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.construct.FlowConstructAware;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.interception.InterceptionCallback;
import org.mule.runtime.core.api.interception.InterceptionCallbackResult;
import org.mule.runtime.core.api.interception.InterceptionHandler;
import org.mule.runtime.core.api.interception.InterceptionHandlerChain;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorManager;
import org.mule.runtime.core.api.interception.ProcessorParameterResolver;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.dsl.api.component.config.ComponentIdentifier;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.namespace.QName;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Execution mediator for {@link Processor} that intercepts the processor execution with an {@link MessageProcessorInterceptorCallback interceptor callback}.
 *
 * @since 4.0
 */
public class InterceptorMessageProcessorExecutionStrategy implements MessageProcessorExecutionMediator, MuleContextAware,
    FlowConstructAware {

  public static final QName SOURCE_FILE_NAME_ANNOTATION =
      new QName("http://www.mulesoft.org/schema/mule/documentation", "sourceFileName");
  public static final QName SOURCE_FILE_LINE_ANNOTATION =
      new QName("http://www.mulesoft.org/schema/mule/documentation", "sourceFileLine");

  private transient Logger logger = LoggerFactory.getLogger(InterceptorMessageProcessorExecutionStrategy.class);

  private MuleContext muleContext;
  private FlowConstruct flowConstruct;

  @Override
  public void setMuleContext(MuleContext muleContext) {
    this.muleContext = muleContext;
  }

  @Override
  public void setFlowConstruct(FlowConstruct flowConstruct) {
    this.flowConstruct = flowConstruct;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Publisher<Event> apply(Publisher<Event> publisher, Processor processor) {
    if (isInterceptable(processor)) {
      logger.debug("Applying interceptor for Processor: '{}'", processor.getClass());

      MessageProcessorInterceptorManager interceptorManager = muleContext.getMessageProcessorInterceptorManager();
      InterceptionHandlerChain interceptionHandlerChain = interceptorManager.retrieveInterceptionHandlerChain();

      AnnotatedObject annotatedObject = (AnnotatedObject) processor;
      Map<String, String> componentParameters = (Map<String, String>) annotatedObject.getAnnotation(ANNOTATION_PARAMETERS);

      return intercept(publisher, interceptionHandlerChain, componentParameters, processor);
    }

    return processor.apply(publisher);
  }

  private Boolean isInterceptable(Processor processor) {
    if (processor instanceof AnnotatedObject) {
      ComponentIdentifier componentIdentifier = getComponentIdentifier(processor);
      if (componentIdentifier != null) {
        return true;
      } else {
        logger.warn("Processor '{}' is an '{}' but doesn't have a componentIdentifier", processor.getClass(),
                    AnnotatedObject.class);
      }
    } else {
      logger.debug("Processor '{}' is not an '{}'", processor.getClass(), AnnotatedObject.class);
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  private Publisher<Event> intercept(Publisher<Event> publisher, InterceptionHandlerChain interceptionHandlerChain,
                                     Map<String, String> parameters,
                                     Processor processor) {

    return from(publisher)
        .flatMap(checkedFunction(request -> {
         AtomicReference<Flux<Event>> flux = new AtomicReference<>(just(request));
         final Map<String, Object> resolvedParameters = resolveParameters(request, processor, parameters);

          AtomicBoolean stopInterception = new AtomicBoolean(false);
          ListIterator<InterceptionHandler> listIterator = interceptionHandlerChain.listIterator();
          AtomicReference<Event> eventLastInterception = new AtomicReference<>(request);
          while (listIterator.hasNext() && !stopInterception.get()) {
            InterceptionHandler interceptionHandler = listIterator.next();

            interceptionHandler.before(getComponentIdentifier(processor), eventLastInterception.get(),
                                       Event.builder(eventLastInterception.get()),
                                       resolvedParameters, new InterceptionCallback() {

                  @Override
                  public InterceptionCallbackResult skipProcessor(Event result) {
                    flux.set(flux.get().map(input -> result));
                    eventLastInterception.set(result);
                    stopInterception.set(true);
                    return null;
                  }

                  @Override
                  public InterceptionCallbackResult nextProcessor(Event newRequest) {
                    flux.set(flux.get().map(input -> newRequest));
                    eventLastInterception.set(newRequest);
                    return null;
                  }

                });
          }

          if (!stopInterception.get()) {
            flux.set(flux.get().transform(processor));
          }

          while (listIterator.hasPrevious()) {
            InterceptionHandler interceptionHandler = listIterator.previous();
            //TODO it should be able to throw a MuleException during the after process
            flux.set(flux.get().doOnNext(resultEvent -> interceptionHandler.after(resultEvent)));
            //flux.set(flux.get().doOnError(MessagingException.class,  exception -> interceptionHandler.after(exception.getEvent())));
          }
          return flux.get();
    }));
  }

  private Map<String, Object> resolveParameters(Event event, Processor processor, Map<String, String> parameters)
      throws MuleException {

    if (processor instanceof ProcessorParameterResolver) {
      return ((ProcessorParameterResolver) processor).resolve(event);
    }

    Map<String, Object> resolvedParameters = new HashMap<>();
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      Object value;
      String paramValue = entry.getValue();
      if (muleContext.getExpressionManager().isExpression(paramValue)) {
        value = muleContext.getExpressionManager().evaluate(paramValue, event, flowConstruct).getValue();
      } else {
        value = valueOf(paramValue);
      }
      resolvedParameters.put(entry.getKey(), value);
    }
    return resolvedParameters;
  }

  protected ComponentIdentifier getComponentIdentifier(Processor processor) {
    AnnotatedObject annotatedObject = (AnnotatedObject) processor;
    return (ComponentIdentifier) annotatedObject.getAnnotation(ANNOTATION_NAME);
  }

}
