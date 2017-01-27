package org.mule.runtime.core.internal.streaming.bytes;

import java.nio.ByteBuffer;

/**
 * A buffer which provides concurrent random access to the entirety
 * of a dataset.
 * <p>
 * It works with the concept of a zero-base position. Each position
 * represents one byte in the stream. Although this buffer tracks the
 * position of each byte, it doesn't have a position itself. That means
 * that pulling data from this buffer does not make any current position
 * to be moved.
 *
 * @since 4.0
 */
public interface InputStreamBuffer {

  int get(ByteBuffer destination, long position, int length);

  void close();
}
