/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.impl.protocol.task.map;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.MapKeySetCodec;
import com.hazelcast.client.impl.protocol.task.AbstractAllPartitionsMessageTask;
import com.hazelcast.instance.Node;
import com.hazelcast.map.impl.MapKeySet;
import com.hazelcast.map.impl.MapService;
import com.hazelcast.map.impl.operation.MapKeySetOperationFactory;
import com.hazelcast.nio.Connection;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.security.permission.ActionConstants;
import com.hazelcast.security.permission.MapPermission;
import com.hazelcast.spi.OperationFactory;

import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapKeySetMessageTask
        extends AbstractAllPartitionsMessageTask<MapKeySetCodec.RequestParameters> {

    public MapKeySetMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    @Override
    protected OperationFactory createOperationFactory() {
        return new MapKeySetOperationFactory(parameters.name);
    }


    @Override
    protected Object reduce(Map<Integer, Object> map) {
        List<Data> list = new ArrayList<Data>();
        MapService service = getService(MapService.SERVICE_NAME);
        for (Object o : map.values()) {
            Set keys = ((MapKeySet) service.getMapServiceContext().toObject(o)).getKeySet();
            list.addAll(keys);
        }
        return list;
    }

    @Override
    protected MapKeySetCodec.RequestParameters decodeClientMessage(ClientMessage clientMessage) {
        return MapKeySetCodec.decodeRequest(clientMessage);
    }

    @Override
    protected ClientMessage encodeResponse(Object response) {
        return MapKeySetCodec.encodeResponse((Collection<Data>) response);
    }


    public String getServiceName() {
        return MapService.SERVICE_NAME;
    }

    public Permission getRequiredPermission() {
        return new MapPermission(parameters.name, ActionConstants.ACTION_READ);
    }

    @Override
    public String getDistributedObjectName() {
        return parameters.name;
    }

    @Override
    public String getMethodName() {
        return "keySet";
    }

    @Override
    public Object[] getParameters() {
        return null;
    }
}
