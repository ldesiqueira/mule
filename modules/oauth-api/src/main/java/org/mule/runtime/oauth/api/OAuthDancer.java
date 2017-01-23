/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.oauth.api;

import org.mule.runtime.oauth.api.exception.RequestAuthenticationException;

import java.nio.charset.Charset;
import java.util.function.Supplier;

/**
 * Implementations provide OAuth dance implementations for a specific grant types:
 * <p>
 * Also allows for a limited tokens management.
 *
 * @since 4.0
 */
public interface OAuthDancer {

  /**
   * 
   * @param resourceOwner The resource owner to get the token for.
   * @return the token to send on the authorized request.
   */
  String accessToken(String resourceOwner) throws RequestAuthenticationException;

  /**
   * Performs the refresh of the access token in an non-blocking way, calling the corresponding method in the {@code callback}
   * when {@code refreshCondition} returns {@code true}.
   * <p>
   * If {@code refreshCondition} returns {@code false}, this method just returns without calling any method in the
   * {@code callback}.
   * 
   * @param refreshCondition
   * @param encoding
   * @param callback
   */
  void refreshTokenIfNeeded(Supplier<Boolean> refreshCondition, Charset encoding, TokenRefreshCallback callback);

  /**
   * Clears the OAuth context in this dancer for a resource owner id.
   * 
   * @param resourceOwner The resource owner to invalidate.
   */
  void clearTokens(String resourceOwner);

  // /**
  // * Creates the HTTP authorization and callback listeners used in the dance for the Authorization Code Grant Type.
  // */
  // void createAuthorizationGrantTypeListener();

  interface TokenRefreshCallback {

    void onSuccess();

    void onError();
  }
}
