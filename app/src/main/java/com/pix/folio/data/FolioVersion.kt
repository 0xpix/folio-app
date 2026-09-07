package com.pix.folio.data

object FolioVersion {
    fun hasNumericVersion(value: String): Boolean = Regex(".*\\d+.*").matches(value)

    fun compare(left: String, right: String): Int {
        val a = tokens(left)
        val b = tokens(right)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        val leftBeta = left.contains("beta", ignoreCase = true)
        val rightBeta = right.contains("beta", ignoreCase = true)
        return when {
            leftBeta && !rightBeta -> -1
            !leftBeta && rightBeta -> 1
            else -> left.compareTo(right, ignoreCase = true)
        }
    }

    private fun tokens(value: String): List<Int> = Regex("\\d+")
        .findAll(value)
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
}
