package local.koenami.android.updates

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdatePolicyTest {
    @Test fun numericVersionsDoNotUseLexicographicOrdering() {
        assertTrue(UpdatePolicy.newer("v1.10", "1.9"))
        assertTrue(UpdatePolicy.newer("v2.0.1", "2.0"))
        assertFalse(UpdatePolicy.newer("v1.0.0", "1.0"))
        assertFalse(UpdatePolicy.newer("v1.0", "1.1"))
        assertTrue(UpdatePolicy.newer("v1.2", "1.1-dev"))
        listOf("v1.1-beta", "latest", "1.2.3.4", "-1.0", "1.99999", "1.1 ", "v1.1+meta").forEach {
            assertNull(it, UpdatePolicy.version(it)); assertFalse(UpdatePolicy.newer(it, "1.0"))
        }
    }
    @Test fun sourceMustBeExactRepositoryAndHttps() {
        val root = "https://github.com/${UpdatePolicy.REPOSITORY}/releases"
        assertTrue(UpdatePolicy.sourceUrl("$root/tag/v1.1", false))
        assertTrue(UpdatePolicy.sourceUrl("$root/download/v1.1/Koenami-v1.1.apk", true))
        listOf("http://github.com/${UpdatePolicy.REPOSITORY}/releases/tag/v1.1",
            "$root/tag/v1.1?redirect=elsewhere", "$root/tag/%2E%2E", "$root/tag/..",
            "$root/tag/v1.1#other", "$root/download/v1.1/nested/anything.apk",
            "https://evil.invalid/${UpdatePolicy.REPOSITORY}/releases/tag/v1.1",
            "https://github.com.evil.invalid/${UpdatePolicy.REPOSITORY}/releases/tag/v1.1",
            "https://user@github.com/${UpdatePolicy.REPOSITORY}/releases/tag/v1.1",
            "https://github.com:443/${UpdatePolicy.REPOSITORY}/releases/tag/v1.1").forEach {
            assertFalse(it, UpdatePolicy.sourceUrl(it, it.endsWith("apk")))
        }
    }
    @Test fun redirectsAllowOnlyGithubAssetInfrastructure() {
        assertTrue(UpdatePolicy.downloadHost("https://release-assets.githubusercontent.com/a/b?token=temporary"))
        assertFalse(UpdatePolicy.downloadHost("https://release-assets.githubusercontent.com.evil.invalid/a"))
        assertFalse(UpdatePolicy.downloadHost("http://github.com/a"))
        assertFalse(UpdatePolicy.downloadHost("https://user@github.com/a"))
        assertFalse(UpdatePolicy.downloadHost("file:///tmp/a.apk"))
    }
    @Test fun dailyWindowSurvivesRestartAndClockRollback() {
        val now = 2 * UpdatePolicy.DAY
        assertTrue(UpdatePolicy.due(0, now))
        assertFalse(UpdatePolicy.due(now, now + UpdatePolicy.DAY - 1))
        assertTrue(UpdatePolicy.due(now, now + UpdatePolicy.DAY))
        assertTrue(UpdatePolicy.due(now, now - 1))
    }
    @Test fun checksumCoversActualBytes() {
        val file = File.createTempFile("koenami-update-test", ".bin")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdatePolicy.sha256(file))
            file.appendText("x")
            assertNotEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdatePolicy.sha256(file))
        } finally { file.delete() }
    }
}
