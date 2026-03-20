package com.wxn.base.util

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object Coroutines {

    //使用 AtomicReference 保证线程安全
    private val appScopeRef = AtomicReference<CoroutineScope>(null)
    private val appMainScopeRef = AtomicReference<CoroutineScope>(null)

    //标记是否已初始化
    private val isInitialized = AtomicBoolean(false)

    private fun createScope() :CoroutineScope {
        val job = SupervisorJob()
        val dispatcher = Dispatchers.IO
        val exceptionHandler = CoroutineExceptionHandler{
                ctx, throwable ->
            Logger.e(throwable)
        }
        return CoroutineScope(job + dispatcher + exceptionHandler)
    }

    private fun createMainScope(): CoroutineScope {
        val job = SupervisorJob()
        val dispatcher = Dispatchers.Main
        val exceptionHandler = CoroutineExceptionHandler{
                ctx, throwable ->
            Logger.e(throwable)
        }
        return CoroutineScope(job + dispatcher + exceptionHandler)
    }

    /**
     * 初始化 Application 级别的 CoroutineScope
     * 应该在 BookApplication.onCreate() 中调用
     *
     * @param appScope Application 级别的 IO Scope
     * @param appMainScope Application 级别的 Main Scope
     */
    fun init(appScope: CoroutineScope, appMainScope: CoroutineScope) {
        if (isInitialized.compareAndSet(false, true)) {
            appScopeRef.set(appScope)
            appMainScopeRef.set(appMainScope)
            Logger.i("Coroutines: Application scope initialized")
        } else {
            Logger.w("Coroutines: Already initialized, skipping")
        }
    }

    /**
     * 释放所有 Application 级别的 CoroutineScope
     * 应该在 BookApplication.onTerminate() 或 onLowMemory() 中调用
     *
     * 注意：onTerminate 在生产环境不保证调用，建议在 onLowMemory() 或 onTrimMemory() 中也调用
     */
//    fun release() {
//        val scope = appScopeRef.getAndSet(null)
//        val mainScope = appMainScopeRef.getAndSet(null)
//        if (scope != null) {
//            scope.cancel()
//            Logger.i("Coroutines: Application scope released")
//        }
//        if (mainScope != null) {
//            mainScope.cancel()
//            Logger.i("Coroutines: Application main scope released")
//        }
//        isInitialized.set(false)
//    }

    /**
     * 获取 Application 级别的 CoroutineScope (IO)
     * 如果未初始化，返回临时的降级 Scope
     */
    fun scope(): CoroutineScope {
        return appScopeRef.get() ?: createScope()
    }

    /**
     * 获取 Application 级别的 CoroutineScope (Main)
     * 如果未初始化，返回临时的降级 Scope
     */
    fun mainScope(): CoroutineScope {
        return appMainScopeRef.get() ?: createMainScope()
    }
}

fun CoroutineScope.launchIO(exceptionHandler: ((Throwable)->Unit)? = null, block: suspend CoroutineScope.() -> Unit): Job {
    val dispatcher = Dispatchers.IO
    val exceptionHandler = CoroutineExceptionHandler{
            ctx, throwable ->
        Logger.e(throwable)
        exceptionHandler?.invoke(throwable)
    }
    val context = this.coroutineContext + dispatcher + exceptionHandler
    return launch(context = context, block = block)
}

fun CoroutineScope.launchMain(exceptionHandler: ((Throwable)->Unit)? = null, block: suspend CoroutineScope.() -> Unit): Job {
    val dispatcher = Dispatchers.Main
    val exceptionHandler = CoroutineExceptionHandler{
            ctx, throwable ->
        Logger.e(throwable)
        exceptionHandler?.invoke(throwable)
    }
    val context = this.coroutineContext + dispatcher + exceptionHandler
    return launch(context = context, block = block)
}

/****
 * 如果block运行抛出异常，则尝试attempts次，每次间隔delayBetweenAttempts 毫秒
 * 返回block运行的结果
 */
suspend fun <T> retry(
    attempts: Int = 3,
    delayBetweenAttempts: Long = 1000L,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    repeat(attempts) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt < attempts - 1) {
                delay(delayBetweenAttempts)
            }
        }
    }
    throw lastException ?: IllegalStateException("Retry failed")
}