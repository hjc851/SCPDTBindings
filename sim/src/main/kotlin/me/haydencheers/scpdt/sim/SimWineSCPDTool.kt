package me.haydencheers.scpdt.sim

import me.haydencheers.scpdt.SCPDTool
import me.haydencheers.scpdt.common.HungarianAlgorithm
import me.haydencheers.scpdt.util.TempUtil
import java.io.File
import java.lang.IllegalArgumentException
import java.lang.Integer.max
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

class SimWineSCPDTool: SCPDTool {
    override val id: String
        get() = "Sim-3.0.2_Wine32"

    private val wine: Path
    private val wineStr: String

    constructor(): this(Paths.get(System.getenv("WINE_EXEC") ?: "/usr/bin/wine"))

    constructor(wine: Path) {
        this.wine = wine
        this.wineStr = wine.toAbsolutePath().toString()

        if (!Files.exists(wine) || !Files.isExecutable(wine)) throw IllegalArgumentException("wine executable does not exist, or is not executable")
    }

    private val simResourceName = "/sim_exe_3_0_2/sim_java.exe"

    private lateinit var simPath: Path
    private lateinit var simPathStr: String

    override fun thaw(path: Path) {
        val simResource = this.javaClass.getResourceAsStream(simResourceName)
        this.simPath = path.resolve("sim_java.exe")
        this.simPathStr = simPath.toAbsolutePath().toString()
        Files.copy(simResource, simPath)
        simResource.close()
    }

    override fun close() {
        Files.delete(simPath)
    }

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
        if (!::simPath.isInitialized) throw IllegalStateException("Field simPath is not thawed")

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

            val proc = ProcessBuilder()
                .command(
                    wineStr,
                    simPathStr,
                    "-R",
                    "-s",
                    "-p",
                    lhs.toAbsolutePath().toString(),
                    rhs.toAbsolutePath().toString()
                ).start()

            proc.waitFor()

            val output = proc.inputStream.bufferedReader().use { it.readLines() }

            if (proc.exitValue() != 0) {
                val err = proc.errorStream.bufferedReader().use { it.readLine() }
                throw IllegalStateException("Received error code ${proc.exitValue()}")
            }

            val sim = calculateSimilarity(output, tmp.toAbsolutePath().toString())
            return sim
        }
    }

    override fun evaluateAllFiles(ldir: Path, rdir: Path): List<Triple<String, String, Double>> {
        if (!::simPath.isInitialized) throw IllegalStateException("Field simPath is not thawed")

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (tmp, lhs, rhs) ->
            val lJavaFiles = Files.walk(lhs)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }
                .map { it.toAbsolutePath().toString() }

            val rJavaFiles = Files.walk(rhs)
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .use { it.toList() }
                .map { it.toAbsolutePath().toString() }

            if (lJavaFiles.isEmpty() || rJavaFiles.isEmpty()) return emptyList()

            val proc = ProcessBuilder()
                .command(
                    wineStr,
                    simPathStr,
                    "-R",
                    "-s",
                    "-p",
                    lhs.toAbsolutePath().toString(),
                    rhs.toAbsolutePath().toString()
                ).start()

            proc.waitFor()

            val output = proc.inputStream.bufferedReader().use { it.readLines() }

            if (proc.exitValue() != 0) {
                val err = proc.errorStream.bufferedReader().use { it.readLine() }
                throw IllegalStateException("Received error code ${proc.exitValue()}")
            }

            val lFiles = mutableListOf<String>()
            val rFiles = mutableListOf<String>()

            var i = 0

            val rootDir = tmp.toAbsolutePath().toString()
            while (i < output.size) {
                val line = output[i]

                if (line.startsWith("Total input:")) {
                    val lineNoPrefix = line.removePrefix("Total input: ")
                    val afterNumberIndex = lineNoPrefix.indexOf(" files (")
                    val number = lineNoPrefix.substring(0, afterNumberIndex)

                } else if (line == "") {
                    i++
                    break

                } else {
                    val file = line.removePrefix("File ")
                        .split(": ")[0]
                        .removePrefix(rootDir)
                        .removePrefix(File.separator)

                    if (file.startsWith("lhs") && file.endsWith(".java"))
                        lFiles.add(file)
                    else if (file.startsWith("rhs") && file.endsWith(".java"))
                        rFiles.add(file)
                }

                i++
            }

            if (lFiles.isEmpty() || rFiles.isEmpty()) return emptyList()

            val sims = mutableListOf<Triple<String, String, Double>>()
            while (i < output.size) {
                val line = output[i]

                val component1 = line.split(" consists for ")
                val component2 = component1[1].split(" % of ")

                val sim = component2[0].toDouble()
                val lFile = component1[0].removePrefix(rootDir).removePrefix(File.separator)
                val rFile = component2[1].removeSuffix(" material").removePrefix(rootDir).removePrefix(File.separator)

                if (lFile.startsWith("lhs") && rFile.startsWith("rhs")) {
                    val lIndex = lFiles.indexOf(lFile)
                    val rIndex = rFiles.indexOf(rFile)

                    if (lIndex != -1 && rIndex != -1) {
                        sims.add(Triple(lFile.removePrefix("lhs/"), rFile.removePrefix("rhs/"), sim))
                    }

                } else if (rFile.startsWith("lhs") && lFile.startsWith("rhs")) {
                    val lIndex = rFiles.indexOf(rFile)
                    val rIndex = lFiles.indexOf(lFile)

                    if (lIndex != -1 && rIndex != -1) {
                        sims.add(Triple(rFile.removePrefix("lhs/"), lFile.removePrefix("rhs/"), sim))
                    }
                }

                i++
            }

            return sims
        }
    }

    private fun calculateSimilarity(output: List<String>, rootDir: String): Double {
        val lFiles = mutableListOf<String>()
        val rFiles = mutableListOf<String>()

        var i = 0

        while (i < output.size) {
            val line = output[i]

            if (line.startsWith("Total input:")) {
                val lineNoPrefix = line.removePrefix("Total input: ")
                val afterNumberIndex = lineNoPrefix.indexOf(" files (")
                val number = lineNoPrefix.substring(0, afterNumberIndex)

            } else if (line == "") {
                i++
                break

            } else {
                val file = line.removePrefix("File ")
                    .split(": ")[0]
                    .removePrefix(rootDir)
                    .removePrefix(File.separator)

                if (file.startsWith("lhs") && file.endsWith(".java"))
                    lFiles.add(file)
                else if (file.startsWith("rhs") && file.endsWith(".java"))
                    rFiles.add(file)
            }

            i++
        }

        if (lFiles.isEmpty() || rFiles.isEmpty())
            return 0.0

        val simMatrix  = Array(lFiles.size) { DoubleArray(rFiles.size) { 100.0 } }

        while (i < output.size) {
            val line = output[i]

            val component1 = line.split(" consists for ")
            val component2 = component1[1].split(" % of ")

            val sim = component2[0].toDouble()
            val lFile = component1[0].removePrefix(rootDir).removePrefix(File.separator)
            val rFile = component2[1].removeSuffix(" material").removePrefix(rootDir).removePrefix(File.separator)

            if (lFile.startsWith("lhs") && rFile.startsWith("rhs")) {
                val lIndex = lFiles.indexOf(lFile)
                val rIndex = rFiles.indexOf(rFile)

                if (lIndex != -1 && rIndex != -1)
                    simMatrix[lIndex][rIndex] = 100.0 - sim

            } else if (rFile.startsWith("rhs") && lFile.startsWith("lhs")) {
                val lIndex = rFiles.indexOf(rFile)
                val rIndex = lFiles.indexOf(lFile)

                if (lIndex != -1 && rIndex != -1)
                    simMatrix[lIndex][rIndex] = 100.0 - sim
            }

            i++
        }

        val hung = HungarianAlgorithm(simMatrix)
        val matches = hung.execute()

        val bestMatches = mutableListOf<Triple<String, String, Double>>()
        matches.forEachIndexed { lIndex, rIndex ->
            try {
                if (rIndex != -1) {
                    bestMatches.add(
                        Triple(lFiles[lIndex], rFiles[rIndex], 100.0 - simMatrix[lIndex][rIndex])
                    )
                }
            } catch (e: Exception) {
                throw e
            }
        }

        val maxFileCount = max(lFiles.size, rFiles.size)
        val sim = bestMatches.map { it.third }
            .toTypedArray()
            .copyOf(maxFileCount)
            .map { it ?: 0.0 }
            .average()

        return sim
    }
}

fun main() {
    val tool = SimWineSCPDTool()
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