// Replaces only our local enhancement in an already-open Debug WebView, for quick layout QA.
import fs from 'node:fs/promises';
import path from 'node:path';
import {connect} from './cdp-client.mjs';
const base=path.resolve('app/src/main/assets/enhancement');
const manifest=JSON.parse(await fs.readFile(path.join(base,'manifest.json'),'utf8'));
const styles=Object.fromEntries(await Promise.all(manifest.styles.map(async p=>[p,await fs.readFile(path.join(base,p),'utf8')])));
const scripts=await Promise.all(manifest.scripts.map(p=>fs.readFile(path.join(base,p),'utf8')));
const client=await connect();
try {
  const state=await client.evaluate('({capture:window.__koenamiAndroid?.captureState(),enhanced:document.documentElement.hasAttribute("data-koenami-android")})');
  if(state.capture?.record||state.capture?.live||state.capture?.settling)throw Error('Stop active audio before changing Debug adapter');
  await client.evaluate('window.__koenamiAndroid?.unmount();delete window.__koenamiAndroid;');
  await client.evaluate(`(()=>{window.__koenamiStyles=${JSON.stringify(styles)};${scripts.join('\n')}window.__koenamiAndroid?.visual(true);return window.__koenamiAndroid?.diagnostics();})()`).then(x=>console.log(JSON.stringify(x,null,2)));
} finally {client.close();}
