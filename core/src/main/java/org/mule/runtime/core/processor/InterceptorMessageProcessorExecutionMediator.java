/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.processor;

import static java.lang.String.valueOf;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.rx.Exceptions.checkedConsumer;
import static org.mule.runtime.core.api.rx.Exceptions.checkedFunction;
import static org.mule.runtime.dsl.api.component.config.ComponentIdentifier.ANNOTATION_NAME;
import static org.mule.runtime.dsl.api.component.config.ComponentIdentifier.ANNOTATION_PARAMETERS;
import static reactor.core.publisher.Flux.from;
import static reactor.core.publisher.Flux.just;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.meta.AnnotatedObject;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.construct.FlowConstructAware;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.interception.InterceptableMessageProcessor;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorManager;
import org.mule.runtime.core.api.interception.ProcessorParameterResolver;
import org.mule.runtime.core.api.message.InternalMessage;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.dsl.api.component.config.ComponentIdentifier;
import org.mule.runtime.core.exception.MessagingException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

/**
 * Execution mediator for {@link Processor} that intercepts the processor execution with an {@link org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback interceptor callback}.
 *
 * @since 4.0
 */
public class InterceptorMessageProcessorExecutionMediator implements MessageProcessorExecutionMediator, MuleContextAware,
    FlowConstructAware {

  private transient Logger logger = LoggerFactory.getLogger(InterceptorMessageProcessorExecutionMediator.class);

  private MuleContext muleContext;
  private FlowConstruct flowConstruct;
  public static final QName SOURCE_FILE_NAME_ANNOTATION =
      new QName("http://www.mulesoft.org/schema/mule/documentation", "sourceFileName");
  public static final QName SOURCE_FILE_LINE_ANNOTATION =
      new QName("http://www.mulesoft.org/schema/mule/documentation", "sourceFileLine");

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
    if (shouldIntercept(processor)) {
      logger.debug("Applying interceptor for Processor: '{}'", processor.getClass());

      AnnotatedObject annotatedObject = (AnnotatedObject) processor;
      ComponentIdentifier componentIdentifier = (ComponentIdentifier) annotatedObject.getAnnotation(ANNOTATION_NAME);
      MessageProcessorInterceptorManager interceptorManager = muleContext.getMessageProcessorInterceptorManager();
      MessageProcessorInterceptorCallback interceptorCallback = interceptorManager.retrieveInterceptorCallback();

      Map<String, String> componentParameters = (Map<String, String>) annotatedObject.getAnnotation(ANNOTATION_PARAMETERS);

      //TODO resolve parameters! (delegate to each processor)
      return intercept(publisher, interceptorCallback, componentIdentifier, componentParameters, processor);
    }

    return processor.apply(publisher);
  }

  private Boolean shouldIntercept(Processor processor) {
    if (processor instanceof AnnotatedObject) {
      AnnotatedObject annotatedObject = (AnnotatedObject) processor;
      ComponentIdentifier componentIdentifier = (ComponentIdentifier) annotatedObject.getAnnotation(ANNOTATION_NAME);
      if (componentIdentifier != null) {
        MessageProcessorInterceptorManager interceptorManager = muleContext.getMessageProcessorInterceptorManager();
        MessageProcessorInterceptorCallback interceptorCallback = interceptorManager.retrieveInterceptorCallback();
        if (null != interceptorCallback) {
          return true;
        } else {
          logger.debug("Processor '{}' does not have a Interceptor Callback", processor.getClass());
        }
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
  private Publisher<Event> intercept(Publisher<Event> publisher, MessageProcessorInterceptorCallback interceptorCallback,
                                     ComponentIdentifier componentIdentifier, Map<String, String> parameters,
                                     Processor processor) {
    return from(publisher)
        .flatMap(checkedFunction(request -> {
          Map<String, Object> resolvedParameters = resolveParameters(request, processor, parameters);
          return just(request)
              //TODO should before/after be blocking or non-blocking (map (event) or flatMap (Publisher<Event>))
              .map(checkedFunction(event -> Event.builder(event)
                  .message(doBefore(event, interceptorCallback, componentIdentifier, resolvedParameters, processor))
                  .build()))
              .transform(checkedFunction(s -> doTransform(s, interceptorCallback, componentIdentifier, resolvedParameters,
                                                          processor)))
              .map(checkedFunction(result -> Event.builder(result).message(InternalMessage
                  .builder(interceptorCallback.after(componentIdentifier, result.getMessage(), resolvedParameters, null))
                  .build()).build()))

              //TODO should I handle or just notify the error.
              .doOnError(MessagingException.class,
                         checkedConsumer(exception -> doAfter(interceptorCallback, componentIdentifier,
                                                              exception.getEvent().getMessage(),
                                                              resolvedParameters,
                                                              exception, processor)));
        }));
  }

  protected Message doBefore(Event event, MessageProcessorInterceptorCallback interceptorCallback,
                             ComponentIdentifier componentIdentifier, Map<String, Object> parameters, Processor processor)
      throws MuleException {

    String sourceFileName = (String) ((AnnotatedObject) processor).getAnnotation(SOURCE_FILE_NAME_ANNOTATION);
    Integer sourceFileLine = (Integer) ((AnnotatedObject) processor).getAnnotation(SOURCE_FILE_LINE_ANNOTATION);

    logger.debug("Intercepting before processor: " + componentIdentifier.toString() + " line: " + sourceFileLine + " - "
        + sourceFileName);

    return interceptorCallback.before(componentIdentifier, event.getMessage(), parameters);
  }



  protected Publisher<Event> doTransform(Publisher<Event> publisher, MessageProcessorInterceptorCallback interceptorCallback,
                                         ComponentIdentifier componentIdentifier, Map<String, Object> parameters,
                                         Processor processor) {
    return from(publisher).flatMap(checkedFunction(event -> {
      if (interceptorCallback.shouldExecuteProcessor(componentIdentifier, event.getMessage(), parameters)) {
        return just(event).transform(processor);
      } else {
        Publisher<Event> next = just(event).map(checkedFunction(request -> Event.builder(event)
            .message(InternalMessage
                .builder(interceptorCallback
                    .getResult(componentIdentifier, request.getMessage(), parameters))
                .build())
            .build()));
        //TODO Remove this, we should not allow to intercept this kind of processors
        if (processor instanceof InterceptableMessageProcessor) {
          try {
            InterceptableMessageProcessor interceptableMessageProcessor = (InterceptableMessageProcessor) processor;
            Processor nextProcessor = interceptableMessageProcessor.getNext();
            if (nextProcessor != null) {
              next = nextProcessor.apply(next);
            }
          } catch (Exception e) {
            throw new MuleRuntimeException(createStaticMessage("Error while getting next processor from interceptor"),
                                           e);
          }
        }
        return next;
      }
    }));
  }

  protected Message doAfter(MessageProcessorInterceptorCallback interceptorCallback,
                            ComponentIdentifier componentIdentifier, Message resultMessage, Map<String, Object> parameters,
                            MessagingException exception, Processor processor)
      throws MuleException {

            String sourceFileName = (String)((AnnotatedObject) processor).getAnnotation(SOURCE_FILE_NAME_ANNOTATION);
      Integer sourceFileLine = (Integer)((AnnotatedObject) processor).getAnnotation(SOURCE_FILE_LINE_ANNOTATION);


    logger.debug("Intercepting after processor: " + componentIdentifier.toString()+ " line: " + sourceFileLine + " - " + sourceFileName);

    return interceptorCallback.after(componentIdentifier, resultMessage, parameters, exception);
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

}
