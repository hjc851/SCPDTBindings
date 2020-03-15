package me.haydencheers.scpdt.sherlocksydney

import me.haydencheers.scpdt.SCPDTool
import me.haydencheers.scpdt.common.HungarianAlgorithm
import me.haydencheers.scpdt.util.TempUtil
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.max

object SherlockSydneySCPDT: SCPDTool {
    override val id: String
        get() = "Sherlock-Sydney"

    private val sherlock = this.javaClass.getResource("/sherlock-master/sherlock").path

    init {
        if (!Files.isExecutable(Paths.get(sherlock))) throw IllegalArgumentException("sherlock executable is not flagged as executable")
    }

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
        if (Files.list(ldir).use { it.count() } == 0.toLong()) return 0.0
        if (Files.list(rdir).use { it.count() } == 0.toLong()) return 0.0

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (dir, lhs, rhs) ->
            val out = Files.createTempFile(dir, "results", ".txt")

            val proc = ProcessBuilder()
                .command (
                    sherlock,
                    "-t",
                    "0%",
                    "-e",
                    "java",
                    "-r",
                    lhs.toAbsolutePath().toString(),
                    rhs.toAbsolutePath().toString()
                )
                .redirectOutput(out.toFile())
                .start()

            proc.waitFor()

            val output = Files.readAllLines(out)
            val sim =  calculateSimilarity(output, dir.toAbsolutePath().toString())

            return sim
        }
    }

    private fun calculateSimilarity(output: List<String>, rootDir: String): Double {
        val lFiles = mutableSetOf<String>()
        val rFiles = mutableSetOf<String>()
        val simMap = mutableMapOf<String, MutableMap<String, Double>>()

        for (line in output) {
            val components = line.split(";")

            val lhs = components[0].removePrefix(rootDir).removePrefix("/")
            val rhs = components[1].removePrefix(rootDir).removePrefix("/")
            val sim = components[2].removeSuffix("%").toDouble()

            if (lhs.startsWith("lhs") && rhs.startsWith("rhs")) {
                lFiles.add(lhs)
                rFiles.add(rhs)

                simMap.getOrPut(lhs) { mutableMapOf() }
                    .put(rhs, sim)

            } else if (lhs.startsWith("rhs") && rhs.startsWith("lhs")) {
                lFiles.add(rhs)
                rFiles.add(lhs)

                simMap.getOrPut(rhs) { mutableMapOf() }
                    .put(lhs, sim)
            }
        }

        if (lFiles.isEmpty() || rFiles.isEmpty())
            return 0.0

        val lIndicies = lFiles.mapIndexed { index, file -> file to index }.toMap()
        val rIndicies = rFiles.mapIndexed { index, file -> file to index }.toMap()
        val lFileNames = lFiles.mapIndexed { index, file -> index to file }.toMap()
        val rFileNames = rFiles.mapIndexed { index, file -> index to file }.toMap()

        val simMatrix  = Array(lFiles.size) { DoubleArray(rFiles.size) { 100.0 } }
        for ((lfile, rMap) in simMap) {
            val lIndex = lIndicies[lfile]!!
            for ((rfile, sim) in rMap) {
                val rIndex = rIndicies[rfile]!!
                simMatrix[lIndex][rIndex] = 100.0 - sim
            }
        }

        val hung = HungarianAlgorithm(simMatrix)
        val matches = hung.execute()

        val bestMatches = mutableListOf<Triple<String, String, Double>>()
        matches.forEachIndexed { lIndex, rIndex ->
            try {
                if (rIndex != -1) {
                    bestMatches.add(
                        Triple(lFileNames[lIndex]!!, rFileNames[rIndex]!!, 100.0 - simMatrix[lIndex][rIndex])
                    )
                }
            } catch (e: java.lang.Exception) {
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