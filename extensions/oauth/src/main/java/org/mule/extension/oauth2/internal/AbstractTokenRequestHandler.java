/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.oauth2.internal;

import static java.lang.String.format;
import static org.mule.extension.http.api.HttpConstants.HttpStatus.BAD_REQUEST;
import static org.mule.extension.http.api.HttpConstants.Methods.POST;
import static org.mule.extension.http.api.HttpHeaders.Names.AUTHORIZATION;
import static org.mule.extension.oauth2.api.exception.OAuthErrors.TOKEN_URL_FAIL;
import static org.mule.extension.oauth2.internal.OAuthConstants.ACCESS_TOKEN_EXPRESSION;
import static org.mule.extension.oauth2.internal.OAuthConstants.EXPIRATION_TIME_EXPRESSION;
import static org.mule.extension.oauth2.internal.OAuthConstants.REFRESH_TOKEN_EXPRESSION;
import static org.mule.runtime.api.metadata.MediaType.ANY;
import static org.mule.runtime.api.metadata.MediaType.parse;
import static org.mule.runtime.core.util.SystemUtils.getDefaultEncoding;
import static org.mule.runtime.core.util.concurrent.ThreadNameHelper.getPrefix;
import static org.mule.runtime.module.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.module.http.api.HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.registry.RegistrationException;
import org.mule.runtime.core.util.CollectionUtils;
import org.mule.runtime.core.util.IOUtils;
import org.mule.runtime.core.util.StringUtils;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.mule.runtime.extension.api.runtime.operation.ParameterResolver;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.operation.Result.Builder;
import org.mule.service.http.api.HttpService;
import org.mule.service.http.api.client.HttpClient;
import org.mule.service.http.api.client.HttpClientConfiguration;
import org.mule.service.http.api.domain.ParameterMap;
import org.mule.service.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.service.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.service.http.api.domain.message.request.HttpRequest;
import org.mule.service.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.service.http.api.domain.message.response.HttpResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;

//TODO MULE-11412 Remove MuleContextAware
public abstract class AbstractTokenRequestHandler implements Initialisable, Startable, Stoppable, MuleContextAware {

  private static final Logger LOGGER = getLogger(AbstractTokenRequestHandler.class);

  // TODO MULE-11412 Add @Inject
  protected MuleContext muleContext;
  private DeferredExpressionResolver resolver;

  // TODO MULE-11412 Add @Inject
  private HttpService httpService;

  /**
   * MEL expression to extract the access token parameter from the response of the call to tokenUrl.
   */
  @Parameter
  @Optional(defaultValue = ACCESS_TOKEN_EXPRESSION)
  protected ParameterResolver<String> responseAccessToken;

  @Parameter
  @Optional(defaultValue = REFRESH_TOKEN_EXPRESSION)
  protected ParameterResolver<String> responseRefreshToken;

  /**
   * MEL expression to extract the expiresIn parameter from the response of the call to tokenUrl.
   */
  @Parameter
  @Optional(defaultValue = EXPIRATION_TIME_EXPRESSION)
  protected ParameterResolver<String> responseExpiresIn;

  @Parameter
  @Alias("custom-parameter-extractors")
  @Optional
  protected List<ParameterExtractor> parameterExtractors;

  /**
   * After executing an API call authenticated with OAuth it may be that the access token used was expired, so this attribute
   * allows a MEL expressions that will be evaluated against the http response of the API callback to determine if the request
   * failed because it was done using an expired token. In case the evaluation returns true (access token expired) then mule will
   * automatically trigger a refresh token flow and retry the API callback using a new access token. Default value evaluates if
   * the response status code was 401 or 403.
   */
  @Parameter
  @Optional(defaultValue = "#[attributes.statusCode == 401 or attributes.statusCode == 403]")
  private ParameterResolver<Boolean> refreshTokenWhen;

  /**
   * The oauth authentication server url to get access to the token. Mule, after receiving the authentication code from the oauth
   * server (through the redirectUrl) will call this url to get the access token.
   */
  @Parameter
  private String tokenUrl;

  private TlsContextFactory tlsContextFactory;

  private HttpClient client;

  private static final int TOKEN_REQUEST_TIMEOUT_MILLIS = 60000;

  public ParameterResolver<Boolean> getRefreshTokenWhen() {
    return refreshTokenWhen;
  }

  protected MuleContext getMuleContext() {
    return muleContext;
  }

  public void setTokenUrl(String tokenUrl) {
    this.tokenUrl = tokenUrl;
  }

  public void setTlsContextFactory(final TlsContextFactory tlsContextFactory) {
    this.tlsContextFactory = tlsContextFactory;
  }

  protected TokenResponse invokeTokenUrl(Map<String, String> tokenRequestFormToSend, String authorization,
                                         boolean retrieveRefreshToken)
      throws MuleException, TokenUrlResponseException {
    try {
      final Charset encoding = MediaType.ANY.getCharset().orElse(getDefaultEncoding(muleContext));
      final HttpRequestBuilder requestBuilder = HttpRequest.builder()
          .setUri(tokenUrl).setMethod(POST.name())
          .setEntity(new ByteArrayHttpEntity(encodeString(encoding, tokenRequestFormToSend).getBytes()))
          .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED.toRfcString());

      if (authorization != null) {
        requestBuilder.addHeader(AUTHORIZATION, authorization);
      }

      // TODO MULE-11272 Support doing non-blocking requests
      final HttpResponse response = client.send(requestBuilder.build(), TOKEN_REQUEST_TIMEOUT_MILLIS, true, null);

      MediaType responseContentType =
          response.getHeaderValueIgnoreCase(CONTENT_TYPE) != null ? parse(response.getHeaderValueIgnoreCase(CONTENT_TYPE)) : ANY;
      ParameterMap headers = new ParameterMap();
      for (String headerName : response.getHeaderNames()) {
        headers.put(headerName, response.getHeaderValues(headerName));
      }

      final Builder<Object, HttpResponseAttributes> responseBuilder =
          Result.<Object, HttpResponseAttributes>builder().mediaType(responseContentType)
              .attributes(new HttpResponseAttributes(response.getStatusCode(), response.getReasonPhrase(), headers));

      final String readBody = IOUtils.toString(((InputStreamHttpEntity) response.getEntity()).getInputStream());
      if (responseContentType.withoutParameters().matches(APPLICATION_X_WWW_FORM_URLENCODED)) {
        responseBuilder.output(decodeString(readBody, responseContentType.getCharset().orElse(getDefaultEncoding(muleContext))));
      } else {
        responseBuilder.output(readBody);
      }

      if (response.getStatusCode() >= BAD_REQUEST.getStatusCode()) {
        throw new TokenUrlResponseException(getTokenUrl(), responseBuilder.build());
      }
      return processTokenResponse(responseBuilder.build(), retrieveRefreshToken);
    } catch (IOException e) {
      throw new TokenUrlResponseException(e, getTokenUrl());
    } catch (TimeoutException e) {
      throw new TokenUrlResponseException(e, getTokenUrl());
    }
  }

  // TODO MULE-11283 Remove this
  private static String encodeString(Charset encoding, Map parameters) {
    String body;
    StringBuilder result = new StringBuilder();
    for (Map.Entry<?, ?> entry : (Set<Map.Entry<?, ?>>) ((parameters).entrySet())) {
      String paramName = entry.getKey().toString();
      Object paramValue = entry.getValue();

      Iterable paramValues = paramValue instanceof Iterable ? (Iterable) paramValue : Arrays.asList(paramValue);
      for (Object value : paramValues) {
        try {
          paramName = URLEncoder.encode(paramName, encoding.name());
          paramValue = value != null ? URLEncoder.encode(value.toString(), encoding.name()) : null;
        } catch (UnsupportedEncodingException e) {
          throw new MuleRuntimeException(e);
        }

        if (result.length() > 0) {
          result.append("&");
        }
        result.append(paramName);
        if (paramValue != null) {
          // Allowing parameters name with no value assigned
          result.append("=");
          result.append(paramValue);
        }
      }
    }

    body = result.toString();
    return body;
  }

  // TODO MULE-11283 Remove this
  private static ParameterMap decodeString(String encodedString, Charset encoding) {
    ParameterMap queryParams = new ParameterMap();
    if (!StringUtils.isBlank(encodedString)) {
      String[] pairs = encodedString.split("&");
      for (String pair : pairs) {
        int idx = pair.indexOf("=");

        if (idx != -1) {
          addParam(queryParams, pair.substring(0, idx), pair.substring(idx + 1), encoding);
        } else {
          addParam(queryParams, pair, null, encoding);

        }
      }
    }
    return queryParams;
  }

  private static void addParam(ParameterMap queryParams, String name, String value, Charset encoding) {
    queryParams.put(decode(name, encoding), decode(value, encoding));
  }

  private static String decode(String text, Charset encoding) {
    if (text == null) {
      return null;
    }
    try {
      return URLDecoder.decode(text, encoding.name());
    } catch (UnsupportedEncodingException e) {
      throw new MuleRuntimeException(e);
    }
  }

  protected String getTokenUrl() {
    return tokenUrl;
  }

  public TokenResponse processTokenResponse(Result<Object, HttpResponseAttributes> tokenUrlResponse,
                                            boolean retrieveRefreshToken) {
    TokenResponse response = new TokenResponse();

    response.accessToken = resolver.resolveExpression(responseAccessToken, tokenUrlResponse);
    response.accessToken = isEmpty(response.accessToken) ? null : response.accessToken;
    if (response.accessToken == null) {
      LOGGER.error("Could not extract access token from token URL. "
          + "Expressions used to retrieve access token was " + responseAccessToken);
    }
    if (retrieveRefreshToken) {
      response.refreshToken = resolver.resolveExpression(responseRefreshToken, tokenUrlResponse);
      response.refreshToken = isEmpty(response.refreshToken) ? null : response.refreshToken;
    }
    response.expiresIn = resolver.resolveExpression(responseExpiresIn, tokenUrlResponse);
    if (!CollectionUtils.isEmpty(parameterExtractors)) {
      for (ParameterExtractor parameterExtractor : parameterExtractors) {
        response.customResponseParameters.put(parameterExtractor.getParamName(),
                                              resolver.resolveExpression(parameterExtractor.getValue(), tokenUrlResponse));
      }
    }

    return response;
  }

  protected boolean tokenResponseContentIsValid(TokenResponse response) {
    return response.getAccessToken() != null;
  }

  protected boolean isEmpty(String value) {
    return value == null || org.mule.runtime.core.util.StringUtils.isEmpty(value) || "null".equals(value);
  }

  protected class TokenUrlResponseException extends ModuleException {

    private static final long serialVersionUID = -570499835977961241L;

    private Object tokenUrlResponse;

    public TokenUrlResponseException(String tokenUrl, Result<Object, HttpResponseAttributes> build) {
      super(format("HTTP response from token URL %s returned a failure status code", tokenUrl), TOKEN_URL_FAIL);
      this.tokenUrlResponse = build.getOutput();
    }

    public TokenUrlResponseException(final Exception cause, String tokenUrl) {
      super(cause, TOKEN_URL_FAIL, format("Exception when calling token URL %s", tokenUrl));
    }

    public Object getTokenUrlResponse() {
      return tokenUrlResponse;
    }
  }

  public static class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String expiresIn;
    private Map<String, Object> customResponseParameters = new HashMap<>();

    public String getAccessToken() {
      return accessToken;
    }

    public String getRefreshToken() {
      return refreshToken;
    }

    public String getExpiresIn() {
      return expiresIn;
    }

    public Map<String, Object> getCustomResponseParameters() {
      return customResponseParameters;
    }
  }

  @Override
  public void initialise() throws InitialisationException {
    try {
      this.httpService = muleContext.getRegistry().lookupObject(HttpService.class);
    } catch (RegistrationException e) {
      throw new InitialisationException(e, this);
    }

    String threadNamePrefix = format("%soauthToken.requester", getPrefix(muleContext));
    HttpClientConfiguration clientConfiguration = new HttpClientConfiguration.Builder()
        .setTlsContextFactory(tlsContextFactory)
        .setThreadNamePrefix(threadNamePrefix)
        .build();

    client = httpService.getClientFactory().create(clientConfiguration);
  }

  @Override
  public void start() {
    client.start();
  }

  @Override
  public void stop() {
    client.stop();
  }

  @Override
  public void setMuleContext(MuleContext context) {
    this.muleContext = context;
    this.resolver = new DeferredExpressionResolver(context);
  }
}
