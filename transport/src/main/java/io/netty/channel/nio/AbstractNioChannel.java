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
package io.netty.channel.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link Channel} implementations which use a Selector based approach.
 * Channel的抽象基类实现，使用Selector
 */
public abstract class AbstractNioChannel extends AbstractChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractNioChannel.class);

    private static final ClosedChannelException DO_CLOSE_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), AbstractNioChannel.class, "doClose()");

    /**
     * Netty的NIO Channel对象，持有的Java原生NIO的Channel对象，SelectableChannel是JDK中的Channel
     */
    private final SelectableChannel ch;

    /**
     * 感兴趣读事件的操作位值
     */
    protected final int readInterestOp;

    volatile SelectionKey selectionKey;

    /**
     * TODO 芋艿
     */
    boolean readPending;

    /**
     * 移除对“读”事件感兴趣的 Runnable 对象
     */
    private final Runnable clearReadPendingRunnable = new Runnable() {
        @Override
        public void run() {
            clearReadPending0();
        }
    };

    /**
     * 目前正在连接远程地址的 ChannelPromise 对象。
     * <p>
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    /**
     * 连接超时监听 ScheduledFuture 对象。
     */
    private ScheduledFuture<?> connectTimeoutFuture;
    /**
     * 正在连接的远程地址
     */
    private SocketAddress requestedRemoteAddress;

    /**
     * Create a new instance
     *
     * @param parent         the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param ch             the underlying {@link SelectableChannel} on which it operates
     * @param readInterestOp the ops to set to receive data from the {@link SelectableChannel}
     */
    protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent);
        this.ch = ch;
        this.readInterestOp = readInterestOp;
        try {
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                ch.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized socket.", e2);
                }
            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }

    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    /**
     * Return the current {@link SelectionKey}
     */
    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    /**
     * @deprecated No longer supported.
     * No longer supported.
     */
    @Deprecated
    protected boolean isReadPending() {
        return readPending;
    }

    /**
     * @deprecated Use {@link #clearReadPending()} if appropriate instead.
     * No longer supported.
     */
    @Deprecated
    protected void setReadPending(final boolean readPending) {
        if (isRegistered()) {
            EventLoop eventLoop = eventLoop();
            if (eventLoop.inEventLoop()) {
                setReadPending0(readPending);
            } else {
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        setReadPending0(readPending);
                    }
                });
            }
        } else {
            // Best effort if we are not registered yet clear readPending.
            // NB: We only set the boolean field instead of calling clearReadPending0(), because the SelectionKey is
            // not set yet so it would produce an assertion failure.
            this.readPending = readPending;
        }
    }

    /**
     * Set read pending to {@code false}.
     */
    protected final void clearReadPending() {
        if (isRegistered()) {
            EventLoop eventLoop = eventLoop();
            if (eventLoop.inEventLoop()) {
                clearReadPending0();
            } else {
                eventLoop.execute(clearReadPendingRunnable);
            }
        } else {
            // Best effort if we are not registered yet clear readPending. This happens during channel initialization.
            // NB: We only set the boolean field instead of calling clearReadPending0(), because the SelectionKey is
            // not set yet so it would produce an assertion failure.
            readPending = false;
        }
    }

    private void setReadPending0(boolean readPending) {
        this.readPending = readPending;
        if (!readPending) {
            ((AbstractNioUnsafe) unsafe()).removeReadOp();
        }
    }

    private void clearReadPending0() {
        // TODO 芋艿
        readPending = false;
        // 移除对“读”事件的感兴趣。
        ((AbstractNioUnsafe) unsafe()).removeReadOp();
    }

    /**
     * Special {@link Unsafe} sub-type which allows to access the underlying {@link SelectableChannel}
     * 特殊的Unsafe子类，可以访问基础的SelectableChannel
     */
    public interface NioUnsafe extends Unsafe {
        /**
         * Return underlying {@link SelectableChannel}
         * 返回SelectableChannel
         */
        SelectableChannel ch();

        /**
         * Finish connect
         * 结束连接
         */
        void finishConnect();

        /**
         * 从SelectableChannel读数据
         * Read from underlying {@link SelectableChannel}
         */
        void read();

        /**
         * 强制刷数据
         */
        void forceFlush();
    }

    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {

        protected final void removeReadOp() {
            SelectionKey key = selectionKey();
            // 忽略，如果 SelectionKey 不合法，例如已经取消
            // Check first if the key is still valid as it may be canceled as part of the deregistration
            // from the EventLoop
            // See https://github.com/netty/netty/issues/2104
            if (!key.isValid()) {
                return;
            }
            // 移除对“读”事件的感兴趣。
            int interestOps = key.interestOps();
            if ((interestOps & readInterestOp) != 0) {
                // only remove readInterestOp if needed
                key.interestOps(interestOps & ~readInterestOp);
            }
        }

        @Override
        public final SelectableChannel ch() {
            return javaChannel();
        }

        /**
         * connect事件框架逻辑
         * 1.校验不允许有多个连接操作同时进行
         * 2.调用子类的doConnect方法完成连接，如果完成则通过fulfillConnectPromise设置结果为成功并处触发Channel的Active事件
         * 3.如果未完成，则通过定时器，在未超时的时间段内检测结果
         * 4.通过监听器机制，如果取消了连接操作，也取消定时任务，并做相关清理操作
         */
        @Override
        public final void connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            //1.Channel已经被关闭则返回
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                //2.已有连接操作正在进行，则直接抛出异常，禁止同时发起多个连接
                if (connectPromise != null) {
                    // Already a connect in process.
                    throw new ConnectionPendingException();
                }

                //3.记录Channel是否激活
                boolean wasActive = isActive();

                //4.执行连接远程地址，doConnect由子类完成，(这个方法是Channel中的)
                if (doConnect(remoteAddress, localAddress)) {
                    //5.连接操作完成后执行
                    fulfillConnectPromise(promise, wasActive);
                } else {
                    //6.连接未完成
                    connectPromise = promise;
                    // 记录 requestedRemoteAddress
                    requestedRemoteAddress = remoteAddress;
                    //7.连接超时机制，使用EventLoop发起定时任务，监听连接远程地址超时。若连接超时，则回调通知connectPromise超时异常，默认30*1000毫秒
                    // Schedule connect timeout.
                    int connectTimeoutMillis = config().getConnectTimeoutMillis();
                    if (connectTimeoutMillis > 0) {
                        connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                                ChannelPromise connectPromise = AbstractNioChannel.this.connectPromise;
                                ConnectTimeoutException cause = new ConnectTimeoutException("connection timed out: " + remoteAddress);
                                //8.失败了就关闭Channel
                                if (connectPromise != null && connectPromise.tryFailure(cause)) {
                                    close(voidPromise());
                                }
                            }
                        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                    }

                    //9.添加监听器，监听连接远程地址取消。 如果连接操作取消，则连接超时检测任务也取消
                    promise.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isCancelled()) {
                                //10.操作取消了，那么定时任务也取消
                                if (connectTimeoutFuture != null) {
                                    connectTimeoutFuture.cancel(false);
                                }
                                //11.置空connectPromise
                                connectPromise = null;
                                close(voidPromise());
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                //12.回调通知promise发生异常
                promise.tryFailure(annotateConnectException(t, remoteAddress));
                closeIfClosed();
            }
        }

        /**
         * 连接成功之后调用
         */
        private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
            //1.已经取消关闭或者已经被通知了，直接返回
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            //2.获得Channel是否激活
            // Get the state as trySuccess() may trigger an ChannelFutureListener that will close the Channel.
            // We still need to ensure we call fireChannelActive() in this case.
            boolean active = isActive();

            //3.如果已经取消链接的尝试，会返回false
            // trySuccess() will return false if a user cancelled the connection attempt.
            boolean promiseSet = promise.trySuccess();

            // Regardless if the connection attempt was cancelled, channelActive() event should be triggered,
            // because what happened is what happened.
            //4.到这Channel是活跃的，那么触发Active事件
            if (!wasActive && active) {
                pipeline().fireChannelActive();
            }

            // If a user cancelled the connection attempt, close the channel, which is followed by channelInactive().
            //5.操作已被取消，关闭Channel
            if (!promiseSet) {
                close(voidPromise());
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, Throwable cause) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            // 回调通知 promise 发生异常
            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(cause);
            // 关闭
            closeIfClosed();
        }

        /**
         * 结束连接
         * finishConnect()只由EventLoop处理就绪selectionKey的OP_CONNECT事件时调用，从而完成连接操作。
         * 注意：连接操作被取消或者超时不会使该方法被调用。
         */
        @Override
        public final void finishConnect() {
            // Note this method is invoked by the event loop only if the connection attempt was
            // neither cancelled nor timed out.
            //1.判断是否在EventLoop的线程中(当连接尝试没有取消或者超时的话，方法会被EventLoop调用)
            assert eventLoop().inEventLoop();

            try {
                //2.记录Channel是否激活
                boolean wasActive = isActive();
                //3.执行完成连接，由子类实现(模板模式)
                doFinishConnect();
                //4.通知connectPromise连接完成
                fulfillConnectPromise(connectPromise, wasActive);
            } catch (Throwable t) {
                //5.异常后，通知connectPromise连接异常
                fulfillConnectPromise(connectPromise, annotateConnectException(t, requestedRemoteAddress));
            } finally {
                //6.连接完成，取消超时检测任务
                // Check for null as the connectTimeoutFuture is only created if a connectTimeoutMillis > 0 is used
                // See https://github.com/netty/netty/issues/1770
                if (connectTimeoutFuture != null) {
                    connectTimeoutFuture.cancel(false);
                }
                connectPromise = null;
            }
        }

        @Override
        protected final void flush0() {
            // Flush immediately only when there's no pending flush.
            // If there's a pending flush operation, event loop will call forceFlush() later,
            // and thus there's no need to call it now.
            if (!isFlushPending()) {
                super.flush0();
            }
        }

        @Override
        public final void forceFlush() {
            // directly call super.flush0() to force a flush now
            super.flush0();
        }

        private boolean isFlushPending() {
            SelectionKey selectionKey = selectionKey();
            return selectionKey.isValid() // 合法
                    && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0; // 对 SelectionKey.OP_WRITE 事件不感兴趣。
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioEventLoop;
    }

    @Override
    protected void doRegister() throws Exception {
        boolean selected = false;
        for (; ; ) {
            try {
                selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
                return;
            } catch (CancelledKeyException e) {
                // TODO TODO 1003 doRegister 异常
                if (!selected) {
                    // Force the Selector to select now as the "canceled" SelectionKey may still be
                    // cached and not removed because no Select.select(..) operation was called yet.
                    eventLoop().selectNow();
                    selected = true;
                } else {
                    // We forced a select operation on the selector before but the SelectionKey is still cached
                    // for whatever reason. JDK bug ?
                    throw e;
                }
            }
        }
    }

    @Override
    protected void doDeregister() throws Exception {
        eventLoop().cancel(selectionKey());
    }

    @Override
    protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        readPending = true;

        final int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

    /**
     * Connect to the remote peer
     */
    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    /**
     * Finish the connect
     */
    protected abstract void doFinishConnect() throws Exception;

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the original one.
     * Note that this method does not create an off-heap copy if the allocation / deallocation cost is too high,
     * but just returns the original {@link ByteBuf}..
     */
    protected final ByteBuf newDirectBuffer(ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.safeRelease(buf);
            return Unpooled.EMPTY_BUFFER;
        }

        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            ByteBuf directBuf = alloc.directBuffer(readableBytes);
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(buf);
            return directBuf;
        }

        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
        if (directBuf != null) {
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(buf);
            return directBuf;
        }

        // Allocating and deallocating an unpooled direct buffer is very expensive; give up.
        return buf;
    }

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the specified holder.
     * The caller must ensure that the holder releases the original {@link ByteBuf} when the holder is released by
     * this method.  Note that this method does not create an off-heap copy if the allocation / deallocation cost is
     * too high, but just returns the original {@link ByteBuf}..
     */
    protected final ByteBuf newDirectBuffer(ReferenceCounted holder, ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.safeRelease(holder);
            return Unpooled.EMPTY_BUFFER;
        }

        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            ByteBuf directBuf = alloc.directBuffer(readableBytes);
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(holder);
            return directBuf;
        }

        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
        if (directBuf != null) {
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(holder);
            return directBuf;
        }

        // Allocating and deallocating an unpooled direct buffer is very expensive; give up.
        if (holder != buf) {
            // Ensure to call holder.release() to give the holder a chance to release other resources than its content.
            buf.retain();
            ReferenceCountUtil.safeRelease(holder);
        }

        return buf;
    }

    @Override
    protected void doClose() throws Exception {
        // 通知 connectPromise 异常失败
        ChannelPromise promise = connectPromise;
        if (promise != null) {
            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(DO_CLOSE_CLOSED_CHANNEL_EXCEPTION);
            connectPromise = null;
        }

        // 取消 connectTimeoutFuture 等待
        ScheduledFuture<?> future = connectTimeoutFuture;
        if (future != null) {
            future.cancel(false);
            connectTimeoutFuture = null;
        }
    }

}
