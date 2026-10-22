package local.koenami.android.web

import org.junit.Assert.*
import org.junit.Test

class OriginPolicyTest {
    @Test fun bridgeRequiresOfficialOriginMainFrameAndTrainingDocument() {
        assertTrue(OriginPolicy.bridge(OriginPolicy.ORIGIN,true,OriginPolicy.HOME))
        assertFalse(OriginPolicy.bridge(OriginPolicy.ORIGIN,false,OriginPolicy.HOME))
        assertFalse(OriginPolicy.bridge("https://evil.example",true,OriginPolicy.HOME))
        assertFalse(OriginPolicy.bridge(OriginPolicy.ORIGIN,true,"https://koe.transnavi.jp/ja/"))
        assertFalse(OriginPolicy.bridge("https://koe.transnavi.jp:444",true,OriginPolicy.HOME))
    }
    @Test fun trustedTrainingOnly() {
        assertTrue(OriginPolicy.training(OriginPolicy.HOME))
        assertTrue(OriginPolicy.training("https://koe.transnavi.jp:443/zh-CN/#map"))
        listOf("http://koe.transnavi.jp/zh-CN/", "https://koe.transnavi.jp.evil.com/zh-CN/",
            "https://koe.transnavi.jp:444/zh-CN/", "https://user@koe.transnavi.jp/zh-CN/",
            "https://koe.transnavi.jp/ja/", "https://koe.transnavi.jp/%7ah-CN/",
            "file:///zh-CN/", "javascript:alert(1)", "https://koe.transnavi.jp/zh-CN/?next=evil",
            "https://koe.transnavi.jp\\@evil.com/zh-CN/").forEach { assertFalse(it, OriginPolicy.training(it)) }
    }
}
