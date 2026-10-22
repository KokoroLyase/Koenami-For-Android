// Local Debug WebView test helper; not included in Android assets.
export async function connect() {
  const targets = await (await fetch('http://127.0.0.1:9222/json', {signal:AbortSignal.timeout(5000)})).json();
  const target = targets.find(t => t.url.startsWith('https://koe.transnavi.jp/zh-CN'));
  if (!target) throw new Error('Official Debug WebView not found');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Socket timeout')),5000); socket.onopen=()=>{clearTimeout(timer);resolve();};socket.onerror=reject;});
  let sequence=0; const pending=new Map();
  socket.onmessage=e=>{const m=JSON.parse(e.data),p=pending.get(m.id);if(p){pending.delete(m.id);clearTimeout(p.timer);m.error?p.reject(m.error):p.resolve(m.result);}};
  socket.onclose=()=>{for(const p of pending.values()){clearTimeout(p.timer);p.reject(Error('WebView closed'));}};
  const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout'));},20000);pending.set(id,{resolve,reject,timer});socket.send(JSON.stringify({id,method,params}));});
  const evaluate=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;};
  const tap=async selector=>{
    const point=await evaluate(`(()=>{const e=${selector.startsWith('shadow:')?`document.querySelector('#take-select').shadowRoot.querySelector(${JSON.stringify(selector.slice(7))})`:`document.querySelector(${JSON.stringify(selector)})`};if(!e)throw Error('Missing test control');e.scrollIntoView({block:'nearest'});const r=e.getBoundingClientRect();if(r.width<=0||r.height<=0)throw Error('Test control is hidden');return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
    await send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[point]});await send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
    await new Promise(resolve=>setTimeout(resolve,400));
  };
  return {send,evaluate,tap,close:()=>socket.close()};
}
