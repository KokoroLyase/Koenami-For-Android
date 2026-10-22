(() => {
  const modules = new Map(), failures = new Map();
  let desired=false, mounted=false, root=null, style=null;
  const unmount=()=>{
    for(const module of [...modules.values()].reverse()) {
      try {module.unmount();} catch (_) {failures.set(module.id,'unmount');}
    }
    style?.remove(); style=null; document.documentElement.removeAttribute('data-koenami-android');
    mounted=false;root=null;
  };
  const set=on=>{
    desired=!!on;unmount();failures.clear();
    if(!desired)return true;
    if(!window.__koenamiAdapter.core() || !document.head){failures.set('core','structure');return false;}
    style=document.createElement('style');style.id='koenami-android-style';
    style.textContent='html[data-koenami-android]{--koenami-enhanced:1}\n'+Object.values(window.__koenamiStyles||{}).join('\n');
    document.head.append(style);document.documentElement.setAttribute('data-koenami-android','1.0');
    if(getComputedStyle(document.documentElement).getPropertyValue('--koenami-enhanced').trim()!=='1'){
      unmount();failures.set('core','style');return false;
    }
    mounted=true;root=window.__koenamiAdapter.one('graph');
    for(const module of modules.values()) {
      try {
        if(module.detect())module.mount();else failures.set(module.id,'structure');
      } catch (_) {try{module.unmount();}catch(_){} failures.set(module.id,'mount');}
    }
    return true;
  };
  window.__koenamiVisual=Object.freeze({
    register(module){if(modules.has(module.id))throw Error('Duplicate enhancement module');modules.set(module.id,module);},
    set,unmount,
    recheck(){if(desired&&(!window.__koenamiAdapter.core()||!root?.isConnected||root!==window.__koenamiAdapter.one('graph')))set(true);},
    health:()=>({desired,mounted,modules:[...modules.keys()].filter(id=>!failures.has(id)),failed:Object.fromEntries(failures)})
  });
})();
