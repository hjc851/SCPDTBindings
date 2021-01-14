//import me.haydencheers.scpdt.SCPDTool
//import me.haydencheers.scpdt.jplag.JPlagSCPDT
//import org.junit.Test
//import java.nio.file.Files
//import java.nio.file.Paths
//import java.util.concurrent.CompletableFuture
//import java.util.concurrent.Semaphore
//import kotlin.math.roundToInt
//import kotlin.streams.toList
//
//class JPlagTest {
//
//    @Test
//    fun test() {
//        val tool = JPlagSCPDT()
//        tool.thaw()
//
//        val root = Paths.get("/home/haydencheers/Desktop/SENG1110A12017-ALL")
//        val result = tool.evaluateSubmissions(root)
//
//        result.pairwiseSubmissionSimilarities
//            .sortedByDescending { it.third }
//            .forEach { (l, r, score) ->
//                println("$l:$r $score")
//            }
//
//        tool.close()
//    }
//
//    @Test
//    fun testPairwiseWithFiles() {
//        val tool = JPlagSCPDT()
//        tool.thaw()
//
//        val root = Paths.get("/home/haydencheers/Desktop/SENG1110A12017-ALL")
//        val dirs = Files.list(root)
//            .filter { Files.isDirectory(it) && !Files.isHidden(it) }
//            .toList()
//
//        val results = mutableListOf<SCPDTool.PairwiseWithFileSimilarityResult>()
//
//        val count = ((dirs.size / 2.0) * (dirs.size-1 + 0)).roundToInt()
//        val sem = Semaphore(count)
//
//        for (l in 0 until dirs.size) {
//            val ldir = dirs[l]
//
//            for (r in l+1 until dirs.size) {
//                val rdir = dirs[r]
//
//                sem.acquire()
//
//                CompletableFuture.runAsync {
//                    println("Comparing ${ldir} vs ${rdir}")
//                    val res = tool.evaluatePairwiseAndFiles(ldir, rdir)
//                    results.add(res)
//                }.whenComplete { void, throwable ->
//                    sem.release()
//                    throwable?.printStackTrace(System.err)
//                }
//            }
//        }
//
//        sem.acquire(count)
//
//        results.sortedByDescending { it.sim }
//            .forEach { println(it) }
//
//        tool.close()
//    }
//}