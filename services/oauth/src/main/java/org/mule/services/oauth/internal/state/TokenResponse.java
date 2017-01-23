/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal.state;

import java.util.HashMap;
import java.util.Map;

public class TokenResponse {

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