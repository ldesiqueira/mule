/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.api;


public interface OAuthContext {


  String authorizationHeader(String resourceOwnerId);

  /*
   * ClientCredentials methods
   */

  void refreshAccessToken(String clientId, String clientSecret, boolean encodeInBody, String scopes)
      throws TokenUrlResponseException, TokenNotFoundException;

  /**
   * AuthorizationCode methods
   */
  // Config:
  // responseAccessToken
  // responseRefreshToken
  // responseExpiresIn
  // parameterExtractors

  void refreshAccessToken(String clientId, String clientSecret, String externalCallbackUrl, String resourceOwnerId)
      throws NoRefreshTokenException, TokenUrlResponseException, TokenNotFoundException;
}
