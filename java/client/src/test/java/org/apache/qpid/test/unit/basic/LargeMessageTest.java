/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.test.unit.basic;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.message.JMSTextMessage;

import javax.jms.Destination;
import javax.jms.Session;
import javax.jms.MessageConsumer;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;
import java.util.List;
import java.util.ArrayList;

public class LargeMessageTest extends TestCase
{
    private static final Logger _logger = Logger.getLogger(LargeMessageTest.class);

    private AMQConnection _connection;
    private Destination _destination;
    private AMQSession _session;
    private final List<JMSTextMessage> received = new ArrayList<JMSTextMessage>();
    public String _broker = "vm://:1";

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
        try
        {
            init(new AMQConnection(_broker, "guest", "guest", "LargeMessageTest", "test"));
        }
        catch (Exception e)
        {
            fail("Unable to initialilse connection: " + e);
        }
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    private void init(AMQConnection connection) throws Exception
    {
        Destination destination = new AMQQueue("LargeMessageTest", true);
        init(connection, destination);
    }

    private void init(AMQConnection connection, Destination destination) throws Exception
    {
        _connection = connection;
        _destination = destination;
        _session = (AMQSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        connection.start();
    }

    // Test boundary of 1 packet to 2 packets
    public void test64kminus1()
    {
        checkLargeMessage(64 * 1024 - 1);
    }

    public void test64k()
    {
        checkLargeMessage(64 * 1024);
    }

    public void test64kplus1()
    {
        checkLargeMessage(64 * 1024 + 1);
    }

    //Test packet boundary of 3 packtes
    public void test128kminus1()
    {
        checkLargeMessage(128 * 1024 - 1);
    }

    public void test128k()
    {
        checkLargeMessage(128 * 1024);
    }

    public void test128kplus1()
    {
        checkLargeMessage(128 * 1024 + 1);
    }

    // Testing larger messages

    public void test256k()
    {
        checkLargeMessage(256 * 1024);
    }

    public void test512k()
    {
        checkLargeMessage(512 * 1024);
    }

    public void test1024k()
    {
        checkLargeMessage(1024 * 1024);
    }

    protected void checkLargeMessage(int messageSize)
    {
        try
        {
            MessageConsumer consumer = _session.createConsumer(_destination);
            MessageProducer producer = _session.createProducer(_destination);
            _logger.info("Testing message of size:" + messageSize);

            String _messageText = buildLargeMessage(messageSize);

            _logger.error("Message built");

            producer.send(_session.createTextMessage(_messageText));

            TextMessage result = (TextMessage) consumer.receive(1000);

            assertNotNull("Null message recevied", result);
            assertEquals("Message Size", _messageText.length(), result.getText().length());
            assertEquals("Message content differes", _messageText, result.getText());
        }
        catch (JMSException e)
        {
            e.printStackTrace();
            fail("Excpetion occured:" + e.getCause());
        }
    }

    private String buildLargeMessage(int size)
    {
        StringBuilder builder = new StringBuilder(size);

        char ch = 'a';

        for (int i = 1; i <= size; i++)
        {
            builder.append(ch);

            if (i % 1000 == 0)
            {
                ch++;
                if (ch == 'z' + 1)
                {
                    ch = 'a';
                }
            }
        }

        return builder.toString();
    }


    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(LargeMessageTest.class);
    }
}
