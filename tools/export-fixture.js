// Debug-only synthetic JSON export: exceeds four 48 KiB chunks and uses a detached anchor.
(async () => {
  const bytes = new TextEncoder().encode(JSON.stringify({fixture:'Koenami M0',message:'中文导出验证',values:Array.from({length:40000},(_,i)=>i)}));
  const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))].map(b=>b.toString(16).padStart(2,'0')).join('');
  const a = document.createElement('a'); a.download = 'Koenami-M0-export.json';
  a.href = URL.createObjectURL(new Blob([bytes], {type:'application/json'}));
  a.click(); setTimeout(()=>URL.revokeObjectURL(a.href), 1000);
  return {size:bytes.length,sha256:hash};
})();
