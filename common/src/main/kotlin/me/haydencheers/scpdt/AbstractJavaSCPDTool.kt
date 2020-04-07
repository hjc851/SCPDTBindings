package me.haydencheers.scpdt

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

abstract class AbstractJavaSCPDTool: SCPDTool {

    companion object {
        val java: Path
        val javaStr: String

        init {
            val java = when {
                System.getenv().containsKey("JAVA_HOME") -> Paths.get(System.getenv("JAVA_HOME")).resolve("bin/java")
                System.getProperties().containsKey("java.home") -> Paths.get(System.getProperty("java.home")).resolve("bin/java")
                else -> throw IllegalStateException("java.home is not passed by environment variable (JAVA_HOME) or system property (java.home)")
            }

            if (!Files.exists(java) || !Files.isExecutable(java)) {
                throw IllegalStateException("java.home does not exist, does not contain a java executable, or java is not executable")
            }

            this.java = java
            this.javaStr = java.toAbsolutePath().toString()
        }
    }


    protected fun runJava(vararg command: String, env: Map<String, String> = mutableMapOf(), asyncCallback: ((InputStream, OutputStream) -> Unit)? = null): JavaResult {

        val stdout = File.createTempFile("scpdt-java-exec", "io")
        val stderr = File.createTempFile("scpdt-java-exec", "io")

        val proc = ProcessBuilder()
            .apply { environment().putAll(env) }
            .command(javaStr, *command)
            .redirectOutput(stdout)
            .redirectError(stderr)
            .start()

        asyncCallback?.apply {
            CompletableFuture.runAsync {
                this(proc.inputStream, proc.outputStream)
            }
        }

        val result = proc.waitFor()

        return JavaResult (
            result,
            stdout.bufferedReader().lines(),
            stderr.bufferedReader().lines()
        )
    }

    data class JavaResult (
        val exitCode: Int,
        val out: Stream<String>,
        val err: Stream<String>
    ): Closeable {
        override fun close() {
            out.close()
            err.close()
        }
    }
}