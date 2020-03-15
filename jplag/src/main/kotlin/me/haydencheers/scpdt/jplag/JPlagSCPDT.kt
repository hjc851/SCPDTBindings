package me.haydencheers.scpdt.jplag

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.util.TempUtil
import java.nio.file.Path
import java.nio.file.Files
import kotlin.streams.toList

object JPlagSCPDT: AbstractJavaSCPDTool() {
    override val id: String
        get() = "JPlag"

    private val jarPath = this.javaClass.getResource("/jplag-2.12.1-SNAPSHOT-jar-with-dependencies.jar").path

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
        if (Files.list(ldir).use { it.count() } == 0.toLong()) return 0.0
        if (Files.list(rdir).use { it.count() } == 0.toLong()) return 0.0

        TempUtil.copyPairwiseInputsToTempDirectory(ldir, rdir).use { (tmp, lhs, rhs) ->
            // Run the jar
            val result = this.runJava(
                "-jar",
                jarPath,
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

                result.close()
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
}