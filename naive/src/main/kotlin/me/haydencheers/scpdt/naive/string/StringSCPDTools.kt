package me.haydencheers.scpdt.naive.string

import me.haydencheers.scpdt.naive.AbstractNaiveSCPDT

class NaiveStringTilingSCPDT (
    override val threshold: Int = 20
): AbstractNaiveSCPDT() {
    override val id: String
        get() = "Naive String Tiling"

    override val className: String
        get() = "frontend.NaiveStringTiling"
}

class NaiveStringEditDistanceSCPDT: AbstractNaiveSCPDT() {
    override val id: String
        get() = "Naive String Edit Distance"

    override val className: String
        get() = "frontend.NaiveStringEditDistance"

    override val threshold: Int
        get() = 0
}
