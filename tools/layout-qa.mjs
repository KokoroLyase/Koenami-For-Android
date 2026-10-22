import fs from 'node:fs/promises';
import {connect} from './cdp-client.mjs';
const client=await connect(),results=[];
try {
  await client.evaluate('window.__koenamiAndroid.back()');
  for(const [width,height] of [[320,568],[360,800],[390,844],[412,915],[844,390]]) {
    await client.send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:true});
    await client.evaluate('window.__koenamiAndroid.visual(true);document.querySelector(".studio").scrollTop=0');
    await new Promise(resolve=>setTimeout(resolve,650));
    results.push(await client.evaluate(`(()=>{const rect=id=>document.querySelector(id).getBoundingClientRect().toJSON();const record=rect('#record'),live=rect('#live-mode'),footer=rect('.transport');const indicators=Array.from(document.querySelectorAll('.indicator'));const metricsComplete=indicators.length===5&&indicators.every(e=>{const r=e.getBoundingClientRect();const parts=Array.from(e.querySelectorAll('.indicator-heading,.indicator-values strong,.indicator-values small,.indicator-values em')).map(x=>x.getBoundingClientRect());return r.x>=0&&r.right<=innerWidth+1&&parts.length===4&&parts.every(p=>p.width>0&&p.x>=r.x-1&&p.right<=r.right+1)&&parts.every((p,i)=>i===0||p.y>=parts[i-1].bottom-1);});return {width:${width},height:${height},viewport:[innerWidth,innerHeight],overflow:document.documentElement.scrollWidth>innerWidth+1,recordVisible:record.y>=0&&record.bottom<=innerHeight+1,liveVisible:live.y>=0&&live.bottom<=innerHeight+1,metricsComplete,toolbarHidden:rect('.toolbar').height===0,footer:footer.height,mapHeight:rect('#voice-map').height,modules:window.__koenamiAndroid.diagnostics().modules};})()`));
  }
} finally {await client.send('Emulation.clearDeviceMetricsOverride');await client.evaluate('window.__koenamiAndroid.visual(true)');client.close();}
await fs.writeFile('evidence/device-layout-matrix.json',JSON.stringify(results,null,2));
console.log(JSON.stringify(results,null,2));
if(results.some(r=>r.overflow||!r.recordVisible||!r.liveVisible||!r.metricsComplete||!r.toolbarHidden))process.exitCode=1;
