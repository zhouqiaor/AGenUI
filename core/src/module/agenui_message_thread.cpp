#include "agenui_message_thread.h"
#include "agenui_logger_internal.h"
#include <chrono>

#if defined(__APPLE__)
#include <pthread.h>
#elif defined(__linux__) || defined(__ANDROID__) || defined(HARMONY)
#include <pthread.h>
#endif

namespace agenui {

// Stack size for the message thread. The default std::thread stack is ~8 MB
// on most platforms, which is insufficient for deeply nested Yoga layout
// recursion (YGLayoutNodeInternal uses one stack frame per tree level).
// 16 MB gives a safe margin for trees up to ~256 levels without hitting the
// OS thread stack limit. Only applied on Linux/Android/HarmonyOS where
// pthread stack size is directly controllable; Windows keeps std::thread.
#if defined(__linux__) || defined(__ANDROID__) || defined(HARMONY)
static constexpr size_t kMessageThreadStackSize = 16 * 1024 * 1024;  // 16 MB
#endif

MessageThread::MessageThread(const std::string& name) : _name(name), _isRunning(false), _shouldStop(false) {
}

MessageThread::~MessageThread() {
}

bool MessageThread::start() {
    if (_isRunning) {
        AGENUI_LOG("MessageThread already running");
        return true;
    }

    _shouldStop = false;
    _isRunning = true;

#if defined(__linux__) || defined(__ANDROID__) || defined(HARMONY)
    // Use pthread_create with a 16 MB stack to avoid Yoga layout recursion
    // stack overflow. std::thread uses the system default (~8 MB) which is
    // insufficient for deeply nested component trees during YGLayoutNodeInternal
    // recursion. This is platform-isolated: Linux/Android/HarmonyOS use
    // pthread with custom stack; Windows and Apple keep std::thread.
    pthread_attr_t attr;
    if (pthread_attr_init(&attr) != 0) {
        AGENUI_LOG("pthread_attr_init failed, falling back to std::thread");
        _workerThread = std::thread(&MessageThread::workerThreadLoop, this);
        _threadId = _workerThread.get_id();
        AGENUI_LOG("started (std::thread fallback), thread_id: %zu",
                   std::hash<std::thread::id>{}(_threadId));
        return true;
    }
    pthread_attr_setstacksize(&attr, kMessageThreadStackSize);

    auto entry = [](void* arg) -> void* {
        auto* self = static_cast<MessageThread*>(arg);
        self->workerThreadLoop();
        return nullptr;
    };

    pthread_t tid;
    int ret = pthread_create(&tid, &attr, entry, this);
    pthread_attr_destroy(&attr);

    if (ret != 0) {
        AGENUI_LOG("pthread_create failed (ret=%d), falling back to std::thread", ret);
        _workerThread = std::thread(&MessageThread::workerThreadLoop, this);
        _threadId = _workerThread.get_id();
        AGENUI_LOG("started (std::thread fallback), thread_id: %zu",
                   std::hash<std::thread::id>{}(_threadId));
        return true;
    }

    // Store native handle so join() works in stop()
    _nativeThread = tid;
    _useNativeThread = true;
    _threadId = std::thread::id{};

    AGENUI_LOG("started (pthread, stack=%zuMB), tid: %lu",
               kMessageThreadStackSize / (1024 * 1024),
               (unsigned long)tid);
#else
    // Windows / Apple: use std::thread with default stack
    _workerThread = std::thread(&MessageThread::workerThreadLoop, this);
    _threadId = _workerThread.get_id();

    AGENUI_LOG("started (std::thread), thread_id: %zu",
               std::hash<std::thread::id>{}(_threadId));
#endif
    return true;
}

void MessageThread::stop() {
    if (!_isRunning) {
        return;
    }

    // Signal stop
    _shouldStop = true;
    _isRunning = false;

    // Wake up the worker thread
    _condition.notify_one();

    // Drain the task queues
    {
        std::lock_guard<std::mutex> lock(_queueMutex);
        while (!_taskQueue.empty()) {
            _taskQueue.pop();
        }
        while (!_delayedTaskQueue.empty()) {
            _delayedTaskQueue.pop();
        }
    }

    // Wait for the worker thread to exit
#if defined(__linux__) || defined(__ANDROID__) || defined(HARMONY)
    if (_useNativeThread) {
        void* retval = nullptr;
        pthread_join(_nativeThread, &retval);
        _useNativeThread = false;
    } else if (_workerThread.joinable()) {
        _workerThread.join();
    }
#else
    if (_workerThread.joinable()) {
        _workerThread.join();
    }
#endif

    AGENUI_LOG("stopped");
}

void MessageThread::post(std::function<void()> task) {
    if (!_isRunning) {
        AGENUI_LOG("MessageThread not running, task ignored");
        return;
    }

    if (!task) {
        return;
    }

    // Enqueue the task
    {
        std::lock_guard<std::mutex> lock(_queueMutex);
        _taskQueue.push(task);
    }

    // Wake up the worker thread
    _condition.notify_one();
}

void MessageThread::postDelayed(std::function<void()> task, unsigned long delayMillis) {
    if (!_isRunning) {
        AGENUI_LOG("MessageThread not running, delayed task ignored");
        return;
    }

    if (!task) {
        return;
    }

    // Compute execution time
    auto executeTime = std::chrono::steady_clock::now() + std::chrono::milliseconds(delayMillis);

    // Enqueue the delayed task
    {
        std::lock_guard<std::mutex> lock(_queueMutex);
        _delayedTaskQueue.push({task, executeTime});
    }

    // Wake up the worker thread
    _condition.notify_one();
}

bool MessageThread::isRunning() const {
    return _isRunning;
}

std::thread::id MessageThread::getThreadId() const {
    return _threadId;
}

void MessageThread::workerThreadLoop() {
    // Set thread name for debugger identification
#if defined(__APPLE__)
    pthread_setname_np(_name.c_str());
#elif defined(__linux__) || defined(__ANDROID__) || defined(HARMONY)
    pthread_setname_np(pthread_self(), _name.c_str());
#endif

    AGENUI_LOG("[%s] worker loop started", _name.c_str());

    while (!_shouldStop) {
        std::function<void()> task;
        bool hasTask = false;

        // Dequeue a task
        {
            std::unique_lock<std::mutex> lock(_queueMutex);

            // Promote expired delayed tasks
            processDelayedTasks();

            // Compute wait duration
            std::chrono::milliseconds waitTime(100); // default 100ms
            if (!_delayedTaskQueue.empty()) {
                auto now = std::chrono::steady_clock::now();
                auto nextTaskTime = _delayedTaskQueue.top().executeTime;
                if (nextTaskTime > now) {
                    waitTime = std::chrono::duration_cast<std::chrono::milliseconds>(nextTaskTime - now);
                }
            }

            // Wait for a task or stop signal
            if (_taskQueue.empty() && !_shouldStop) {
                _condition.wait_for(lock, waitTime, [this] {
                    return !_taskQueue.empty() || _shouldStop;
                });
            }

            // Exit if stopped and all queues are empty
            if (_shouldStop && _taskQueue.empty() && _delayedTaskQueue.empty()) {
                break;
            }

            // Dequeue next task
            if (!_taskQueue.empty()) {
                task = _taskQueue.front();
                _taskQueue.pop();
                hasTask = true;
            }
        }

        // Execute
        if (hasTask && task) {
            task();
        }
    }

    AGENUI_LOG("[%s] worker loop stopped", _name.c_str());
}

void MessageThread::processDelayedTasks() {
    auto now = std::chrono::steady_clock::now();

    // Move expired delayed tasks to the main queue
    while (!_delayedTaskQueue.empty()) {
        const auto& delayedTask = _delayedTaskQueue.top();
        if (delayedTask.executeTime <= now) {
            _taskQueue.push(delayedTask.task);
            _delayedTaskQueue.pop();
        } else {
            break;
        }
    }
}

} // namespace agenui
