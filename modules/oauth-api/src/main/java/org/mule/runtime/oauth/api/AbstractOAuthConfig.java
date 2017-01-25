package org.mule.runtime.oauth.api;

import java.util.Map;

public class AbstractOAuthConfig {

  private String responseAccessTokenExpr;
  private String responseRefreshTokenExpr;
  private String responseExpiresInExpr;
  private Map<String, String> customParamtersExprs;

  public String getResponseAccessTokenExpr() {
    return responseAccessTokenExpr;
  }

  public String getResponseRefreshTokenExpr() {
    return responseRefreshTokenExpr;
  }

  public String getResponseExpiresInExpr() {
    return responseExpiresInExpr;
  }

  public Map<String, String> getCustomParamtersExprs() {
    return customParamtersExprs;
  }
}
