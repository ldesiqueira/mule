/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.oauth.api;

public class ClientCredentialsConfig {

  private final String clientId;
  private final String clientSecret;

  private final String tokenUrl;

  private final String scopes;
  private final boolean encodeClientCredentialsInBody;

  public static ClientCredentialsConfigBuilder builder(String clientId, String clientSecret, String tokenUrl) {
    return new ClientCredentialsConfigBuilder().clientId(clientId).clientSecret(clientSecret);
  }

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public String getTokenUrl() {
    return tokenUrl;
  }

  public String getScopes() {
    return scopes;
  }

  public boolean isEncodeClientCredentialsInBody() {
    return encodeClientCredentialsInBody;
  }

  private ClientCredentialsConfig(String clientId, String clientSecret, String tokenUrl, String scopes,
                                  boolean encodeClientCredentialsInBody) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenUrl = tokenUrl;
    this.scopes = scopes;
    this.encodeClientCredentialsInBody = encodeClientCredentialsInBody;
  }

  public static class ClientCredentialsConfigBuilder {

    private String clientId;
    private String clientSecret;

    private String tokenUrl;

    private String scopes = null;
    private boolean encodeClientCredentialsInBody = false;

    public ClientCredentialsConfigBuilder clientId(String clientId) {
      this.clientId = clientId;
      return this;
    }

    public ClientCredentialsConfigBuilder clientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
      return this;
    }

    public ClientCredentialsConfigBuilder tokenUrl(String tokenUrl) {
      this.tokenUrl = tokenUrl;
      return this;
    }

    public ClientCredentialsConfigBuilder scopes(String scopes) {
      this.scopes = scopes;
      return this;
    }

    public ClientCredentialsConfigBuilder encodeClientCredentialsInBody(boolean encodeClientCredentialsInBody) {
      this.encodeClientCredentialsInBody = encodeClientCredentialsInBody;
      return this;
    }

    public ClientCredentialsConfig build() {
      return new ClientCredentialsConfig(clientId, clientSecret, tokenUrl, scopes, encodeClientCredentialsInBody);
    }
  }

}
