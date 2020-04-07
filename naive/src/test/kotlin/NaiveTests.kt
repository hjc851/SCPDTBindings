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

    val root = Paths.get("/home/haydencheers/Desktop/SENG1110A12017-ALL")

    @Test
    fun testStringTiling() {
        val tool = NaiveStringTilingSCPDT()
        tool.thaw()

        test(tool, root)

        tool.close()
    }

    @Test
    fun testStringEditDistance() {
        val tool = NaiveStringEditDistanceSCPDT()
        tool.thaw()

        test(tool, root)

        tool.close()
    }

    @Test
    fun testTokenTiling() {
        val tool = NaiveTokenTilingSCPDT()
        tool.thaw()

        test(tool, root)

        tool.close()
    }

    @Test
    fun testTokenEditDistance() {
        val tool = NaiveTokenEditDistanceSCPDT()
        tool.thaw()

        test(tool, root)

        tool.close()
    }

    @Test
    fun testTreeEditDistance() {
        val tool = NaiveTreeEditDistanceSCPDT()
        tool.thaw()

        test(tool, root)

        tool.close()
    }

    @Test
    fun testPDG() {
        val tool = NaivePDGEditDistanceSCPDT()
        tool.thaw()

        test(tool, root)

        tool.close()
    }

    private fun test(tool: SCPDTool, root: Path) {
        val result = tool.evaluateSubmissions(root)

        result.sortedByDescending { it.third }
            .forEach { (l, r, score) ->
                println("$l:$r $score")
            }
    }
}