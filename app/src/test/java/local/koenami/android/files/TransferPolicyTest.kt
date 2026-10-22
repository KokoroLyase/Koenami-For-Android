package local.koenami.android.files

import org.junit.Assert.*
import org.junit.Test

class TransferPolicyTest {
    @Test fun rejectsPathsAndExecutableMismatches() {
        assertTrue(TransferPolicy.nameAndType("中文录音.wav", "audio/wav"))
        assertTrue(TransferPolicy.nameAndType("report.HTML", "text/html"))
        listOf("../record.wav", "C:\\record.wav", "a/b.wav", "a\n.wav", " .wav", "a.wav ", ".", "script.exe")
            .forEach { assertFalse(it, TransferPolicy.nameAndType(it,"audio/wav")) }
        assertFalse(TransferPolicy.nameAndType("a.apk", "application/zip"))
        assertFalse(TransferPolicy.nameAndType("a.wav", "unknown/type"))
    }
    @Test fun enforcesChunkOrderLimitsAndDeclaredSize() {
        assertTrue(TransferPolicy.chunk(0,0,0,49152,49152))
        assertFalse(TransferPolicy.chunk(0,1,0,1,49152))
        assertFalse(TransferPolicy.chunk(1,0,49152,1,49153))
        assertFalse(TransferPolicy.chunk(0,0,0,49153,100000))
        assertFalse(TransferPolicy.chunk(0,0,0,100,99))
        assertFalse(TransferPolicy.chunk(0,0,0,0,99))
        assertFalse(TransferPolicy.size(0,Long.MAX_VALUE))
        assertFalse(TransferPolicy.size(TransferPolicy.MAX_SIZE+1,Long.MAX_VALUE))
        assertFalse(TransferPolicy.size(1024,1024))
    }
}
