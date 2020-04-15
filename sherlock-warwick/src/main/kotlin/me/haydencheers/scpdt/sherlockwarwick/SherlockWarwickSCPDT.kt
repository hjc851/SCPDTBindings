package me.haydencheers.scpdt.sherlockwarwick

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.common.HungarianAlgorithm
import me.haydencheers.scpdt.util.TempUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.max
import kotlin.streams.toList

class SherlockWarwickSCPDT: AbstractJavaSCPDTool() {
    override val id: String
        get() = "Sherlock-Warwick"

    private val sherlockResourceName = "/sherlock-warwick/sherlock.jar"
    private val shellResourceName = "/sherlock-warwick/SWShell.jar"

    private lateinit var sherlockJar: Path
    private lateinit var sherlockJarStr: String

    private lateinit var shellJar: Path
    private lateinit var shellJarStr: String

    override fun thaw(path: Path) {
        val sherlockResource = this.javaClass.getResourceAsStream(sherlockResourceName)
        this.sherlockJar = path.resolve("sherlock.jar")
        this.sherlockJarStr = sherlockJar.toAbsolutePath().toString()
        Files.copy(sherlockResource, sherlockJar)
        sherlockResource.close()

        val shellResource = this.javaClass.getResourceAsStream(shellResourceName)
        this.shellJar = path.resolve("SWShell.jar")
        this.shellJarStr = shellJar.toAbsolutePath().toString()
        Files.copy(shellResource, shellJar)
        shellResource.close()
    }

    override fun close() {
        Files.delete(sherlockJar)
        Files.delete(shellJar)
    }

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
        if (!::sherlockJar.isInitialized) throw IllegalStateException("Field sherlockJar is not thawed")
        if (!::shellJar.isInitialized) throw IllegalStateException("Field shellJar is not thawed")

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

            val result = runJava(
                "-cp",
                "$sherlockJarStr:$shellJarStr",
                "uk.ac.warwick.dcs.cobalt.sherlock.SWShell",
                tmp.toAbsolutePath().toString(),
                "-p",
                "-d",
                "-v"
            )

            if (lJavaFiles.isEmpty() || rJavaFiles.isEmpty()) return 0.0

            // Handle error
            if (result.exitCode != 0) {
                val out = result.out.toList()
                val err = result.err.toList()

                result.close()
                throw IllegalStateException("Received error code ${result.exitCode}")
            }

            // Get the console output
            val output = result.out.toList()
            result.close()

            val mappings = output.dropWhile { it != "====File Id Mappings====" }
                .drop(1)
                .dropLastWhile { it != "====Results====" }
                .dropLast(1)

            val fileIdMappings = mappings.map { it.split(":") }
                .map { it[1] to it[0] }
                .toMap()

            val results = output.takeLastWhile { it != "====Results====" }

            val mappedResults = results.map { it.split(":") }
                .mapNotNull {
                    val l = it[0].split("/")
                        .last()
                        .split(".")
                        .dropLast(1)
                        .joinToString(".")
                        .let { fileIdMappings.getValue(it) }

                    val r = it[1].split("/")
                        .last()
                        .split(".")
                        .dropLast(1)
                        .joinToString(".")
                        .let { fileIdMappings.getValue(it) }

                    val sim = it[2].toDouble()

                    if (l.contains("/lhs/") && r.contains("/rhs/")) {
                        return@mapNotNull Triple(l, r, sim)
                    } else if (l.contains("/rhs/") && r.contains("/lhs/")) {
                        return@mapNotNull Triple(r, l, sim)
                    } else {
                        return@mapNotNull null
                    }
                }.groupBy { it.first to it.second }
                .map { Triple(it.key.first, it.key.second, it.value.map { it.third }.max() ?: 0.0) }

            if (mappedResults.isEmpty())
                return 0.0

            return calculateSim(lJavaFiles, rJavaFiles, mappedResults)
        }
    }

    override fun evaluateAllFiles(ldir: Path, rdir: Path): List<Triple<String, String, Double>> {
        if (!::sherlockJar.isInitialized) throw IllegalStateException("Field sherlockJar is not thawed")
        if (!::shellJar.isInitialized) throw IllegalStateException("Field shellJar is not thawed")

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (tmp, lhs, rhs) ->
            val lJavaFiles = Files.walk(lhs)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }
                .map { it.toAbsolutePath().toString() }

            val rJavaFiles = Files.walk(rhs)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }
                .map { it.toAbsolutePath().toString() }

            val result = runJava(
                "-cp",
                "$sherlockJarStr:$shellJarStr",
                "uk.ac.warwick.dcs.cobalt.sherlock.SWShell",
                tmp.toAbsolutePath().toString(),
                "-p",
                "-d",
                "-v"
            )

            if (lJavaFiles.isEmpty() || rJavaFiles.isEmpty()) return emptyList()

            // Handle error
            if (result.exitCode != 0) {
                val out = result.out.toList()
                val err = result.err.toList()

                result.close()
                throw IllegalStateException("Received error code ${result.exitCode}")
            }

            // Get the console output
            val output = result.out.toList()
            result.close()

            val mappings = output.dropWhile { it != "====File Id Mappings====" }
                .drop(1)
                .dropLastWhile { it != "====Results====" }
                .dropLast(1)

            val fileIdMappings = mappings.map { it.split(":") }
                .map { it[1] to it[0] }
                .toMap()

            val results = output.takeLastWhile { it != "====Results====" }

            val mappedResults = results.map { it.split(":") }
                .mapNotNull {
                    val l = it[0].split("/")
                        .last()
                        .split(".")
                        .dropLast(1)
                        .joinToString(".")
                        .let { fileIdMappings.getValue(it) }

                    val r = it[1].split("/")
                        .last()
                        .split(".")
                        .dropLast(1)
                        .joinToString(".")
                        .let { fileIdMappings.getValue(it) }

                    val sim = it[2].toDouble()

                    if (l.contains("/lhs/") && r.contains("/rhs/")) {
                        return@mapNotNull Triple(l, r, sim)
                    } else if (l.contains("/rhs/") && r.contains("/lhs/")) {
                        return@mapNotNull Triple(r, l, sim)
                    } else {
                        return@mapNotNull null
                    }
                }.groupBy { it.first to it.second }
                .map { Triple(it.key.first, it.key.second, it.value.map { it.third }.max() ?: 0.0) }

            val allResultsMap = mutableMapOf<String, MutableMap<String, Double>>()
            for (lfile in lJavaFiles) {
                for (rfile in rJavaFiles) {
                    allResultsMap.getOrPut(lfile) {mutableMapOf()} [rfile] = 0.0
                }
            }

            for (result in mappedResults) {
                allResultsMap.getValue(result.first)
                    .put(result.second, result.third)
            }

            val allResults = allResultsMap.flatMap { (l, values) ->
                val lpath = Paths.get(l)

                values.map { (r, score) ->
                    val rpath = Paths.get(r)

                    Triple(lhs.relativize(lpath).toString(), rhs.relativize(rpath).toString(), score)
                }
            }

            return allResults
        }
    }

    private fun calculateSim(
        lJavaFiles: List<String>,
        rJavaFiles: List<String>,
        mappedResults: List<Triple<String, String, Double>>
    ): Double {
        val simMap = mutableMapOf<String, MutableMap<String, Double>>()
        for (result in mappedResults) {
            simMap.getOrPut(result.first, ::mutableMapOf)
                .put(result.second, result.third)
        }

        val simMatrix = Array(lJavaFiles.size) { DoubleArray(rJavaFiles.size) { 100.0 } }
        lJavaFiles.forEachIndexed { l, lfile ->
            rJavaFiles.forEachIndexed { r, rfile ->
                val sim = simMap.get(lfile)?.get(rfile) ?: 0.0
                simMatrix[l][r] = 100.0 - sim
            }
        }

        val hung = HungarianAlgorithm(simMatrix)
        val matches = hung.execute()

        val bestMatches = mutableListOf<Triple<String, String, Double>>()
        matches.forEachIndexed { lIndex, rIndex ->
            try {
                if (rIndex != -1) {
                    bestMatches.add(
                        Triple(lJavaFiles[lIndex]!!, rJavaFiles[rIndex]!!, 100.0 - simMatrix[lIndex][rIndex])
                    )
                }
            } catch (e: java.lang.Exception) {
                throw e
            }
        }

        val maxFileCount = max(lJavaFiles.size, rJavaFiles.size)
        val sim = bestMatches.map { it.third }
            .toTypedArray()
            .copyOf(maxFileCount)
            .map { it ?: 0.0 }
            .average()

        return sim
    }
}

fun main() {
    val tool = SherlockWarwickSCPDT()
    tool.thaw()

    val root = Paths.get("/media/haydencheers/Data/PrEP/datasets/COMP2240_2018_A1")

    val dirs = Files.list(root)
        .filter { Files.isDirectory(it) && !Files.isHidden(it) }
        .toList()

    for (l in 0 until dirs.size) {
        val ldir = dirs[l]

        for (r in l+1 until dirs.size) {
            val rdir = dirs[r]

            val res = tool.evaluateAllFiles(ldir, rdir)
        }
    }

    println("Done")
    tool.close()
}