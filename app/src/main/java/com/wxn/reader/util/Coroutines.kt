package com.wxn.reader.util

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Coroutines {

    fun scope() :CoroutineScope {
        val job = SupervisorJob()
        val dispatcher = Dispatchers.IO
        val exceptionHandler = CoroutineExceptionHandler{
                ctx, throwable ->
//            log(throwable)
            Logger.e(throwable)
        }
        return CoroutineScope(job + dispatcher + exceptionHandler)
    }

    fun mainScope(): CoroutineScope {
        val job = SupervisorJob()
        val dispatcher = Dispatchers.Main
        val exceptionHandler = CoroutineExceptionHandler{
                ctx, throwable ->
//            log(throwable)
            Logger.e(throwable)
        }
        return CoroutineScope(job + dispatcher + exceptionHandler)
    }

}