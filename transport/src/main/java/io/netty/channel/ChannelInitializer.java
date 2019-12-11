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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ConcurrentMap;

/**
 * 一个特殊的ChannelInboundHandler，提供了一个便捷的方式在Channel注册到EventLoop的时候来初始化这个Channel
 * A special {@link ChannelInboundHandler} which offers an easy way to initialize a {@link Channel} once it was
 * registered to its {@link EventLoop}.
 * <p>
 * 很多时候是在 Bootstrap#handler/ServerBootstrap#handler或者ServerBootstrap#childHandler 方法中使用，
 * 用于初始化 Channel 的 ChannelPipeline
 * <p>
 * Implementations are most often used in the context of {@link Bootstrap#handler(ChannelHandler)} ,
 * {@link ServerBootstrap#handler(ChannelHandler)} and {@link ServerBootstrap#childHandler(ChannelHandler)} to
 * setup the {@link ChannelPipeline} of a {@link Channel}.
 *
 * <pre>
 * 使用示例如下：
 * public class MyChannelInitializer extends {@link ChannelInitializer} {
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("myHandler", new MyHandler());
 *     }
 * }
 *
 * {@link ServerBootstrap} bootstrap = ...;
 * ...
 * bootstrap.childHandler(new MyChannelInitializer());
 * ...
 * </pre>
 * 注意到该类是由 @Sharable 标注的，因此实现必须是线程安全的，能够复用
 * Be aware that this class is marked as {@link Sharable} and so the implementation must be safe to be re-used.
 *
 * @param <C> A sub-type of {@link Channel}
 */
@Sharable
public abstract class ChannelInitializer<C extends Channel> extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelInitializer.class);

    // We use a ConcurrentMap as a ChannelInitializer is usually shared between all Channels in a Bootstrap /
    // ServerBootstrap. This way we can reduce the memory usage compared to use Attributes.
    /**
     * 由于 ChannelInitializer 通常可以在所有的 Bootstrap/ServerBootstrap 通道中共享，因此我们用一个 ConcurrentMap
     * 这种方式相对于使用 Attributes 方式，可以减少内存的使用，相当于不同的通道，对应的 ChannelHandlerContext 不同，由
     * ConcurrentMap 来解决了并发问题
     */
    private final ConcurrentMap<ChannelHandlerContext, Boolean> initMap = PlatformDependent.newConcurrentHashMap();

    /**
     * This method will be called once the {@link Channel} was registered. After the method returns this instance
     * will be removed from the {@link ChannelPipeline} of the {@link Channel}.
     * <p>
     * Channel被注册到EventLoop的时候initChannel会被调用，ChannelInitializer实现类必须重写该方法。
     * 并且该方法调用返回之后，ChannelInitializer实例会从ChannelPipeline移除
     *
     * @param ch the {@link Channel} which was registered.
     * @throws Exception is thrown if an error occurs. In that case it will be handled by
     *                   {@link #exceptionCaught(ChannelHandlerContext, Throwable)} which will by default close
     *                   the {@link Channel}.
     */
    protected abstract void initChannel(C ch) throws Exception;

    @Override
    @SuppressWarnings("unchecked")
    public final void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        // Normally this method will never be called as handlerAdded(...) should call initChannel(...) and remove
        // the handler.
        //添加该行日志调试
        logger.info("进入 ChannelInitializer 的 channelRegistered 方法... " );
        // 初始化 Channel
        if (initChannel(ctx)) {
            //添加该行日志调试
            logger.info("ChannelInitializer 的 channelRegistered 方法进入if逻辑执行... " );
            // we called initChannel(...) so we need to call now pipeline.fireChannelRegistered() to ensure we not
            // miss an event.
            // 重新触发 Channel Registered 事件
            ctx.pipeline().fireChannelRegistered();
        } else {
            // 继续向下一个节点传播Channel Registered 事件
            // Called initChannel(...) before which is the expected behavior, so just forward the event.
            ctx.fireChannelRegistered();
        }
    }

    /**
     * Handle the {@link Throwable} by logging and closing the {@link Channel}. Sub-classes may override this.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (logger.isWarnEnabled()) {
            logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        }
        ctx.close();
    }

    /**
     * {@inheritDoc} If override this method ensure you call super!
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        //1.已注册
        if (ctx.channel().isRegistered()) {
            //添加该行日志调试
            logger.info("ChannelInitializer 的 handlerAdded 方法执行... " );
            // This should always be true with our current DefaultChannelPipeline implementation.
            // The good thing about calling initChannel(...) in handlerAdded(...) is that there will be no ordering
            // surprises if a ChannelInitializer will add another ChannelInitializer. This is as all handlers
            // will be added in the expected order.
            initChannel(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean initChannel(ChannelHandlerContext ctx) throws Exception {
        // Guard against re-entrance. 解决并发问题，原来没有放进去的话会返回null，原来有就不会放进去，返回旧值
        if (initMap.putIfAbsent(ctx, Boolean.TRUE) == null) {
            try {
                //1.初始化通道，调用用户自己的实现添加 handler
                initChannel((C) ctx.channel());
            } catch (Throwable cause) {
                //2.发生异常时，执行异常处理，记录日志并关闭 ChannelHandlerContext
                // Explicitly call exceptionCaught(...) as we removed the handler before calling initChannel(...).
                // We do so to prevent multiple calls to initChannel(...).
                exceptionCaught(ctx, cause);
            } finally {
                //3.从 pipeline 移除 ChannelInitializer 自身(这行日志是我自己添加的)
                logger.info("initChannel移除: " + ctx
                        + ", 内部的handler是: " + ctx.handler()
                        + ", handler类型是: " + ctx.handler().getClass()
                        + ", ctx 类型是: " + ctx.getClass());
                remove(ctx);
            }
            //4.初始化成功
            return true;
        }
        //5.初始化失败
        return false;
    }

    private void remove(ChannelHandlerContext ctx) {
        try {
            ChannelPipeline pipeline = ctx.pipeline();

            //1.从 pipeline 找到 ChannelHandler 对应的 ChannelHandlerContext 节点
            //(pipeline 中的节点是ChannelHandlerContext，ChannelHandlerContext包装了ChannelHandler)
            if (pipeline.context(this) != null) {
                //2.移除对应的handler(ChannelInitializer也是一种handler，内部会把 handler 包
                // 装成的ChannelHandlerContext 节点，再删除节点，pipeline内部是链表结构，节点
                // 是ChannelHandlerContext类型)
                pipeline.remove(this);
            }
        } finally {
            //3.从initMap移除
            initMap.remove(ctx);
        }
    }
}
