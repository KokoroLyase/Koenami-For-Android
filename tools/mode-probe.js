(() => {
  const record = document.getElementById('record'), map = document.getElementById('voice-map');
  const result = [];
  for (let i=0;i<10;i++) {
    window.__koenamiAndroid.visual(true);
    result.push(document.querySelectorAll('#koenami-android-style').length === 1);
    window.__koenamiAndroid.visual(false);
    result.push(document.querySelectorAll('#koenami-android-style').length === 0);
  }
  window.__koenamiAndroid.visual(true);
  return {allModesClean:result.every(Boolean),recordPreserved:record===document.getElementById('record'),
    canvasPreserved:map===document.getElementById('voice-map'),
    mapHeight:map.getBoundingClientRect().height,
    horizontalOverflow:document.documentElement.scrollWidth>innerWidth,
    adapter:window.__koenamiAndroid.diagnostics()};
})();
