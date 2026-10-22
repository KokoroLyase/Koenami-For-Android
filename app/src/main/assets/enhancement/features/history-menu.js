(() => {
  const a=window.__koenamiAdapter;let style,host,observer,timer;
  const detect=()=>{
    const h=a.one('history'),r=h?.shadowRoot;if(!r?.querySelector('.trigger')||!r.querySelector('.list[popover] .items'))return false;
    const items=r.querySelector('.items');
    return [...items.children].every(row=>row.matches('.choice-row')&&row.querySelector('.title')&&row.querySelector('.row-actions'));
  };
  const unmount=()=>{clearTimeout(timer);observer?.disconnect();style?.remove();host?.removeAttribute('data-koenami-history');};
  window.__koenamiVisual.register({id:'history',detect,
    mount(){
      host=a.one('history');host.setAttribute('data-koenami-history','');
      style=document.createElement('style');style.id='koenami-android-history-style';
      style.textContent=window.__koenamiStyles['styles/history-shadow.css'];host.shadowRoot.append(style);
      observer=new MutationObserver(()=>{if(timer)return;timer=setTimeout(()=>{timer=null;if(!detect()){unmount();window.__koenamiVisual.fail('history','structure');}},300);});
      observer.observe(host.shadowRoot.querySelector('.items'),{childList:true});
    },unmount
  });
})();
