/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.oauth.internal;

import org.mule.services.oauth.internal.state.ResourceOwnerOAuthContext;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

public abstract class AbstractOAuthDancer {

  private final Function<String, Lock> lockProvider;
  private final Map<String, ResourceOwnerOAuthContext> tokensStore;

  protected AbstractOAuthDancer(Function<String, Lock> lockProvider, Map<String, ResourceOwnerOAuthContext> tokensStore) {
    this.lockProvider = lockProvider;
    this.tokensStore = tokensStore;
  }

  /**
   * Retrieves the oauth context for a particular user. If there's no state for that user a new state is retrieve so never returns
   * null.
   *
   * @param resourceOwnerId id of the user.
   * @return oauth state
   */
  protected ResourceOwnerOAuthContext getContextForResourceOwner(final String resourceOwnerId) {
    ResourceOwnerOAuthContext resourceOwnerOAuthContext = null;
    if (!tokensStore.containsKey(resourceOwnerId)) {
      final Lock lock = lockProvider.apply(toString() + "-config-oauth-context");
      lock.lock();
      try {
        if (!tokensStore.containsKey(resourceOwnerId)) {
          resourceOwnerOAuthContext = new ResourceOwnerOAuthContext(createLockForResourceOwner(resourceOwnerId), resourceOwnerId);
          tokensStore.put(resourceOwnerId, resourceOwnerOAuthContext);
        }
      } finally {
        lock.unlock();
      }
    }
    if (resourceOwnerOAuthContext == null) {
      resourceOwnerOAuthContext = tokensStore.get(resourceOwnerId);
      resourceOwnerOAuthContext.setRefreshUserOAuthContextLock(createLockForResourceOwner(resourceOwnerId));
    }
    return resourceOwnerOAuthContext;
  }

  private Lock createLockForResourceOwner(String resourceOwnerId) {
    return lockProvider.apply(toString() + "-" + resourceOwnerId);
  }

  /**
   * Updates the resource owner oauth context information
   *
   * @param resourceOwnerOAuthContext
   */
  protected void updateResourceOwnerOAuthContext(ResourceOwnerOAuthContext resourceOwnerOAuthContext) {
    final Lock resourceOwnerContextLock = resourceOwnerOAuthContext.getRefreshUserOAuthContextLock();
    resourceOwnerContextLock.lock();
    try {
      tokensStore.put(resourceOwnerOAuthContext.getResourceOwnerId(), resourceOwnerOAuthContext);
    } finally {
      resourceOwnerContextLock.unlock();
    }
  }

}
