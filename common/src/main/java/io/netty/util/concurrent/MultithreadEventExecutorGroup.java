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
package io.netty.util.concurrent;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于多线程的 EventExecutor ( 事件执行器 ) 的分组抽象类,用多线程处理任务
 *
 * Abstract base class for {@link EventExecutorGroup} implementations that handles their tasks
 * with multiple threads atthe same time.
 */
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    /**
     * EventExecutor 数组，可以理解为一个 EventExecutor 对应一个线程
     */
    private final EventExecutor[] children;

    /**
     * (不可变)只读的EventExecutor数组
     *
     * @see #MultithreadEventExecutorGroup(int, Executor, EventExecutorChooserFactory, Object...)
     */
    private final Set<EventExecutor> readonlyChildren;

    /**
     * 已终止的EventExecutor数量
     */
    private final AtomicInteger terminatedChildren = new AtomicInteger();

    /**
     * 用于终止EventExecutor的异步 Future
     */
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);

    /**
     * EventExecutor选择器，可以定义
     */
    private final EventExecutorChooserFactory.EventExecutorChooser chooser;

    /**
     * Create a new instance.
     * 构造方法
     *
     * @param nThreads      the number of threads that will be used by this instance.
     * @param threadFactory the ThreadFactory to use, or {@code null} if the default should be used.
     * @param args          arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads the number of threads that will be used by this instance.
     * @param executor the Executor to use, or {@code null} if the default should be used.
     * @param args     arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    /**
     * Create a new instance.
     * 最核心的构造方法
     *
     * @param nThreads       the number of threads that will be used by this instance.
     * @param executor       the Executor to use, or {@code null} if the default should be used.
     * @param chooserFactory the {@link EventExecutorChooserFactory} to use.
     * @param args           arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory, Object... args) {
        //1.线程数不能小于0
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        //2.创建执行器，默认是每个任务一个线程
        if (executor == null) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        //3.创建children EventExecutor 数组
        children = new EventExecutor[nThreads];

        //4.循环创建 EventExecutor 数组中的EventExecutor
        for (int i = 0; i < nThreads; i++) {
            //是否创建成功
            boolean success = false;
            try {
                //5.创建EventExecutor对象，newChild是抽象方法交给子类实现，
                //不同的子类创建的执行器是不同类型的，有 NioEventLoop、KQueueEventLoop等
                children[i] = newChild(executor, args);
                //6.标记创建成功
                success = true;
            } catch (Exception e) {
                //7.创建失败抛出IllegalStateException异常
                //TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                //8.创建失败，关闭所有已创建的 EventExecutor;注意这个成功的标志位 success 是每次循环进来都会赋值，
                //因此相当于每一次都会去判断是否失败，只要有一个失败，就会把前面的创建好的EventExecutor关闭
                if (!success) {
                    //9.关闭所有已创建的 EventExecutor
                    for (int j = 0; j < i; j++) {
                        children[j].shutdownGracefully();
                    }
                    //10.确保所有已创建的 EventExecutor 已关闭，异常的话就等待直到关闭掉
                    for (int j = 0; j < i; j++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        //11.创建 EventExecutor 选择器，默认选择策略是轮询
        chooser = chooserFactory.newChooser(children);

        //12.创建监听器，用于 EventExecutor 终止时的监听
        final FutureListener<Object> terminationListener = new FutureListener<Object>() {

            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                //已经终止的EventExecutor数量等于总数
                if (terminatedChildren.incrementAndGet() == children.length) {
                    // 设置结果，并通知监听器们。
                    terminationFuture.setSuccess(null);
                }
            }

        };

        //13.给每个EventExecutor设置监听器
        for (EventExecutor e : children) {
            e.terminationFuture().addListener(terminationListener);
        }

        //14.创建不可变(只读)的EventExecutor数组,通过工具类返回一个只读的Set，里面包含的 EventExecutor和EventExecutor 数组是一样的
        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass());
    }

    /**
     * 获取一个事件执行器，用选择器来选一个，默认是轮询
     */
    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return readonlyChildren.iterator();
    }

    /**
     * 返回 EventExecutor 数量
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    public final int executorCount() {
        return children.length;
    }

    /**
     * 创建一个 EventExecutor ，后续next方法返回的就是EventExecutor，这个方法会被服务于 MultithreadEventExecutorGroup的线程调用
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    /**
     * 关闭，逐个关闭内部的 EventExecutor
     * */
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l : children) {
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() {
        for (EventExecutor l : children) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l : children) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isShutdown() {
        for (EventExecutor l : children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventExecutor l : children) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop:
        for (EventExecutor l : children) {
            for (; ; ) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    break loop;
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }
}
