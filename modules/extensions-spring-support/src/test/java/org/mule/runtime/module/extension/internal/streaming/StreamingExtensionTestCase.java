/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.streaming;

import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.mule.functional.junit4.ExtensionFunctionalTestCase;
import org.mule.runtime.api.streaming.CursorStream;
import org.mule.runtime.api.streaming.CursorStreamProvider;
import org.mule.runtime.core.api.stream.bytes.CursorStreamProviderFactory;
import org.mule.test.marvel.MarvelExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

public class StreamingExtensionTestCase extends ExtensionFunctionalTestCase {

  private CursorStreamProviderFactory cursorStreamProviderFactory;
  private String data = randomAlphabetic(2048);
  private CursorStreamProvider cursorStreamProvider = null;
  private List<CursorStream> openCursors = new LinkedList<>();

  @Override
  protected Class<?>[] getAnnotatedExtensionClasses() {
    return new Class<?>[]{MarvelExtension.class};
  }

  @Override
  protected String getConfigFile() {
    return "streaming-extension-config.xml";
  }

  @Override
  protected void doSetUp() throws Exception {
    cursorStreamProviderFactory = CursorStreamProviderFactory.createDefault();
  }

  @Override
  protected void doTearDown() throws Exception {
    if (cursorStreamProvider != null) {
      assertProviderAndCursorClosed();
    }
  }

  @Test
  public void readBytesFromCursorProvider() throws Exception {
    CursorStreamProvider cursorStreamProvider = getCursorStreamProvider();
    String read = (String) flowRunner("readStream").withPayload(cursorStreamProvider).run().getMessage().getPayload().getValue();
    assertThat(read, is(data));
  }

  private CursorStreamProvider getCursorStreamProvider() {
    if (cursorStreamProvider == null) {
      cursorStreamProvider = new TestCursorStreamProviderWrapper(cursorStreamProviderFactory.of(getTestStream()).getLeft());
    }

    return cursorStreamProvider;
  }

  private InputStream getTestStream() {
    return new ByteArrayInputStream(data.getBytes());
  }

  private void assertProviderAndCursorClosed() {
    try {
      int openCount = 0;
      for (CursorStream cursor : openCursors) {
        if (!cursor.isClosed()) {
          openCount++;
        }

        assertThat("Cursor leak!", openCount, is(0));
        assertThat(cursorStreamProvider.isClosed(), is(true));
      }
    } finally {
      cursorStreamProvider.close();
    }
  }

  public class TestCursorStreamProviderWrapper implements CursorStreamProvider {
    private CursorStreamProvider delegate;

    public TestCursorStreamProviderWrapper(CursorStreamProvider delegate) {
      this.delegate = delegate;
    }

    @Override
    public CursorStream openCursor() {
      CursorStream cursor = delegate.openCursor();
      openCursors.add(cursor);

      return cursor;
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public boolean isClosed() {
      return delegate.isClosed();
    }
  }
}
