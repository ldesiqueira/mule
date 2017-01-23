/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.integration.interception;

import static org.junit.Assert.fail;
import static org.mule.functional.functional.FlowAssert.verify;
import static org.mule.runtime.api.dsl.DslConstants.CORE_NAMESPACE;
import org.mule.runtime.api.dsl.config.ComponentIdentifier;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.core.api.config.ConfigurationException;
import org.mule.runtime.core.api.interception.InterceptionCallback;
import org.mule.runtime.core.api.interception.InterceptionCallbackResult;
import org.mule.runtime.core.api.interception.InterceptionHandler;
import org.mule.test.AbstractIntegrationTestCase;
import org.mule.test.runner.RunnerDelegateTo;

import java.util.List;
import java.util.Map;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.runners.MockitoJUnitRunner;

@RunnerDelegateTo(MockitoJUnitRunner.class)
public class MessageProcessorInterceptionFlowTestCase extends AbstractIntegrationTestCase {

  public static final String INTERCEPTED = "intercepted";
  public static final String EXPECTED_INTERCEPTED_MESSAGE = TEST_MESSAGE + " " + INTERCEPTED;
  public static final String INPUT_MESSAGE = "inputMessage";
  public static final String EXPECTED_MESSAGE = "expectedMessage";

  private ComponentIdentifier loggerComponentIdentifier =
      new ComponentIdentifier.Builder().withNamespace(CORE_NAMESPACE).withName("logger").build();
  private ComponentIdentifier setPayloadComponentIdentifier =
      new ComponentIdentifier.Builder().withNamespace(CORE_NAMESPACE).withName("set-payload").build();
  private ComponentIdentifier fileReadComponentIdentifier =
      new ComponentIdentifier.Builder().withNamespace("file").withName("read").build();
  private ComponentIdentifier customInterceptorComponentIdentifier =
      new ComponentIdentifier.Builder().withNamespace(CORE_NAMESPACE).withName("custom-interceptor").build();

  private InterceptionHandlerHolder firstInterceptionHandlerHolder = new InterceptionHandlerHolder();
  private InterceptionHandlerHolder secondInterceptionHandlerHolder = new InterceptionHandlerHolder();

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
        muleContext.getMessageProcessorInterceptorManager().addInterceptionHandler(firstInterceptionHandlerHolder);
        muleContext.getMessageProcessorInterceptorManager().addInterceptionHandler(secondInterceptionHandlerHolder);
      }

      @Override
      public boolean isConfigured() {
        return true;
      }
    });
    super.addBuilders(builders);
  }

  @Test
  public void interceptSetPayloadMessageProcessor() throws Exception {
    InterceptionHandler handler = (componentIdentifier, event, eventBuilder, parameters, callback) -> callback.skipProcessor(eventBuilder.message(getInterceptedMessage(TEST_MESSAGE)).build());
    firstInterceptionHandlerHolder.setHandler(handler);
    String flow = "setPayloadFlow";
    flowRunner(flow).withVariable(EXPECTED_MESSAGE, EXPECTED_INTERCEPTED_MESSAGE).withVariable(INPUT_MESSAGE, "mock").run().getMessage();
    verify(flow);
  }

  @Test
  public void doNotInterceptSetPayloadMessageProcessor() throws Exception {
    InterceptionHandler handler = (componentIdentifier, event, eventBuilder, parameters, callback) -> callback.nextProcessor(event);
    firstInterceptionHandlerHolder.setHandler(handler);
    secondInterceptionHandlerHolder.setHandler(new NoOpInterceptionHandler());
    String flow = "setPayloadFlow";
    flowRunner(flow).withVariable(EXPECTED_MESSAGE, TEST_MESSAGE).withVariable(INPUT_MESSAGE, TEST_MESSAGE).run().getMessage();
    verify(flow);
  }

  @Test
  public void interceptSetPayloadMessageProcessorSkipOnFirstInterceptor() throws Exception {
    InterceptionHandler handler = (componentIdentifier, event, eventBuilder, parameters, callback) -> callback.skipProcessor(eventBuilder.message(getInterceptedMessage(TEST_MESSAGE)).build());
    firstInterceptionHandlerHolder.setHandler(handler);
    secondInterceptionHandlerHolder.setHandler((componentIdentifier, event, eventBuilder, parameters, callback) -> {
      fail("Should not be executed");
      return null;
    });

    String flow = "setPayloadFlow";
    flowRunner(flow).withVariable(EXPECTED_MESSAGE, EXPECTED_INTERCEPTED_MESSAGE).withVariable(INPUT_MESSAGE, "mock").run().getMessage();
    verify(flow);
  }

  @Test
  public void interceptSetPayloadMessageProcessorSkipOnSecondInterceptor() throws Exception {
    InterceptionHandler handler = (componentIdentifier, event, eventBuilder, parameters, callback) ->
        callback.nextProcessor(eventBuilder.message(getInterceptedMessage(event.getVariable(INPUT_MESSAGE).getValue())).build());
    firstInterceptionHandlerHolder.setHandler(handler);
    secondInterceptionHandlerHolder.setHandler((componentIdentifier, event, eventBuilder, parameters, callback) -> callback.skipProcessor(event));

    String flow = "setPayloadFlow";
    flowRunner(flow).withVariable(EXPECTED_MESSAGE, EXPECTED_INTERCEPTED_MESSAGE).withVariable(INPUT_MESSAGE, TEST_MESSAGE).run().getMessage();
    verify(flow);
  }

  @Test
  public void interceptSetPayloadMessageProcessorBothContinue() throws Exception {
    InterceptionHandler handler = (componentIdentifier, event, eventBuilder, parameters, callback) ->
        callback.nextProcessor(eventBuilder.addVariable(INPUT_MESSAGE, TEST_MESSAGE + " " + INTERCEPTED).build());
    firstInterceptionHandlerHolder.setHandler(handler);
    secondInterceptionHandlerHolder.setHandler((componentIdentifier, event, eventBuilder, parameters, callback) ->
        callback.nextProcessor(eventBuilder.addVariable(INPUT_MESSAGE, event.getVariable(INPUT_MESSAGE).getValue() + " " + INTERCEPTED).build()));

    String flow = "setPayloadFlow";
    flowRunner(flow).withVariable(EXPECTED_MESSAGE, EXPECTED_INTERCEPTED_MESSAGE + " " + INTERCEPTED).withVariable(INPUT_MESSAGE, "mock").run().getMessage();
    verify(flow);
  }

  //@Test
  //public void interceptSetPayloadOnInnerFlowInterception() throws Exception {
  //  when(interceptionHandler.before(any(ComponentIdentifier.class), any(Event.class), anyMap()))
  //      .then(invocation -> invocation.getArguments()[1]);
  //  when(interceptionHandler.shouldExecuteProcessor(argThat(not(equalTo(setPayloadComponentIdentifier))), any(Event.class),
  //                                                  anyMap())).thenReturn(true);
  //  when(interceptionHandler.shouldExecuteProcessor(argThat(equalTo(setPayloadComponentIdentifier)), any(Event.class),
  //                                                  anyMap())).thenAnswer(invocation -> {
  //                                                    Map<String, Object> parameters =
  //                                                        (Map<String, Object>) invocation.getArguments()[2];
  //                                                    return !parameters.getOrDefault("value", "").equals("another");
  //                                                  });
  //  when(interceptionHandler.getResult(argThat(equalTo(setPayloadComponentIdentifier)), any(Event.class), anyMap()))
  //      .then(getInterceptedMessage());
  //  when(interceptionHandler.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
  //      .then(invocation -> invocation.getArguments()[1]);
  //
  //  String flow = "flowWithInnerFlow";
  //  flowRunner(flow).withVariable("expectedMessage", "zaraza " + INTERCEPTED).run().getMessage();
  //  verify(flow);
  //
  //  verifyInterceptor(true);
  //}
  //
  //@Test
  //public void interceptOperationMessageProcessor() throws Exception {
  //  final File root = temporaryFolder.getRoot();
  //
  //  when(interceptionHandler.before(argThat(not(equalTo(fileReadComponentIdentifier))), any(Event.class), anyMap()))
  //      .then(invocation -> invocation.getArguments()[1]);
  //  when(interceptionHandler.before(argThat(equalTo(fileReadComponentIdentifier)), any(Event.class),
  //                                  (Map<String, Object>) argThat(hasEntry("path", (Object) root.getAbsolutePath()))))
  //                                      .then(invocation -> invocation.getArguments()[1]);
  //  when(interceptionHandler.shouldExecuteProcessor(argThat(not(equalTo(fileReadComponentIdentifier))), any(Event.class),
  //                                                  anyMap())).thenReturn(true);
  //  when(interceptionHandler.shouldExecuteProcessor(argThat(equalTo(fileReadComponentIdentifier)), any(Event.class), anyMap()))
  //      .thenReturn(false);
  //  when(interceptionHandler.getResult(argThat(equalTo(fileReadComponentIdentifier)), any(Event.class), anyMap()))
  //      .then(getInterceptedMessage());
  //  when(interceptionHandler.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
  //      .then(invocation -> invocation.getArguments()[1]);
  //
  //  String flow = "operationProcessorFlow";
  //  flowRunner(flow).withVariable("expectedMessage", EXPECTED_INTERCEPTED_MESSAGE)
  //      .withVariable("source", root).withPayload(TEST_MESSAGE).run().getMessage();
  //  verify(flow);
  //  verifyInterceptor(true);
  //}
  //
  //@Test
  //public void interceptCustomInterceptorMessageProcessor() throws Exception {
  //  when(interceptionHandler.before(any(ComponentIdentifier.class), any(Event.class), anyMap()))
  //      .then(invocation -> invocation.getArguments()[1]);
  //  when(interceptionHandler.shouldExecuteProcessor(argThat(not(equalTo(customInterceptorComponentIdentifier))),
  //                                                  any(Event.class), anyMap())).thenReturn(true);
  //  when(interceptionHandler.shouldExecuteProcessor(argThat(equalTo(customInterceptorComponentIdentifier)), any(Event.class),
  //                                                  anyMap())).thenReturn(false);
  //  when(interceptionHandler.getResult(argThat(equalTo(customInterceptorComponentIdentifier)), any(Event.class), anyMap()))
  //      .then(getInterceptedMessage());
  //  when(interceptionHandler.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
  //      .then(invocation -> invocation.getArguments()[1]);
  //
  //  String flow = "customInterceptorProcessorFlow";
  //  flowRunner(flow).withVariable("expectedMessage", EXPECTED_INTERCEPTED_MESSAGE).withPayload(TEST_MESSAGE).run().getMessage();
  //  verify(flow);
  //  verifyInterceptor(true);
  //}
  //
  //@Test
  //public void shouldExecuteCustomInterceptorMessageProcessor() throws Exception {
  //  when(interceptionHandler.before(any(ComponentIdentifier.class), any(Event.class), anyMap()))
  //      .then(invocation -> invocation.getArguments()[1]);
  //  when(interceptionHandler.shouldExecuteProcessor(any(ComponentIdentifier.class), any(Event.class), anyMap()))
  //      .thenReturn(true);
  //  when(interceptionHandler.after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any()))
  //      .then(invocation -> invocation.getArguments()[1]);
  //
  //  String flow = "customInterceptorNotInvokedProcessorFlow";
  //  flowRunner(flow).withVariable("expectedMessage", TEST_MESSAGE + "!").withPayload(TEST_MESSAGE).run().getMessage();
  //  verify(flow);
  //  verifyInterceptor(false);
  //}

  private Message getInterceptedMessage(Object value) {
    return Message.builder().payload(value + " " + INTERCEPTED).build();
  }

  //private void verifyInterceptor(boolean intercepted) throws MuleException {
  //  Mockito.verify(interceptionHandler, atLeast(1)).before(any(ComponentIdentifier.class), any(Event.class), anyMap());
  //  Mockito.verify(interceptionHandler, atLeast(1)).shouldExecuteProcessor(any(ComponentIdentifier.class), any(Event.class),
  //                                                                         anyMap());
  //  Mockito.verify(interceptionHandler, atMost(intercepted ? 1 : 0)).getResult(any(ComponentIdentifier.class), any(Event.class),
  //                                                                             anyMap());
  //  Mockito.verify(interceptionHandler, atLeast(1)).after(any(ComponentIdentifier.class), any(Event.class), anyMap(), any());
  //}

  private class InterceptionHandlerHolder implements InterceptionHandler {

    private InterceptionHandler handler;

    public void setHandler(InterceptionHandler handler) {
      this.handler = handler;
    }

    @Override
    public InterceptionCallbackResult before(ComponentIdentifier componentIdentifier, Event event, Event.Builder eventBuilder,
                                             Map<String, Object> parameters, InterceptionCallback callback) {
      return handler.before(componentIdentifier, event, eventBuilder, parameters, callback);
    }

    @Override
    public void after(Event event) {
      handler.after(event);
    }

  }


  private class NoOpInterceptionHandler implements InterceptionHandler {

    @Override
    public InterceptionCallbackResult before(ComponentIdentifier componentIdentifier, Event event, Event.Builder eventBuilder,
                                             Map<String, Object> parameters, InterceptionCallback callback) {
      return null;
    }
  }
}
