/* M0 adapter. No official business nodes are moved, cloned, or replaced. */
(() => {
  'use strict';
  if (window !== window.top || location.origin !== 'https://koe.transnavi.jp' ||
      !['/zh-CN/', '/zh-CN'].includes(location.pathname) || location.search) return;
  if (window.__koenamiAndroid) return;
  const selectors = window.__koenamiAdapter.selectors;
  let session = '', suspended = false, enabled = false, busy = false, mounted = false;
  let captureObserver, observerRoot, mountObserver, remountTimer;
  const acks = new Map();
  const originalClick = HTMLAnchorElement.prototype.click;
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;
  const objectUrls = new Map();
  function createAdapter(object) {
    const url = originalCreate.call(URL, object);
    // Keep no strong reference and never postpone official revocation. This avoids a CSP-blocked
    // connect-src blob fetch by handing the original page-created Blob to its own download path.
    if (object instanceof Blob && typeof WeakRef === 'function') objectUrls.set(url, new WeakRef(object));
    return url;
  }
  function revokeAdapter(url) { objectUrls.delete(String(url)); return originalRevoke.call(URL, url); }
  URL.createObjectURL = createAdapter;
  URL.revokeObjectURL = revokeAdapter;
  const q = key => document.querySelector(selectors[key]);
  const captureState = () => ({
    record: q('record')?.getAttribute('aria-pressed') === 'true',
    live: q('live')?.getAttribute('aria-checked') === 'true',
    unknown: !(q('record') instanceof HTMLButtonElement) || !(q('live') instanceof HTMLButtonElement),
    settling: !!q('record')?.disabled && !!q('live')?.disabled
  });
  const stopCapture = () => {
    suspended = true;
    const state = captureState();
    if (state.record && !q('record').disabled) q('record').click();
    if (state.live && !q('live').disabled) q('live').click();
    document.querySelectorAll('audio,video').forEach(el => el.pause());
    return state;
  };
  const notice = message => {
    let box = document.getElementById('koenami-android-notice');
    if (!box) {
      box = document.createElement('p'); box.id = 'koenami-android-notice';
      box.setAttribute('role', 'status');
      box.style.cssText = 'position:fixed;bottom:12px;left:12px;right:12px;z-index:2147483647;background:#292330;color:white;padding:16px;border-radius:12px';
      document.body?.append(box);
    }
    box.textContent = message;
    setTimeout(() => box.remove(), 6500);
  };
  function send(message) {
    return new Promise((resolve, reject) => {
      if (!session || !window.KoenamiFiles) return reject(new Error('文件桥未就绪'));
      const key = `${message.id}:${message.seq ?? -1}`;
      const timer = setTimeout(() => { acks.delete(key); reject(new Error('文件传输超时')); }, 15000);
      acks.set(key, { resolve, reject, timer });
      try { window.KoenamiFiles.postMessage(JSON.stringify({ ...message, session })); }
      catch (error) { clearTimeout(timer); acks.delete(key); reject(error); }
    });
  }
  if (window.KoenamiFiles) window.KoenamiFiles.onmessage = event => {
    try {
      const message = JSON.parse(event.data), key = `${message.id}:${message.seq}`;
      const ack = acks.get(key); if (!ack) return;
      clearTimeout(ack.timer); acks.delete(key);
      message.ok ? ack.resolve() : ack.reject(new Error('原生文件校验失败'));
    } catch (_) { /* Invalid responses never complete a transfer. */ }
  };
  async function exportBlob(blobPromise, name) {
    // Blob acquisition is started synchronously by the click wrapper, before official revokeObjectURL.
    if (busy) { notice('已有导出正在进行，请完成后再试'); return; }
    busy = true;
    const id = crypto.randomUUID(), startSession = session;
    let seq = 0, began = false;
    try {
      const blob = await blobPromise;
      let mime = blob.type.split(';')[0];
      const extensions = { wav: 'audio/wav', zip: 'application/zip', json: 'application/json', png: 'image/png', html: 'text/html' };
      const ext = name.split('.').pop().toLowerCase();
      if (!mime || mime === 'application/octet-stream') mime = extensions[ext] || '';
      if (blob.size < 1 || blob.size > 512 * 1024 * 1024) throw new Error('文件大小超出范围，请逐条导出');
      const safeName = name.replace(/[\x00-\x1f\x7f\\/:*?"<>|]/g, '_').slice(0, 150);
      await send({ op: 'begin', id, name: safeName, mime, size: blob.size });
      began = true;
      for (let offset = 0; offset < blob.size; offset += 49152) {
        if (startSession !== session) throw new Error('页面已变化');
        const bytes = new Uint8Array(await blob.slice(offset, offset + 49152).arrayBuffer());
        let binary = ''; for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        await send({ op: 'chunk', id, seq: seq++, data: btoa(binary) });
      }
      await send({ op: 'end', id, seq });
      notice('文件已暂存，请在系统选择器中完成保存');
    } catch (error) {
      if (began) try { await send({ op: 'cancel', id }); } catch (_) { }
      notice(`导出未完成：${error.message}。官网录音未清除。`);
    } finally { busy = false; }
  }
  function clickAdapter(...args) {
    let url;
    try { url = new URL(this.href); } catch (_) { return originalClick.apply(this, args); }
    if (this.hasAttribute('download') && this.download && url.protocol === 'blob:' &&
        url.origin === location.origin && session && window.KoenamiFiles) {
      const blob = objectUrls.get(this.href)?.deref();
      const promise = blob ? Promise.resolve(blob) : fetch(this.href).then(response => { if (!response.ok) throw new Error('Blob 读取失败'); return response.blob(); });
      // Catch immediately even if another export is busy.
      promise.catch(() => {});
      void exportBlob(promise, this.download); return;
    }
    return originalClick.apply(this, args);
  }
  HTMLAnchorElement.prototype.click = clickAdapter;
  const detect = () => window.__koenamiAdapter.core();
  function visual(on) { enabled=!!on; return window.__koenamiVisual.set(enabled); }
  function mount() {
    if (!detect()) { mounted=false; window.__koenamiVisual.set(false); return; }
    if (observerRoot !== q('record')) {
      captureObserver?.disconnect();
      captureObserver = new MutationObserver(() => { if (suspended) stopCapture(); });
      for (const key of ['record', 'live']) captureObserver.observe(q(key), { attributes: true, attributeFilter: ['aria-pressed', 'aria-checked', 'disabled'] });
      observerRoot = q('record');
    }
    mounted = true; visual(enabled);
  }
  const ready = () => {
    mount();
    // Only child structure is observed. No full-DOM text, attribute, or canvas scanning.
    mountObserver = new MutationObserver(records => {
      if (records.every(r => [...r.addedNodes, ...r.removedNodes].every(n => n.id?.startsWith('koenami-android-')))) return;
      if (remountTimer) return;
      remountTimer = setTimeout(() => { remountTimer = null; if (!observerRoot?.isConnected || !mounted) mount(); else window.__koenamiVisual.recheck(); }, 300);
    });
    mountObserver.observe(document.body, { childList: true, subtree: true });
  };
  document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', ready, { once: true }) : ready();
  document.addEventListener('visibilitychange', () => { if (document.hidden) stopCapture(); });
  window.addEventListener('pagehide', stopCapture);
  const back = () => {
    const roots = [document, ...[...document.querySelectorAll('koe-select')].map(el => el.shadowRoot).filter(Boolean)];
    for (const root of roots) {
      const popover = root.querySelector(':popover-open');
      if (popover) { popover.hidePopover(); return true; }
    }
    const dialog = document.querySelector('dialog[open]');
    if (dialog) {
      const close = dialog.querySelector('[data-close]');
      if (close) { close.click(); return true; }
    }
    return false;
  };
  window.__koenamiAndroid = Object.freeze({
    bind(value, optimized) { session = value; mount(); visual(optimized); },
    foreground() { suspended = false; },
    visual, captureState, stopCapture, back,
    diagnostics: () => ({ adapter: '1.0', modules: window.__koenamiVisual.health(), mounted, enhanced: document.documentElement.hasAttribute('data-koenami-android'),
      secureContext: window.isSecureContext, getUserMedia: !!navigator.mediaDevices?.getUserMedia,
      audioContext: !!window.AudioContext, offlineAudioContext: !!window.OfflineAudioContext,
      audioWorklet: !!window.AudioWorkletNode, worker: !!window.Worker, wasm: !!window.WebAssembly,
      indexedDB: !!window.indexedDB, popover: 'showPopover' in HTMLElement.prototype,
      resizeObserver: !!window.ResizeObserver, pointerEvents: !!window.PointerEvent, weakRef: typeof WeakRef === 'function', bridge: !!session }),
    unmount() {
      visual(false); captureObserver?.disconnect(); mountObserver?.disconnect(); clearTimeout(remountTimer);
      if (HTMLAnchorElement.prototype.click === clickAdapter) HTMLAnchorElement.prototype.click = originalClick;
      if (URL.createObjectURL === createAdapter) URL.createObjectURL = originalCreate;
      if (URL.revokeObjectURL === revokeAdapter) URL.revokeObjectURL = originalRevoke;
      objectUrls.clear();
      for (const ack of acks.values()) { clearTimeout(ack.timer); ack.reject(new Error('适配已卸载')); }
      acks.clear(); session = '';
    }
  });
})();
