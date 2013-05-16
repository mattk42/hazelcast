/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.client.connection;

import com.hazelcast.client.exception.ClusterClientException;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.ObjectDataInputStream;
import com.hazelcast.nio.serialization.ObjectDataOutputStream;
import com.hazelcast.nio.serialization.SerializationService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Holds the socket to one of the members of Hazelcast Cluster.
 *
 * @author fuad-malikov
 */
final class ConnectionImpl implements Connection {

    private static int CONN_ID = 1;
    private static final int BUFFER_SIZE = 16 << 10; // 32k

    private static synchronized int newConnId() {
        return CONN_ID++;
    }

    private final Socket socket;
    private final Address endpoint;
    private final ObjectDataOutputStream out;
    private final ObjectDataInputStream in;
    private final int id = newConnId();
//    private long lastRead = System.currentTimeMillis();

    public ConnectionImpl(Address address, SerializationService serializationService) {
        this.endpoint = address;
        try {
            final InetSocketAddress isa = address.getInetSocketAddress();
            final Socket socket = new Socket();
            try {
                socket.setKeepAlive(true);
//                socket.setTcpNoDelay(true);
                socket.setSoLinger(true, 5);
                socket.setSendBufferSize(BUFFER_SIZE);
                socket.setReceiveBufferSize(BUFFER_SIZE);
                socket.connect(isa, 3000);
            } catch (IOException e) {
                socket.close();
                throw e;
            }
            this.socket = socket;
            this.out = serializationService.createObjectDataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE));
            this.in = serializationService.createObjectDataInputStream(
                    new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE));
        } catch (Exception e) {
            throw new ClusterClientException(e);
        }
    }

    Socket getSocket() {
        return socket;
    }

    @Override
    public Address getEndpoint() {
        return endpoint;
    }

    void write(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    @Override
    public boolean write(Data data) throws IOException {
        data.writeData(out);
        out.flush();
        return true;
    }

    @Override
    public Data read() throws IOException {
        Data data = new Data();
        data.readData(in);
        return data;
    }

    @Override
    public void close() throws IOException {
        out.close();
        in.close();
        socket.close();
    }

    @Override
    public int getId() {
        return id;
    }

    //    public long getLastRead() {
//        return lastRead;
//    }

    @Override
    public String toString() {
        return "Connection [" + endpoint + " -> " +
                socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "]";
    }
}
