// Debug PoC only. Requires adb forward to this app's devtools socket; never shipped in APK.
import fs from 'node:fs/promises';
const targets = await (await fetch('http://127.0.0.1:9222/json', {signal:AbortSignal.timeout(5000)})).json();
const target = targets.find(t => t.url.startsWith('https://koe.transnavi.jp/zh-CN'));
if (!target) throw new Error('Official PoC target not found');
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { const opening = setTimeout(()=>reject(new Error('Debug socket not foreground/ready')),5000); socket.onopen = () => {clearTimeout(opening);resolve();}; socket.onerror = reject; });
let sequence = 0;
const waiters = new Map();
const timer = setTimeout(() => {
  for (const pending of waiters.values()) pending.reject(new Error('Debug target timed out'));
  socket.close();
}, 20000);
socket.onclose = () => { for (const pending of waiters.values()) pending.reject(new Error('Debug target closed')); };
socket.onmessage = event => {
  const message = JSON.parse(event.data);
  const pending = waiters.get(message.id);
  if (pending) { waiters.delete(message.id); message.error ? pending.reject(message.error) : pending.resolve(message.result); }
};
const send = (method, params) => new Promise((resolve, reject) => {
  const id = ++sequence; waiters.set(id, { resolve, reject }); socket.send(JSON.stringify({ id, method, params }));
});
try {
  if (process.argv[2] === 'screenshot') {
    const result = await send('Page.captureScreenshot', { format: 'png' });
    await fs.writeFile(process.argv[3], Buffer.from(result.data, 'base64'));
    console.log('Saved screenshot');
  } else {
    const expression = await fs.readFile(process.argv[2], 'utf8');
    const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
    console.log(JSON.stringify(result.result.value, null, 2));
  }
} finally { clearTimeout(timer); socket.close(); }
