// Regression for the user's screenshot: analysis actions must never sit behind transport.
import fs from 'node:fs/promises';
import {connect} from './cdp-client.mjs';
const client=await connect(),results=[];
const settle=()=>new Promise(resolve=>setTimeout(resolve,350));
const geometry=()=>client.evaluate(`(()=>{
  const rect=s=>document.querySelector(s).getBoundingClientRect().toJSON();
  const header=rect('#koenami-android-analysis-header'),footer=rect('.transport'),studio=rect('.studio'),legend=rect('.legend'),tools=rect('.graph-tools');
  const button=document.querySelector('#koenami-android-analysis-header button');
  const b=button.getBoundingClientRect();
  const hit=document.elementFromPoint(b.x+b.width/2,b.y+b.height/2);
  return {header,footer,studio,legend,tools,hit:button===hit||button.contains(hit),overflow:document.documentElement.scrollWidth>innerWidth+1};
})()`);
try {
  for(const [width,height] of [[320,568],[360,800],[390,844],[412,915],[844,390]]) {
    await client.send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:true});
    await client.evaluate('window.__koenamiAndroid.visual(true);document.querySelector(".studio").scrollTop=0');
    await settle();
    const collapsed=await geometry();
    await client.evaluate('document.querySelector("#koenami-android-analysis-header button").click()');
    await settle();
    const expanded=await geometry();
    const canvas=await client.evaluate(`(()=>{const r=document.querySelector('#signal-canvas').getBoundingClientRect(),s=document.querySelector('.studio').getBoundingClientRect();return {visible:r.height>0&&r.top>=s.top-1&&r.bottom<=s.bottom+1};})()`);
    await client.evaluate('document.querySelector("#koenami-android-analysis-header button:last-child").click()');
    await settle();
    const fullscreen=await geometry();
    const fullCanvas=await client.evaluate(`(()=>{const r=document.querySelector('#signal-canvas').getBoundingClientRect(),f=document.querySelector('.transport').getBoundingClientRect();return {visible:r.height>0&&r.top>=0&&r.bottom<=f.top+1};})()`);
    await client.evaluate('window.__koenamiAndroid.back();document.querySelector("#koenami-android-analysis-header button").click();document.querySelector(".studio").scrollTop=0');
    await settle();
    results.push({width,height,collapsed,expanded,canvas,fullscreen,fullCanvas});
  }
} finally {
  await client.send('Emulation.clearDeviceMetricsOverride');
  await client.evaluate('window.__koenamiAndroid.visual(true);document.querySelector(".studio").scrollTop=0');
  await settle();client.close();
}
await fs.writeFile('evidence/bottom-layout-matrix.json',JSON.stringify(results,null,2));
const bad=results.filter(r=>[r.collapsed,r.expanded,r.fullscreen].some(s=>s.overflow||!s.hit||s.header.top<0||s.header.bottom>s.footer.top+1)||!r.canvas.visible||!r.fullCanvas.visible||r.collapsed.studio.bottom>r.collapsed.header.top+1||r.collapsed.legend.right>r.collapsed.tools.left||Math.abs(r.collapsed.legend.bottom-r.collapsed.tools.bottom)>1);
console.log(JSON.stringify({cases:results.length,failed:bad.map(r=>({width:r.width,height:r.height,canvas:r.canvas,fullCanvas:r.fullCanvas,hit:[r.collapsed.hit,r.expanded.hit,r.fullscreen.hit]}))},null,2));
if(bad.length)process.exitCode=1;
