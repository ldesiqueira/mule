/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import org.mule.services.oauth.api.OAuthService;


public class OAuthServiceImplementation implements OAuthService {

  @Override
  public String getName() {
    return "OAuthService";
  }

}
