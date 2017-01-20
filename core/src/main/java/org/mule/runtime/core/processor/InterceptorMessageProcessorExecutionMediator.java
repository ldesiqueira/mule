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
import reactor.core.publisher.Flux;

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

  public static final QName SOURCE_FILE_NAME_ANNOTATION =
      new QName("http://www.mulesoft.org/schema/mule/documentation", "sourceFileName");
  public static final QName SOURCE_FILE_LINE_ANNOTATION =
      new QName("http://www.mulesoft.org/schema/mule/documentation", "sourceFileLine");

  private transient Logger logger = LoggerFactory.getLogger(InterceptorMessageProcessorExecutionMediator.class);

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
    if (shouldIntercept(processor)) {
      logger.debug("Applying interceptor for Processor: '{}'", processor.getClass());

      MessageProcessorInterceptorManager interceptorManager = muleContext.getMessageProcessorInterceptorManager();
      MessageProcessorInterceptorCallback interceptorCallback = interceptorManager.retrieveInterceptorCallback();

      AnnotatedObject annotatedObject = (AnnotatedObject) processor;
      Map<String, String> componentParameters = (Map<String, String>) annotatedObject.getAnnotation(ANNOTATION_PARAMETERS);

      return intercept(publisher, interceptorCallback, componentParameters, processor);
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
                                     Map<String, String> parameters,
                                     Processor processor) {
    return from(publisher)
        .flatMap(checkedFunction(request -> {
          return fluxIntercept(interceptorCallback, parameters, processor, request);
        }));
  }

  private Flux<Event> fluxIntercept(MessageProcessorInterceptorCallback interceptorCallback, Map<String, String> parameters,
                                    Processor processor, Event request)
      throws MuleException {

    Map<String, Object> resolvedParameters = resolveParameters(request, processor, parameters);

    return just(request)
        //TODO should before/after be blocking or non-blocking (map (event) or flatMap (Publisher<Event>)) --> there should be two mode those who are only meant to notify data and  those such a debugger and mocking  meant to be blocking
        .map(checkedFunction(event -> doBefore(event, interceptorCallback, resolvedParameters, processor)))
        .transform(checkedFunction(s -> doTransform(s, interceptorCallback, resolvedParameters, processor)))
        .map(checkedFunction(resultEvent -> doAfter(interceptorCallback, resultEvent, resolvedParameters, null, processor)))
        //TODO should I handle or just notify the error.
        .doOnError(MessagingException.class,
                   checkedConsumer(exception -> doAfter(interceptorCallback, exception.getEvent(), resolvedParameters,
                                                        exception, processor)));
  }



  protected Event doBefore(Event event, MessageProcessorInterceptorCallback interceptorCallback,
                           Map<String, Object> parameters, Processor processor)
      throws MuleException {

    ComponentIdentifier componentIdentifier = getComponentIdentifier(processor);
    String sourceFileName = (String) ((AnnotatedObject) processor).getAnnotation(SOURCE_FILE_NAME_ANNOTATION);
    Integer sourceFileLine = (Integer) ((AnnotatedObject) processor).getAnnotation(SOURCE_FILE_LINE_ANNOTATION);

    logger.debug("Intercepting before processor: " + componentIdentifier.toString() + " line: " + sourceFileLine + " - "
        + sourceFileName);

    return interceptorCallback.before(componentIdentifier, event, parameters);
  }

  protected Publisher<Event> doTransform(Publisher<Event> publisher, MessageProcessorInterceptorCallback interceptorCallback,
                                         Map<String, Object> parameters, Processor processor) {

    ComponentIdentifier componentIdentifier = getComponentIdentifier(processor);
    return from(publisher).flatMap(checkedFunction(event -> {
      if (shouldNotAllowMocking(processor) ||
          interceptorCallback.shouldExecuteProcessor(componentIdentifier, event, parameters)) {

        return just(event).transform(processor);
      } else {
        Publisher<Event> next = just(event).map(checkedFunction(request -> Event.builder(event)
            .message(InternalMessage
                .builder(interceptorCallback.getResult(componentIdentifier, request, parameters))
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

  // TODO here we should filter those routing processors that we do not allow to mock (and log it)
  private boolean shouldNotAllowMocking(Processor processor) {
    return false;
  }

  protected Event doAfter(MessageProcessorInterceptorCallback interceptorCallback, Event resultEvent,
                          Map<String, Object> parameters, MessagingException exception, Processor processor)
      throws MuleException {

    ComponentIdentifier componentIdentifier = getComponentIdentifier(processor);
    String sourceFileName = (String) ((AnnotatedObject) processor).getAnnotation(SOURCE_FILE_NAME_ANNOTATION);
    Integer sourceFileLine = (Integer) ((AnnotatedObject) processor).getAnnotation(SOURCE_FILE_LINE_ANNOTATION);
    logger.debug("Intercepting after processor: " + componentIdentifier.toString() + " line: " + sourceFileLine + " - "
        + sourceFileName);

    return interceptorCallback.after(componentIdentifier, resultEvent, parameters, exception);
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
