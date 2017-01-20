/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import org.mule.runtime.oauth.api.OAuthCallbackServersManager;
import org.mule.runtime.oauth.api.OAuthService;
import org.mule.service.http.api.HttpService;


public class OAuthServiceImplementation implements OAuthService {

  private HttpService httpService;

  public OAuthServiceImplementation(HttpService httpService) {
    this.httpService = httpService;
  }

  @Override
  public String getName() {
    return "OAuthService";
  }

  @Override
  public OAuthCallbackServersManager getServersManager() {
    return new DefaultOAuthCallbackServersManager(httpService);
  }
}
