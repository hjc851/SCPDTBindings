import me.haydencheers.scpdt.plaggie.PlaggieSCPDT
import org.junit.Test
import java.nio.file.Paths

class PlaggieTest {

    val root = Paths.get("/home/haydencheers/Desktop/SENG1110A12017-ALL")

    @Test
    fun test() {
        val tool = PlaggieSCPDT
        tool.thaw()

        val result = tool.evaluateSubmissions(root)

        result.pairwiseSubmissionSimilarities
            .sortedByDescending { it.third }
            .forEach { (l, r, score) ->
                println("$l:$r $score")
            }

        tool.close()
    }
}