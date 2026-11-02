package com.kamalahmed.prayerapp.update

/**
 * Minimal semantic version: MAJOR.MINOR.PATCH[-pre.release][+build]. A leading "v" is ignored.
 * Ordering follows semver 2.0: a pre-release (0.2.0-beta.1) sorts before its release (0.2.0);
 * numeric pre-release identifiers compare numerically (beta.10 > beta.2), alphanumerics lexically.
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int, val pre: List<String> = emptyList()) : Comparable<SemVer> {
    val isPreRelease get() = pre.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        if (pre.isEmpty() || other.pre.isEmpty()) return compareValues(other.pre.size.coerceAtMost(1), pre.size.coerceAtMost(1))
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val a = pre[i]; val b = other.pre[i]
            val an = a.toLongOrNull(); val bn = b.toLongOrNull()
            val c = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (c != 0) return c
        }
        return pre.size.compareTo(other.pre.size)
    }

    override fun toString() = "$major.$minor.$patch" + if (pre.isEmpty()) "" else "-" + pre.joinToString(".")

    companion object {
        private val RX = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(s: String?): SemVer? {
            val m = RX.matchEntire(s?.trim() ?: return null) ?: return null
            val (a, b, c, p) = m.destructured
            return runCatching {
                SemVer(a.toInt(), b.ifEmpty { "0" }.toInt(), c.ifEmpty { "0" }.toInt(),
                    if (p.isEmpty()) emptyList() else p.split('.').filter { it.isNotEmpty() })
            }.getOrNull()
        }
    }
}

object VersionCompare {
    /**
     * True if [remoteName]/[remoteCode] is newer than the installed version. versionName decides;
     * when names are equal (or unparseable) a higher versionCode published in the release wins.
     */
    fun isNewer(remoteName: String, remoteCode: Int?, localName: String, localCode: Int): Boolean {
        val r = SemVer.parse(remoteName); val l = SemVer.parse(localName)
        if (r != null && l != null) {
            val c = r.compareTo(l)
            if (c != 0) return c > 0
        }
        return remoteCode != null && remoteCode > localCode
    }
}
