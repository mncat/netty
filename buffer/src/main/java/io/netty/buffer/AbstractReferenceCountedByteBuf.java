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

package io.netty.buffer;

import io.netty.util.IllegalReferenceCountException;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * ByteBuf 的用于实现引用计数的抽象类
 * Abstract base class for {@link ByteBuf} implementations that count references.
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {

    /**
     * {@link #refCnt} 一样计数的更新器，原子更新对象的引用计数成员变量，
     * 所有的ByteBuf实现类都继承自AbstractReferenceCountedByteBuf，因此对于所有的实例而言引用计数成员变量都是
     * AbstractReferenceCountedByteBuf类的 refCnt 字段
     */
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> refCntUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");

    /**
     * 引用计数成员变量，volatile保证可见性
     */
    private volatile int refCnt;

    /**
     * 一个ByteBuf对象一旦被创建，引用计数就是1
     */
    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        // 设置最大容量
        super(maxCapacity);
        // 初始 refCnt 为 1
        refCntUpdater.set(this, 1);
    }

    /**
     * 获取引用计数
     */
    @Override
    public int refCnt() {
        return refCnt;
    }

    /**
     * 直接修改 refCnt
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int refCnt) {
        refCntUpdater.set(this, refCnt);
    }

    /**
     * 引用计数自增
     */
    @Override
    public ByteBuf retain() {
        return retain0(1);
    }

    /**
     * 引用计数增加 increment，校验 increment 为正数
     */
    @Override
    public ByteBuf retain(int increment) {
        return retain0(checkPositive(increment, "increment"));
    }

    /**
     * 引用计数增加的实现方法
     */
    private ByteBuf retain0(final int increment) {
        //1.由更新器来实现增加，方法返回的是旧值
        int oldRef = refCntUpdater.getAndAdd(this, increment);
        //2.如果 旧值<=0，或者 increment<=0，那么就还原旧值，并且抛出异常
        if (oldRef <= 0 || oldRef + increment < oldRef) {
            // Ensure we don't resurrect (which means the refCnt was 0) and also that we encountered an overflow.
            // 加回去，负负得正。
            refCntUpdater.getAndAdd(this, -increment);
            // 抛出 IllegalReferenceCountException 异常
            throw new IllegalReferenceCountException(oldRef, increment);
        }
        return this;
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    /**
     * 引用计数释放
     */
    @Override
    public boolean release() {
        return release0(1);
    }

    /**
     * 引用计数释放 decrement
     */
    @Override
    public boolean release(int decrement) {
        return release0(checkPositive(decrement, "decrement"));
    }

    /**
     * 引用计数释放的实现方法
     */
    @SuppressWarnings("Duplicates")
    private boolean release0(int decrement) {
        //1.减少
        int oldRef = refCntUpdater.getAndAdd(this, -decrement);
        //2.oldRef等于减少的值，说明减去后为0，需要释放
        if (oldRef == decrement) {
            // 释放
            deallocate();
            return true;
            //3.减少的值得大于原有oldRef会导致减去后为负数，不允许，或者decrement为负数，也不允许
        } else if (oldRef < decrement || oldRef - decrement > oldRef) {
            // Ensure we don't over-release, and avoid underflow.
            // 加回去，负负得正。
            refCntUpdater.getAndAdd(this, decrement);
            // 抛出 IllegalReferenceCountException 异常
            throw new IllegalReferenceCountException(oldRef, -decrement);
        }
        return false;
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     * 当引用计数减少为0时，调用
     */
    protected abstract void deallocate();
}




























