package me.haydencheers.scpdt.plaggie

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.util.TempUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

object PlaggieSCPDT: AbstractJavaSCPDTool() {
    override val id: String
        get() = "Plaggie"

    private val jar = this.javaClass.getResource("/plaggie/plaggie-shell.jar").path
    private val lib = this.javaClass.getResource("/plaggie/java_cup.jar").path

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
        if (Files.list(ldir).use { it.count() } == 0.toLong()) return 0.0
        if (Files.list(rdir).use { it.count() } == 0.toLong()) return 0.0

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (tmp, lhs, rhs) ->
            val result = runJava(
                "-cp",
                "$jar:$lib",
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