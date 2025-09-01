package com.wxn.base.util

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Coroutines {

    fun scope() :CoroutineScope {
        val job = SupervisorJob()
        val dispatcher = Dispatchers.IO
        val exceptionHandler = CoroutineExceptionHandler{
                ctx, throwable ->
            Logger.e(throwable)
        }
        return CoroutineScope(job + dispatcher + exceptionHandler)
    }

    fun mainScope(): CoroutineScope {
        val job = SupervisorJob()
        val dispatcher = Dispatchers.Main
        val exceptionHandler = CoroutineExceptionHandler{
                ctx, throwable ->
            Logger.e(throwable)
        }
        return CoroutineScope(job + dispatcher + exceptionHandler)
    }

}

fun CoroutineScope.launchIO(exceptionHandler: ((Throwable)->Unit)? = null, block: suspend CoroutineScope.() -> Unit) {
    val job = SupervisorJob()
    val dispatcher = Dispatchers.IO
    val exceptionHandler = CoroutineExceptionHandler{
            ctx, throwable ->
        Logger.e(throwable)
        exceptionHandler?.invoke(throwable)
    }
    val context = this.coroutineContext + dispatcher + exceptionHandler + job
    launch(context = context, block = block)
}

fun CoroutineScope.launchMain(exceptionHandler: ((Throwable)->Unit)? = null, block: suspend CoroutineScope.() -> Unit) {
    val job = SupervisorJob()
    val dispatcher = Dispatchers.Main
    val exceptionHandler = CoroutineExceptionHandler{
            ctx, throwable ->
        Logger.e(throwable)
        exceptionHandler?.invoke(throwable)
    }
    val context = this.coroutineContext + dispatcher + exceptionHandler + job
    launch(context = context, block = block)
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