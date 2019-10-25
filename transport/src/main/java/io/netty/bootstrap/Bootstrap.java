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
package io.netty.bootstrap;

import io.netty.channel.*;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.NameResolver;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 便于客户端启动Channel
 * A {@link Bootstrap} that makes it easy to bootstrap a {@link Channel} to use
 * for clients.
 *
 * <p>The {@link #bind()} methods are useful in combination with connectionless transports such as datagram (UDP).
 * For regular TCP connections, please use the provided {@link #connect()} methods.</p>
 */
public class Bootstrap extends AbstractBootstrap<Bootstrap, Channel> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);

    /**
     * 默认地址解析器对象
     */
    private static final AddressResolverGroup<?> DEFAULT_RESOLVER = DefaultAddressResolverGroup.INSTANCE;

    /**
     * Bootstrap启动器配置对象
     */
    private final BootstrapConfig config = new BootstrapConfig(this);
    /**
     * 地址解析器对象
     */
    @SuppressWarnings("unchecked")
    private volatile AddressResolverGroup<SocketAddress> resolver = (AddressResolverGroup<SocketAddress>) DEFAULT_RESOLVER;

    /**
     * 远程连接地址
     */
    private volatile SocketAddress remoteAddress;

    public Bootstrap() {
    }

    /**
     * 构造方法
     */
    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        resolver = bootstrap.resolver;
        remoteAddress = bootstrap.remoteAddress;
    }

    /**
     * Sets the {@link NameResolver} which will resolve the address of the unresolved named address.
     *
     * @param resolver the {@link NameResolver} for this {@code Bootstrap}; may be {@code null}, in which case a default
     *                 resolver will be used
     * @see io.netty.resolver.DefaultAddressResolverGroup
     * 设置地址解析器
     */
    @SuppressWarnings("unchecked")
    public Bootstrap resolver(AddressResolverGroup<?> resolver) {
        this.resolver = (AddressResolverGroup<SocketAddress>) (resolver == null ? DEFAULT_RESOLVER : resolver);
        return this;
    }

    /**
     * The {@link SocketAddress} to connect to once the {@link #connect()} method
     * is called.
     * 设置远程地址
     */
    public Bootstrap remoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }

    /**
     * @see #remoteAddress(SocketAddress)
     */
    public Bootstrap remoteAddress(String inetHost, int inetPort) {
        remoteAddress = InetSocketAddress.createUnresolved(inetHost, inetPort);
        return this;
    }

    /**
     * @see #remoteAddress(SocketAddress)
     */
    public Bootstrap remoteAddress(InetAddress inetHost, int inetPort) {
        remoteAddress = new InetSocketAddress(inetHost, inetPort);
        return this;
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     * 连接到远程地址
     */
    public ChannelFuture connect() {
        //1.参数校验
        validate();
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress not set");
        }
        //2.地址解析并连接
        return doResolveAndConnect(remoteAddress, config.localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     * 重载方法
     */
    public ChannelFuture connect(String inetHost, int inetPort) {
        return connect(InetSocketAddress.createUnresolved(inetHost, inetPort));
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     * 重载方法
     */
    public ChannelFuture connect(InetAddress inetHost, int inetPort) {
        return connect(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect(SocketAddress remoteAddress) {
        //1.参数校验
        validate();
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        //2.地址解析并连接
        return doResolveAndConnect(remoteAddress, config.localAddress());
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     * 重载方法
     */
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        validate();
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        return doResolveAndConnect(remoteAddress, localAddress);
    }

    /**
     * @see #connect()
     * 解析地址并连接的实现主体
     */
    private ChannelFuture doResolveAndConnect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
        //1.初始化并注册Channel对象，异步注册返回一个ChannelFuture对象
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();

        if (regFuture.isDone()) {
            //2.若执行失败，直接返回
            if (!regFuture.isSuccess()) {
                return regFuture;
            }
            //3.解析地址并连接
            return doResolveAndConnect0(channel, remoteAddress, localAddress, channel.newPromise());
        } else {
            //4.如果未完成，则给ChannelFuture添加一个监听器，当事件到达的时候执行响应处理，
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            //5.添加一个监听器用来监听操作完成事件operationComplete。
            regFuture.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    // Directly obtain the cause and do a null check so we only need one volatile read in case of a
                    // failure.
                    Throwable cause = future.cause();
                    if (cause != null) {
                        //6.判断若操作完成的时候有异常，则设置PendingRegistrationPromise为失败。
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        //7.若执行成功，则设置PendingRegistrationPromise为注册成功，最后也会调用doResolveAndConnect0方法，这与第3步是一样的。
                        // Registration was successful, doResolveAndConnect0so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        // 解析地址并连接
                        doResolveAndConnect0(channel, remoteAddress, localAddress, promise);
                    }
                }

            });
            return promise;
        }
    }

    /**
     * @see #connect()
     * 解析地址并连接的实现
     */
    private ChannelFuture doResolveAndConnect0(final Channel channel, SocketAddress remoteAddress,
                                               final SocketAddress localAddress, final ChannelPromise promise) {
        try {
            //1.得到当前Channel绑定的一个EventLoop对象和与当前EventLoop对象绑定的一个地址解析器对象
            final EventLoop eventLoop = channel.eventLoop();
            final AddressResolver<SocketAddress> resolver = this.resolver.getResolver(eventLoop);

            //2.如果地址解析器不支持或者已经解析过，则直接使用该远程地址进行连接
            if (!resolver.isSupported(remoteAddress) || resolver.isResolved(remoteAddress)) {
                // Resolver has no idea about what to do with the specified remote address or it's resolved already.
                doConnect(remoteAddress, localAddress, promise);
                return promise;
            }

            //3.解析地址
            final Future<SocketAddress> resolveFuture = resolver.resolve(remoteAddress);

            //4.如果解析器正好解析完成，则判断解析结果；如果解析抛出异常则关闭连接，设置状态是失败；如果解析成功则进行连接操作。
            if (resolveFuture.isDone()) {
                // 解析远程地址失败，关闭 Channel ，并回调通知 promise 异常
                final Throwable resolveFailureCause = resolveFuture.cause();
                if (resolveFailureCause != null) {
                    // Failed to resolve immediately
                    channel.close();
                    promise.setFailure(resolveFailureCause);
                } else {
                    // Succeeded to resolve immediately; cached? (or did a blocking lookup)
                    // 连接远程地址
                    doConnect(resolveFuture.getNow(), localAddress, promise);
                }
                return promise;
            }

            // Wait until the name resolution is finished.
            //5.如果一段时间后才解析完成，则添加一个监听器，监听未来监听的事件。处理逻辑与4相同
            resolveFuture.addListener(new FutureListener<SocketAddress>() {
                @Override
                public void operationComplete(Future<SocketAddress> future) throws Exception {
                    // 解析远程地址失败，关闭 Channel ，并回调通知 promise 异常
                    if (future.cause() != null) {
                        channel.close();
                        promise.setFailure(future.cause());
                        // 解析远程地址成功，连接远程地址
                    } else {
                        doConnect(future.getNow(), localAddress, promise);
                    }
                }
            });
        } catch (Throwable cause) {
            // 发生异常，并回调通知 promise 异常
            promise.tryFailure(cause);
        }
        return promise;
    }

    /**
     * connect连接的主体
     */
    private static void doConnect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise connectPromise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        final Channel channel = connectPromise.channel();
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (localAddress == null) {
                    channel.connect(remoteAddress, connectPromise);
                } else {
                    channel.connect(remoteAddress, localAddress, connectPromise);
                }
                connectPromise.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        });
    }

    /**
     * 初始化Channel
     * */
    @Override
    @SuppressWarnings("unchecked")
    void init(Channel channel) throws Exception {
        //1.获取Channel的pipeline，pipeline是在创建Channel的同时创建的
        ChannelPipeline p = channel.pipeline();

        //2.添加处理器到pipeline
        p.addLast(config.handler());

        //3.初始化Channel的选项集合
        final Map<ChannelOption<?>, Object> options = options0();
        synchronized (options) {
            setChannelOptions(channel, options, logger);
        }

        //4.初始化 Channel 的属性集合
        final Map<AttributeKey<?>, Object> attrs = attrs0();
        synchronized (attrs) {
            for (Entry<AttributeKey<?>, Object> e : attrs.entrySet()) {
                channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }
        }
    }

    /**
     * 参数校验
     * */
    @Override
    public Bootstrap validate() {
        super.validate();
        //handler不能为空
        if (config.handler() == null) {
            throw new IllegalStateException("handler not set");
        }
        return this;
    }

    @Override
    @SuppressWarnings("CloneDoesntCallSuperClone")
    public Bootstrap clone() {
        return new Bootstrap(this);
    }

    /**
     * Returns a deep clone of this bootstrap which has the identical configuration except that it uses
     * the given {@link EventLoopGroup}. This method is useful when making multiple {@link Channel}s with similar
     * settings.
     */
    public Bootstrap clone(EventLoopGroup group) {
        Bootstrap bs = new Bootstrap(this);
        bs.group = group;
        return bs;
    }

    @Override
    public final BootstrapConfig config() {
        return config;
    }

    final SocketAddress remoteAddress() {
        return remoteAddress;
    }

    final AddressResolverGroup<?> resolver() {
        return resolver;
    }
}
