package local.koenami.android.files

/** Pure, independently testable limits for untrusted page file messages. */
object TransferPolicy {
    const val MAX_SIZE = 536870912L
    const val MAX_CHUNK = 49152
    fun nameAndType(name: String, mime: String): Boolean {
        if (name.length !in 1..160 || name.trim() != name || name.any { it < ' ' || it == '\u007f' || it in "/\\:*?\"<>|" }) return false
        val extensions = when (mime) {
            "audio/wav", "audio/x-wav" -> setOf("wav", "wave")
            "application/zip" -> setOf("zip")
            "application/json" -> setOf("json")
            "image/png" -> setOf("png")
            "text/html" -> setOf("html", "htm")
            else -> return false
        }
        return name.substringAfterLast('.', "").lowercase() in extensions
    }
    fun size(value: Long, space: Long) = value in 1..MAX_SIZE && space > value + 16777216L
    fun chunk(expectedSequence: Int, actualSequence: Int, received: Long, length: Int, total: Long) =
        expectedSequence == actualSequence && length in 1..MAX_CHUNK && received >= 0 && received + length <= total
}
