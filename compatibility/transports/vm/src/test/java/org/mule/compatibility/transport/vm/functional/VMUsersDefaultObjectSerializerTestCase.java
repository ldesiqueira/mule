/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.compatibility.transport.vm.functional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import org.mule.functional.extensions.CompatibilityFunctionalTestCase;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.message.InternalMessage;
import org.mule.runtime.core.api.serialization.ObjectSerializer;
import org.mule.runtime.core.api.serialization.SerializationProtocol;
import org.mule.runtime.core.config.builders.SimpleConfigurationBuilder;
import org.mule.runtime.core.api.serialization.JavaObjectSerializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class VMUsersDefaultObjectSerializerTestCase extends CompatibilityFunctionalTestCase {

  private ObjectSerializer objectSerializer;

  @Override
  protected void addBuilders(List<ConfigurationBuilder> builders) {
    super.addBuilders(builders);

    objectSerializer = new CustomObjectSerializer();

    Map<String, Object> serializerMap = new HashMap<>();
    serializerMap.put("customSerializer", objectSerializer);
    builders.add(0, new SimpleConfigurationBuilder(serializerMap));
  }

  @Override
  protected String getConfigFile() {
    return "vm/vm-uses-default-object-serializer-test-flow.xml";
  }

  @Test
  public void serializeWithKryo() throws Exception {
    final String payload = "payload";
    flowRunner("dispatch").withPayload(payload).run();

    InternalMessage response = muleContext.getClient().request("vm://in", 5000).getRight().get();
    assertThat(response, is(notNullValue()));
    assertThat(getPayloadAsString(response), is(payload));

    ArgumentCaptor<InternalMessage> messageArgumentCaptor = ArgumentCaptor.forClass(InternalMessage.class);
    verify(objectSerializer.getInternalProtocol(), atLeastOnce()).serialize(messageArgumentCaptor.capture());
    InternalMessage capturedMessage = messageArgumentCaptor.getValue();
    assertThat(capturedMessage, is(notNullValue()));
    assertThat(getPayloadAsString(capturedMessage), is(payload));

    verify(objectSerializer.getInternalProtocol(), atLeastOnce()).deserialize(any(byte[].class));
  }

  private static class CustomObjectSerializer implements ObjectSerializer, MuleContextAware {

    private final JavaObjectSerializer objectSerializer;
    private SerializationProtocol internalProtocol;
    private SerializationProtocol externalProtocol;

    public CustomObjectSerializer() {
      objectSerializer = new JavaObjectSerializer();
    }

    @Override
    public void setMuleContext(MuleContext context) {
      objectSerializer.setMuleContext(context);
    }

    @Override
    public SerializationProtocol getInternalProtocol() {
      if (internalProtocol == null) {
        internalProtocol = spy(objectSerializer.getInternalProtocol());
      }

      return internalProtocol;
    }

    @Override
    public SerializationProtocol getExternalProtocol() {
      if (externalProtocol == null) {
        externalProtocol = spy(objectSerializer.getExternalProtocol());
      }
      return externalProtocol;
    }
  }

}
