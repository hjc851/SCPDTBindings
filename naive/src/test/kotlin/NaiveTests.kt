import me.haydencheers.scpdt.SCPDTool
import me.haydencheers.scpdt.naive.graph.NaivePDGEditDistanceSCPDT
import me.haydencheers.scpdt.naive.string.NaiveStringEditDistanceSCPDT
import me.haydencheers.scpdt.naive.string.NaiveStringTilingSCPDT
import me.haydencheers.scpdt.naive.token.NaiveTokenEditDistanceSCPDT
import me.haydencheers.scpdt.naive.token.NaiveTokenTilingSCPDT
import me.haydencheers.scpdt.naive.tree.NaiveTreeEditDistanceSCPDT
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class NaiveTests {

    val root = Paths.get("/home/haydencheers/Desktop/PhD Data Sets/SENG1110A12017_Seeded/All")

    @Test
    fun testStringTiling() {
        test(NaiveStringTilingSCPDT(), root)
    }

    @Test
    fun testStringEditDistance() {
        test(NaiveStringEditDistanceSCPDT(), root)
    }

    @Test
    fun testTokenTiling() {
        test(NaiveTokenTilingSCPDT(), root)
    }

    @Test
    fun testTokenEditDistance() {
        test(NaiveTokenEditDistanceSCPDT(), root)
    }

    @Test
    fun testTreeEditDistance() {
        test(NaiveTreeEditDistanceSCPDT(), root)
    }

    @Test
    fun testPDG() {
        test(NaivePDGEditDistanceSCPDT(), root)
    }

    private fun test(tool: SCPDTool, root: Path) {
        val result = tool.evaluateDirectory(root)

        result.results
            .sortedByDescending { it.third }
            .forEach { (l, r, score) ->
                println("$l:$r $score")
            }
    }
}