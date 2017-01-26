/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static reactor.core.Exceptions.unwrap;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.streaming.CursorStream;
import org.mule.runtime.api.streaming.CursorStreamProvider;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.DefaultMuleException;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.util.func.CheckedFunction;

public final class CursorStreamUtils {

  public static <T> T withCursoredEvent(Event event, CheckedFunction<Event, T> f) throws MuleException {
    Reference<Throwable> exception = new Reference<>();
    CheckedFunction<Event, T> function = new CheckedFunction<Event, T>() {

      @Override
      public T applyChecked(Event event) throws Throwable {
        return f.apply(event);
      }

      @Override
      public T handleException(Throwable throwable) {
        exception.set(unwrap(throwable));
        return null;
      }
    };

    Object payload = event.getMessage().getPayload().getValue();
    CursorStream cursor = null;
    try {
      if (payload instanceof CursorStreamProvider) {
        cursor = ((CursorStreamProvider) payload).openCursor();
        event = Event.builder(event)
            .message(Message.builder(event.getMessage())
                         .payload(cursor)
                         .build())
            .build();
      }

      T value = function.apply(event);

      if (value == null) {
        Throwable t = exception.get();
        if (t != null) {
          if (t instanceof MuleException) {
            throw (MuleException) t;
          } else if (t instanceof Exception) {
            throw new DefaultMuleException(t);
          } else {
            throw new MuleRuntimeException(t);
          }
        }
      }

      return value;
    } finally {
      closeQuietly(cursor);
    }
  }

  private CursorStreamUtils() {
  }
}
