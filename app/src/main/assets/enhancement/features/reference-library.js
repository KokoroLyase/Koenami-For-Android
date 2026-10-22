(() => {
  const a=window.__koenamiAdapter;let note;
  window.__koenamiVisual.register({id:'library',
    detect:()=>a.one('library') instanceof HTMLDialogElement && a.one('library').querySelector('#sample-scroll') && a.one('library').querySelector('#search'),
    mount(){
      document.documentElement.setAttribute('data-koenami-library','');
      note=document.createElement('p');note.id='koenami-android-library-note';note.textContent='自己的参考音频需收藏后才会持久保留。';
      a.one('library').append(note);
    },
    unmount(){note?.remove();document.documentElement.removeAttribute('data-koenami-library');}
  });
})();
