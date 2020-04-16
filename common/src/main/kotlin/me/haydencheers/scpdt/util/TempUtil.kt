package me.haydencheers.scpdt.util

import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

object TempUtil {
    data class TempInputTriple (
        val tempDir: Path,
        val lhsInput: Path,
        val rhsInput: Path
    ): Closeable {
        override fun close() {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .use { it.forEach(Files::delete) }
        }
    }

    fun copyPairwiseInputsToTempDirectory (
        ldir: Path,
        rdir: Path
    ): TempInputTriple {
        val tmp = Files.createTempDirectory("scpdt-tmp")
        val lhs = tmp.resolve("lhs")
        val rhs = tmp.resolve("rhs")

        // Copy left
        Files.walk(ldir)
            .forEachOrdered { src ->
                if (Files.isDirectory(src) || Files.isRegularFile(src) && src.fileName.toString().endsWith(".java"))
                    Files.copy(src, lhs.resolve(ldir.relativize(src)))
            }

        // Copy right
        Files.walk(rdir)
            .forEachOrdered { src ->
                if (Files.isDirectory(src) || Files.isRegularFile(src) && src.fileName.toString().endsWith(".java"))
                    Files.copy(src, rhs.resolve(rdir.relativize(src)))
            }

        return TempInputTriple(tmp, lhs, rhs)
    }

    fun makeTempDirectory(): Path {
        return Files.createTempDirectory("scpdt-tmp")
    }
}