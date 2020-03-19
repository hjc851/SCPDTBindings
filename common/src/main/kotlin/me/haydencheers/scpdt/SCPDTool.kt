package me.haydencheers.scpdt

import me.haydencheers.scpdt.util.TempUtil
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

    /***
     * Evaluates the submission in @ldir for similarity with the submission in @rdir
     * @ldir: Root folder of lhs submission
     * @rdir: Root folder of rhs submission
     */
    fun evaluatePairwise(ldir: Path, rdir: Path): Double

    /***
     * Evaluates two files lfile and rfile for similarity
     * @
     */
    fun evaluateFiles (
        lfile: Path,
        rfile: Path
    ): Double {
        if (!Files.isRegularFile(lfile)) throw IllegalArgumentException("lfile must be a .java file")
        if (!Files.isRegularFile(rfile)) throw IllegalArgumentException("rfile must be a .java file")

        val tmp = TempUtil.makeTempDirectory()
        val lroot = Files.createDirectory(tmp.resolve("lhs"))
        val rroot = Files.createDirectory(tmp.resolve("rhs"))

        Files.copy(lfile, lroot.resolve(lfile.fileName))
        Files.copy(rfile, rroot.resolve(rfile.fileName))

        try {
            return evaluatePairwise(lroot, rroot)
        } catch (e: Exception) {
            throw e
        } finally {
            Files.walk(tmp)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::delete)
        }
    }

    /***
     * Evaluates the pairwise similarity of all assignment submissions in the provided directory
     * @dir: Root folder containing assignment submissions
     * @executor: Executor to enable parallel comparison (default n-2 threads, where n is number of virtual cores)
     */
    fun evaluateDirectory (
        dir: Path,
        asyncErrorCallback: ((Throwable, String, String) -> Unit)? = null,
        executor: ExecutorService = SHARED_EXECUTION_POOL
    ): DirectoryEvaluationResult {
        if (!Files.isDirectory(dir)) throw IllegalArgumentException("dir must be a folder")

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
                    if (throwable != null) asyncErrorCallback?.invoke(throwable, lname, rname)
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