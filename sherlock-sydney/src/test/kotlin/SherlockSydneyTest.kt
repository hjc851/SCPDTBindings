//import me.haydencheers.scpdt.sherlocksydney.SherlockSydneySCPDT
//import org.junit.Test
//import java.nio.file.Paths
//
//class SherlockSydneyTest {
//
//    val root = Paths.get("/home/haydencheers/Desktop/SENG1110A12017-ALL")
//
//    @Test
//    fun test() {
//        val tool = SherlockSydneySCPDT
//        tool.thaw()
//
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
//}