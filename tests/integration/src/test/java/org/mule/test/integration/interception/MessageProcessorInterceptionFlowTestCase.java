/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.integration.interception;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.mule.runtime.api.dsl.config.ComponentIdentifier;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.core.api.config.ConfigurationException;
import org.mule.runtime.core.api.interception.MessageProcessorInterceptorCallback;
import org.mule.test.AbstractIntegrationTestCase;
import org.mule.test.runner.RunnerDelegateTo;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;
import static org.mule.functional.functional.FlowAssert.verify;
import static org.mule.runtime.api.dsl.DslConstants.CORE_NAMESPACE;

@RunnerDelegateTo(MockitoJUnitRunner.class)
public class MessageProcessorInterceptionFlowTestCase extends AbstractIntegrationTestCase {

  public static final String INTERCEPTED = "intercepted";
  public static final String EXPECTED_INTERCEPTED_MESSAGE = TEST_MESSAGE + " " + INTERCEPTED;

  private ComponentIdentifier loggerComponentIdentifier =
      new ComponentIdentifier.Builder().withNamespace(CORE_NAMESPACE).withName("logger").build();
  private ComponentIdentifier setPayloadComponentIdentifier =
      new ComponentIdentifier.Builder().withNamespace(CORE_NAMESPACE).withName("set-payload").build();
  private ComponentIdentifier fileReadComponentIdentifier =
      new ComponentIdentifier.Builder().withNamespace("file").withName("read").build();
  private ComponentIdentifier customInterceptorComponentIdentifier =
      new ComponentIdentifier.Builder().withNamespace(CORE_NAMESPACE).withName("custom-interceptor").build();

  @Mock
  private MessageProcessorInterceptorCallback interceptorCallback;

  @ClassRule
  public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Override
  protected String getConfigFile() {
    return "org/mule/test/integration/interception/interception-flow.xml";
  }

  @Override
  protected void addBuilders(List<ConfigurationBuilder> builders) {
    builders.add(new ConfigurationBuilder() {

      @Override
      public void configure(MuleContext muleContext) throws ConfigurationException {
        muleContext.getMessageProcessorInterceptorManager().setInterceptionCallback(interceptorCallback);
      }

      @Override
      public boolean isConfigured() {
        return true;
      }
    });
    super.addBuilders(builders);
  }

  @Test
  public void interceptLoggerMessageProcessor() throws Exception {
    when(interceptorCallback.before(any(ComponentIdentifier.class), any(Event.class), anyMap()))
        .then(invocation -> invocation.getArguments()[1]);
    when(interceptorCallback.shouldExecuteProcessor(argThat(not(equalTo(loggerComponentIdentifier))), any(Event.class),
                                                    anyMap())).thenReturn(true);
    when(interceptorCallback.shouldExecuteProcessor(argThat(equalTo(loggerComponentIdentifier)), any(Event.class), anyMap()))
        .thenReturn(false);
    when(interceptorCallback.getResult(argThat(equalTo(loggerComponentIdentifier)), any(Event.class), anyMap()))
        .then(getInterceptedMessage());
    when(interceptorCallback.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
        .then(invocation -> invocation.getArguments()[1]);

    String flow = "loggerProcessorFlow";
    flowRunner(flow).withVariable("expectedMessage", EXPECTED_INTERCEPTED_MESSAGE).withPayload(TEST_MESSAGE).run().getMessage();
    verify(flow);
    verifyInterceptor(true);
  }

  @Test
  public void interceptSetPayloadOnInnerFlowInterception() throws Exception {
    when(interceptorCallback.before(any(ComponentIdentifier.class), any(Event.class), anyMap()))
        .then(invocation -> invocation.getArguments()[1]);
    when(interceptorCallback.shouldExecuteProcessor(argThat(not(equalTo(setPayloadComponentIdentifier))), any(Event.class),
                                                    anyMap())).thenReturn(true);
    when(interceptorCallback.shouldExecuteProcessor(argThat(equalTo(setPayloadComponentIdentifier)), any(Event.class),
                                                    anyMap())).thenAnswer(invocation -> {
                                                      Map<String, Object> parameters =
                                                          (Map<String, Object>) invocation.getArguments()[2];
                                                      return !parameters.getOrDefault("value", "").equals("another");
                                                    });
    when(interceptorCallback.getResult(argThat(equalTo(setPayloadComponentIdentifier)), any(Event.class), anyMap()))
        .then(getInterceptedMessage());
    when(interceptorCallback.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
        .then(invocation -> invocation.getArguments()[1]);

    String flow = "flowWithInnerFlow";
    flowRunner(flow).withVariable("expectedMessage", "zaraza " + INTERCEPTED).run().getMessage();
    verify(flow);

    verifyInterceptor(true);
  }

  @Test
  public void interceptOperationMessageProcessor() throws Exception {
    final File root = temporaryFolder.getRoot();

    when(interceptorCallback.before(argThat(not(equalTo(fileReadComponentIdentifier))), any(Event.class), anyMap()))
        .then(invocation -> invocation.getArguments()[1]);
    when(interceptorCallback.before(argThat(equalTo(fileReadComponentIdentifier)), any(Event.class),
                                    (Map<String, Object>) argThat(hasEntry("path", (Object) root.getAbsolutePath()))))
                                        .then(invocation -> invocation.getArguments()[1]);
    when(interceptorCallback.shouldExecuteProcessor(argThat(not(equalTo(fileReadComponentIdentifier))), any(Event.class),
                                                    anyMap())).thenReturn(true);
    when(interceptorCallback.shouldExecuteProcessor(argThat(equalTo(fileReadComponentIdentifier)), any(Event.class), anyMap()))
        .thenReturn(false);
    when(interceptorCallback.getResult(argThat(equalTo(fileReadComponentIdentifier)), any(Event.class), anyMap()))
        .then(getInterceptedMessage());
    when(interceptorCallback.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
        .then(invocation -> invocation.getArguments()[1]);

    String flow = "operationProcessorFlow";
    flowRunner(flow).withVariable("expectedMessage", EXPECTED_INTERCEPTED_MESSAGE)
        .withVariable("source", root).withPayload(TEST_MESSAGE).run().getMessage();
    verify(flow);
    verifyInterceptor(true);
  }

  @Test
  public void interceptCustomInterceptorMessageProcessor() throws Exception {
    when(interceptorCallback.before(any(ComponentIdentifier.class), any(Event.class), anyMap()))
        .then(invocation -> invocation.getArguments()[1]);
    when(interceptorCallback.shouldExecuteProcessor(argThat(not(equalTo(customInterceptorComponentIdentifier))),
                                                    any(Event.class), anyMap())).thenReturn(true);
    when(interceptorCallback.shouldExecuteProcessor(argThat(equalTo(customInterceptorComponentIdentifier)), any(Event.class),
                                                    anyMap())).thenReturn(false);
    when(interceptorCallback.getResult(argThat(equalTo(customInterceptorComponentIdentifier)), any(Event.class), anyMap()))
        .then(getInterceptedMessage());
    when(interceptorCallback.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
        .then(invocation -> invocation.getArguments()[1]);

    String flow = "customInterceptorProcessorFlow";
    flowRunner(flow).withVariable("expectedMessage", EXPECTED_INTERCEPTED_MESSAGE).withPayload(TEST_MESSAGE).run().getMessage();
    verify(flow);
    verifyInterceptor(true);
  }

  @Test
  public void shouldExecuteCustomInterceptorMessageProcessor() throws Exception {
    when(interceptorCallback.before(any(ComponentIdentifier.class), any(Event.class), anyMap()))
        .then(invocation -> invocation.getArguments()[1]);
    when(interceptorCallback.shouldExecuteProcessor(any(ComponentIdentifier.class), any(Event.class), anyMap()))
        .thenReturn(true);
    when(interceptorCallback.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
        .then(invocation -> invocation.getArguments()[1]);

    String flow = "customInterceptorNotInvokedProcessorFlow";
    flowRunner(flow).withVariable("expectedMessage", TEST_MESSAGE + "!").withPayload(TEST_MESSAGE).run().getMessage();
    verify(flow);
    verifyInterceptor(false);
  }

  private Answer<Message> getInterceptedMessage() {
    return invocation -> {
      final Message response = Message.builder()
          .payload(((Message) invocation.getArguments()[1]).getPayload().getValue() + " " + INTERCEPTED)
          .build();
      return response;
    };
  }

  private void verifyInterceptor(boolean intercepted) throws MuleException {
    Mockito.verify(interceptorCallback, atLeast(1)).before(any(ComponentIdentifier.class), any(Event.class), anyMap());
    Mockito.verify(interceptorCallback, atLeast(1)).shouldExecuteProcessor(any(ComponentIdentifier.class), any(Event.class),
                                                                           anyMap());
    Mockito.verify(interceptorCallback, atMost(intercepted ? 1 : 0)).getResult(any(ComponentIdentifier.class), any(Event.class),
                                                                               anyMap());
    Mockito.verify(interceptorCallback, atLeast(1)).after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any());
  }

}
