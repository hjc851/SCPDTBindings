package me.haydencheers.scpdt

import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import kotlin.streams.toList

interface SCPDTool {

    companion object {
        val SHARED_EXECUTION_POOL: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()-2)
    }

    val id: String

    fun evaluatePairwise(ldir: Path, rdir: Path): Double

    fun evaluateDirectory (
        dir: Path,
        executor: ExecutorService = SHARED_EXECUTION_POOL
    ): DirectoryEvaluationResult {

        val dirs = Files.list(dir)
            .filter { Files.isDirectory(it) && !Files.isHidden(it) }
            .use { it.toList() }

        val count = dirs.count() * (dirs.count()-1) / 2
        val sem = Semaphore(count)

        val ids = mutableSetOf<String>()
        val results = Collections.synchronizedList(mutableListOf<Triple<String, String, Double>>())

        for (l in 0 until dirs.size) {
            val ldir = dirs[l]
            val lname = ldir.fileName.toString()
            ids.add(lname)

            for (r in l+1 until dirs.size) {
                val rdir = dirs[r]
                val rname = rdir.fileName.toString()
                ids.add(rname)

                sem.acquire()

                CompletableFuture.runAsync(Runnable {
                    val sim = this.evaluatePairwise(ldir, rdir)
                    results.add(Triple(lname, rname, sim))
                }, executor).whenComplete { void, throwable ->
                    throwable?.printStackTrace()
                    sem.release()
                }
            }
        }

        println("Awaiting ${count-sem.availablePermits()} permits")
        while (!sem.tryAcquire(count, 5, TimeUnit.SECONDS)) {
            println("Awaiting ${count-sem.availablePermits()} permits")
        }

        return DirectoryEvaluationResult(ids, results)
    }

    data class DirectoryEvaluationResult (
        val ids: Set<String>,
        val results: List<Triple<String, String, Double>>
    )
}