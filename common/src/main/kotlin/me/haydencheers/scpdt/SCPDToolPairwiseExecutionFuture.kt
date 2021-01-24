package me.haydencheers.scpdt

import java.io.Closeable
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class SCPDToolPairwiseExecutionFuture(
    private val handle: Process,
    private val bundle: Any,
    private val completionDelegate: SCPDToolPairwiseExecutionCompletionDelegate
): Closeable {
    fun isFinished(): Boolean {
        return !handle.isAlive
    }

    fun interrupt() {
        handle.destroy()
    }

    fun getResult(): SCPDToolPairwiseExecutionResult {
        if (!isFinished()) throw IllegalStateException("Process is not finished!")
        return completionDelegate.complete(handle, bundle)
    }

    fun waitFor(time: Long, unit: TimeUnit): Boolean {
        return handle.waitFor(time, unit)
    }

    override fun close() {
        completionDelegate.close(this.handle, this.bundle)
    }
}

interface SCPDToolPairwiseExecutionCompletionDelegate {
    fun complete(handle: Process, bundle: Any): SCPDToolPairwiseExecutionResult
    fun close(handle: Process, bundle: Any)
}