(() => {
  const ids = ['record', 'live-mode', 'play-mine', 'play-reference', 'take-select', 'sample-browser'];
  const rect = el => { const r = el.getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height}; };
  return {
    url: location.href, viewport: {width:innerWidth,height:innerHeight,dpr:devicePixelRatio},
    adapter: window.__koenamiAndroid?.diagnostics(),
    capture: window.__koenamiAndroid?.captureState(),
    controls: Object.fromEntries(ids.map(id => { const el = document.getElementById(id); return [id, el && {
      tag: el.tagName, disabled: el.disabled, pressed: el.getAttribute('aria-pressed'), checked: el.getAttribute('aria-checked'),
      options: el.tagName === 'KOE-SELECT' ? el.querySelectorAll('option').length : undefined, rect: rect(el)
    }]; })),
    resources: performance.getEntriesByType('resource').map(r => new URL(r.name)).filter(u => /\.wasm$|worker|capture/.test(u.pathname)).map(u => u.origin + u.pathname)
  };
})();
