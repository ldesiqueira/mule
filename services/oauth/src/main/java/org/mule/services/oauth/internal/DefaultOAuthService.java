/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import org.mule.runtime.oauth.api.ClientCredentialsConfig;
import org.mule.runtime.oauth.api.OAuthDancer;
import org.mule.runtime.oauth.api.OAuthHttpListenersServersManager;
import org.mule.runtime.oauth.api.OAuthService;
import org.mule.service.http.api.HttpService;
import org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;


public class DefaultOAuthService implements OAuthService {

  private HttpService httpService;

  public DefaultOAuthService(HttpService httpService) {
    this.httpService = httpService;
  }

  @Override
  public String getName() {
    return "OAuthService";
  }

  @Override
  public <T> OAuthDancer createClientCredentialsGrantTypeDancer(ClientCredentialsConfig config,
                                                                Function<String, Lock> lockProvider,
                                                                Map<String, T> tokensStore) {
    return new ClientCredentialsOAuthDancer(config, lockProvider, (Map<String, ResourceOwnerOAuthContext>) tokensStore);
  }

  @Override
  public <T> OAuthDancer createAuthorizationCodeGrantTypeDancer(Function<String, Lock> lockProvider,
                                                                Map<String, T> tokensStore) {
    return new AuthorizationCodeOAuthDancer(lockProvider, (Map<String, ResourceOwnerOAuthContext>) tokensStore);
  }

  @Override
  public OAuthHttpListenersServersManager getServersManager() {
    return new DefaultOAuthCallbackServersManager(httpService);
  }
}
