package org.mule.runtime.core.internal.stream.bytes.factory;

import org.mule.runtime.core.internal.stream.bytes.CursorStreamProvider;
import org.mule.runtime.core.util.ByteUnit;

import java.io.InputStream;

public abstract class CursorStreamProviderFactory {

  private final int bufferSize;

  public CursorStreamProviderFactory(int maxInMemorySize, ByteUnit sizeUnit) {
    bufferSize = sizeUnit.toBytes(maxInMemorySize);
  }

  protected int getBufferSize() {
    return bufferSize;
  }

  public abstract CursorStreamProvider of(InputStream inputStream);
}
