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
package io.netty.channel;

/**
 * ChannelInboundInvoker定义了ChannelInboundHandler之间的回调事件的回调方法，由用户进行具体实现。
 */
public interface ChannelInboundInvoker {

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler的channelRegistered方法被调用
     * <p>
     * A {@link Channel} was registered to its {@link EventLoop}.
     * This will result in having the  {@link ChannelInboundHandler#channelRegistered(ChannelHandlerContext)} method
     * called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelRegistered();

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler的channelUnregistered方法被调用
     * <p>
     * A {@link Channel} was unregistered from its {@link EventLoop}.
     * This will result in having the  {@link ChannelInboundHandler#channelUnregistered(ChannelHandlerContext)} method
     * called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelUnregistered();

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler的channelActive方法被调用
     * A {@link Channel} is active now, which means it is connected.
     * <p>
     * This will result in having the  {@link ChannelInboundHandler#channelActive(ChannelHandlerContext)} method
     * called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelActive();

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler的channelInactive方法被调用
     * A {@link Channel} is inactive now, which means it is closed.
     * <p>
     * This will result in having the  {@link ChannelInboundHandler#channelInactive(ChannelHandlerContext)} method
     * called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelInactive();

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler的exceptionCaught方法被调用
     * A {@link Channel} received an {@link Throwable} in one of its inbound operations.
     * <p>
     * This will result in having the  {@link ChannelInboundHandler#exceptionCaught(ChannelHandlerContext, Throwable)}
     * method  called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireExceptionCaught(Throwable cause);

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler的userEventTriggered方法被调用
     * A {@link Channel} received an user defined event.
     * <p>
     * This will result in having the  {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)}
     * method  called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireUserEventTriggered(Object event);

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler的channelRead方法被调用
     * A {@link Channel} received a message.
     * <p>
     * This will result in having the {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)}
     * method  called of the next {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelRead(Object msg);

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler一个channelReadComplete事件
     * <p>
     * Triggers an {@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext)}
     * event to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     */
    ChannelInboundInvoker fireChannelReadComplete();

    /**
     * 触发ChannelPipeline中后面一个ChannelInboundHandler一个channelWritabilityChanged事件
     * <p>
     * Triggers an {@link ChannelInboundHandler#channelWritabilityChanged(ChannelHandlerContext)}
     * event to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     */
    ChannelInboundInvoker fireChannelWritabilityChanged();
}
