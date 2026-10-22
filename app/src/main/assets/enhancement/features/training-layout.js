(() => {
  const a=window.__koenamiAdapter, visual=window.__koenamiVisual;
  const root=document.documentElement;
  let nodes=[],listeners=[],resize,focusBefore;
  const attr=(name,on)=>root.toggleAttribute(`data-koenami-${name}`,on);
  const button=(id,text,parent,handler)=>{
    const b=document.createElement('button');b.id=`koenami-android-${id}`;b.type='button';b.textContent=text;
    const listener=()=>handler(b);b.addEventListener('click',listener);listeners.push([b,listener]);nodes.push(b);parent.append(b);return b;
  };
  const leave=()=>{
    if(!root.hasAttribute('data-koenami-map-full'))return false;
    attr('map-full',false);nodes.find(n=>n.id==='koenami-android-map-full').textContent='全屏';
    focusBefore?.focus({preventScroll:true});return true;
  };
  visual.register({id:'training',
    detect:()=>a.core()&&a.one('toolbar')&&a.one('profile')&&document.querySelector('.graph')&&a.one('reference'),
    mount(){
      attr('training',true);
      const tools=button('tools','官网工具 ▾',a.one('toolbar'),b=>{
        const on=!root.hasAttribute('data-koenami-tools');attr('tools',on);b.setAttribute('aria-expanded',String(on));b.textContent=on?'收起工具 ▴':'官网工具 ▾';
      });tools.setAttribute('aria-expanded','false');
      const expand=button('reference','展开文本',a.one('reference').parentElement,b=>{
        const on=!root.hasAttribute('data-koenami-reference-open');attr('reference-open',on);b.setAttribute('aria-expanded',String(on));b.textContent=on?'收起文本':'展开文本';
      });expand.setAttribute('aria-expanded','false');
      button('map-full','全屏',document.querySelector('.graph'),b=>{
        if(leave())return;focusBefore=document.activeElement;attr('map-full',true);b.textContent='退出全屏';b.focus({preventScroll:true});
      });
      const update=()=>{const height=Math.ceil(a.one('transport').getBoundingClientRect().height);root.style.setProperty('--koenami-transport-height',`${height}px`);};
      resize=new ResizeObserver(update);resize.observe(a.one('transport'));update();
    },
    back:leave,
    unmount(){leave();resize?.disconnect();for(const [b,l] of listeners)b.removeEventListener('click',l);listeners=[];nodes.forEach(n=>n.remove());nodes=[];
      for(const name of ['training','tools','reference-open','map-full'])attr(name,false);root.style.removeProperty('--koenami-transport-height');}
  });
})();
