import fs from 'node:fs/promises';
import {connect} from './cdp-client.mjs';
const c=await connect(),results=[];
const settle=()=>new Promise(r=>setTimeout(r,400));
const snapshot=`(()=>{
 const fields={'.profile-panel':['display','grid-template-areas','padding','gap'], '#verdict-readout':['display','grid-template-areas','padding','min-height'],'.indicator':['display','grid-template-areas','padding','min-height'],'.indicator-heading':['font-size'],'.indicator-values':['display'],'.indicator-values strong':['font-size'],'.indicator-values small':['font-size'],'.indicator-values em':['font-size'], '#report-button':['flex-direction','padding','border-left-width','min-height'],'.graph-toolbar button':['font-size','padding','min-height'],'.legend':['right','font-size','padding'],'.graph-tools':['bottom','right','padding'],'#reset-view':['width','height','min-height'],'#zoom-in':['display'],'#zoom-out':['display'],'#find-me':['display']};
 return Object.fromEntries(Object.entries(fields).map(([selector,props])=>[selector,Object.fromEntries(props.map(p=>[p,getComputedStyle(document.querySelector(selector)).getPropertyValue(p)]))]));
})()`;
const geometry=`(()=>{const r=s=>document.querySelector(s).getBoundingClientRect().toJSON(),t=r('.graph-toolbar'),l=r('.legend'),g=r('.graph'),two=r('[data-dimension="2"]'),three=r('[data-dimension="3"]'),variance=r('[data-projection="variance"]'),contrast=r('[data-projection="contrast"]');return {rows:Math.abs(two.top-three.top)<1&&variance.height>0&&contrast.height>0&&variance.top>=two.bottom+3.9&&contrast.top>=variance.bottom+3.9,toolbar:t,legend:l,wrap:document.documentElement.hasAttribute('data-koenami-map-controls-wrap'),separated:l.left>=t.right+7.9||l.top>=t.bottom+5.9,inside:l.left>=g.left&&l.right<=g.right+1&&l.bottom<=g.bottom+1};})()`;
try {
 for(const [width,height] of [[320,568],[360,800],[390,844],[412,915],[560,900],[844,390]]) {
  await c.send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:true});
  await c.evaluate('window.__koenamiAndroid.visual(false);document.querySelector(\'[data-dimension="3"]\').click()');await settle();
  const official=await c.evaluate(snapshot);
  await c.evaluate('window.__koenamiAndroid.visual(true)');await settle();
  const enhanced=await c.evaluate(snapshot);
  const differences=Object.keys(official).filter(k=>JSON.stringify(official[k])!==JSON.stringify(enhanced[k]));
  await c.evaluate('document.querySelector(\'[data-dimension="2"]\').click()');await settle();
  const two=await c.evaluate(geometry);
  const controls=await c.evaluate(`(()=>{const before=document.querySelector('#voice-map');const two=document.querySelector('[data-dimension="2"]').getAttribute('aria-pressed')==='true'&&!document.querySelector('#projection-controls').hidden;document.querySelector('[data-projection="contrast"]').click();const contrast=document.querySelector('[data-projection="contrast"]').getAttribute('aria-pressed')==='true';const toggle=document.querySelector('#show-female'),checked=toggle.checked;toggle.closest('label').click();const legend=!toggle.checked===checked;toggle.closest('label').click();return {two,contrast,legend,legendRestored:toggle.checked===checked,sameCanvas:before===document.querySelector('#voice-map')};})()`);
  await c.evaluate('document.querySelector(\'[data-dimension="3"]\').click()');await settle();
  const three=await c.evaluate(geometry);
  await c.tap('[data-projection="variance"]');
  const projectionFrom3D=await c.evaluate(`document.querySelector('[data-dimension="2"]').getAttribute('aria-pressed')==='true'&&document.querySelector('[data-projection="variance"]').getAttribute('aria-pressed')==='true'`);
  controls.projectionFrom3D=projectionFrom3D;
  const header=await c.evaluate(`(()=>{const r=s=>document.querySelector(s).getBoundingClientRect(),cy=r=>r.top+r.height/2,bar=r('.toolbar'),text=r('#selected-text'),expand=r('#koenami-android-reference'),favorite=r('.target-heading .favorite'),source=r('#source-link');return {compact:bar.height>0&&bar.height<=36,textAligned:Math.abs(cy(text)-cy(expand))<=1,actionsAligned:Math.abs(cy(favorite)-cy(source))<=1,toolsAligned:[...document.querySelectorAll('.toolbar-end button')].filter(e=>e.getBoundingClientRect().height>0).every(e=>Math.abs(cy(e.getBoundingClientRect())-cy(bar))<=1),noToolToggle:!document.querySelector('#koenami-android-tools')};})()`);
  results.push({width,height,differences,controls,two,three,header,official,enhanced});
 }
} finally {await c.send('Emulation.clearDeviceMetricsOverride');await c.evaluate('window.__koenamiAndroid.visual(true)');c.close();}
await fs.writeFile('evidence/official-ui-matrix.json',JSON.stringify(results,null,2));
const failed=results.filter(r=>r.differences.length||Object.values(r.controls).some(v=>!v)||Object.values(r.header).some(v=>!v)||!r.two.rows||!r.three.rows||!r.two.separated||!r.two.inside||!r.three.separated||!r.three.inside);
console.log(JSON.stringify({cases:results.length,failed:failed.map(r=>({width:r.width,height:r.height,differences:r.differences,controls:r.controls,two:r.two,three:r.three,header:r.header}))},null,2));
if(failed.length)process.exitCode=1;
