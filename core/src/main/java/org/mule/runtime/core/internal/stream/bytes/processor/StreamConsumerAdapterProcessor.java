/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes.processor;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.startIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.stopIfNeeded;
import static reactor.core.publisher.Mono.from;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Lifecycle;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.construct.FlowConstructAware;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.api.streaming.CursorStream;
import org.mule.runtime.api.streaming.CursorStreamProvider;

import javax.inject.Inject;

import org.reactivestreams.Publisher;

public class StreamConsumerAdapterProcessor implements Processor, Lifecycle, MuleContextAware, FlowConstructAware {

  private final Processor delegate;

  @Inject
  private MuleContext muleContext;

  public StreamConsumerAdapterProcessor(Processor delegate) {
    this.delegate = delegate;
  }

  @Override
  public Event process(Event event) throws MuleException {
    StreamingContext streamingContext = buildStreamingContext(event);

    event = delegate.process(streamingContext.event);

    return process(event, streamingContext);
  }

  @Override
  public Publisher<Event> apply(Publisher<Event> publisher) {
    Reference<StreamingContext> streamingContext = new Reference<>(null);
    return from(publisher).map(event -> {
      StreamingContext ctx = buildStreamingContext(event);
      streamingContext.set(ctx);
      return ctx.event;
    }).transform(delegate)
        .map(event -> process(event, streamingContext.get()));
  }

  private StreamingContext buildStreamingContext(Event event) {
    Object payload = event.getMessage().getPayload().getValue();

    if (payload instanceof CursorStreamProvider) {
      CursorStreamProvider cursorStreamProvider = (CursorStreamProvider) payload;
      CursorStream cursor = cursorStreamProvider.openCursor();

      return new StreamingContext(replacePayload(event, cursor), cursorStreamProvider, cursor);
    }

    return new StreamingContext(event, null, null);
  }

  private Event process(Event resultEvent, StreamingContext streamingContext) {
    Object resultPayload = resultEvent.getMessage().getPayload().getValue();

    if (resultPayload == streamingContext.cursor && streamingContext.cursor != null) {
      closeQuietly(streamingContext.cursor);
      resultEvent = replacePayload(resultEvent, streamingContext.cursorStreamProvider);
    }

    return resultEvent;
  }

  private Event replacePayload(Event event, Object payload) {
    return Event.builder(event)
        .message(Message.builder(event.getMessage())
            .payload(payload)
            .build())
        .build();
  }

  private class StreamingContext {

    private final Event event;
    private final CursorStreamProvider cursorStreamProvider;
    private final CursorStream cursor;

    public StreamingContext(Event event, CursorStreamProvider cursorStreamProvider, CursorStream cursor) {
      this.event = event;
      this.cursorStreamProvider = cursorStreamProvider;
      this.cursor = cursor;
    }
  }

  @Override
  public void setFlowConstruct(FlowConstruct flowConstruct) {
    if (delegate instanceof FlowConstructAware) {
      ((FlowConstructAware) delegate).setFlowConstruct(flowConstruct);
    }
  }

  @Override
  public void setMuleContext(MuleContext context) {
    muleContext = context;
    if (delegate instanceof MuleContextAware) {
      ((MuleContextAware) delegate).setMuleContext(context);
    }
  }

  @Override
  public void stop() throws MuleException {
    stopIfNeeded(delegate);
  }

  @Override
  public void dispose() {
    if (delegate instanceof Disposable) {
      ((Disposable) delegate).dispose();
    }
  }

  @Override
  public void start() throws MuleException {
    startIfNeeded(delegate);
  }

  @Override
  public void initialise() throws InitialisationException {
    initialiseIfNeeded(delegate, true, muleContext);
  }
}
