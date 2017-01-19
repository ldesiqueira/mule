/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.api;

import org.mule.runtime.api.service.Service;
import org.mule.runtime.core.api.lock.LockFactory;
import org.mule.runtime.core.api.store.ListableObjectStore;

public interface OAuthService extends Service {

  // 2 methods: one for auth code, another for client credentials ?
  OAuthContext createOAuthContext(final LockFactory lockFactory, ListableObjectStore objectStore, final String configName);
}
