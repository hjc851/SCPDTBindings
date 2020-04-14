package me.haydencheers.scpdt.sherlocksydney

import me.haydencheers.scpdt.SCPDTool
import me.haydencheers.scpdt.common.HungarianAlgorithm
import me.haydencheers.scpdt.util.TempUtil
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import kotlin.math.max
import kotlin.streams.toList

class SherlockSydneySCPDT: SCPDTool {
    override val id: String
        get() = "Sherlock-Sydney"

    private val sherlockResourceName = "/sherlock-master/sherlock"

    private lateinit var sherlockPath: Path
    private lateinit var sherlockPathStr: String

    override fun thaw(path: Path) {
        val sherlockResource = this.javaClass.getResourceAsStream(sherlockResourceName)
        this.sherlockPath = path.resolve("sherlock")
        this.sherlockPathStr = sherlockPath.toAbsolutePath().toString()
        Files.copy(sherlockResource, sherlockPath)
        sherlockResource.close()

        val perms = mutableSetOf<PosixFilePermission>()
        perms.add(PosixFilePermission.OWNER_EXECUTE)
        Files.setPosixFilePermissions(sherlockPath, perms)
    }

    override fun close() {
        Files.delete(sherlockPath)
    }

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
        if (!::sherlockPath.isInitialized) throw IllegalStateException("Field sherlockPath is not thawed")

        if (Files.list(ldir).use { it.count() } == 0.toLong()) return 0.0
        if (Files.list(rdir).use { it.count() } == 0.toLong()) return 0.0

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (dir, lhs, rhs) ->
            val out = Files.createTempFile(dir, "results", ".txt")

            val proc = ProcessBuilder()
                .command (
                    sherlockPathStr,
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

    override fun evaluateAllFiles(ldir: Path, rdir: Path): List<Triple<String, String, Double>> {
        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (dir, lhs, rhs) ->
            val out = Files.createTempFile(dir, "results", ".txt")

            val proc = ProcessBuilder()
                .command (
                    sherlockPathStr,
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

            val lfiles = Files.walk(lhs)
                .filter { Files.isRegularFile(it) && !Files.isHidden(it) && it.fileName.toString().endsWith(".java") }
                .map { it.toAbsolutePath().toString() }
                .toList()
                .toTypedArray()

            val rfiles = Files.walk(rhs)
                .filter { Files.isRegularFile(it) && !Files.isHidden(it) && it.fileName.toString().endsWith(".java") }
                .map { it.toAbsolutePath().toString() }
                .toList()
                .toTypedArray()

            if (lfiles.isEmpty() || rfiles.isEmpty()) return emptyList()

            val output = Files.readAllLines(out)
            val scores = mutableListOf<Triple<String, String, Double>>()
            for (lfile in lfiles) {
                for (rfile in rfiles) {
                    for (line in output) {
                        if (line.contains(lfile) && line.contains(rfile)) {
                            val lpath = Paths.get(lfile)
                            val rpath = Paths.get(rfile)

                            val lf = lhs.relativize(lpath).toString()
                            val rf = rhs.relativize(rpath).toString()
                            val sim = line.split(";").last().removeSuffix("%").toDouble()

                            scores.add(Triple(lf, rf, sim))
                        }
                    }
                }
            }

            return scores
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

fun main() {
    val tool = SherlockSydneySCPDT()
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