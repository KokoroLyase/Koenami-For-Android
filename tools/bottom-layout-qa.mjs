// The analysis canvas must be visible on first render, without a click or scrolling.
import fs from 'node:fs/promises';
import {connect} from './cdp-client.mjs';
const client=await connect(),results=[];
const settle=()=>new Promise(resolve=>setTimeout(resolve,400));
const geometry=()=>client.evaluate(`(()=>{
  const rect=s=>document.querySelector(s).getBoundingClientRect().toJSON();
  const hit=e=>{const r=e.getBoundingClientRect(),at=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return at===e||e.contains(at);};
  const header=rect('#koenami-android-analysis-header'),footer=rect('.transport'),panel=rect('.signal-panel'),canvas=rect('#signal-canvas');
  const controls=['#record','#play-mine','#live-mode','#loopback','#playback-speed','#speed-reset','#compare-ab','.upload-button','#take-select'];
  return {header,footer,panel,canvas,studio:rect('.studio'),legend:rect('.legend'),tools:rect('.graph-tools'),
    open:document.documentElement.hasAttribute('data-koenami-analysis-open'),
    hit:[...document.querySelectorAll('#koenami-android-analysis-header button')].every(hit),
    controls:controls.map(selector=>({selector,rect:rect(selector),hit:hit(document.querySelector(selector))})),
    canvasVisible:canvas.height>=64&&canvas.top>=0&&canvas.bottom<=footer.top+1&&canvas.top>=panel.top&&canvas.bottom<=panel.bottom+1,
    overflow:document.documentElement.scrollWidth>innerWidth+1};
})()`);
try {
  for(const [width,height] of [[320,568],[360,800],[390,844],[412,915],[844,390]]) {
    await client.send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:true});
    await client.evaluate('window.__koenamiAndroid.visual(true);document.querySelector(".studio").scrollTop=0');
    await settle();
    const initial=await geometry();
    await client.tap('#koenami-android-analysis-header button');
    await settle();
    const collapsed=await geometry();
    await client.tap('#koenami-android-analysis-header button');
    await settle();
    const expanded=await geometry();
    await client.tap('#koenami-android-analysis-header button:last-child');
    await settle();
    const fullscreen=await geometry();
    await client.evaluate('window.__koenamiAndroid.back()');
    await settle();
    await client.tap('#koenami-android-map-full');
    await settle();
    const mapFullscreen=await client.evaluate(`(()=>{const g=document.querySelector('#voice-map').getBoundingClientRect(),f=document.querySelector('.transport').getBoundingClientRect();return {visible:g.top>=0&&g.height>0&&g.bottom<=f.top+1,analysisHidden:getComputedStyle(document.querySelector('.signal-panel')).display==='none'};})()`);
    await client.evaluate('window.__koenamiAndroid.back();document.querySelector(".studio").scrollTop=0');
    await settle();
    const restored=await geometry();
    results.push({width,height,initial,collapsed,expanded,fullscreen,mapFullscreen,restored});
  }
} finally {
  await client.send('Emulation.clearDeviceMetricsOverride');
  await client.evaluate('window.__koenamiAndroid.visual(true);document.querySelector(".studio").scrollTop=0');
  await settle();client.close();
}
await fs.writeFile('evidence/bottom-layout-matrix.json',JSON.stringify(results,null,2));
const bad=results.filter(r=>[r.initial,r.expanded,r.fullscreen,r.restored].some(s=>s.overflow||!s.hit||!s.canvasVisible||s.header.top<0||s.panel.bottom>s.footer.top+1)||
  !r.initial.open||r.initial.footer.height>116||r.initial.studio.bottom>r.initial.panel.top+1||
  r.initial.controls.some(c=>c.rect.left<0||c.rect.right>r.width+1||c.rect.top<r.initial.footer.top||c.rect.bottom>r.height+1||!c.hit)||
  r.collapsed.open||r.collapsed.panel.height>40||!r.collapsed.hit||
  !r.mapFullscreen.visible||!r.mapFullscreen.analysisHidden||
  r.initial.legend.right>r.initial.tools.left||Math.abs(r.initial.legend.bottom-r.initial.tools.bottom)>1);
console.log(JSON.stringify({cases:results.length,failed:bad.map(r=>({width:r.width,height:r.height,initial:r.initial,collapsed:r.collapsed,mapFullscreen:r.mapFullscreen}))},null,2));
if(bad.length)process.exitCode=1;
