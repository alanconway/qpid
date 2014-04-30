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

package org.apache.qpid.server.virtualhostnode.berkeleydb;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;

import com.sleepycat.je.rep.MasterStateException;
import com.sleepycat.je.rep.ReplicatedEnvironment;

public class BDBHARemoteReplicationNodeImpl extends AbstractConfiguredObject<BDBHARemoteReplicationNodeImpl> implements BDBHARemoteReplicationNode<BDBHARemoteReplicationNodeImpl>
{
    private static final Logger LOGGER = Logger.getLogger(BDBHARemoteReplicationNodeImpl.class);

    private final ReplicatedEnvironmentFacade _replicatedEnvironmentFacade;
    private final String _address;

    private volatile long _joinTime;
    private volatile long _lastTransactionId;

    @ManagedAttributeField(afterSet="afterSetRole")
    private volatile String _role;

    private final AtomicReference<State> _state;

    public BDBHARemoteReplicationNodeImpl(BDBHAVirtualHostNodeImpl virtualHostNode, Map<String, Object> attributes, ReplicatedEnvironmentFacade replicatedEnvironmentFacade)
    {
        super(parentsMap(virtualHostNode), attributes);
        _address = (String)attributes.get(ADDRESS);
        _replicatedEnvironmentFacade = replicatedEnvironmentFacade;
        _state = new AtomicReference<State>(State.ACTIVE);
    }

    @Override
    public State getState()
    {
        return _state.get();
    }

    @Override
    public String getGroupName()
    {
        return _replicatedEnvironmentFacade.getGroupName();
    }

    @Override
    public String getAddress()
    {
        return _address;
    }

    @Override
    public String getRole()
    {
        return _role;
    }

    @Override
    public long getJoinTime()
    {
        return _joinTime;
    }

    @Override
    public long getLastKnownReplicationTransactionId()
    {
        return _lastTransactionId;
    }

    public void delete()
    {
        this.deleted();
    }

    protected void afterSetRole()
    {
        try
        {
            String nodeName = getName();
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Trying to transfer master to " + nodeName);
            }

            _replicatedEnvironmentFacade.transferMasterAsynchronously(nodeName);

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("The mastership has been transfered to " + nodeName);
            }
        }
        catch(Exception e)
        {
            throw new IllegalConfigurationException("Cannot transfer mastership to " + getName(), e);
        }
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.DELETED)
        {
            String nodeName = getName();

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Deleting node '"  + nodeName + "' from group '" + getGroupName() + "'");
            }

            try
            {
                _replicatedEnvironmentFacade.removeNodeFromGroup(nodeName);
                _state.set(State.DELETED);
                delete();
                return true;
            }
            catch(MasterStateException e)
            {
                throw new IllegalStateTransitionException("Node '" + nodeName + "' cannot be deleted when role is a master");
            }
            catch (Exception e)
            {
                throw new IllegalStateTransitionException("Unexpected exception on node '" + nodeName + "' deletion", e);
            }
        }
        return false;
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if (changedAttributes.contains(ROLE))
        {
            String currentRole = getRole();
            if (!ReplicatedEnvironment.State.REPLICA.name().equals(currentRole))
            {
                throw new IllegalArgumentException("Cannot transfer mastership when not a replica");
            }
            if (!ReplicatedEnvironment.State.MASTER.name().equals(((BDBHARemoteReplicationNode<?>)proxyForValidation).getRole()))
            {
                throw new IllegalArgumentException("Changing role to other value then " + ReplicatedEnvironment.State.MASTER.name() + " is unsupported");
            }
        }

        if (changedAttributes.contains(JOIN_TIME))
        {
            throw new IllegalArgumentException("Cannot change derived attribute " + JOIN_TIME);
        }

        if (changedAttributes.contains(LAST_KNOWN_REPLICATION_TRANSACTION_ID))
        {
            throw new IllegalArgumentException("Cannot change derived attribute " + LAST_KNOWN_REPLICATION_TRANSACTION_ID);
        }
    }

    void setRole(String role)
    {
        _role = role;
    }

    void setJoinTime(long joinTime)
    {
        _joinTime = joinTime;
    }

    void setLastTransactionId(long lastTransactionId)
    {
        _lastTransactionId = lastTransactionId;
    }

}
