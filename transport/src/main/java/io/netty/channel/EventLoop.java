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
package io.netty.channel;

import io.netty.util.concurrent.OrderedEventExecutor;

/**
 * Will handle all the I/O operations for a {@link Channel} once registered.
 * <p>
 * EventLoop 用于处理注册在它上面的 Channel 的所有的IO事件，
 * 一个 EventLoop 实例通常可以处理多个 Channel，但是这取决于内部的实现细节
 *
 * One {@link EventLoop} instance will usually handle more than one {@link Channel} but this may depend on
 * implementation details and internals.
 */
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {

    /**
     * 返回所属的 EventExecutorGroup
     */
    @Override
    EventLoopGroup parent();
}