package net.dontdrinkandroot.sara

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.Job
import net.dontdrinkandroot.sara.SignalInterruptSource.install
import platform.posix.SIGINT
import platform.posix._exit
import platform.posix.signal
import kotlin.concurrent.AtomicReference

/**
 * Source of user-initiated interrupts (e.g. Ctrl+C / SIGINT).
 *
 * Allows the REPL to observe and consume an interrupt request, and to bind the currently
 * running turn [Job] so the signal handler can cancel it. Injected into [Sara] for
 * testability: production uses [SignalInterruptSource], tests use a fake.
 */
interface InterruptSource {
    /**
     * Atomically consumes a pending interrupt. Returns true if an interrupt was pending
     * (and is now cleared), false otherwise.
     */
    fun consumeInterrupt(): Boolean

    /**
     * Binds the [job] that represents the currently running turn, or null when no turn is
     * running. The signal handler cancels this job when an interrupt arrives.
     */
    fun setTurnJob(job: Job?)
}

/**
 * Production [InterruptSource] backed by a POSIX SIGINT handler.
 *
 * Install once at startup via [install]. A second SIGINT before the first is consumed
 * force-exits the process (safety net for stuck blocking calls).
 */
@OptIn(ExperimentalForeignApi::class)
object SignalInterruptSource : InterruptSource {

    private object InterruptMarker

    private val interruptFlag = AtomicReference<Any?>(null)
    private val currentTurnJob = AtomicReference<Job?>(null)

    private val sigintHandler = staticCFunction { _: Int ->
        if (interruptFlag.value != null) {
            _exit(130)
        }
        interruptFlag.value = InterruptMarker
        currentTurnJob.value?.cancel()
        Unit
    }

    /**
     * Installs the SIGINT handler. Must be called once before the REPL loop starts.
     */
    fun install() {
        signal(SIGINT, sigintHandler)
    }

    override fun consumeInterrupt(): Boolean = interruptFlag.compareAndSet(InterruptMarker, null)

    override fun setTurnJob(job: Job?) {
        currentTurnJob.value = job
    }
}
