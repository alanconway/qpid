/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.handler;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.AMQChannel;
import org.apache.commons.configuration.Configuration;

public class QueueDeclareHandler implements StateAwareMethodListener<QueueDeclareBody>
{
    private static final Logger _log = Logger.getLogger(QueueDeclareHandler.class);

    private static final QueueDeclareHandler _instance = new QueueDeclareHandler();

    public static QueueDeclareHandler getInstance()
    {
        return _instance;
    }

    @Configured(path = "queue.auto_register", defaultValue = "false")
    public boolean autoRegister;

    private final AtomicInteger _counter = new AtomicInteger();


    protected QueueDeclareHandler()
    {
        Configurator.configure(this);
    }

    public void methodReceived(AMQStateManager stateManager, QueueDeclareBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();
        MessageStore store = virtualHost.getMessageStore();




        final AMQShortString queueName;

        // if we aren't given a queue name, we create one which we return to the client

        if (body.getQueue() == null)
        {
            queueName = createName();
        }
        else
        {
            queueName = body.getQueue().intern();
        }

        AMQQueue queue;
        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?

        synchronized (queueRegistry)
        {



            if (((queue = queueRegistry.getQueue(queueName)) == null))
            {

                if (body.getPassive())
                {
                    String msg = "Queue: " + queueName + " not found on VirtualHost(" + virtualHost + ").";
                    throw body.getChannelException(AMQConstant.NOT_FOUND, msg);
                }
                else
                {
                    queue = createQueue(body, virtualHost, session);
                    if (queue.isDurable() && !queue.isAutoDelete())
                    {
                        store.createQueue(queue);
                    }
                    queueRegistry.registerQueue(queue);
                    if (autoRegister)
                    {
                        Exchange defaultExchange = exchangeRegistry.getDefaultExchange();

                        queue.bind(queueName, null, defaultExchange);
                        _log.info("Queue " + queueName + " bound to default exchange(" + defaultExchange.getName() + ")");
                    }
                }
            }
            else if (queue.getOwner() != null && !session.getContextKey().equals(queue.getOwner()))
            {
                throw body.getChannelException(AMQConstant.ALREADY_EXISTS, "Cannot declare queue('" + queueName + "'),"
                                                                           + " as exclusive queue with same name "
                                                                           + "declared on another client ID('"
                                                                           + queue.getOwner() + "')");
            }

            AMQChannel channel = session.getChannel(channelId);

            if (channel == null)
            {
                throw body.getChannelNotFoundException(channelId);
            }

            //set this as the default queue on the channel:
            channel.setDefaultQueue(queue);
        }

        if (!body.getNowait())
        {
            MethodRegistry methodRegistry = session.getMethodRegistry();
            QueueDeclareOkBody responseBody =
                    methodRegistry.createQueueDeclareOkBody(queueName,
                                                            queue.getMessageCount(),
                                                            queue.getConsumerCount());
            session.writeFrame(responseBody.generateFrame(channelId));

            _log.info("Queue " + queueName + " declared successfully");
        }
    }

    protected AMQShortString createName()
    {
        return new AMQShortString("tmp_" + UUID.randomUUID());
    }

    protected AMQQueue createQueue(QueueDeclareBody body, VirtualHost virtualHost, final AMQProtocolSession session)
            throws AMQException
    {
        final QueueRegistry registry = virtualHost.getQueueRegistry();
        AMQShortString owner = body.getExclusive() ? session.getContextKey() : null;
        final AMQQueue queue = new AMQQueue(body.getQueue(), body.getDurable(), owner, body.getAutoDelete(), virtualHost);
        final AMQShortString queueName = queue.getName();

        if (body.getExclusive() && !body.getDurable())
        {
            final AMQProtocolSession.Task deleteQueueTask =
                    new AMQProtocolSession.Task()
                    {
                        public void doTask(AMQProtocolSession session) throws AMQException
                        {
                            if (registry.getQueue(queueName) == queue)
                            {
                                queue.delete();
                            }
                        }
                    };

            session.addSessionCloseTask(deleteQueueTask);

            queue.addQueueDeleteTask(new AMQQueue.Task()
            {
                public void doTask(AMQQueue queue)
                {
                    session.removeSessionCloseTask(deleteQueueTask);
                }
            });
        }// if exclusive and not durable

        Configuration virtualHostDefaultQueueConfiguration = VirtualHostConfiguration.getDefaultQueueConfiguration(queue);
        if (virtualHostDefaultQueueConfiguration != null)
        {
            Configurator.configure(queue, virtualHostDefaultQueueConfiguration);
        }

        return queue;
    }
}
