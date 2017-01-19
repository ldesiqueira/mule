package org.mule.runtime.core.internal.stream;

import org.mule.runtime.core.stream.bytes.CursorStreamSupplier;

import java.io.InputStream;

public interface RepeatableStreamFactory {

  CursorStreamSupplier repeatable(InputStream inputStream);
}
