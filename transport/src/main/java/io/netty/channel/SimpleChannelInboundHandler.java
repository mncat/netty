/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link ChannelInboundHandlerAdapter} which allows to explicit only handle a specific type of messages.
 * 只允许处理指定类型的消息，下面是一个简单的示例实现，只处理String类型的消息：
 * For example here is an implementation which only handle {@link String} messages.
 *
 * <pre>
 *     public class StringHandler extends {@link SimpleChannelInboundHandler}&lt;{@link String}&gt; {
 *
 *         {@code @Override}
 *         protected void channelRead0({@link ChannelHandlerContext} ctx, {@link String} message)
 *                 throws {@link Exception} {
 *             System.out.println(message);
 *         }
 *     }
 * </pre>
 * <p>
 * Be aware that depending of the constructor parameters it will release all handled messages by passing them to
 * {@link ReferenceCountUtil#release(Object)}. In this case you may need to use
 * {@link ReferenceCountUtil#retain(Object)} if you pass the object to the next handler in the {@link ChannelPipeline}.
 *
 * <h3>Forward compatibility notice</h3>
 * <p>
 * Please keep in mind that {@link #channelRead0(ChannelHandlerContext, I)} will be renamed to
 * {@code messageReceived(ChannelHandlerContext, I)} in 5.0.
 * </p>
 * <p>
 * SimpleChannelInboundHandler可以处理指定类型的消息。可以实现SimpleChannelInboundHandler来对指定类型的消息的自定义处理。
 */
public abstract class SimpleChannelInboundHandler<I> extends ChannelInboundHandlerAdapter {

    /**
     * 类型匹配器对象
     */
    private final TypeParameterMatcher matcher;
    /**
     * 使用完消息，是否自动释放，如果自动释放，则会调用ReferenceCountUtil.release(msg)释放引用计数
     *
     * @see #channelRead(ChannelHandlerContext, Object)
     */
    private final boolean autoRelease;

    /**
     * see {@link #SimpleChannelInboundHandler(boolean)} with {@code true} as boolean parameter.
     * 构造方法
     */
    protected SimpleChannelInboundHandler() {
        this(true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param autoRelease {@code true} if handled messages should be released automatically by passing them to
     *                    {@link ReferenceCountUtil#release(Object)}.
     *                    构造方法
     */
    protected SimpleChannelInboundHandler(boolean autoRelease) {
        // 获得 matcher
        matcher = TypeParameterMatcher.find(this, SimpleChannelInboundHandler.class, "I");
        this.autoRelease = autoRelease;
    }

    /**
     * see {@link #SimpleChannelInboundHandler(Class, boolean)} with {@code true} as boolean value.
     * 构造方法
     */
    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType) {
        this(inboundMessageType, true);
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType The type of messages to match
     * @param autoRelease        {@code true} if handled messages should be released automatically by passing them to
     *                           {@link ReferenceCountUtil#release(Object)}.
     *                           构造方法
     */
    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType, boolean autoRelease) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
        this.autoRelease = autoRelease;
    }

    /**
     * 返回true表示消息应该被处理
     * 返回false，消息会被传递给ChannelPipeline中的下一个ChannelInboundHandler
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelInboundHandler} in the {@link }.
     */
    public boolean acceptInboundMessage(Object msg) {
        return matcher.match(msg);
    }

    /**
     * 实现好的骨架方法，子类实现channelRead0即可
     * 方法内部已经将资源的释放实现好了，子类在channelRead0中不需要释放资源
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //1.是否要释放消息
        boolean release = true;
        try {
            //2.判断是否为匹配的消息
            if (acceptInboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                //3.处理消息
                channelRead0(ctx, imsg);
            } else {
                //4.不需要释放消息，因为消息都不需要处理
                release = false;
                //5.触发Channel Read到下一个节点，由下一个节点处理消息
                ctx.fireChannelRead(msg);
            }
        } finally {
            //6.判断，是否要释放消息，两个标志都为true时，才释放，释放消息会将引用计数减1
            if (autoRelease && release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    /**
     * <strong>Please keep in mind that this method will be renamed to
     * {@code messageReceived(ChannelHandlerContext, I)} in 5.0.</strong>
     * <p>
     * Is called for each message of type {@link I}.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link SimpleChannelInboundHandler}
     *            belongs to
     * @param msg the message to handle
     * @throws Exception is thrown if an error occurred
     *                   <p>
     *                   需要子类实现的抽象方法,在接收到数据时被调用
     */
    protected abstract void channelRead0(ChannelHandlerContext ctx, I msg) throws Exception;
}
