package me.haydencheers.scpdt.naive

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.SCPDToolPairwiseExecutionFuture
import me.haydencheers.scpdt.SCPDToolPairwiseExecutionResult
import me.haydencheers.scpdt.naive.graph.NaivePDGEditDistanceSCPDT
import me.haydencheers.scpdt.naive.string.NaiveStringEditDistanceSCPDT
import me.haydencheers.scpdt.naive.string.NaiveStringTilingSCPDT
import me.haydencheers.scpdt.naive.token.NaiveTokenEditDistanceSCPDT
import me.haydencheers.scpdt.naive.token.NaiveTokenTilingSCPDT
import me.haydencheers.scpdt.naive.tree.NaiveTreeEditDistanceSCPDT
import me.haydencheers.scpdt.util.TempUtil
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

abstract class AbstractNaiveSCPDT : AbstractJavaSCPDTool() {
    abstract val className: String
    abstract val filewiseClassName: String
    abstract val threshold: Int

    private val jarResourceName = "/frontend-1.0-SNAPSHOT.jar"

    private lateinit var jarPath: Path
    private lateinit var jarPathStr: String

    override fun thaw(path: Path) {
        val jarResource = this.javaClass.getResourceAsStream(jarResourceName)
        this.jarPath = path.resolve("naive-frontend.jar")
        this.jarPathStr = jarPath.toAbsolutePath().toString()
        Files.copy(jarResource, jarPath)
        jarResource.close()
    }

    override fun close() {
        Files.delete(jarPath)
    }

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
        if (!::jarPath.isInitialized) throw IllegalStateException("Field jarPath is not thawed")

        if (Files.list(ldir).use { it.count() } == 0.toLong()) return 0.0
        if (Files.list(rdir).use { it.count() } == 0.toLong()) return 0.0

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (tmp, lhs, rhs) ->
            val lJavaFiles = Files.walk(lhs)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }
                .map { it.toAbsolutePath().toString() }

            val rJavaFiles = Files.walk(rhs)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }
                .map { it.toAbsolutePath().toString() }

            val result = runJava (
                "-cp",
                jarPathStr,
                className,
                lhs.toAbsolutePath().toString(),
                rhs.toAbsolutePath().toString(),
                env = mapOf("THRESHOLD" to threshold.toString())
            )

            if (lJavaFiles.isEmpty() || rJavaFiles.isEmpty()) return 0.0

            if (result.exitCode != 0) {
                val out = result.out.toList()
                val err = result.err.toList()

                result.close()
                throw IllegalStateException("Received error code ${result.exitCode}")
            }

            val out = result.out.toList()
            if (out.size != 2) {
                throw IllegalStateException("Invalid output, expecting two lines")
            }

            val sim = out.drop(1)
                .single()
                .split(":")
                .last()
                .toDouble()

            result.close()
            return sim * 100
        }
    }

    override fun evaluateAllFiles(ldir: Path, rdir: Path): List<Triple<String, String, Double>> {
        if (!::jarPath.isInitialized) throw IllegalStateException("Field jarPath is not thawed")

        if (Files.list(ldir).use { it.count() } == 0.toLong()) return emptyList()
        if (Files.list(rdir).use { it.count() } == 0.toLong()) return emptyList()

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (tmp, lhs, rhs) ->
            val lJavaFiles = Files.walk(lhs)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }
                .map { it.toAbsolutePath().toString() }

            val rJavaFiles = Files.walk(rhs)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }
                .map { it.toAbsolutePath().toString() }

            val result = runJava (
                "-cp",
                jarPathStr,
                filewiseClassName,
                lhs.toAbsolutePath().toString(),
                rhs.toAbsolutePath().toString(),
                env = mapOf("THRESHOLD" to threshold.toString())
            )

            if (result.exitCode != 0) {
                val out = result.out.toList()
                val err = result.err.toList()

                result.close()
                throw IllegalStateException("Received error code ${result.exitCode}")
            }

            val out = result.out.toList()
                .drop(1)

            val scores = mutableListOf<Triple<String, String, Double>>()
            for (line in out) {
                val components = line.split(":")

                val lpath = Paths.get(components[0])
                val rpath = Paths.get(components[1])
                val sim = components[2].toDouble()

                scores.add(Triple(lhs.relativize(lpath).toString(), rhs.relativize(rpath).toString(), sim))
            }

            result.close()
            return scores
        }
    }

    //
    //  Async
    //

    override fun executePairwiseAsync(ldir: Path, rdir: Path): SCPDToolPairwiseExecutionFuture {
        if (!::jarPath.isInitialized) throw IllegalStateException("Tool is not thawed")

        if (Files.list(ldir).use { it.count() } == 0.toLong()) throw IllegalStateException("LHS is empty")
        if (Files.list(rdir).use { it.count() } == 0.toLong()) throw IllegalStateException("RHS is empty")

        val tmpHandle = TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir)
        val (tmp, lhs, rhs) = tmpHandle

        val lJavaFiles = Files.walk(lhs)
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
            .use { it.toList() }
            .map { it.toAbsolutePath().toString() }

        val rJavaFiles = Files.walk(rhs)
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
            .use { it.toList() }
            .map { it.toAbsolutePath().toString() }

        if (lJavaFiles.isEmpty() || rJavaFiles.isEmpty()) throw IllegalStateException("LHS or RHS is empty")

        val procHandle = runJavaAsync (
            "-cp",
            jarPathStr,
            className,
            lhs.toAbsolutePath().toString(),
            rhs.toAbsolutePath().toString(),
            env = mapOf("THRESHOLD" to threshold.toString())
        )

        return SCPDToolPairwiseExecutionFuture(procHandle.proc, JavaAsyncBundle(tmpHandle, procHandle), this)
    }

    override fun complete(handle: Process, bundle: Any): SCPDToolPairwiseExecutionResult {
        bundle as JavaAsyncBundle

        val proc = bundle.procHandle.proc
        val stdout = bundle.procHandle.stdout
        val stderr = bundle.procHandle.stderr

        if (proc.exitValue() != 0) {
            return SCPDToolPairwiseExecutionResult.Error("Received error code ${proc.exitValue()}")
        }

        val out = stdout.readLines()
        if (out.size != 2) {
            throw IllegalStateException("Invalid output, expecting two lines")
        }

        val sim = out.drop(1)
            .single()
            .split(":")
            .last()
            .toDouble()

        return SCPDToolPairwiseExecutionResult.Success(sim * 100)
    }

    override fun close(handle: Process, bundle: Any) {
        handle.destroy()
        (bundle as? Closeable)?.close()
    }
}

fun main() {
    val tool = NaivePDGEditDistanceSCPDT()
    tool.thaw()

    val root = Paths.get("/media/haydencheers/Data/PrEP/datasets/Algorithms")

    val dirs = Files.list(root)
        .filter { Files.isDirectory(it) && !Files.isHidden(it) }
        .toList()

    for (l in 0 until dirs.size) {
        val ldir = dirs[l]

        (l+1 until dirs.size).toList()
            .parallelStream()
            .forEach { r ->
                val rdir = dirs[r]

                println("$l vs $r")
                val res = tool.evaluateAllFiles(ldir, rdir)
            }
    }

    println("Done")
    tool.close()
}