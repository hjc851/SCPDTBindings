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
     * Evaluates the submission in @ldir for similarity with the submission in @rdir
     * @ldir: Root folder of lhs submission
     * @rdir: Root folder of rhs submission
     */
    fun evaluatePairwise(ldir: Path, rdir: Path): Double

    fun evaluatePairwiseAndFiles(ldir: Path, rdir: Path, executor: ExecutorService = SHARED_EXECUTION_POOL): PairwiseWithFileSimilarityResult {
        val sim = evaluatePairwise(ldir, rdir)

        val fileSimilarities = mutableListOf<Triple<String, String, Double>>()

        val lJavaFiles = Files.walk(ldir)
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
            .use { it.toList() }

        val rJavaFiles = Files.walk(rdir)
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
            .use { it.toList() }

        for (lfile in lJavaFiles) {
            val lname = ldir.relativize(lfile).toString()

            for (rfile in rJavaFiles) {
                 val rname = rdir.relativize(rfile).toString()

                val fsim = evaluateFiles(lfile, rfile)
                fileSimilarities.add(Triple(lname, rname, fsim))
            }
        }

        return PairwiseWithFileSimilarityResult(ldir.fileName.toString(), rdir.fileName.toString(), sim, fileSimilarities)
    }

    data class PairwiseWithFileSimilarityResult (
        val lhs: String,
        val rhs: String,
        val sim: Double,
        val fileSimilarities: List<Triple<String, String, Double>>
    )

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
    fun evaluateSubmissions (
        dir: Path,
        asyncErrorCallback: ((Throwable, String, String) -> Unit)? = null,
        executor: ExecutorService = SHARED_EXECUTION_POOL
    ): SubmissionSetEvaluationResult {
        if (!Files.isDirectory(dir)) throw IllegalArgumentException("dir must be a folder")

        val dirs = Files.list(dir)
            .filter { Files.isDirectory(it) && !Files.isHidden(it) }
            .use { it.toList() }

        val count = dirs.count() * (dirs.count()-1) / 2
        val sem = Semaphore(count)

        val submissionIds = mutableSetOf<String>()
        val submissionPairwiseResults = Collections.synchronizedList(mutableListOf<Triple<String, String, Double>>())

        for (l in 0 until dirs.size) {
            val ldir = dirs[l]
            val lname = ldir.fileName.toString()
            submissionIds.add(lname)

            for (r in l+1 until dirs.size) {
                val rdir = dirs[r]
                val rname = rdir.fileName.toString()
                submissionIds.add(rname)

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

        return SubmissionSetEvaluationResult(submissionIds, submissionPairwiseResults)
    }

    data class SubmissionSetEvaluationResult (
        val submissionIds: Set<String>,
        val pairwiseSubmissionSimilarities: List<Triple<String, String, Double>>
    )

    /***
     * Evaluates the pairwise similarity of all assignment submissions in the provided directory
     * @dir: Root folder containing assignment submissions
     * @executor: Executor to enable parallel comparison (default n-2 threads, where n is number of virtual cores)
     */
    fun evaluateSubmissionsAndFiles (
        dir: Path,
        asyncErrorCallback: ((Throwable, String, String) -> Unit)? = null,
        executor: ExecutorService = SHARED_EXECUTION_POOL
    ): SubmissionSetAndFilesEvaluationResult {
        if (!Files.isDirectory(dir)) throw IllegalArgumentException("dir must be a folder")

        val dirs = Files.list(dir)
            .filter { Files.isDirectory(it) && !Files.isHidden(it) }
            .use { it.toList() }

        val count = dirs.count() * (dirs.count()-1) / 2
        val sem = Semaphore(count)

        val submissionIds = mutableSetOf<String>()
        val submissionPairwiseResults = Collections.synchronizedList(mutableListOf<Triple<String, String, Double>>())
        val filewiseSubmissionResults = Collections.synchronizedMap(mutableMapOf<Pair<String, String>, List<Triple<String, String, Double>>>())

        for (l in 0 until dirs.size) {
            val ldir = dirs[l]
            val lname = ldir.fileName.toString()
            submissionIds.add(lname)

            val lJavaFiles = Files.walk(ldir)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }

            for (r in l+1 until dirs.size) {
                val rdir = dirs[r]
                val rname = rdir.fileName.toString()
                submissionIds.add(rname)

                val rJavaFiles = Files.walk(rdir)
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                    .use { it.toList() }

                sem.acquire()

                CompletableFuture.runAsync(Runnable {
                    val sim = this.evaluatePairwise(ldir, rdir)
                    submissionPairwiseResults.add(Triple(lname, rname, sim))

                    val key = lname to rname
                    val fsims = mutableListOf<Triple<String, String, Double>>()

                    for (ljfile in lJavaFiles) {
                        for (rjfile in rJavaFiles) {
                            val fsim = this.evaluateFiles(ljfile, rjfile)
                            val fsimtrip = Triple(
                                ldir.relativize(ljfile).toString(),
                                rdir.relativize(rjfile).toString(),
                                fsim
                            )
                            fsims.add(fsimtrip)
                        }
                    }

                    filewiseSubmissionResults[key] = fsims

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

        return SubmissionSetAndFilesEvaluationResult(submissionIds, submissionPairwiseResults, filewiseSubmissionResults)
    }

    data class SubmissionSetAndFilesEvaluationResult (
        val submissionIds: Set<String>,
        val pairwiseSubmissionSimilarities: List<Triple<String, String, Double>>,
        val filewiseSubmissionResults: Map<Pair<String, String>, List<Triple<String, String, Double>>>
    )

    //
    //  Thaw
    //

    fun thaw() = thaw(Files.createTempDirectory("scpdtbindings-thaw"))

    /**
     * Thaw the current tool into the specified directory
     */
    fun thaw(path: Path)
}