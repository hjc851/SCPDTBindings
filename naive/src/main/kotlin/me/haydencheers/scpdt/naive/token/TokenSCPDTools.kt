package me.haydencheers.scpdt.naive.token

import me.haydencheers.scpdt.naive.AbstractNaiveSCPDT

class NaiveTokenTilingSCPDT (
    override val threshold: Int = 12
): AbstractNaiveSCPDT() {
    override val id: String
        get() = "Naive Token Tiling"

    override val className: String
        get() = "frontend.NaiveTokenTiling"
}

class NaiveTokenEditDistanceSCPDT: AbstractNaiveSCPDT() {
    override val id: String
        get() = "Naive Token Edit Distance"

    override val className: String
        get() = "frontend.NaiveTokenEditDistance"

    override val threshold: Int
        get() = 0
}