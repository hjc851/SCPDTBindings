package me.haydencheers.scpdt.naive.tree

import me.haydencheers.scpdt.naive.AbstractNaiveSCPDT

class NaiveTreeEditDistanceSCPDT: AbstractNaiveSCPDT() {
    override val id: String
        get() = "Naive Tree Edit Distance"

    override val className: String
        get() = "frontend.NaiveTreeEditDistance"

    override val threshold: Int
        get() = 0
}