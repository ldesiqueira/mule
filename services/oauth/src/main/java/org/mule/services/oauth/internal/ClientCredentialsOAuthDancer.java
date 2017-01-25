/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import static java.lang.String.format;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.services.oauth.internal.OAuthConstants.CLIENT_ID_PARAMETER;
import static org.mule.services.oauth.internal.OAuthConstants.CLIENT_SECRET_PARAMETER;
import static org.mule.services.oauth.internal.OAuthConstants.GRANT_TYPE_CLIENT_CREDENTIALS;
import static org.mule.services.oauth.internal.OAuthConstants.GRANT_TYPE_PARAMETER;
import static org.mule.services.oauth.internal.OAuthConstants.SCOPE_PARAMETER;
import static org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext.DEFAULT_RESOURCE_OWNER_ID;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.mule.runtime.oauth.api.ClientCredentialsConfig;
import org.mule.runtime.oauth.api.OAuthDancer;
import org.mule.runtime.oauth.api.exception.RequestAuthenticationException;
import org.mule.service.http.api.client.HttpClient;
import org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext;
import org.mule.services.oauth.internal.state.TokenResponse;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;


public class ClientCredentialsOAuthDancer extends AbstractOAuthDancer implements OAuthDancer {

  private static final Logger LOGGER = getLogger(ClientCredentialsOAuthDancer.class);

  private ClientCredentialsConfig config;

  public ClientCredentialsOAuthDancer(ClientCredentialsConfig config, Function<String, Lock> lockProvider,
                                      Map<String, ResourceOwnerOAuthContext> tokensStore, HttpClient httpClient,
                                      ExpressionManager expressionManager) {
    super(lockProvider, tokensStore, httpClient, expressionManager);
    this.config = config;
  }

  @Override
  public ClientCredentialsConfig getConfig() {
    return config;
  }

  @Override
  public String accessToken(String resourceOwner) throws RequestAuthenticationException {
    final String accessToken = getContextForResourceOwner(resourceOwner).getAccessToken();
    if (accessToken == null) {
      throw new RequestAuthenticationException(createStaticMessage(format("No access token found. "
          + "Verify that you have authenticated before trying to execute an operation to the API.")));
    }

    return accessToken;
  }

  @Override
  public void refreshTokenIfNeeded(Supplier<Boolean> refreshCondition, Charset encoding, TokenRefreshCallback callback) {
    if (refreshCondition.get()) {
      final Map<String, String> formData = new HashMap<>();

      formData.put(GRANT_TYPE_PARAMETER, GRANT_TYPE_CLIENT_CREDENTIALS);

      if (config.getScopes() != null) {
        formData.put(SCOPE_PARAMETER, config.getScopes());
      }

      String authorization = null;
      String clientId = config.getClientId();
      String clientSecret = config.getClientSecret();
      if (config.isEncodeClientCredentialsInBody()) {
        formData.put(CLIENT_ID_PARAMETER, clientId);
        formData.put(CLIENT_SECRET_PARAMETER, clientSecret);
      } else {
        authorization = "Basic " + encodeBase64String(format("%s:%s", clientId, clientSecret).getBytes());
      }

      // TODO properly handle exceptions
      TokenResponse tokenResponse = null;
      try {
        tokenResponse = invokeTokenUrl(config.getTokenUrl(), formData, authorization, false, encoding);
      } catch (MuleException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Retrieved access token, refresh token and expires from token url are: %s, %s, %s",
                     tokenResponse.getAccessToken(), tokenResponse.getRefreshToken(), tokenResponse.getExpiresIn());
      }

      if (tokenResponse.getAccessToken() == null) {
        // throw new TokenNotFoundException(/* tokenResponse */);
      }

      final ResourceOwnerOAuthContext defaultUserState = getContextForResourceOwner(DEFAULT_RESOURCE_OWNER_ID);
      defaultUserState.setAccessToken(tokenResponse.getAccessToken());
      defaultUserState.setExpiresIn(tokenResponse.getExpiresIn());
      final Map<String, Object> customResponseParameters = tokenResponse.getCustomResponseParameters();
      for (String paramName : customResponseParameters.keySet()) {
        defaultUserState.getTokenResponseParameters().put(paramName, customResponseParameters.get(paramName));
      }
      updateResourceOwnerOAuthContext(defaultUserState);

    }
  }

  @Override
  public void clearTokens(String resourceOwner) {
    // TODO Auto-generated method stub

  }

}
