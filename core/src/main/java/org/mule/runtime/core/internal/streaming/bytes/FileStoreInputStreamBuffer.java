/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import static java.lang.String.format;
import static java.nio.ByteBuffer.allocate;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.internal.streaming.bytes.TempFileHelper.deleteAsync;
import org.mule.runtime.api.exception.MuleRuntimeException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link InputStreamBuffer} which is capable of handling datasets larger than this buffer's size. It works by keeping
 * an in-memory buffer which holds as many information as possible. When information which is ahead
 * of the buffer's current position is requested then the following happens:
 * <p>
 * <ul>
 * <li>The contents of the buffer are written into a temporal file</li>
 * <li>The buffer is cleared</li>
 * <li>The stream is consumed until the buffer is full again or the stream reaches its end</li>
 * <li>If the required data is still ahead of the buffer, then the process is repeated until the data is reached or the stream
 * fully consumed</li>
 * </ul>
 * <p>
 * Another possible scenario, is one in which the data requested is behind the buffer's current position, in which case
 * the data is obtained by reading the temporal file.
 * <p>
 * In either case, what's really important to understand is that the buffer is <b>ALWAYS</b> moving forward. The buffer
 * will never go back and reload data from the temporal file. It only gets data from the stream.
 *
 * @since 4.0
 */
public final class FileStoreInputStreamBuffer extends AbstractInputStreamBuffer {

  private final File bufferFile;
  private final RandomAccessFile fileStore;
  private final Lock fileStoreLock = new ReentrantLock();

  public FileStoreInputStreamBuffer(InputStream stream, int bufferSize) {
    super(stream, bufferSize);
    bufferFile = TempFileHelper.createBufferFile("stream-buffer");
    try {
      fileStore = new RandomAccessFile(bufferFile, "rw");
    } catch (FileNotFoundException e) {
      throw new RuntimeException(format("Buffer file %s was just created but now it doesn't exist",
                                        bufferFile.getAbsolutePath()));
    }
  }

  @Override
  protected int getBackwardsData(ByteBuffer dest, Range requiredRange, int length) {
    releaseBufferLock();
    return checked(() -> {
      // why did I need this buffer?
      ByteBuffer buffer = allocate(length);
      return withFileLock(() -> {
        int read = fileStore.getChannel().read(buffer, requiredRange.start);
        if (read > 0) {
          buffer.flip();
          dest.put(buffer);
        }
        return read;
      });
    });
  }

  @Override
  protected int consumeForwardData(ByteBuffer buffer) throws IOException {
    buffer.clear();
    int result = reloadFromFileStore(buffer);

    if (result > 0) {
      buffer.flip();
      return result;
    }

    result = loadFromStream(buffer);

    if (result >= 0) {
      buffer.flip();
      if (persistInFileStore(buffer)) {
        buffer.flip();
      }
    }

    return result;
  }

  private Integer reloadFromFileStore(ByteBuffer buffer) {
    return checked(() -> withFileLock(() -> fileStore.getChannel().read(buffer)));
  }

  private boolean persistInFileStore(ByteBuffer buffer) {
    try {
      withFileLock(() -> fileStore.getChannel().write(buffer));
      return true;
    } catch (IOException e) {
      throw new MuleRuntimeException(createStaticMessage("Could not write in off-heap file store"), e);
    }
  }

  private <T> T checked(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      throw new MuleRuntimeException(createStaticMessage("Could not read from file store"), e);
    }
  }

  private <T> T withFileLock(Callable<T> callable) throws IOException {
    fileStoreLock.lock();
    try {
      return callable.call();
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new MuleRuntimeException(e);
    } finally {
      fileStoreLock.unlock();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void doClose() {
    closeQuietly(fileStore);
    deleteAsync(bufferFile);
  }
}
