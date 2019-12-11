/*
 * Copyright 2016 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.internal.UnstableApi;

/**
 * EventExecutorChooser 工厂接口,可以创建：EventExecutorChooser 事件执行选择器
 * Factory that creates new {@link EventExecutorChooser}s.
 */
@UnstableApi
public interface EventExecutorChooserFactory {

    /**
     * 工厂接口用于创建一个 EventExecutorChooser 选择器对象
     * Returns a new {@link EventExecutorChooser}.
     */
    EventExecutorChooser newChooser(EventExecutor[] executors);

    /**
     * EventExecutorChooser 选择器接口
     * Chooses the next {@link EventExecutor} to use.
     */
    @UnstableApi
    interface EventExecutorChooser {

        /**
         * 选择器用于获取一个事件执行器
         * Returns the new {@link EventExecutor} to use.
         */
        EventExecutor next();
    }
}