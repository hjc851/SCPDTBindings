package me.haydencheers.scpdt.naive.graph

import me.haydencheers.scpdt.naive.AbstractNaiveSCPDT
import java.nio.file.Path
import java.util.concurrent.ExecutorService

class NaivePDGEditDistanceSCPDT: AbstractNaiveSCPDT() {
    override val className: String
        get() = "frontend.NaivePDGEditDistance"

    override val filewiseClassName: String
        get() = "frontend.FilewiseNaivePDGEditDistance"

    override val threshold: Int
        get() = 0

    override val id: String
        get() = "Naive Program Dependence Graph"
}