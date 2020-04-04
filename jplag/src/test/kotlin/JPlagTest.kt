import me.haydencheers.scpdt.jplag.JPlagSCPDT
import org.junit.Test
import java.nio.file.Paths

class JPlagTest {

    @Test
    fun test() {
        val tool = JPlagSCPDT
        tool.thaw()

        val root = Paths.get("/home/haydencheers/Desktop/SENG1110A12017-ALL")
        val result = tool.evaluateSubmissions(root)

        result.pairwiseSubmissionSimilarities
            .sortedByDescending { it.third }
            .forEach { (l, r, score) ->
                println("$l:$r $score")
            }

        tool.close()
    }
}