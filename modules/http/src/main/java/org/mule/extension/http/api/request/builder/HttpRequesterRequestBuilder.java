/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.http.api.request.builder;

import static java.util.Collections.unmodifiableMap;
import static java.util.Optional.of;
import static org.mule.runtime.api.metadata.MediaType.ANY;
import org.mule.extension.http.api.HttpMessageBuilder;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.runtime.operation.ParameterResolver;

import java.util.HashMap;
import java.util.Map;

/**
 * Component that specifies how to create a proper HTTP request.
 *
 * @since 4.0
 */
public class HttpRequesterRequestBuilder extends HttpMessageBuilder {

  /**
   * URI parameters that should be used to create the request.
   */
  @Parameter
  @Optional
  @Content
  @DisplayName("URI Parameters")
  private Map<String, String> uriParams = new HashMap<>();

  /**
   * Query parameters the request should include.
   */
  @Parameter
  @Optional
  @Content
  @DisplayName("Query Parameters")
  private Map<String, String> queryParams = new HashMap<>();

  /**
   * The mediaType of the body to respond
   */
  //TODO: MULE-10877 this should be replaced by having body as a TypedValue
  @Parameter
  @Optional(defaultValue = "#[mel:message.dataType.mediaType]")
  private ParameterResolver<MediaType> mediaType = new ParameterResolver<MediaType>() {

    @Override
    public MediaType resolve() {
      return ANY;
    }

    @Override
    public java.util.Optional<String> getExpression() {
      return of("#['*/*']");
    }
  };


  // For now, only handle single params
  public String replaceUriParams(String path) {
    for (String uriParamName : uriParams.keySet()) {
      String uriParamValue = uriParams.get(uriParamName);

      if (uriParamValue == null) {
        throw new NullPointerException(String.format("Expression {%s} evaluated to null.", uriParamName));
      }

      path = path.replaceAll(String.format("\\{%s\\}", uriParamName), uriParamValue);
    }
    return path;
  }

  public Map<String, String> getQueryParams() {
    return unmodifiableMap(queryParams);
  }

  public Map<String, String> getUriParams() {
    return unmodifiableMap(uriParams);
  }

  public void setQueryParams(Map<String, String> queryParams) {
    this.queryParams = queryParams;
  }

  public void setUriParams(Map<String, String> uriParams) {
    this.uriParams = uriParams;
  }

  @Override
  public MediaType getMediaType() {
    return mediaType.resolve();
  }
}
