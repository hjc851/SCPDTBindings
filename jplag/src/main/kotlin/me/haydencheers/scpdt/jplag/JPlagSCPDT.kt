package me.haydencheers.scpdt.jplag

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.util.TempUtil
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
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
}