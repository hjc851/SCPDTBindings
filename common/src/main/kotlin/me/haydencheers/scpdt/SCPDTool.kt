package me.haydencheers.scpdt

import me.haydencheers.scpdt.util.TempUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import kotlin.streams.toList

interface SCPDTool: AutoCloseable {

    companion object {
        val SHARED_EXECUTION_POOL: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2)
    }

    val id: String

    /***
     * Evaluates the pairwise similarity of all assignment submissions in the provided directory
     * @dir: Root folder containing assignment submissions
     * @executor: Executor to enable parallel comparison (default n-2 threads, where n is number of virtual cores)
     */
    fun evaluateSubmissions (
        dir: Path,
        asyncErrorCallback: ((Throwable, String, String) -> Unit)? = null,
        executor: ExecutorService = SHARED_EXECUTION_POOL
    ): List<Triple<String, String, Double>> {
        if (!Files.isDirectory(dir)) throw IllegalArgumentException("dir must be a folder")

        val dirs = Files.list(dir)
            .filter { Files.isDirectory(it) && !Files.isHidden(it) }
            .use { it.toList() }

        val count = dirs.count() * (dirs.count()-1) / 2
        val sem = Semaphore(count)

        val submissionPairwiseResults = Collections.synchronizedList(mutableListOf<Triple<String, String, Double>>())

        for (l in 0 until dirs.size) {
            val ldir = dirs[l]
            val lname = ldir.fileName.toString()

            for (r in l+1 until dirs.size) {
                val rdir = dirs[r]
                val rname = rdir.fileName.toString()

                sem.acquire()

                CompletableFuture.runAsync(Runnable {
                    val sim = this.evaluatePairwise(ldir, rdir)
                    submissionPairwiseResults.add(Triple(lname, rname, sim))


                }, executor).whenComplete { void, throwable ->
                    sem.release()
                    throwable?.printStackTrace()
                    if (throwable != null) asyncErrorCallback?.invoke(throwable, lname, rname)
                }
            }
        }

        println("Awaiting ${count-sem.availablePermits()} permits")
        while (!sem.tryAcquire(count, 5, TimeUnit.SECONDS)) {
            println("Awaiting ${count-sem.availablePermits()} permits")
        }

        return submissionPairwiseResults
    }


    /***
     * Evaluates the submission in @ldir for similarity with the submission in @rdir
     * @ldir: Root folder of lhs submission
     * @rdir: Root folder of rhs submission
     */
    fun evaluatePairwise(ldir: Path, rdir: Path): Double

    /***
     * Evaluates all the pairwise similarities of files in lhs with rhs
     */
    fun evaluateAllFiles(ldir: Path, rdir: Path): List<Triple<String, String, Double>> {

        val lfiles = Files.walk(ldir)
            .filter { Files.isRegularFile(it) && !Files.isHidden(it) && it.fileName.toString().endsWith(".java") }
            .toList()

        val rfiles = Files.walk(rdir)
            .filter { Files.isRegularFile(it) && !Files.isHidden(it) && it.fileName.toString().endsWith(".java") }
            .toList()

        val scores = mutableListOf<Triple<String, String, Double>>()
        for (lfile in lfiles) {
            for (rfile in rfiles) {
                val sim = evaluateFiles(lfile, rfile)

                val lname = ldir.relativize(lfile).toString()
                val rname = rdir.relativize(rfile).toString()

                scores.add(Triple(lname, rname, sim))
            }
        }

        return scores
    }

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

    //
    //  Thaw
    //

    fun thaw() = thaw(Files.createTempDirectory("scpdtbindings-thaw"))

    /**
     * Thaw the current tool into the specified directory
     */
    fun thaw(path: Path)
}