package me.haydencheers.scpdt

interface SCPDToolPairwiseExecutionResult {
    data class Error(val reason: String): SCPDToolPairwiseExecutionResult
    data class Success(val score: Double): SCPDToolPairwiseExecutionResult
}