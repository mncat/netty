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
package io.netty.util;

/**
 * netty引用计数接口
 * <p>
 * <p>
 * 一个引用计数对象要求显示的 deallocation (可以理解为显示的释放内存，allocate的反义词)
 * A reference-counted object that requires explicit deallocation.
 * <p>
 * ReferenceCounted初始化的时候,引用计数是1，
 * 调用retain()方法引用计数加一，调用release()计数减一
 * 如果引用计数减少到{@code 0}，则将显式释放对象，访问该释放了的对象通常会导致访问非法。
 * When a new {@link ReferenceCounted} is instantiated, it starts with the reference count of {@code 1}.
 * {@link #retain()} increases the reference count, and {@link #release()} decreases the reference count.
 * If the reference count is decreased to {@code 0}, the object will be deallocated explicitly, and accessing
 * the deallocated object will usually result in an access violation.
 * </p>
 * <p>
 * 如果实现了 ReferenceCounted 接口的是一个容器对象，并且这个对象包含另一个也实现了 ReferenceCounted 接口的对象
 * 那么当容器对象的引用计数变为0的时候，内部被包含的对象也会被释放
 * If an object that implements {@link ReferenceCounted} is a container of other objects that implement
 * {@link ReferenceCounted}, the contained objects will also be released via {@link #release()} when the container's
 * reference count becomes 0.
 * </p>
 */
public interface ReferenceCounted {

    /**
     * 获得对象的引用计数，如果是0以为这对象会被释放
     * <p>
     * Returns the reference count of this object.  If {@code 0}, it means this object has been deallocated.
     */
    int refCnt();

    /**
     * 引用计数加1
     * <p>
     * Increases the reference count by {@code 1}.
     */
    ReferenceCounted retain();

    /**
     * 引用计数加 n
     * <p>
     * Increases the reference count by the specified {@code increment}.
     */
    ReferenceCounted retain(int increment);

    /**
     * 等价于调用 `#touch(null)` 方法，即 hint 方法参数传递为 null 。
     * <p>
     * Records the current access location of this object for debugging purposes.
     * If this object is determined to be leaked, the information recorded by this operation will be provided to you
     * via {@link ResourceLeakDetector}.  This method is a shortcut to {@link #touch(Object) touch(null)}.
     */
    ReferenceCounted touch();

    /**
     * 出于调试目的,用一个额外的任意的(arbitrary)信息记录这个对象的当前访问地址.
     * 如果这个对象被检测到泄露了, 这个操作记录的信息将通过ResourceLeakDetector 提供.
     * <p>
     * <p>
     * Records the current access location of this object with an additional arbitrary information for debugging
     * purposes.  If this object is determined to be leaked, the information recorded by this operation will be
     * provided to you via {@link ResourceLeakDetector}.
     */
    ReferenceCounted touch(Object hint);

    /**
     * 引用计数减1
     * 当引用计数为 0 时，释放，当且仅当引用计数为0时，对象会被释放
     * <p>
     * Decreases the reference count by {@code 1} and deallocates this object if the reference count reaches at
     * {@code 0}.
     *
     * @return {@code true} if and only if the reference count became {@code 0} and this object has been deallocated
     */
    boolean release();

    /**
     * 引用计数减n
     * 当引用计数为 0 时，释放，当且仅当引用计数为0时，对象会被释放
     * <p>
     * Decreases the reference count by the specified {@code decrement} and deallocates this object if the reference
     * count reaches at {@code 0}.
     *
     * @return {@code true} if and only if the reference count became {@code 0} and this object has been deallocated
     */
    boolean release(int decrement);
}
