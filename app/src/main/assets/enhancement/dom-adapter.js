(() => {
  const selectors = Object.freeze({record:'#record',live:'#live-mode',map:'#voice-map',signal:'#signal-canvas',
    graph:'.graph-area',transport:'.transport',library:'#sample-browser',profile:'.profile-panel',
    toolbar:'.toolbar',signalPanel:'.signal-panel',history:'#take-select',reference:'#selected-text'});
  const one = key => document.querySelector(selectors[key]);
  const core = () => one('record') instanceof HTMLButtonElement && one('live') instanceof HTMLButtonElement &&
    one('map') instanceof HTMLCanvasElement && one('signal') instanceof HTMLCanvasElement &&
    document.querySelectorAll('#voice-map').length === 1 && one('graph')?.contains(one('map')) &&
    one('transport')?.contains(one('record'));
  window.__koenamiAdapter = Object.freeze({version:'1.0',selectors,one,core});
})();
