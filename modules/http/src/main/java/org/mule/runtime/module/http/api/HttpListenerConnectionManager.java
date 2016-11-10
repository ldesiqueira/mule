/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.http.api;


import static org.mule.runtime.core.util.SystemUtils.getDefaultEncoding;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.context.WorkManagerSource;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.core.config.i18n.CoreMessages;
import org.mule.runtime.core.execution.MessageProcessingManager;
import org.mule.runtime.core.policy.PolicyManager;
import org.mule.runtime.module.http.internal.listener.HttpListenerRegistry;
import org.mule.runtime.module.http.internal.listener.HttpServerManager;
import org.mule.runtime.module.http.internal.listener.Server;
import org.mule.runtime.module.http.internal.listener.ServerAddress;
import org.mule.runtime.module.http.internal.listener.grizzly.GrizzlyServerManager;
import org.mule.compatibility.transport.socket.api.TcpServerSocketProperties;
import org.mule.compatibility.transport.socket.internal.DefaultTcpServerSocketProperties;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.core.util.concurrent.ThreadNameHelper;

import com.google.common.collect.Iterables;

import java.io.IOException;
import java.util.Collection;

import javax.inject.Inject;

public class HttpListenerConnectionManager implements Initialisable, Disposable, MuleContextAware {

  public static final String HTTP_LISTENER_CONNECTION_MANAGER = "_httpListenerConnectionManager";
  public static final String SERVER_ALREADY_EXISTS_FORMAT =
      "A server in port(%s) already exists for ip(%s) or one overlapping it (0.0.0.0).";
  private static final String LISTENER_THREAD_NAME_PREFIX = "http.listener";

  private HttpListenerRegistry httpListenerRegistry;
  private HttpServerManager httpServerManager;

  private MuleContext muleContext;

  @Inject
  private PolicyManager policyManager;
  @Inject
  private MessageProcessingManager messageProcessingManager;

  @Override
  public void initialise() throws InitialisationException {
    httpListenerRegistry = new HttpListenerRegistry(getDefaultEncoding(muleContext), muleContext.getTransformationService(),
                                                    messageProcessingManager);
    Collection<TcpServerSocketProperties> tcpServerSocketPropertiesBeans =
        muleContext.getRegistry().lookupObjects(TcpServerSocketProperties.class);
    TcpServerSocketProperties tcpServerSocketProperties = new DefaultTcpServerSocketProperties();

    if (tcpServerSocketPropertiesBeans.size() == 1) {
      tcpServerSocketProperties = Iterables.getOnlyElement(tcpServerSocketPropertiesBeans);
    } else if (tcpServerSocketPropertiesBeans.size() > 1) {
      throw new InitialisationException(CoreMessages
          .createStaticMessage("Only one global TCP server socket properties bean should be defined in the config"), this);
    }

    String threadNamePrefix = ThreadNameHelper.getPrefix(muleContext) + LISTENER_THREAD_NAME_PREFIX;
    try {
      httpServerManager =
          new GrizzlyServerManager(threadNamePrefix, httpListenerRegistry, tcpServerSocketProperties, policyManager);
    } catch (IOException e) {
      throw new InitialisationException(e, this);
    }

  }

  @Override
  public synchronized void dispose() {
    httpServerManager.dispose();
  }

  @Override
  public void setMuleContext(MuleContext muleContext) {
    this.muleContext = muleContext;
  }

  public Server createServer(ServerAddress serverAddress, WorkManagerSource workManagerSource, boolean usePersistentConnections,
                             int connectionIdleTimeout) {
    if (!containsServerFor(serverAddress)) {
      try {
        return httpServerManager.createServerFor(serverAddress, workManagerSource, usePersistentConnections,
                                                 connectionIdleTimeout);
      } catch (IOException e) {
        throw new MuleRuntimeException(e);
      }
    } else {
      throw new MuleRuntimeException(CoreMessages
          .createStaticMessage(String.format(SERVER_ALREADY_EXISTS_FORMAT, serverAddress.getPort(), serverAddress.getIp())));
    }
  }

  public boolean containsServerFor(ServerAddress serverAddress) {
    return httpServerManager.containsServerFor(serverAddress);
  }

  public Server createSslServer(ServerAddress serverAddress, WorkManagerSource workManagerSource, TlsContextFactory tlsContext,
                                boolean usePersistentConnections, int connectionIdleTimeout) {
    if (!containsServerFor(serverAddress)) {
      try {
        return httpServerManager.createSslServerFor(tlsContext, workManagerSource, serverAddress, usePersistentConnections,
                                                    connectionIdleTimeout);
      } catch (IOException e) {
        throw new MuleRuntimeException(e);
      }
    } else {
      throw new MuleRuntimeException(CoreMessages
          .createStaticMessage(String.format(SERVER_ALREADY_EXISTS_FORMAT, serverAddress.getPort(), serverAddress.getIp())));
    }
  }

}