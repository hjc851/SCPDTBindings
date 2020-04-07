package me.haydencheers.scpdt.jplag

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.SCPDTool
import me.haydencheers.scpdt.util.CopyUtils
import me.haydencheers.scpdt.util.TempUtil
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ExecutorService
import java.util.stream.Collectors
import kotlin.math.roundToInt
import kotlin.streams.toList

class JPlagSCPDT: AbstractJavaSCPDTool() {
    override val id: String
        get() = "JPlag"

    private val jarResourceName = "/jplag-2.12.1-SNAPSHOT-jar-with-dependencies.jar"

    private lateinit var jarPath: Path
    private lateinit var jarPathStr: String

    override fun thaw(path: Path) {
        val jarResource = this.javaClass.getResourceAsStream(jarResourceName)
        this.jarPath = path.resolve("jplag.jar")
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
            // Run the jar
            val result = this.runJava(
                "-jar",
                jarPathStr,
                "-l",
                "java19",
                "-r",
                tmp.toAbsolutePath().toString(),
                "-s",
                tmp.toAbsolutePath().toString()
            )

            // Handle error
            if (result.exitCode != 0) {
                val out = result.out.toList()
                val err = result.err.toList()

                val lfiles = Files.walk(ldir).filter { Files.isRegularFile(it) }.use { it.toList() }
                val rfiles = Files.walk(rdir).filter { Files.isRegularFile(it) }.use { it.toList() }

                result.close()

                for (line in out)
                    if (line.contains("Not enough valid submissions"))
                        return -1.0

                throw IllegalStateException("Received error code ${result.exitCode}")
            }

            // Get the console output
            val output = result.out.toList()
            result.close()

            val jplagoutput = output.drop(5)
                .dropLast(2)

            // Parse the similarity score
            val sim = jplagoutput.last().split(": ").last().toDouble()
            return sim
        }
    }

    override fun evaluateSubmissions (
        dir: Path,
        asyncErrorCallback: ((Throwable, String, String) -> Unit)?,
        executor: ExecutorService
    ): List<Triple<String, String, Double>> {
        if (!::jarPath.isInitialized) throw IllegalStateException("Field jarPath is not thawed")

        val _tmp = Files.createTempDirectory("jplag-exec")

        val tmpRoot = _tmp.resolve("jplag-src")
        CopyUtils.copyDir(dir, tmpRoot)

        val tmp = Files.createDirectory(_tmp.resolve("jplag-results"))

        val dirs = Files.list(dir)
            .filter { Files.isDirectory(it) && !Files.isHidden(it) }
            .toList()

        val count = ((dirs.size / 2.0) * (dirs.size-1 + 0)).roundToInt()

        val result = this.runJava (
            "-jar",
            jarPathStr,
            "-vld",
            "-l",
            "java19",
            "-r",
            tmp.toAbsolutePath().toString(),
            "-s",
            tmpRoot.toAbsolutePath().toString()
        )

        Files.walk(_tmp)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        if (result.exitCode != 0) {
            val out = result.out.toList()
            val err = result.err.toList()

            result.close()
            throw IllegalStateException("Received error code ${result.exitCode}")
        }

        val ids = Files.list(dir)
            .filter { Files.isDirectory(it) && !Files.isHidden(it) }
            .toList()
            .map { it.fileName.toString() }
            .toSet()

        val jplagout = result.out.toList()
        val scores = jplagout.dropLast(4)
            .takeLast(count)
            .map(this::splitLine)

        result.close()

        return scores
    }

    private fun splitLine(line: String): Triple<String, String, Double> {
        val cmp1 = line.removePrefix("Comparing ").split(": ")

        val names = cmp1[0]
        val cmp2 = names.split("-")

        val lhs = cmp2[0]
        val rhs = cmp2[1]
        val score = cmp1[1].toDouble()

        return Triple(lhs, rhs, score)
    }

    override fun evaluateAllFiles(ldir: Path, rdir: Path): List<Triple<String, String, Double>> {
        if (!::jarPath.isInitialized) throw IllegalStateException("Field jarPath is not thawed")

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

        val tmpResults = Files.createTempDirectory("jplag-results")

        val result = this.runJava (
            "-jar",
            jarPathStr,
            "-vld",
            "-l",
            "java19",
            "-r",
            tmpResults.toAbsolutePath().toString(),
            "-c",
            *lfiles,
            *rfiles
        )

        Files.walk(tmpResults)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)

        if (result.exitCode != 0) {
            val out = result.out.toList()
            val err = result.err.toList()

            result.close()
            throw IllegalStateException("Received error code ${result.exitCode}")
        }

        val comparingLines = result.out.toList()
            .dropLast(4)
            .takeLastWhile { it.startsWith("Comparing ") }

        result.close()

        val scores = mutableListOf<Triple<String, String, Double>>()
        for (lfile in lfiles) {
            for (rfile in rfiles) {
                for (line in comparingLines) {
                    if (line.contains(lfile) && line.contains(rfile)) {
                        val lpath = Paths.get(lfile)
                        val rpath = Paths.get(rfile)

                        val lf = ldir.relativize(lpath).toString()
                        val rf = rdir.relativize(rpath).toString()
                        val sim = line.split(": ").last().toDouble()

                        scores.add(Triple(lf, rf, sim))
                    }
                }
            }
        }

        return scores
    }
}

fun main() {
    val tool = JPlagSCPDT()
    tool.thaw()

    val root = Paths.get("/home/haydencheers/Desktop/SENG1110A12017-ALL")
    val result = tool.evaluateSubmissions(root)

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