import me.haydencheers.scpdt.jplag.JPlagSCPDT
import org.junit.Test
import java.nio.file.Paths

class JPlagTest {

    val tool = JPlagSCPDT

    @Test
    fun test() {
        val root = Paths.get("/home/haydencheers/Desktop/PhD Data Sets/COMP2240 2018 A1 A2 A3/COMP2240_18_A1_Dataset")
        val result = tool.evaluateDirectory(root)

        result.results
            .sortedByDescending { it.third }
            .forEach { (l, r, score) ->
                println("$l:$r $score")
            }
    }
}