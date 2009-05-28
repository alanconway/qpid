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
#ifndef _Connector_
#define _Connector_


#include "qpid/framing/OutputHandler.h"
#include "qpid/framing/ProtocolVersion.h"

#include <boost/shared_ptr.hpp>

#include <string>

namespace qpid {

namespace sys {
class ShutdownHandler;
class SecurityLayer;
class Poller;
}

namespace framing {
class InputHandler;
class AMQFrame;
}

namespace client {

struct ConnectionSettings;
class ConnectionImpl;

///@internal
class Connector : public framing::OutputHandler
{
  public:
    // Protocol connector factory related stuff (it might be better to separate this code from the TCP Connector in the future)
    typedef Connector* Factory(boost::shared_ptr<qpid::sys::Poller>,
                               framing::ProtocolVersion, const ConnectionSettings&, ConnectionImpl*);
    static Connector* create(const std::string& proto,
                             boost::shared_ptr<qpid::sys::Poller>,
                             framing::ProtocolVersion, const ConnectionSettings&, ConnectionImpl*);
    static void registerFactory(const std::string& proto, Factory* connectorFactory);

    virtual ~Connector() {};
    virtual void connect(const std::string& host, int port) = 0;
    virtual void init() {};
    virtual void close() = 0;
    virtual void send(framing::AMQFrame& frame) = 0;
    virtual void abort() = 0;

    virtual void setInputHandler(framing::InputHandler* handler) = 0;
    virtual void setShutdownHandler(sys::ShutdownHandler* handler) = 0;
    virtual sys::ShutdownHandler* getShutdownHandler() const = 0;
    virtual framing::OutputHandler* getOutputHandler() = 0;
    virtual const std::string& getIdentifier() const = 0;

    virtual void activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer>);
};

}}


#endif
