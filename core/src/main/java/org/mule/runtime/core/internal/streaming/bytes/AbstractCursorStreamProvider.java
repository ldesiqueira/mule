/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import static java.util.Collections.newSetFromMap;
import static org.mule.runtime.api.util.Preconditions.checkState;
import org.mule.runtime.api.streaming.CursorStream;
import org.mule.runtime.api.streaming.CursorStreamProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractCursorStreamProvider implements CursorStreamProvider{

  protected final InputStream wrappedStream;
  protected final int bufferSize;
  private final Set<CursorStream> cursors = newSetFromMap(new ConcurrentHashMap<>());

  private AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * Creates a new instance
   *
   * @param wrappedStream the original stream to be decorated
   * @param bufferSize    the size in bytes of the buffer
   */
  public AbstractCursorStreamProvider(InputStream wrappedStream, int bufferSize) {
    this.wrappedStream = wrappedStream;
    this.bufferSize = bufferSize;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final CursorStream openCursor() {
    checkState(!closed.get(), "Cannot open a new cursor on a closed stream");
    CursorStream cursor = doOpenCursor();
    cursors.add(cursor);

    return new ManagedCursorStreamDecorator(cursor);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    closed.set(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isClosed() {
    return closed.get();
  }

  protected abstract void releaseResources();

  protected abstract CursorStream doOpenCursor();

  private void closeCursor(CursorStream cursor) {
    cursors.remove(cursor);
    if (closed.get() && allCursorsClosed()) {
      releaseResources();
    }
  }

  private boolean allCursorsClosed() {
    return cursors.isEmpty();
  }

  private class ManagedCursorStreamDecorator extends CursorStream {

    private final CursorStream delegate;

    private ManagedCursorStreamDecorator(CursorStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void close() throws IOException {
      try {
        delegate.close();
      } finally {
        closeCursor(delegate);
      }
    }

    @Override
    public long getPosition() {
      return delegate.getPosition();
    }

    @Override
    public void seek(long position) throws IOException {
      delegate.seek(position);
    }

    @Override
    public boolean isClosed() {
      return delegate.isClosed();
    }

    @Override
    public int read() throws IOException {
      return delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
      return delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
      return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
      return delegate.available();
    }

    @Override
    public void mark(int readlimit) {
      delegate.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
      delegate.reset();
    }

    @Override
    public boolean markSupported() {
      return delegate.markSupported();
    }
  }
}
