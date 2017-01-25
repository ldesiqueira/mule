/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import static java.util.Collections.singletonMap;
import static org.mule.runtime.api.metadata.MediaType.ANY;
import static org.mule.runtime.api.metadata.MediaType.parse;

import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.mule.runtime.core.util.IOUtils;
import org.mule.runtime.core.util.MapUtils;
import org.mule.runtime.core.util.StringUtils;
import org.mule.runtime.oauth.api.AbstractOAuthConfig;
import org.mule.runtime.oauth.api.exception.TokenUrlResponseException;
import org.mule.service.http.api.client.HttpClient;
import org.mule.service.http.api.domain.ParameterMap;
import org.mule.service.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.service.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.service.http.api.domain.message.request.HttpRequest;
import org.mule.service.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.service.http.api.domain.message.response.HttpResponse;
import org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext;
import org.mule.services.oauth.internal.state.TokenResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

public abstract class AbstractOAuthDancer {

  private static final int TOKEN_REQUEST_TIMEOUT_MILLIS = 60000;

  private final Function<String, Lock> lockProvider;
  private final Map<String, ResourceOwnerOAuthContext> tokensStore;
  private final HttpClient httpClient;
  private final ExpressionManager expressionManager;

  protected AbstractOAuthDancer(Function<String, Lock> lockProvider, Map<String, ResourceOwnerOAuthContext> tokensStore,
                                HttpClient httpClient, ExpressionManager expressionManager) {
    this.lockProvider = lockProvider;
    this.tokensStore = tokensStore;
    this.httpClient = httpClient;
    this.expressionManager = expressionManager;
  }

  protected abstract AbstractOAuthConfig getConfig();

  protected TokenResponse invokeTokenUrl(String tokenUrl, Map<String, String> tokenRequestFormToSend, String authorization,
                                         boolean retrieveRefreshToken, Charset encoding)
      throws MuleException, TokenUrlResponseException {
    try {
      final HttpRequestBuilder requestBuilder = HttpRequest.builder()
          .setUri(tokenUrl).setMethod("POST")
          .setEntity(new ByteArrayHttpEntity(encodeString(encoding, tokenRequestFormToSend).getBytes()))
          .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED.toRfcString());

      if (authorization != null) {
        requestBuilder.addHeader(AUTHORIZATION, authorization);
      }

      // TODO MULE-11272 Support doing non-blocking requests
      final HttpResponse response = httpClient.send(requestBuilder.build(), TOKEN_REQUEST_TIMEOUT_MILLIS, true, null);

      MediaType responseContentType =
          response.getHeaderValueIgnoreCase(CONTENT_TYPE) != null ? parse(response.getHeaderValueIgnoreCase(CONTENT_TYPE)) : ANY;
      ParameterMap headers = new ParameterMap();
      for (String headerName : response.getHeaderNames()) {
        headers.put(headerName, response.getHeaderValues(headerName));
      }

      String readBody = IOUtils.toString(((InputStreamHttpEntity) response.getEntity()).getInputStream());
      Object body = readBody;
      if (responseContentType.withoutParameters().matches(APPLICATION_X_WWW_FORM_URLENCODED)) {
        body = decodeString(readBody, responseContentType.getCharset().orElse(encoding));
      }

      if (response.getStatusCode() >= 400) {
        throw new TokenUrlResponseException(tokenUrl);
      }

      TokenResponse tokenResponse = new TokenResponse();

      tokenResponse
          .setAccessToken(resolveExpression(getConfig().getResponseAccessTokenExpr(), body, headers, responseContentType));
//      if (tokenResponse.getAccessToken() == null) {
//        LOGGER.error("Could not extract access token from token URL. "
//            + "Expressions used to retrieve access token was " + responseAccessToken);
//      }
      if (retrieveRefreshToken) {
        tokenResponse
            .setRefreshToken(resolveExpression(getConfig().getResponseRefreshTokenExpr(), body, headers, responseContentType));
      }
      tokenResponse.setExpiresIn(resolveExpression(getConfig().getResponseExpiresInExpr(), body, headers, responseContentType));
      if (!MapUtils.isEmpty(getConfig().getCustomParamtersExprs())) {
        Map<String, Object> customParams = new HashMap<>();
        for (Entry<String, String> customParamExpr : getConfig().getCustomParamtersExprs().entrySet()) {
          customParams.put(customParamExpr.getKey(),
                           resolveExpression(customParamExpr.getValue(), body, headers, responseContentType));
        }
        tokenResponse.setCustomResponseParameters(customParams);
      }

      return tokenResponse;
    } catch (IOException e) {
      throw new TokenUrlResponseException(tokenUrl, e);
    } catch (TimeoutException e) {
      throw new TokenUrlResponseException(tokenUrl, e);
    }
  }

  private <T> T resolveExpression(String expr, Object body, ParameterMap headers, MediaType responseContentType) {
    if (expr == null) {
      return null;
    } else if (!expressionManager.isExpression(expr)) {
      return (T) expr;
    } else {
      BindingContext resultContext = BindingContext.builder()
          .addBinding("payload",
                      new TypedValue(body, DataType.builder().fromObject(body)
                          .mediaType(responseContentType).build()))
          .addBinding("attributes",
                      new TypedValue(singletonMap("headers", headers.toImmutableParameterMap()), DataType.fromType(Map.class)))
          .addBinding("dataType",
                      new TypedValue(DataType.builder().fromObject(body).mediaType(responseContentType)
                          .build(), DataType.fromType(DataType.class)))
          .build();

      return (T) expressionManager.evaluate(expr, resultContext).getValue();
    }
  }

  // TODO MULE-11283 Remove these
  public static final String CONTENT_TYPE = "Content-Type";
  public static final MediaType APPLICATION_X_WWW_FORM_URLENCODED = MediaType.create("application", "x-www-form-urlencoded");
  public static final String AUTHORIZATION = "Authorization";

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

  /**
   * Retrieves the oauth context for a particular user. If there's no state for that user a new state is retrieve so never returns
   * null.
   *
   * @param resourceOwnerId id of the user.
   * @return oauth state
   */
  protected ResourceOwnerOAuthContext getContextForResourceOwner(final String resourceOwnerId) {
    ResourceOwnerOAuthContext resourceOwnerOAuthContext = null;
    if (!tokensStore.containsKey(resourceOwnerId)) {
      final Lock lock = lockProvider.apply(toString() + "-config-oauth-context");
      lock.lock();
      try {
        if (!tokensStore.containsKey(resourceOwnerId)) {
          resourceOwnerOAuthContext = new ResourceOwnerOAuthContext(createLockForResourceOwner(resourceOwnerId), resourceOwnerId);
          tokensStore.put(resourceOwnerId, resourceOwnerOAuthContext);
        }
      } finally {
        lock.unlock();
      }
    }
    if (resourceOwnerOAuthContext == null) {
      resourceOwnerOAuthContext = tokensStore.get(resourceOwnerId);
      resourceOwnerOAuthContext.setRefreshUserOAuthContextLock(createLockForResourceOwner(resourceOwnerId));
    }
    return resourceOwnerOAuthContext;
  }

  private Lock createLockForResourceOwner(String resourceOwnerId) {
    return lockProvider.apply(toString() + "-" + resourceOwnerId);
  }

  /**
   * Updates the resource owner oauth context information
   *
   * @param resourceOwnerOAuthContext
   */
  protected void updateResourceOwnerOAuthContext(ResourceOwnerOAuthContext resourceOwnerOAuthContext) {
    final Lock resourceOwnerContextLock = resourceOwnerOAuthContext.getRefreshUserOAuthContextLock();
    resourceOwnerContextLock.lock();
    try {
      tokensStore.put(resourceOwnerOAuthContext.getResourceOwnerId(), resourceOwnerOAuthContext);
    } finally {
      resourceOwnerContextLock.unlock();
    }
  }

}
