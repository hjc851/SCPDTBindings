package me.haydencheers.scpdt.plaggie

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.util.TempUtil
import java.nio.file.Files
import java.nio.file.Path
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
            val output = result.out.toList()
                .dropWhile { !it.startsWith("lhs:rhs") }
            result.close()

            val score = output.first().split(":")[2]
            val sim = score.toDouble() * 100
            return sim
        }
    }
}