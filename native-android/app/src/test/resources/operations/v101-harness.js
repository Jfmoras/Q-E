// Regenerate: OPS_JS=<V10.1 operations.js> APP_JS=<V10.1 app.js> node v101-harness.js "$(cat cases.json)" > expected-v101.json
// Runs the unchanged operations.js against a minimal fake DOM and replays each scripted case, recording the
// stored mwi-operations-v1 data (machines included; report op: month 'now' = current UTC month), the #message text and the rendered figures after every step.
const fs=require('fs');
const src=fs.readFileSync(process.env.OPS_JS,'utf8');
const appLines=fs.readFileSync(process.env.APP_JS,'utf8').split('\n');
// app.js num/money/blank state only, to build the autosave the "Import current Q&E quotation details" button reads.
const strip=s=>s.replace(/<[^>]+>/g,'\n').split('\n').map(x=>x.trim()).filter(Boolean);
async function runCase(cs){
  const els={},listeners={},store={};
  const mk=id=>els[id]||(els[id]={id,value:'',innerHTML:'',textContent:'',files:[],hidden:false,dataset:{},style:{},
    addEventListener(t,f){},appendChild(){},replaceChildren(){},scrollIntoView(){}});
  global.document={getElementById:mk,querySelectorAll:()=>[],addEventListener:(t,f)=>{listeners[t]=f},createElement:()=>mk('tmp')};
  global.localStorage={getItem:k=>k in store?store[k]:null,setItem:(k,v)=>{store[k]=String(v)},removeItem:k=>{delete store[k]}};
  global.location={search:cs.search||''};
  global.indexedDB={open(){const r={};setTimeout(()=>r.onerror&&r.onerror(),0);return r}};
  if(cs.pending)store['mwi-project-import-pending']=JSON.stringify(cs.pending);
  if(cs.autosave)store['mwi-autosave-v1']=JSON.stringify(cs.autosave);
  const realNow=Date.now;
  new Function(src)();
  const steps=[];
  const snap=()=>{
    const db=JSON.parse(store['mwi-operations-v1']||'{"projects":[],"materials":[],"movements":[]}');
    steps.push({db:{projects:db.projects||[],materials:db.materials||[],movements:db.movements||[],
      machines:(db.machines||[]).map(m=>({...m,history:m.history.map(h=>({...h,at:''}))}))},
      machineList:strip(mk('machineList').innerHTML),reportBody:strip(mk('reportBody').innerHTML),message:mk('message').textContent,
      form:{customer:mk('pCustomer').value,name:mk('pName').value,value:mk('pValue').value,notes:mk('pNotes').value},
      projectList:strip(mk('projectList').innerHTML).filter(x=>x.startsWith('₹')||/^(Payment|Revision|Expense) ₹/.test(x)),
      materialList:strip(mk('materialList').innerHTML),movementList:strip(mk('movementList').innerHTML).map(x=>x.slice(16))});
  };
  snap();
  for(const op of cs.ops||[]){
    const [k,...a]=op;
    const pid=i=>JSON.parse(store['mwi-operations-v1']).projects[i].id;
    const mid=i=>JSON.parse(store['mwi-operations-v1']).materials[i].id;
    if(k==='create'){const [c,n,v,p,notes]=a;mk('pCustomer').value=c;mk('pName').value=n;mk('pValue').value=v;mk('pPaid').value=p;mk('pNotes').value=notes;await mk('createProject').onclick()}
    else if(k==='entry'){const [i,amount,kind,note]=a;const id=pid(i);mk('amount-'+id).value=amount;mk('kind-'+id).value=kind;mk('note-'+id).value=note;await listeners.click({target:{dataset:{add:id}}})}
    else if(k==='status'){const [i,v]=a;await listeners.change({target:{dataset:{status:pid(i)},value:v}})}
    else if(k==='notes'){const [i,v]=a;await listeners.change({target:{dataset:{projectnotes:pid(i)},value:v}})}
    else if(k==='material'){const [n,u,c,r,m]=a;mk('iName').value=n;mk('iUnit').value=u;mk('iCategory').value=c;mk('iRate').value=r;mk('iMin').value=m;mk('addMaterial').onclick()}
    else if(k==='move'){const [mi,q,t,w,pi]=a;mk('moveMaterial').value=mi<0?'':mid(mi);mk('moveQty').value=q;mk('moveType').value=t;mk('moveWorker').value=w;mk('moveProject').value=pi<0?'':pid(pi);mk('recordMovement').onclick()}
    else if(k==='import'){mk('importQuote').onclick()}
    else if(k==='machine'){const [n,se,w]=a;mk('mName').value=n;mk('mSerial').value=se;mk('mWorker').value=w;mk('addMachine').onclick()}
    else if(k==='mstatus'){const [i,v]=a;await listeners.change({target:{dataset:{machine:'MWI-'+String(i+1).padStart(4,'0')},value:v}})}
    else if(k==='mworker'){const [i,v]=a;await listeners.change({target:{dataset:{worker:'MWI-'+String(i+1).padStart(4,'0')},value:v}})}
    else if(k==='report'){const [m]=a;mk('reportMonth').value=m==='now'?new Date().toISOString().slice(0,7):m;mk('reportMonth').onchange()}
    await new Promise(r=>setTimeout(r,1));
    snap();
  }
  return steps;
}
(async()=>{const cases=JSON.parse(process.argv[2]);const out=[];for(const c of cases)out.push(await runCase(c));console.log(JSON.stringify(out,null,1))})();
