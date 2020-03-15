import me.haydencheers.scpdt.sherlocksydney.SherlockSydneySCPDT
import org.junit.Test
import java.nio.file.Paths

class SherlockSydneyTest {

    val tool = SherlockSydneySCPDT

    @Test
    fun test() {
        val result = tool.evaluateDirectory(Paths.get("/home/haydencheers/Desktop/PhD Data Sets/SENG1110A12017_Seeded/All"))

        result.results
            .sortedByDescending { it.third }
            .forEach { (l, r, score) ->
                println("$l:$r $score")
            }
    }
}