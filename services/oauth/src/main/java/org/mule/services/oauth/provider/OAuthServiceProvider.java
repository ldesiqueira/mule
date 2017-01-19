/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.provider;

import static java.util.Collections.singletonList;

import org.mule.runtime.api.service.ServiceDefinition;
import org.mule.runtime.api.service.ServiceProvider;
import org.mule.services.oauth.api.OAuthService;
import org.mule.services.oauth.internal.OAuthServiceImplementation;

import java.util.List;

public class OAuthServiceProvider implements ServiceProvider {

  private OAuthServiceImplementation service = new OAuthServiceImplementation();
  private ServiceDefinition serviceDefinition = new ServiceDefinition(OAuthService.class, service);

  @Override
  public List<ServiceDefinition> providedServices() {
    return singletonList(serviceDefinition);
  }
}
