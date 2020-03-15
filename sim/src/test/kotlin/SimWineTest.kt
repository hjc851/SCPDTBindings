import me.haydencheers.scpdt.sim.SimWineSCPDTool
import org.junit.Test
import java.nio.file.Paths

class SimWineTest {

    val tool = SimWineSCPDTool()

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