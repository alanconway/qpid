/*
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
 */

package org.apache.qpid.server.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class PluggableFactoryLoader<T extends Pluggable>
{
    private final Map<String, T> _factoriesMap;
    private final Set<String> _types;

    
    public PluggableFactoryLoader(Class<T> factoryClass)
    {
        this(factoryClass, true);
    }

    public PluggableFactoryLoader(Class<T> factoryClass, boolean atLeastOnce)
    {
        Map<String, T> fm = new HashMap<String, T>();
        QpidServiceLoader<T> qpidServiceLoader = new QpidServiceLoader<T>();
        Iterable<T> factories = atLeastOnce? qpidServiceLoader.atLeastOneInstanceOf(factoryClass) : qpidServiceLoader.instancesOf(factoryClass);
        for (T factory : factories)
        {
            String descriptiveType = factory.getType();
            if (fm.containsKey(descriptiveType))
            {
                throw new IllegalStateException(factoryClass.getSimpleName() + " with type name '" + descriptiveType
                        + "' is already registered using class '" + fm.get(descriptiveType).getClass().getName()
                        + "', can not register class '" + factory.getClass().getName() + "'");
            }
            fm.put(descriptiveType, factory);
        }
        _factoriesMap = Collections.unmodifiableMap(fm);
        _types = Collections.unmodifiableSortedSet(new TreeSet<String>(_factoriesMap.keySet()));
    }

    public T get(String type)
    {
        return _factoriesMap.get(type);
    }

    public Collection<String> getDescriptiveTypes()
    {
        return _types;
    }
}
