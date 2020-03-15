package me.haydencheers.scpdt.naive

import me.haydencheers.scpdt.AbstractJavaSCPDTool
import me.haydencheers.scpdt.util.TempUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

abstract class AbstractNaiveSCPDT : AbstractJavaSCPDTool() {
    abstract val className: String
    abstract val threshold: Int

    val jar = this.javaClass.getResource("/frontend-1.0-SNAPSHOT.jar").path

    override fun evaluatePairwise(ldir: Path, rdir: Path): Double {
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
                jar,
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
}
