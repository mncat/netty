/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/**
 * A TCP/IP socket {@link Channel}.
 */
public interface SocketChannel extends DuplexChannel {

    /**
     * 父Channel
     * */
    @Override
    ServerSocketChannel parent();

    /**
     * 返回Channel配置对象
     * */
    @Override
    SocketChannelConfig config();

    /**
     * 返回本地地址
     * */
    @Override
    InetSocketAddress localAddress();

    /**
     * 返回远程地址
     * */
    @Override
    InetSocketAddress remoteAddress();
}
