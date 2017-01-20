/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.stream.bytes;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.junit.rules.ExpectedException.none;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.io.ByteArrayInputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@SmallTest
public class ByteArrayCursorStreamProviderTestCase extends AbstractMuleTestCase {

  @Rule
  public ExpectedException expectedException = none();

  @Test
  public void dataBiggerThanBuffer() throws Exception {
    expectedException.expect(StreamMaximumSizeExceededException.class);

    final int bufferSize = 1024;
    String data = randomAlphanumeric(bufferSize);
    new ByteArrayCursorStreamProvider(new ByteArrayInputStream(data.getBytes()), bufferSize);
  }

}
