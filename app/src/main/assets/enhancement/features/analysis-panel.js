(() => {
  const a=window.__koenamiAdapter,root=document.documentElement;
  let header,toggle,full,previous;
  const leave=()=>{if(!root.hasAttribute('data-koenami-analysis-full'))return false;root.removeAttribute('data-koenami-analysis-full');full.textContent='全屏分析';previous?.focus({preventScroll:true});return true;};
  window.__koenamiVisual.register({id:'analysis',
    detect:()=>a.one('signalPanel')?.contains(a.one('signal')),
    mount(){
      root.setAttribute('data-koenami-analysis','');
      header=document.createElement('div');header.id='koenami-android-analysis-header';
      toggle=document.createElement('button');toggle.type='button';toggle.textContent='分析与对比 ▾';toggle.setAttribute('aria-expanded','false');toggle.setAttribute('aria-controls','signal-canvas');
      full=document.createElement('button');full.type='button';full.textContent='全屏分析';
      toggle.onclick=()=>{leave();const on=!root.hasAttribute('data-koenami-analysis-open');root.toggleAttribute('data-koenami-analysis-open',on);toggle.setAttribute('aria-expanded',String(on));toggle.textContent=on?'收起分析 ▴':'分析与对比 ▾';};
      full.onclick=()=>{if(leave())return;previous=document.activeElement;root.setAttribute('data-koenami-analysis-open','');root.setAttribute('data-koenami-analysis-full','');toggle.setAttribute('aria-expanded','true');toggle.textContent='收起分析 ▴';full.textContent='退出全屏';full.focus({preventScroll:true});};
      header.append(toggle,full);a.one('signalPanel').prepend(header);
    },back:leave,
    unmount(){leave();header?.remove();for(const n of ['analysis','analysis-open','analysis-full'])root.removeAttribute(`data-koenami-${n}`);}
  });
})();
