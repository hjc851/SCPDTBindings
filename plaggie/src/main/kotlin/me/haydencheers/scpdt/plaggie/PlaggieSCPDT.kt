package me.haydencheers.scpdt.plaggie

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.SCPDToolPairwiseExecutionFuture
import me.haydencheers.scpdt.SCPDToolPairwiseExecutionResult
import me.haydencheers.scpdt.util.TempUtil
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

class PlaggieSCPDT: AbstractJavaSCPDTool() {
    override val id: String
        get() = "Plaggie"

    private val jarResourceName = "/plaggie/plaggie-shell.jar"
    private val dependencyResourceName = "/plaggie/java_cup.jar"

    private lateinit var jarPath: Path
    private lateinit var jarPathStr: String

    private lateinit var dependencyPath: Path
    private lateinit var dependencyPathStr: String

    override fun thaw(path: Path) {
        val jarResource = this.javaClass.getResourceAsStream(jarResourceName)
        this.jarPath = path.resolve("plaggie-shell.jar")
        this.jarPathStr = jarPath.toAbsolutePath().toString()
        Files.copy(jarResource, jarPath)
        jarResource.close()

        val dependencyResource = this.javaClass.getResourceAsStream(dependencyResourceName)
        this.dependencyPath = path.resolve("java_cup.jar")
        this.dependencyPathStr = dependencyPath.toAbsolutePath().toString()
        Files.copy(dependencyResource, dependencyPath)
        dependencyResource.close()
    }

    override fun close() {
        Files.delete(jarPath)
        Files.delete(dependencyPath)
    }

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
        if (!::jarPath.isInitialized) throw IllegalStateException("Field jarPath is not thawed")
        if (!::dependencyPath.isInitialized) throw IllegalStateException("Field dependencyPath is not thawed")

        if (Files.list(ldir).use { it.count() } == 0.toLong()) return 0.0
        if (Files.list(rdir).use { it.count() } == 0.toLong()) return 0.0

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (tmp, lhs, rhs) ->
            val result = runJava(
                "-cp",
                "$jarPathStr:$dependencyPathStr",
                "plag.parser.plaggie.PShell",
                tmp.toAbsolutePath().toString()
            )

            // Handle error
            if (result.exitCode != 0) {
                val out = result.out.toList()
                val err = result.err.toList()

                result.close()
                throw IllegalStateException("Received error code ${result.exitCode}")
            }

            // Get the console output
            val out = result.out.toList()
            val score = out.last()

            result.close()

            val scoreVal = score.split(":")[2]
            val sim = scoreVal.toDouble() * 100
            return sim
        }
    }

    override fun evaluateAllFiles(ldir: Path, rdir: Path): List<Triple<String, String, Double>> {
        if (!::jarPath.isInitialized) throw IllegalStateException("Field jarPath is not thawed")
        if (!::dependencyPath.isInitialized) throw IllegalStateException("Field dependencyPath is not thawed")

        val lfiles = Files.walk(ldir)
            .filter { Files.isRegularFile(it) && !Files.isHidden(it) && it.fileName.toString().endsWith(".java") }
            .map { it.toAbsolutePath().toString() }
            .toList()
            .toTypedArray()

        val rfiles = Files.walk(rdir)
            .filter { Files.isRegularFile(it) && !Files.isHidden(it) && it.fileName.toString().endsWith(".java") }
            .map { it.toAbsolutePath().toString() }
            .toList()
            .toTypedArray()

        if (lfiles.isEmpty() || rfiles.isEmpty()) return emptyList()

        val result = runJava(
            "-cp",
            "$jarPathStr:$dependencyPathStr",
            "plag.parser.plaggie.PFShell",
            *lfiles,
            *rfiles
        )

        val lines = result.out.toList()
            .takeLastWhile { it != "----RESULTS----" }

        result.close()

        val scores = mutableListOf<Triple<String, String, Double>>()
        for (lfile in lfiles) {
            for (rfile in rfiles) {
                for (line in lines) {
                    if (line.contains(lfile) && line.contains(rfile)) {
                        val lpath = Paths.get(lfile)
                        val rpath = Paths.get(rfile)

                        val lf = ldir.relativize(lpath).toString()
                        val rf = rdir.relativize(rpath).toString()
                        val sim = line.split(":").last().toDouble()

                        scores.add(Triple(lf, rf, sim))
                    }
                }
            }
        }

        return scores
    }

    //
    //  Async
    //

    override fun executePairwiseAsync(ldir: Path, rdir: Path): SCPDToolPairwiseExecutionFuture {
        if (!::jarPath.isInitialized) throw IllegalStateException("Field jarPath is not thawed")

        if (Files.list(ldir).use { it.count() } == 0.toLong()) throw IllegalStateException("LHS is empty")
        if (Files.list(rdir).use { it.count() } == 0.toLong()) throw IllegalStateException("RHS is empty")

        val tmpHandle = TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir)
        val tmp = tmpHandle.tempDir

        val procHandle = runJavaAsync(
            "-cp",
            "$jarPathStr:$dependencyPathStr",
            "plag.parser.plaggie.PShell",
            tmp.toAbsolutePath().toString()
        )

        return SCPDToolPairwiseExecutionFuture(
            procHandle.proc,
            JavaAsyncBundle(tmpHandle, procHandle),
            this
        )
    }

    override fun complete(handle: Process, bundle: Any): SCPDToolPairwiseExecutionResult {
        val bundle = bundle as JavaAsyncBundle

        val proc = bundle.procHandle.proc
        val stdout = bundle.procHandle.stdout
        val stderr = bundle.procHandle.stderr

        if (proc.exitValue() != 0) {
            return SCPDToolPairwiseExecutionResult.Error("Received error code ${proc.exitValue()}")
        }

        // Get the console output
        val out = stdout.readLines()
        val score = out.last()

        val scoreVal = score.split(":")[2]
        val sim = scoreVal.toDouble() * 100
        return SCPDToolPairwiseExecutionResult.Success(sim)
    }

    override fun close(handle: Process, bundle: Any) {
        handle.destroy()
        (bundle as? Closeable)?.close()
    }
}

fun main() {
    val tool = PlaggieSCPDT()
    tool.thaw()

    val root = Paths.get("/media/haydencheers/Data/PrEP/datasets/Algorithms")
    val sims = tool.evaluateSubmissions(root)

    val dirs = Files.list(root)
        .filter { Files.isDirectory(it) && !Files.isHidden(it) }
        .toList()

    for (l in 0 until dirs.size) {
        val ldir = dirs[l]

        for (r in l+1 until dirs.size) {
            val rdir = dirs[r]

            val res = tool.evaluateAllFiles(ldir, rdir)
            Unit
        }
    }

    tool.close()
}