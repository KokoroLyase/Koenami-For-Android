package local.koenami.android

import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener

@RunWith(AndroidJUnit4::class)
class ContainerDeviceTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var web: WebView
    @Before fun open() {
        scenario=ActivityScenario.launch(MainActivity::class.java)
        fun find(view: View): WebView? {
            if(view is WebView)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let{return it}
            return null
        }
        scenario.onActivity{web=requireNotNull(find(it.findViewById(android.R.id.content)))}
        val end=System.currentTimeMillis()+90000
        while(System.currentTimeMillis()<end){if(js("!!window.__koenamiAndroid?.diagnostics().mounted")=="true")return;Thread.sleep(250)}
        fail("Official page/adapter did not become ready in 90 seconds")
    }
    @After fun close(){if(::scenario.isInitialized)scenario.close()}
    private fun js(expression: String): String {
        val latch=CountDownLatch(1);var result="null"
        scenario.onActivity{web.evaluateJavascript(expression){result=it;latch.countDown()}}
        assertTrue("WebView callback timeout",latch.await(12,TimeUnit.SECONDS));return result
    }
    @Test fun secureContainerAndOfficialDocument() {
        scenario.onActivity{
            assertTrue(web.settings.javaScriptEnabled);assertTrue(web.settings.domStorageEnabled)
            assertFalse(web.settings.allowFileAccess);assertTrue(web.settings.safeBrowsingEnabled)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,web.settings.mixedContentMode)
            assertTrue(web.settings.mediaPlaybackRequiresUserGesture)
            assertEquals("https://koe.transnavi.jp/zh-CN/",web.url)
        }
        assertEquals("true",js("window.isSecureContext && !!window.KoenamiFiles && window.top===window"))
    }
    @Test fun tenVisualSwitchesPreserveBusinessNodes() {
        val raw=js("JSON.stringify((()=>{const record=document.querySelector('#record'),canvas=document.querySelector('#voice-map');for(let i=0;i<10;i++){window.__koenamiAndroid.visual(false);window.__koenamiAndroid.visual(true);}return {record:record===document.querySelector('#record'),canvas:canvas===document.querySelector('#voice-map'),styles:document.querySelectorAll('#koenami-android-style').length,tools:document.querySelectorAll('#koenami-android-tools').length};})())")
        val result=JSONObject(JSONTokener(raw).nextValue() as String)
        assertTrue(result.getBoolean("record"));assertTrue(result.getBoolean("canvas"))
        assertEquals(1,result.getInt("styles"));assertEquals(1,result.getInt("tools"))
    }
    @Test fun rawModeRetainsInterop() {
        assertEquals("true",js("(()=>{window.__koenamiAndroid.visual(false);return !document.documentElement.hasAttribute('data-koenami-android')&&!!window.KoenamiFiles&&!!window.__koenamiAndroid.stopCapture;})()"))
        js("window.__koenamiAndroid.visual(true)")
    }
    @Test fun importedAudioSurvivesSignedUpgrade() {
        val verifyOnly = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("upgradeCheck") == "true"
        if (!verifyOnly) js("""
            (()=>{
              const rate=16000,count=rate*5,bytes=new Uint8Array(44+count*2),v=new DataView(bytes.buffer);
              const text=(at,s)=>{for(let i=0;i<s.length;i++)bytes[at+i]=s.charCodeAt(i)};
              text(0,'RIFF');v.setUint32(4,36+count*2,true);text(8,'WAVE');text(12,'fmt ');
              v.setUint32(16,16,true);v.setUint16(20,1,true);v.setUint16(22,1,true);
              v.setUint32(24,rate,true);v.setUint32(28,rate*2,true);v.setUint16(32,2,true);v.setUint16(34,16,true);
              text(36,'data');v.setUint32(40,count*2,true);
              for(let i=0;i<count;i++)v.setInt16(44+i*2,Math.round(8000*Math.sin(2*Math.PI*160*i/rate)),true);
              const transfer=new DataTransfer();transfer.items.add(new File([bytes],'Koenami-upgrade-fixture.wav',{type:'audio/wav'}));
              const input=document.getElementById('upload');input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));return true;
            })()
        """.trimIndent())
        val end=System.currentTimeMillis()+60000
        while(System.currentTimeMillis()<end){
            val found=js("Array.from(document.querySelectorAll('#take-select option')).some(e=>e.textContent.includes('Koenami-upgrade-fixture'))")
            if(found=="true"){
                js("(()=>{const s=document.getElementById('take-select'),o=Array.from(s.querySelectorAll('option')).find(e=>e.textContent.includes('Koenami-upgrade-fixture'));s.value=o.value;s.dispatchEvent(new Event('change',{bubbles:true}));})()")
                val loaded=System.currentTimeMillis()+15000
                while(System.currentTimeMillis()<loaded){
                    if(js("(()=>{const p=document.getElementById('player');return Number.isFinite(p.duration)&&Math.abs(p.duration-5)<0.01&&p.readyState>=1;})()") == "true")return
                    Thread.sleep(200)
                }
                fail("Saved fixture was listed but its five-second audio did not load")
            }
            Thread.sleep(250)
        }
        fail("The imported fixture did not appear in official recording history")
    }
}
