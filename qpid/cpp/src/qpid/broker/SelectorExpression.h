#ifndef QPID_BROKER_SELECTOREXPRESSION_H
#define QPID_BROKER_SELECTOREXPRESSION_H

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

// Work around broken header files in gcc 4.1.2 (RHEL5) - needs to be included before anything else
#include "qpid/sys/IntegerTypes.h"

#include <iosfwd>
#include <string>

namespace qpid {
namespace broker {

class SelectorEnv;

class TopExpression {
public:
    virtual ~TopExpression() {};
    virtual void repr(std::ostream&) const = 0;
    virtual bool eval(const SelectorEnv&) const = 0;

    static TopExpression* parse(const std::string& exp);
};

}}

#endif
