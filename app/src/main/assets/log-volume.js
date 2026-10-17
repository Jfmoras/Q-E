(() => {
  'use strict';
  const $=id=>document.getElementById(id);
  const store='mwi-log-volume-v1';
  const fresh=()=>({id:String(Date.now())+Math.random().toString(36).slice(2),length:'',girth:'',quantity:'1'});
  let rows;
  try {
    const loaded=JSON.parse(localStorage.getItem(store)||'[]');
    rows=Array.isArray(loaded)&&loaded.length?loaded.filter(x=>x&&typeof x==='object').map(x=>({id:String(x.id||fresh().id),length:String(x.length??''),girth:String(x.girth??''),quantity:String(x.quantity??'1')})):[fresh()];
  } catch {rows=[fresh()];}
  const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const volume=r=>{
    const l=Number(r.length),g=Number(r.girth),q=Number(r.quantity);
    if(!String(r.length).trim()||!String(r.girth).trim()||!String(r.quantity).trim()||!Number.isFinite(l)||!Number.isFinite(g)||!Number.isFinite(q)||l<=0||g<=0||!Number.isInteger(q)||q<1)return null;
    return Math.round((((g/4)**2*l/144)+Number.EPSILON)*100)/100*q;
  };
  const fmt=x=>x.toLocaleString('en-IN',{minimumFractionDigits:2,maximumFractionDigits:2});
  const save=()=>{try{localStorage.setItem(store,JSON.stringify(rows))}catch{}};
  function totals(){
    let sum=0;
    rows.forEach((r,i)=>{
      const v=volume(r),out=$('logVolumeRows').querySelectorAll('.log-result')[i];
      if(out)out.textContent=v===null?'—':fmt(v)+' CFT';
      if(v!==null)sum+=v;
    });
    $('logVolumeTotal').textContent=fmt(sum)+' CFT';
    save();
  }
  function render(){
    $('logVolumeRows').innerHTML=rows.map((r,i)=>`<div class="log-entry" data-index="${i}">
      <label>Length (feet)<input class="input log-input" data-field="length" type="number" min="0" step="any" inputmode="decimal" placeholder="e.g. 10" value="${escape(r.length)}"></label>
      <label>Girth (inches)<input class="input log-input" data-field="girth" type="number" min="0" step="any" inputmode="decimal" placeholder="e.g. 175" value="${escape(r.girth)}"></label>
      <label>Logs<input class="input log-input" data-field="quantity" type="number" min="1" step="1" inputmode="numeric" value="${escape(r.quantity)}"></label>
      <div class="log-result-wrap"><label>Hoppus volume</label><div class="log-result" aria-live="polite">—</div></div>
      <button class="secondary log-remove" data-remove="${i}" type="button" aria-label="Remove log ${i+1}">Remove</button>
    </div>`).join('');
    totals();
  }
  function show(){
    $('homeView').classList.add('hidden');
    $('workspaceView').classList.add('hidden');
    $('notesView').classList.add('hidden');
    $('logVolumeView').classList.remove('hidden');
    $('goHome').classList.remove('hidden');
    window.scrollTo(0,0);
  }
  document.addEventListener('click',e=>{
    const b=e.target.closest('button');if(!b)return;
    if(b.id==='logVolumeHome'){show();return}
    if(b.id==='goHome'){ $('logVolumeView').classList.add('hidden'); return}
    if(b.id==='addLogRow'){rows.push(fresh());render();const entries=$('logVolumeRows').querySelectorAll('.log-entry');entries[entries.length-1]?.querySelector('input')?.focus();return}
    if(b.hasAttribute('data-remove')){rows.splice(Number(b.dataset.remove),1);if(!rows.length)rows=[fresh()];render();return}
    if(b.id==='clearLogRows'){rows=[fresh()];render();return}
  });
  $('logVolumeRows').addEventListener('input',e=>{
    const input=e.target.closest('.log-input'),entry=e.target.closest('.log-entry');
    if(!input||!entry)return;
    rows[Number(entry.dataset.index)][input.dataset.field]=input.value;
    totals();
  });
  render();
})();