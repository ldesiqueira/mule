/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import static java.lang.String.format;
import static java.nio.ByteBuffer.allocate;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.internal.stream.bytes.TempFileHelper.deleteAsync;
import org.mule.runtime.api.exception.MuleRuntimeException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FileStoreOffHeapMemory implements OffHeapMemory {

  private final File bufferFile;
  private final RandomAccessFile fileStore;
  private final Lock fileStoreLock = new ReentrantLock();

  public FileStoreOffHeapMemory() {
    bufferFile = TempFileHelper.createBufferFile("stream-buffer");
    try {
      fileStore = new RandomAccessFile(bufferFile, "rw");
    } catch (FileNotFoundException e) {
      throw new RuntimeException(format("Buffer file %s was just created but now it doesn't exist",
                                        bufferFile.getAbsolutePath()));
    }
  }

  public int get(ByteBuffer dest, StreamBuffer.Range requiredRange, int length) {
    return checkedRead(() -> {
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

  public boolean put(ByteBuffer buffer) {
    try {
      withFileLock(() -> fileStore.getChannel().write(buffer));
      return true;
    } catch (IOException e) {
      throw new MuleRuntimeException(createStaticMessage("Could not write in off-heap file store"), e);
    }
  }

  public int get(ByteBuffer dest) {
    return checkedRead(() -> withFileLock(() -> fileStore.getChannel().read(dest)));
  }

  private <T> T checkedRead(Callable<T> callable) {
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

  @Override
  public void release() {
    closeQuietly(fileStore);
    deleteAsync(bufferFile);
  }
}
