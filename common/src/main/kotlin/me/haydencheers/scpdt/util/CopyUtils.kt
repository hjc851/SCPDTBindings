package me.haydencheers.scpdt.util

import java.nio.file.Files
import java.nio.file.Path

object CopyUtils {
    /**
     * Copies all files in @from to @to
     */
    fun copyDir(from: Path, to: Path) {
        if (!Files.isDirectory(from)) throw IllegalArgumentException("File $from does not exist")

        Files.walk(from)
            .filter { !Files.isHidden(it) && !it.fileName.toString().startsWith(".") }
            .forEachOrdered { file ->
                Files.copy(file, to.resolve(from.relativize(file).toString()))
            }
    }
}