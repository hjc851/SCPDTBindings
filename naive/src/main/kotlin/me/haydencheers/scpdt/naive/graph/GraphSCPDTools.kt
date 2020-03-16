package me.haydencheers.scpdt.naive.graph

import me.haydencheers.scpdt.naive.AbstractNaiveSCPDT

class NaivePDGEditDistanceSCPDT: AbstractNaiveSCPDT() {
    override val className: String
        get() = "frontend.NaivePDGEditDistance"

    override val threshold: Int
        get() = 0

    override val id: String
        get() = "Naive Program Dependence Graph"
}