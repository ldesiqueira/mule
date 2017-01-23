/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import org.mule.runtime.oauth.api.OAuthDancer;
import org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;


public class AuthorizationCodeOAuthDancer extends AbstractOAuthDancer implements OAuthDancer {

  public AuthorizationCodeOAuthDancer(Function<String, Lock> lockProvider, Map<String, ResourceOwnerOAuthContext> tokensStore) {
    super(lockProvider, tokensStore);
  }

  @Override
  public String accessToken(String resourceOwner) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void refreshTokenIfNeeded(Supplier<Boolean> refreshCondition, TokenRefreshCallback callback) {
    // TODO Auto-generated method stub

  }

  @Override
  public void clearTokens(String resourceOwner) {
    // TODO Auto-generated method stub

  }

}
