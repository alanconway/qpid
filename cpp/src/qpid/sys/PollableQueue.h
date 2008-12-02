#ifndef QPID_SYS_POLLABLEQUEUE_H
#define QPID_SYS_POLLABLEQUEUE_H

/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include "qpid/sys/PollableCondition.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/DispatchHandle.h"
#include "qpid/sys/Monitor.h"
#include <boost/function.hpp>
#include <boost/bind.hpp>
#include <algorithm>
#include <deque>

namespace qpid {
namespace sys {

class Poller;

/**
 * A queue that can be polled by sys::Poller.  Any thread can push to
 * the queue, on wakeup the poller thread processes all items on the
 * queue by passing them to a callback in a batch.
 */
template <class T>
class PollableQueue {
  public:
    /**
     * Callback to process an item from the queue.
     *
     * @return If true the item is removed from the queue else it
     * remains on the queue and the queue is stopped.
     */
    typedef boost::function<bool (const T&)> Callback;

    /** When the queue is selected by the poller, values are passed to callback cb. */
    PollableQueue(const Callback& cb, const boost::shared_ptr<sys::Poller>& poller);

    ~PollableQueue();
    
    /** Push a value onto the queue. Thread safe */
    void push(const T& t);

    /** Start polling. */ 
    void start();

    /** Stop polling and wait for the current callback, if any, to complete. */
    void stop();

    /** Are we currently stopped?*/
    bool isStopped() const { ScopedLock l(lock); return stopped; }

    size_t size() { ScopedLock l(lock); return queue.size(); }
    bool empty() { ScopedLock l(lock); return queue.empty(); }
    
  private:
    typedef std::deque<T> Queue;
    typedef sys::Monitor::ScopedLock ScopedLock;
    typedef sys::Monitor::ScopedUnlock ScopedUnlock;

    void dispatch(sys::DispatchHandle&);
    
    mutable sys::Monitor lock;
    Callback callback;
    boost::shared_ptr<sys::Poller> poller;
    PollableCondition condition;
    DispatchHandle handle;
    Queue queue;
    Thread dispatcher;
    bool stopped;
};

template <class T> PollableQueue<T>::PollableQueue(
    const Callback& cb, const boost::shared_ptr<sys::Poller>& p) 
    : callback(cb), poller(p),
      handle(condition, boost::bind(&PollableQueue<T>::dispatch, this, _1), 0, 0), stopped(true)
{
    handle.startWatch(poller);
    handle.unwatch();
}

template <class T> void PollableQueue<T>::start() {
    ScopedLock l(lock);
    if (!stopped) return;
    stopped = false;
    if (!queue.empty()) condition.set();
    handle.rewatch();
}

template <class T> PollableQueue<T>::~PollableQueue() {
    handle.stopWatch();
}

template <class T> void PollableQueue<T>::push(const T& t) {
    ScopedLock l(lock);
    if (queue.empty()) condition.set();
    queue.push_back(t);
}

template <class T> void PollableQueue<T>::dispatch(sys::DispatchHandle& h) {
    ScopedLock l(lock);     // Prevent concurrent push
    assert(dispatcher.id() == 0 || dispatcher.id() == Thread::current().id());
    dispatcher = Thread::current();
    while (!stopped && !queue.empty()) {
        T value = queue.front();
        queue.pop_front();
        bool ok = false;
        {   // unlock to allow concurrent push or call to stop() in callback.
            ScopedUnlock u(lock);
            // FIXME aconway 2008-12-02: not exception safe if callback throws.
            ok = callback(value);
        }
        if (!ok) { // callback cannot process value,  put it back.
            queue.push_front(value);
            stopped=true;
        }
    }
    dispatcher = Thread();
    if (queue.empty()) condition.clear();
    if (stopped) lock.notifyAll();
    else h.rewatch();
}

template <class T> void PollableQueue<T>::stop() {
    ScopedLock l(lock);
    if (stopped) return;
    handle.unwatch();
    stopped = true;
    // Avoid deadlock if stop is called from the dispatch thread
    while (dispatcher.id() && dispatcher.id() != Thread::current().id())
        lock.wait();
}

}} // namespace qpid::sys

#endif  /*!QPID_SYS_POLLABLEQUEUE_H*/
