/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.security;

import org.mule.runtime.api.security.Authentication;
import org.mule.runtime.api.security.SecurityContext;
import org.mule.runtime.extension.api.security.SecurityContextHandler;
import org.mule.runtime.api.security.SecurityException;
import org.mule.runtime.api.security.SecurityProviderNotFoundException;
import org.mule.runtime.api.security.UnknownAuthenticationTypeException;
import org.mule.runtime.core.api.MuleSession;
import org.mule.runtime.core.api.security.SecurityManager;
import org.mule.runtime.core.api.security.SecurityProvider;
import org.mule.runtime.core.security.MuleSecurityManager;

import java.util.List;

/**
 * //TODO
 *
 * @since 4.0
 */
public class DefaultSecurityContextHandler implements SecurityContextHandler {

  private final MuleSession session;
  private SecurityManager manager;

  public DefaultSecurityContextHandler(SecurityManager manager, MuleSession session) {
    this.session = session;
    this.manager = manager;
  }

  @Override
  public SecurityContext updateSecurityContext(List<String> securityProviders, Authentication authentication)
      throws SecurityProviderNotFoundException, SecurityException, UnknownAuthenticationTypeException {
    if (!securityProviders.isEmpty()) {
      addProviders(securityProviders);
    }

    Authentication authResult = manager.authenticate(authentication);

    SecurityContext context = manager.createSecurityContext(authResult);
    context.setAuthentication(authResult);
    session.setSecurityContext(context);
    return context;
  }

  private void addProviders(List<String> securityProviders) throws SecurityProviderNotFoundException {
    // This filter may only allow authentication on a subset of registered
    // security providers
    SecurityManager localManager = new MuleSecurityManager();
    for (String sp : securityProviders) {
      SecurityProvider provider = manager.getProvider(sp);
      if (provider != null) {
        localManager.addProvider(provider);
      } else {
        throw new SecurityProviderNotFoundException(sp);
      }
    }
    this.manager = localManager;
  }

  @Override
  public SecurityContext getSecurityContext() {
    return session.getSecurityContext();
  }
}
