/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

import org.mule.runtime.oauth.api.OAuthDancer;
import org.mule.runtime.oauth.api.exception.RequestAuthenticationException;
import org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext;

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
      
    }
  }

  @Override
  public void clearTokens(String resourceOwner) {
    // TODO Auto-generated method stub

  }

}
