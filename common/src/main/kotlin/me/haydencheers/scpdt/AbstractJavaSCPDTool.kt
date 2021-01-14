package me.haydencheers.scpdt

import me.haydencheers.scpdt.util.TempUtil
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

abstract class AbstractJavaSCPDTool: SCPDTool {

    companion object {
        val java: Path
        val javaStr: String

        var mxHeap: String? = null

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

    data class JavaAsyncBundle(
        val tmpHandle: TempUtil.TempInputTriple,
        val procHandle: JavaExecAsyncHandle
    ): Closeable {
        override fun close() {
            tmpHandle.close()
            procHandle.close()
        }
    }

    data class JavaExecAsyncHandle(
        val proc: Process,
        val stdout: File,
        val stderr: File
    ): Closeable {
        override fun close() {
            stdout.delete()
            stderr.delete()
        }
    }

    protected fun runJavaAsync(
        vararg command: String, env: Map<String, String> = mutableMapOf()
    ): JavaExecAsyncHandle {
        val stdout = File.createTempFile("scpdt-java-exec-out", "io")
        val stderr = File.createTempFile("scpdt-java-exec-err", "io")

        val cmd = mutableListOf<String>()
        cmd.add(javaStr)
        if (mxHeap != null) cmd.add("-Xmx${mxHeap}")
        cmd.addAll(command)

        val proc = ProcessBuilder()
            .apply { environment().putAll(env) }
            .command(cmd)
            .redirectOutput(stdout)
            .redirectError(stderr)
            .start()

        return JavaExecAsyncHandle(
            proc, stdout, stderr
        )
    }

    protected fun runJava(vararg command: String, env: Map<String, String> = mutableMapOf(), asyncCallback: ((InputStream, OutputStream) -> Unit)? = null): JavaResult {
        val stdout = File.createTempFile("scpdt-java-exec-out", "io")
        val stderr = File.createTempFile("scpdt-java-exec-err", "io")

        val cmd = mutableListOf<String>()
        cmd.add(javaStr)
        if (mxHeap != null) cmd.add("-Xmx${mxHeap}")
        cmd.addAll(command)

        val proc = ProcessBuilder()
            .apply { environment().putAll(env) }
            .command(cmd)
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
            stderr.bufferedReader().lines(),
            stdout,
            stderr
        )
    }

    data class JavaResult (
        val exitCode: Int,
        val out: Stream<String>,
        val err: Stream<String>,
        private val tmpOut: File,
        private val tmpErr: File
    ): Closeable {
        override fun close() {
            out.close()
            err.close()

            tmpOut.delete()
            tmpErr.delete()
        }
    }
}