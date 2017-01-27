package org.mule.runtime.core.internal.streaming.bytes;

import org.mule.runtime.api.streaming.CursorStream;
import org.mule.runtime.api.streaming.CursorStreamProvider;

public abstract class CursorStreamAdapter extends CursorStream {

  public abstract CursorStreamProvider getProvider();
}
