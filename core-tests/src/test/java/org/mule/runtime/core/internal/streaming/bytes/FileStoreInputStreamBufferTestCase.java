/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import static java.nio.ByteBuffer.allocate;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.internal.streaming.bytes.OffHeapMode.FILE_STORE;
import static org.mule.runtime.core.internal.streaming.bytes.OffHeapMode.NO_OFF_HEAP;
import org.mule.tck.size.SmallTest;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
@SmallTest
public class FileStoreInputStreamBufferTestCase extends AbstractByteStreamingTestCase {

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"No OffHeap", NO_OFF_HEAP},
        {"FileStore Off Heap", FILE_STORE}
    });
  }

  private final int bufferSize = KB_256;

  private FileStoreInputStreamBuffer buffer;

  public FileStoreInputStreamBufferTestCase(String name, OffHeapMode offHeapMode) {
    super(MB_2);
    buffer = new FileStoreInputStreamBuffer(new ByteArrayInputStream(data.getBytes()), bufferSize, offHeapMode);
  }

  @Test
  public void getSliceOfCurrentBufferSegment() throws Exception {
    final int position = bufferSize / 4;
    int len = (bufferSize / 2) - position;
    ByteBuffer dest = allocate(len);

    assertThat(buffer.get(dest, position, len), is(len));
    assertThat(toString(dest.array()), equalTo(data.substring(position, position + len)));
  }

  @Test
  public void getSliceWhichStartsInCurrentSegmentButEndsInTheNext() throws Exception {
    final int position = bufferSize - 10;
    final int len = bufferSize / 2;
    ByteBuffer dest = allocate(len);

    int totalRead = 0;
    int read;
    int readPosition = position;
    int remainingLen = len;

    do {
      read = buffer.get(dest, readPosition, remainingLen);
      if (read > 0) {
        totalRead += read;
        readPosition += read;
        remainingLen -= read;
      }
    } while (read > 0);

    assertThat(totalRead, is(len));
    assertThat(toString(dest.array()), equalTo(data.substring(position, position + len)));
  }
}
