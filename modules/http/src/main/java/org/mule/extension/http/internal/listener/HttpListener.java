/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.http.internal.listener;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.mule.extension.http.internal.HttpConnectorConstants.CONFIGURATION_OVERRIDES;
import static org.mule.extension.http.internal.HttpConnectorConstants.RESPONSE_SETTINGS;
import static org.mule.extension.http.internal.listener.HttpRequestToResult.transform;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.core.api.Event.setCurrentEvent;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.startIfNeeded;
import static org.mule.runtime.core.config.ExceptionHelper.getTransportErrorMapping;
import static org.mule.runtime.core.exception.Errors.ComponentIdentifiers.SECURITY;
import static org.mule.runtime.core.util.SystemUtils.getDefaultEncoding;
import static org.mule.runtime.extension.api.annotation.param.display.Placement.ADVANCED_TAB;
import static org.mule.service.http.api.HttpConstants.HttpStatus.BAD_REQUEST;
import static org.mule.service.http.api.HttpConstants.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mule.service.http.api.HttpConstants.Protocols.HTTP;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.extension.http.api.HttpStreamingType;
import org.mule.extension.http.api.listener.builder.HttpListenerErrorResponseBuilder;
import org.mule.extension.http.api.listener.builder.HttpListenerSuccessResponseBuilder;
import org.mule.extension.http.internal.HttpListenerMetadataResolver;
import org.mule.extension.http.internal.listener.server.HttpListenerConfig;
import org.mule.extension.http.internal.listener.server.ModuleRequestHandler;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.exception.ErrorTypeRepository;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.execution.OnError;
import org.mule.runtime.extension.api.annotation.execution.OnSuccess;
import org.mule.runtime.extension.api.annotation.metadata.MetadataScope;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.annotation.param.UseConfig;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.source.EmitsResponse;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.mule.runtime.extension.api.runtime.source.SourceCallbackContext;
import org.mule.service.http.api.HttpConstants;
import org.mule.runtime.module.http.internal.HttpMessageParsingException;
import org.mule.runtime.module.http.internal.HttpParser;
import org.mule.runtime.module.http.internal.listener.ListenerPath;
import org.mule.runtime.module.http.internal.listener.matcher.AcceptsAllMethodsRequestMatcher;
import org.mule.runtime.module.http.internal.listener.matcher.DefaultMethodRequestMatcher;
import org.mule.runtime.module.http.internal.listener.matcher.ListenerRequestMatcher;
import org.mule.service.http.api.domain.HttpProtocol;
import org.mule.service.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.service.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.api.domain.request.HttpRequestContext;
import org.mule.service.http.api.server.HttpServer;
import org.mule.service.http.api.server.MethodRequestMatcher;
import org.mule.service.http.api.server.RequestHandler;
import org.mule.service.http.api.server.RequestHandlerManager;
import org.mule.service.http.api.server.async.HttpResponseReadyCallback;
import org.mule.service.http.api.server.async.ResponseStatusCallback;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;

/**
 * Represents a listener for HTTP requests.
 *
 * @since 4.0
 */
@Alias("listener")
@MetadataScope(outputResolver = HttpListenerMetadataResolver.class)
@EmitsResponse
public class HttpListener extends Source<Object, HttpRequestAttributes> {

  private static final Logger LOGGER = getLogger(HttpListener.class);
  private static final String SERVER_PROBLEM = "Server encountered a problem";
  private static final String ERROR_RESPONSE_SETTINGS = "Error Response Settings";
  private static final String RESPONSE_CONTEXT = "responseContext";


  @Inject
  private MuleContext muleContext;

  @UseConfig
  private HttpListenerConfig config;

  @Connection
  private HttpServer server;

  /**
   * Relative path from the path set in the HTTP Listener configuration
   */
  @Parameter
  @Placement(order = 1)
  private String path;

  /**
   * Comma separated list of allowed HTTP methods by this listener. To allow all methods do not defined the attribute.
   */
  @Parameter
  @Optional
  @Placement(order = 2)
  private String allowedMethods;

  /**
   * By default, the request will be parsed (for example, a multi part request will be mapped as a Mule message with null payload
   * and inbound attachments with each part). If this property is set to false, no parsing will be done, and the payload will
   * always contain the raw contents of the HTTP request.
   */
  @ParameterGroup(name = CONFIGURATION_OVERRIDES)
  private ConfigurationOverrides configurationOverrides;

  /**
   * Defines if the response should be sent using streaming or not. If this attribute is not present, the behavior will depend on
   * the type of the payload (it will stream only for InputStream). If set to true, it will always stream. If set to false, it
   * will never stream. As streaming is done the response will be sent user Transfer-Encoding: chunked.
   */
  @Parameter
  @Optional(defaultValue = "AUTO")
  @Placement(tab = ADVANCED_TAB)
  private HttpStreamingType responseStreamingMode;

  private MethodRequestMatcher methodRequestMatcher = AcceptsAllMethodsRequestMatcher.instance();
  private HttpListenerResponseSender responseSender;
  private String[] parsedAllowedMethods;
  private ListenerPath listenerPath;
  private RequestHandlerManager requestHandlerManager;
  private HttpResponseFactory responseFactory;
  private List<ErrorType> knownErrors;
  private Boolean parseRequest;

  //TODO: MULE-10900 figure out a way to have a shared group between callbacks and possibly regular params
  @OnSuccess
  public void onSuccess(
                        @Optional @Placement(
                            tab = RESPONSE_SETTINGS) @NullSafe HttpListenerSuccessResponseBuilder response,
                        SourceCallbackContext callbackContext)
      throws Exception {

    HttpResponseContext context = callbackContext.getVariable(RESPONSE_CONTEXT);
    responseSender.sendResponse(context, response);
  }

  //TODO: MULE-10900 figure out a way to have a shared group between callbacks and possibly regular params
  @OnError
  public void onError(
                      @Optional @Placement(
                          tab = ERROR_RESPONSE_SETTINGS) @NullSafe HttpListenerErrorResponseBuilder errorResponse,
                      SourceCallbackContext callbackContext,
                      Error error) {
    // For now let's use the HTTP transport exception mapping since makes sense and the gateway depends on it.
    final HttpResponseBuilder failureResponseBuilder = createFailureResponseBuilder(error);

    if (errorResponse.getBody() == null) {
      errorResponse.setBody(error.getCause().getMessage());
    }

    HttpResponseContext context = callbackContext.getVariable("responseContext");

    HttpResponse response;
    try {
      response = responseFactory
          .create(failureResponseBuilder, errorResponse, context.isSupportStreaming());
    } catch (Exception e) {
      response = buildErrorResponse();
    }

    final HttpResponseReadyCallback responseCallback = context.getResponseCallback();
    responseCallback.responseReady(response, getResponseFailureCallback(responseCallback));
  }

  private HttpResponseBuilder createFailureResponseBuilder(Error error) {
    final HttpResponseBuilder failureResponseBuilder;
    if (hasCustomResponse(ofNullable(error))) {
      Message errorMessage = error.getErrorMessage();
      checkArgument(errorMessage.getAttributes() instanceof HttpResponseAttributes, "Error message must be HTTP compliant.");
      HttpResponseAttributes attributes = (HttpResponseAttributes) errorMessage.getAttributes();
      failureResponseBuilder = HttpResponse.builder()
          .setStatusCode(attributes.getStatusCode())
          .setReasonPhrase(attributes.getReasonPhrase());
      attributes.getHeaders().forEach(failureResponseBuilder::addHeader);
    } else if (error != null) {
      failureResponseBuilder = createDefaultFailureResponseBuilder(error.getCause());
    } else {
      failureResponseBuilder = HttpResponse.builder();
    }
    return failureResponseBuilder;
  }

  @Override
  public void onStart(SourceCallback<Object, HttpRequestAttributes> sourceCallback) throws MuleException {
    if (allowedMethods != null) {
      parsedAllowedMethods = extractAllowedMethods();
      methodRequestMatcher = new DefaultMethodRequestMatcher(parsedAllowedMethods);
    }

    path = HttpParser.sanitizePathWithStartSlash(path);
    listenerPath = config.getFullListenerPath(path);
    path = listenerPath.getResolvedPath();
    responseFactory =
        new HttpResponseFactory(responseStreamingMode, muleContext.getTransformationService());
    responseSender = new HttpListenerResponseSender(responseFactory);
    startIfNeeded(responseFactory);

    validatePath();
    this.parseRequest = config.resolveParseRequest(configurationOverrides.getParseRequest());
    try {
      requestHandlerManager =
          server.addRequestHandler(new ListenerRequestMatcher(methodRequestMatcher, path), getRequestHandler(sourceCallback));
    } catch (Exception e) {
      throw new MuleRuntimeException(e);
    }
    ErrorTypeRepository errorTypeRepository = muleContext.getErrorTypeRepository();
    java.util.Optional<ErrorType> errorType = errorTypeRepository.lookupErrorType(SECURITY);
    if (errorType.isPresent()) {
      knownErrors = Lists.newArrayList(errorType.get());
    } else {
      throw new MuleRuntimeException(createStaticMessage("Unable to find %s error in the Error Type Repository", SECURITY));
    }
    requestHandlerManager.start();
  }

  @Override
  public void onStop() {
    if (requestHandlerManager != null) {
      requestHandlerManager.stop();
      requestHandlerManager.dispose();
    }
  }

  private RequestHandler getRequestHandler(SourceCallback<Object, HttpRequestAttributes> sourceCallback) {
    return new ModuleRequestHandler() {

      @Override
      public Result<Object, HttpRequestAttributes> createResult(HttpRequestContext requestContext)
          throws HttpMessageParsingException {
        return HttpListener.this.createResult(requestContext);
      }

      @Override
      public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
        // TODO: MULE-9698 Analyse adding security here to reject the DefaultHttpRequestContext and avoid creating a Message
        try {
          HttpResponseContext responseContext = new HttpResponseContext();
          final String httpVersion = requestContext.getRequest().getProtocol().asString();
          responseContext.setHttpVersion(httpVersion);
          responseContext.setSupportStreaming(supportsTransferEncoding(httpVersion));
          responseContext.setResponseCallback(responseCallback);

          SourceCallbackContext context = sourceCallback.createContext();
          context.addVariable(RESPONSE_CONTEXT, responseContext);

          sourceCallback.handle(createResult(requestContext), context);
        } catch (HttpMessageParsingException | IllegalArgumentException e) {
          LOGGER.warn("Exception occurred parsing request:", e);
          sendErrorResponse(BAD_REQUEST, e.getMessage(), responseCallback);
        } catch (RuntimeException e) {
          LOGGER.warn("Exception occurred processing request:", e);
          sendErrorResponse(INTERNAL_SERVER_ERROR, SERVER_PROBLEM, responseCallback);
        } finally {
          setCurrentEvent(null);
        }
      }

      private void sendErrorResponse(final HttpConstants.HttpStatus status, String message,
                                     HttpResponseReadyCallback responseCallback) {
        responseCallback.responseReady(HttpResponse.builder()
            .setStatusCode(status.getStatusCode())
            .setReasonPhrase(status.getReasonPhrase())
            .setEntity(new ByteArrayHttpEntity(message.getBytes()))
            .build(), new ResponseStatusCallback() {

              @Override
              public void responseSendFailure(Throwable exception) {
                LOGGER.warn("Error while sending {} response {}", status.getStatusCode(), exception.getMessage());
                if (LOGGER.isDebugEnabled()) {
                  LOGGER.debug("Exception thrown", exception);
                }
              }

              @Override
              public void responseSendSuccessfully() {}
            });
      }
    };
  }

  private boolean hasCustomResponse(java.util.Optional<Error> error) {
    return error.isPresent() && knownErrors.contains(error.get().getErrorType()) && error.get().getErrorMessage() != null;
  }

  private HttpResponseBuilder createDefaultFailureResponseBuilder(Throwable exception) {
    String exceptionStatusCode = getTransportErrorMapping(HTTP.getScheme(), exception.getClass(), muleContext);
    Integer statusCodeFromException = exceptionStatusCode != null ? Integer.valueOf(exceptionStatusCode) : 500;
    return HttpResponse.builder().setStatusCode(statusCodeFromException).setReasonPhrase(exception.getMessage());
  }

  private Result<Object, HttpRequestAttributes> createResult(HttpRequestContext requestContext)
      throws HttpMessageParsingException {
    return transform(requestContext, getDefaultEncoding(muleContext), parseRequest, listenerPath);
    // TODO: MULE-9748 Analyse RequestContext use in HTTP extension
    // Update RequestContext ThreadLocal for backwards compatibility
    // setCurrentEvent(muleEvent);
    // return muleEvent;
  }

  protected HttpResponse buildResponse(HttpListenerSuccessResponseBuilder listenerResponseBuilder, boolean supportStreaming)
      throws Exception {
    HttpResponseBuilder responseBuilder = HttpResponse.builder();

    return doBuildResponse(responseBuilder, listenerResponseBuilder, supportStreaming);
  }

  protected HttpResponse doBuildResponse(HttpResponseBuilder responseBuilder,
                                         HttpListenerSuccessResponseBuilder listenerResponseBuilder,
                                         boolean supportsStreaming)
      throws Exception {
    return responseFactory.create(responseBuilder, listenerResponseBuilder, supportsStreaming);
  }

  protected HttpResponse buildErrorResponse() {
    final HttpResponseBuilder errorResponseBuilder = HttpResponse.builder();
    final HttpResponse errorResponse = errorResponseBuilder.setStatusCode(INTERNAL_SERVER_ERROR.getStatusCode())
        .setReasonPhrase(INTERNAL_SERVER_ERROR.getReasonPhrase())
        .build();
    return errorResponse;
  }

  private ResponseStatusCallback getResponseFailureCallback(HttpResponseReadyCallback responseReadyCallback) {
    return new ResponseStatusCallback() {

      @Override
      public void responseSendFailure(Throwable throwable) {
        responseReadyCallback.responseReady(buildErrorResponse(), this);
      }

      @Override
      public void responseSendSuccessfully() {
        // TODO: MULE-9749 Figure out how to handle this. Maybe doing nothing is right since this will be executed later if
        // everything goes right.
        // responseCompletationCallback.responseSentSuccessfully();
      }
    };
  }

  private boolean supportsTransferEncoding(String httpVersion) {
    return !(HttpProtocol.HTTP_0_9.asString().equals(httpVersion) || HttpProtocol.HTTP_1_0.asString().equals(httpVersion));
  }

  private String[] extractAllowedMethods() throws InitialisationException {
    final String[] values = this.allowedMethods.split(",");
    final String[] normalizedValues = new String[values.length];
    int normalizedValueIndex = 0;
    for (String value : values) {
      normalizedValues[normalizedValueIndex] = value.trim().toUpperCase();
      normalizedValueIndex++;
    }
    return normalizedValues;
  }

  private void validatePath() {
    final String[] pathParts = this.path.split("/");
    List<String> uriParamNames = new ArrayList<>();
    for (String pathPart : pathParts) {
      if (pathPart.startsWith("{") && pathPart.endsWith("}")) {
        String uriParamName = pathPart.substring(1, pathPart.length() - 1);
        if (uriParamNames.contains(uriParamName)) {
          // TODO: MULE-8946 This should throw a MuleException
          throw new MuleRuntimeException(
                                         createStaticMessage(format(
                                                                    "Http Listener with path %s contains duplicated uri param names",
                                                                    this.path)));
        }
        uriParamNames.add(uriParamName);
      } else {
        if (pathPart.contains("*") && pathPart.length() > 1) {
          // TODO: MULE-8946 This should throw a MuleException
          throw new MuleRuntimeException(createStaticMessage(format(
                                                                    "Http Listener with path %s contains an invalid use of a wildcard. Wildcards can only be used at the end of the path (i.e.: /path/*) or between / characters (.i.e.: /path/*/anotherPath))",
                                                                    this.path)));
        }
      }
    }
  }

  public Boolean getParseRequest() {
    return parseRequest;
  }
}
