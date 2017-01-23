/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext.DEFAULT_RESOURCE_OWNER_ID;

import org.mule.extension.oauth2.api.exception.TokenNotFoundException;
import org.mule.runtime.oauth.api.OAuthDancer;
import org.mule.runtime.oauth.api.exception.RequestAuthenticationException;
import org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext;
import org.mule.services.oauth.internal.state.TokenResponse;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;


public class ClientCredentialsOAuthDancer extends AbstractOAuthDancer implements OAuthDancer {

  public ClientCredentialsOAuthDancer(Function<String, Lock> lockProvider, Map<String, ResourceOwnerOAuthContext> tokensStore) {
    super(lockProvider, tokensStore);
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
  public void refreshTokenIfNeeded(Supplier<Boolean> refreshCondition, TokenRefreshCallback callback) {
    if (refreshCondition.get()) {
      TokenResponse tokenResponse = doRefreshAccessToken(false);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Retrieved access token, refresh token and expires from token url are: %s, %s, %s",
                     tokenResponse.getAccessToken(), tokenResponse.getRefreshToken(), tokenResponse.getExpiresIn());
      }

      if (tokenResponse.getAccessToken() == null) {
        throw new TokenNotFoundException(tokenResponse);
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
