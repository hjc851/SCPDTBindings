package me.haydencheers.scpdt.util

import java.nio.file.Files
import java.nio.file.Path

object CopyUtils {
    /**
     * Copies all files in @from to @to
     */
    fun copyToDir(from: Path, to: Path) {
        if (!Files.isDirectory(from)) throw IllegalArgumentException("File $from does not exist")
        if (!Files.isDirectory(to)) throw IllegalArgumentException("File $to does not exist")

        Files.walk(from)
            .forEachOrdered { file ->
                Files.copy(file, to.resolve(from.relativize(file)))
            }
    }
}