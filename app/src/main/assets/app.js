(() => {
  'use strict';
  const $ = (s, root=document) => root.querySelector(s);
  const $$ = (s, root=document) => [...root.querySelectorAll(s)];
  const num = v => Number(v) || 0;
  const money = v => new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(Number(v)||0);
  const rs = v => `Rs. ${Math.round(Number(v)||0).toLocaleString('en-IN')}`;
  const blankRow = () => ({l:'',lUnit:'ft',w:'',wUnit:'in',t:'',tUnit:'in',q:'',mode:'CFT'});
  const blankItem = () => ({name:'',rows:[blankRow()]});
  const blankCosting = () => ({cftRate:'',kolRate:'',labourMode:'sqft',labourLength:'',labourLengthUnit:'ft',labourWidth:'',labourWidthUnit:'ft',labourQty:'',labourRate:'',labourLumpsum:'',polishMode:'sqft',polishRate:'',polishLumpsum:'',polishAreaOverride:'',consumables:'',other:'',sellingPrice:'',sellingAuto:true});
  const blankQuote = () => ({description:'',unit:'',qty:'',unitPrice:'',images:[],aiOriginal:''});
  const defaultState = () => ({
    tab:'calculator', projectName:'', items:[blankItem()], estimationItems:[], costings:[], quoteItems:[blankQuote()],
    customer:{name:'',location:''}, quote:{discountType:'amount',discountValue:'',gstPercent:'',notes:['']},
    actual:{materials:'',labourHours:''}
  });
  let state = defaultState();

  const lengthUnits=['ft','in','cm','mm','m'];
  const unitLabel=u=>({ft:'ft',in:'in',cm:'cm',mm:'mm',m:'m'}[u]||u);
  const toFeet=(value,unit='ft')=>{const v=num(value);return unit==='in'?v/12:unit==='cm'?v/30.48:unit==='mm'?v/304.8:unit==='m'?v*3.280839895:v};
  const toInches=(value,unit='in')=>{const v=num(value);return unit==='ft'?v*12:unit==='cm'?v/2.54:unit==='mm'?v/25.4:unit==='m'?v*39.37007874:v};
  const fmtMeasure=v=>{const x=Number(v)||0;return x?Number(x.toFixed(3)).toString():'0'};
  const calcRow = r => { const lFt=toFeet(r.l,r.lUnit||'ft'),wIn=toInches(r.w,r.wUnit||'in'),tIn=toInches(r.t,r.tUnit||'in'),raw=lFt*wIn*tIn*num(r.q); return {mode:r.mode,val:raw/(r.mode==='CFT'?144:37.5),lFt,wIn,tIn}; };
  const surfaceSqft = r => { const l=toFeet(r.l,r.lUnit||'ft'),w=toInches(r.w,r.wUnit||'in')/12,t=toInches(r.t,r.tUnit||'in')/12,q=num(r.q); return 2*(l*w+l*t+w*t)*q; };
  const labourAreaSqft=c=>toFeet(c.labourLength,c.labourLengthUnit||'ft')*toFeet(c.labourWidth,c.labourWidthUnit||'ft')*num(c.labourQty);
  const unitOptions = current => lengthUnits.map(u=>`<option value="${u}" ${u===current?'selected':''}>${unitLabel(u)}</option>`).join('');
  const originalMeasure=(value,unit)=>value?`${esc(value)} ${esc(unitLabel(unit))}`:'-';
  const itemTotals = item => item.rows.reduce((a,r)=>{const x=calcRow(r);a[x.mode]+=x.val;return a},{CFT:0,KOL:0});
  const itemSurface = item => item.rows.reduce((a,r)=>a+surfaceSqft(r),0);
  const costingResult = i => {
    const item=state.estimationItems[i]||{rows:[]}, c=state.costings[i]||blankCosting(), tot=itemTotals(item), surface=itemSurface(item);
    const polishArea=num(c.polishAreaOverride)||surface;
    const wood=tot.CFT*num(c.cftRate)+tot.KOL*num(c.kolRate);
    const labourArea=labourAreaSqft(c);
    const labour=(c.labourMode||'sqft')==='lumpsum'?num(c.labourLumpsum):labourArea*num(c.labourRate);
    const polish=c.polishMode==='sqft'?polishArea*num(c.polishRate):num(c.polishLumpsum);
    const cost=wood+labour+polish+num(c.consumables)+num(c.other);
    const sellingBlank=!String(c.sellingPrice||'').trim();if(sellingBlank || c.sellingAuto===true){c.sellingPrice=cost?String(Math.round(cost)):'';c.sellingAuto=true}
    const selling=num(c.sellingPrice), profit=selling-cost;
    return {tot,surface,polishArea,wood,labourArea,labour,polish,cost,selling,profit,markup:cost?profit/cost*100:0,margin:selling?profit/selling*100:0};
  };
  const estimateGrand = () => state.estimationItems.reduce((a,_,i)=>{const r=costingResult(i);a.cost+=r.cost;a.selling+=r.selling;a.profit+=r.profit;return a},{cost:0,selling:0,profit:0});
  const quoteTotals = () => {
    const subtotal=state.quoteItems.reduce((s,x)=>s+num(x.qty)*num(x.unitPrice),0);
    const raw=state.quote.discountType==='percent'?subtotal*num(state.quote.discountValue)/100:num(state.quote.discountValue);
    const discount=Math.min(subtotal,Math.max(0,raw));
    const taxable=Math.max(0,subtotal-discount); const gst=taxable*num(state.quote.gstPercent)/100;
    return {subtotal,discount,taxable,gst,grand:taxable+gst};
  };

  const esc = s => String(s??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
  const attr = esc;
  const uid = () => Math.random().toString(36).slice(2,9);
  const saveAutosave = () => { try { localStorage.setItem('mwi-autosave-v1',JSON.stringify(state)); } catch {} };
  const normalizeUnits = st => { (st.items||[]).forEach(item=>(item.rows||[]).forEach(r=>{r.lUnit=r.lUnit||'ft';r.wUnit=r.wUnit||'in';r.tUnit=r.tUnit||'in'}));(st.estimationItems||[]).forEach(item=>(item.rows||[]).forEach(r=>{r.lUnit=r.lUnit||'ft';r.wUnit=r.wUnit||'in';r.tUnit=r.tUnit||'in'}));(st.costings||[]).forEach(c=>{c.labourLengthUnit=c.labourLengthUnit||'ft';c.labourWidthUnit=c.labourWidthUnit||'ft'});return st; };
  const loadAutosave = () => { try { const x=JSON.parse(localStorage.getItem('mwi-autosave-v1')||'null'); if(x) state=normalizeUnits({...defaultState(),...x}); } catch {} };
  const savedList = () => { try { return JSON.parse(localStorage.getItem('mwi-projects-v1')||'[]'); } catch { return []; } };
  const writeSaved = list => localStorage.setItem('mwi-projects-v1',JSON.stringify(list));
  const savedNotes = () => { try { return JSON.parse(localStorage.getItem('mwi-saved-notes-v1')||'[]'); } catch { return []; } };
  const writeSavedNotes = list => localStorage.setItem('mwi-saved-notes-v1',JSON.stringify(list));
  const aiSettingsStorage='mwi-ai-settings-v1';
  const aiDefaults={endpoint:'https://api.openai.com/v1',model:'gpt-4o-mini'};
  const aiSettings=(()=>{try{return {...aiDefaults,...JSON.parse(localStorage.getItem(aiSettingsStorage)||'{}')}}catch{return {...aiDefaults}}})();
  const aiRequests=new Map();
  const aiProviders={
    openai:{label:'OpenAI',endpoint:'https://api.openai.com/v1',model:'gpt-4o-mini',format:'openai'},
    anthropic:{label:'Anthropic',endpoint:'https://api.anthropic.com/v1/messages',model:'claude-haiku-5-5',format:'anthropic'},
    google:{label:'Google Gemini',endpoint:'https://generativelanguage.googleapis.com/v1beta',model:'gemini-3.8-flash',format:'google'},
    groq:{label:'Groq',endpoint:'https://api.groq.com/openai/v1',model:'llama-3.3-70b-versatile',format:'openai'},
    openrouter:{label:'OpenRouter',endpoint:'https://openrouter.ai/api/v1',model:'openai/gpt-4o-mini',format:'openai'},
    xai:{label:'xAI',endpoint:'https://api.x.ai/v1',model:'grok-4.7',format:'openai'},
    perplexity:{label:'Perplexity',endpoint:'https://api.perplexity.ai',model:'sonar',format:'openai'},
    generic:{label:'Unrecognized provider',endpoint:'https://api.openai.com/v1',model:'gpt-4o-mini',format:'openai'}
  };
  let detectedAiProvider='';
  const converterUnits={mm:{label:'Millimetres (mm)',metres:0.001},cm:{label:'Centimetres (cm)',metres:0.01},m:{label:'Metres (m)',metres:1},in:{label:'Inches (in)',metres:0.0254},ft:{label:'Feet (ft)',metres:0.3048},yd:{label:'Yards (yd)',metres:0.9144},mi:{label:'Miles (mi)',metres:1609.344}};
  window.QENativeAiResult=(id,success,result)=>{
    const request=aiRequests.get(id);
    if(!request)return;
    clearTimeout(request.timeout);
    aiRequests.delete(id);
    if(success)request.resolve(result);else request.reject(new Error(result||'AI rewrite failed.'));
  };
  function hasAiKey(){
    if(window.QENative?.hasApiKey)return window.QENative.hasApiKey();
    try{return Boolean(JSON.parse(localStorage.getItem(aiSettingsStorage)||'{}').apiKey||localStorage.getItem('mwi-openai-api-key-v1'))}catch{return false}
  }
  function detectAiProvider(apiKey){
    const key=apiKey.trim();
    if(key.startsWith('sk-or-v1-'))return 'openrouter';
    if(key.startsWith('sk-ant-'))return 'anthropic';
    if(key.startsWith('AIza'))return 'google';
    if(key.startsWith('gsk_'))return 'groq';
    if(key.startsWith('xai-'))return 'xai';
    if(key.startsWith('pplx-'))return 'perplexity';
    if(key.startsWith('sk-proj-')||key.startsWith('sk-svcacct-'))return 'openai';
    if(key.startsWith('sk-'))return 'generic';
    return key?'generic':'';
  }
  function updateDetectedProvider(apiKey){
    const providerId=detectAiProvider(apiKey);
    const detection=$('#aiDetectionStatus');
    if(!detection)return;
    if(!providerId){detection.textContent='Paste a key to detect the provider.';return}
    detection.textContent=providerId==='generic'
      ?'Provider not recognized from key. Connect AI will try Google Gemini.'
      :`Detected ${aiProviders[providerId].label}. The app will select its connection settings automatically.`;
  }
  function loadAiSettingsFields(){
    let nativeSettings=null;
    if(window.QENative?.getAiSettings){
      try{nativeSettings=JSON.parse(window.QENative.getAiSettings())}catch{}
    }else{
      try{Object.assign(aiSettings,JSON.parse(localStorage.getItem(aiSettingsStorage)||'{}'))}catch{}
    }
    if(nativeSettings)Object.assign(aiSettings,nativeSettings);
    $('#aiApiKey').value='';
    $('#aiDetectionStatus').textContent=hasAiKey()
      ?`Saved API key: ${aiProviders[aiSettings.provider]?.label||'provider unknown'}. Paste a new key to replace it.`
      :'Paste a key to detect the provider.';
    updateAiSettingsStatus();
  }
  async function saveAiSettings(){
    const apiKey=$('#aiApiKey').value.trim();
    if(!apiKey)throw new Error('Paste an API key first.');
    const provider=detectAiProvider(apiKey);
    if(!provider)throw new Error('Paste an API key first.');
    // Unknown key formats are tested ONLY against Google's Gemini endpoint, never arbitrary hosts.
    const selectedProvider=provider==='generic'?'google':provider;
    const preset=aiProviders[selectedProvider];
    const settings={apiKey,provider:selectedProvider,endpoint:preset.endpoint,model:preset.model};
    const button=$('#saveAiSettings');
    button.disabled=true;
    try{
      if(window.QENative?.saveAiSettings){
        const error=window.QENative.saveAiSettings(JSON.stringify(settings));
        if(error)throw new Error(error);
      }else{
        localStorage.setItem(aiSettingsStorage,JSON.stringify(settings));
      }
      Object.assign(aiSettings,settings);
      updateAiSettingsStatus('Checking connection with '+preset.label+'…');
      try {
        await improveDescription('Wooden table', 'concise');
      } catch (firstError) {
        // Only switch models if Google reports the requested model is unavailable.
        if (selectedProvider !== 'google' || !/404|not found|no longer available|not supported/i.test(String(firstError?.message||firstError))) throw firstError;
        settings.model='gemini-3.5-flash-lite';
        if(window.QENative?.saveAiSettings){
          const error=window.QENative.saveAiSettings(JSON.stringify(settings));
          if(error)throw new Error(error);
        }else{
          localStorage.setItem(aiSettingsStorage,JSON.stringify(settings));
        }
        Object.assign(aiSettings,settings);
        updateAiSettingsStatus('Trying an alternative Gemini model…');
        await improveDescription('Wooden table', 'concise');
      }
      $('#aiApiKey').value='';
      $('#aiDetectionStatus').textContent=`Connected to ${preset.label} (${settings.model}).`;
      updateAiSettingsStatus('AI connection tested successfully and saved.');
    }catch(error){
      updateAiSettingsStatus(`Connection not verified: ${error?.message||error}. Check that this is a Gemini API key and has model access.`);
    }finally{button.disabled=false}
  }
  function removeAiKey(){
    if(window.QENative?.clearApiKey){
      const error=window.QENative.clearApiKey();
      if(error)throw new Error(error);
    }else{
      try{const settings=JSON.parse(localStorage.getItem(aiSettingsStorage)||'{}');delete settings.apiKey;localStorage.setItem(aiSettingsStorage,JSON.stringify(settings));localStorage.removeItem('mwi-openai-api-key-v1')}catch{}
    }
    aiSettings.apiKey='';
    aiSettings.provider='';
    const detection=$('#aiDetectionStatus');
    if(detection)detection.textContent='Provider detection starts when you paste an API key.';
    updateAiSettingsStatus('API key removed from this device.');
  }
  function chatCompletionsEndpoint(value){
    const url=new URL(value);
    if(url.protocol!=='https:')throw new Error('The API endpoint must use HTTPS so your key is not sent insecurely.');
    if(!url.pathname.replace(/\/+$/,'').endsWith('/chat/completions')){
      url.pathname=`${url.pathname.replace(/\/+$/,'')}/chat/completions`;
    }
    return url.toString();
  }
  function buildAiRequest(providerId,endpoint,model,apiKey,text,style){
    const provider=aiProviders[providerId]||aiProviders.generic;
    const prompt=`Rewrite the user's woodworking product description in a ${style} style for a customer quotation. Preserve all supplied facts, measurements, materials, and meaning. Do not invent specifications. Return only the improved description.`;
    if(provider.format==='anthropic'){
      const base=endpoint.replace(/\/+$/,'');
      return {url:base.endsWith('/messages')?base:`${base}/messages`,headers:{'x-api-key':apiKey,'anthropic-version':'2023-06-01','anthropic-dangerous-direct-browser-access':'true'},body:{model,max_tokens:350,system:prompt,messages:[{role:'user',content:text}]},extract:data=>data.content?.filter(x=>x.type==='text').map(x=>x.text).join('\n')};
    }
    if(provider.format==='google'){
      const base=endpoint.replace(/\/+$/,'').replace(/\/models$/,'');
      return {url:`${base}/models/${encodeURIComponent(model)}:generateContent`,headers:{'x-goog-api-key':apiKey},body:{systemInstruction:{parts:[{text:prompt}]},contents:[{role:'user',parts:[{text}]}],generationConfig:{maxOutputTokens:350}},extract:data=>data.candidates?.[0]?.content?.parts?.map(x=>x.text||'').join('').trim()};
    }
    return {url:chatCompletionsEndpoint(endpoint),headers:{authorization:`Bearer ${apiKey}`},body:{model,max_tokens:350,messages:[{role:'system',content:prompt},{role:'user',content:text}]},extract:data=>data.choices?.[0]?.message?.content?.trim()};
  }
  function providerErrorMessage(status,endpoint,detail=''){
    if(status===404)return `AI provider returned HTTP 404 from ${endpoint}. Check the API base URL and model. The app appends /chat/completions to base URLs; your provider must support OpenAI-compatible chat completions.${detail?` ${detail}`:''}`;
    return detail||`AI provider request failed (${status}) at ${endpoint}.`;
  }
  async function improveDescription(text,style){
    if(window.QENative?.improveDescription){
      const id=uid();
      return await new Promise((resolve,reject)=>{
        const timeout=setTimeout(()=>{aiRequests.delete(id);reject(new Error('AI request timed out. Check your connection and try again.'))},45000);
        aiRequests.set(id,{resolve,reject,timeout});
        const settings=window.QENative.getAiSettings?JSON.parse(window.QENative.getAiSettings()):aiSettings;
        try{window.QENative.improveDescription(text,style,id,settings.provider||aiSettings.provider||'openai')}
        catch(error){clearTimeout(timeout);aiRequests.delete(id);reject(error)}
      });
    }
    let settings;
    try{settings=JSON.parse(localStorage.getItem(aiSettingsStorage)||'{}')}catch{settings={}}
    const apiKey=settings.apiKey||localStorage.getItem('mwi-openai-api-key-v1')||'';
    if(!apiKey)throw new Error('Add your AI API key in Settings to use this feature.');
    const providerId=settings.provider||detectAiProvider(apiKey)||'generic';
    const model=settings.model||aiSettings.model||aiDefaults.model;
    const request=buildAiRequest(providerId,settings.endpoint||aiSettings.endpoint||aiDefaults.endpoint,model,apiKey,text,style);
    const res=await fetch(request.url,{
      method:'POST',
      headers:{'content-type':'application/json',...request.headers},
      body:JSON.stringify(request.body)
    });
    const data=await res.json().catch(()=>({}));
    if(!res.ok)throw new Error(providerErrorMessage(res.status,request.url,data.error?.message||data.message||''));
    const result=request.extract(data);
    if(!result)throw new Error('AI provider returned an empty description. Verify the selected model and that the endpoint supports OpenAI-compatible chat completions.');
    return result;
  }
  function updateAiSettingsStatus(message){
    const status=$('#aiSettingsStatus');
    if(status)status.textContent=message||(hasAiKey()?'API key saved on this device.':'No API key saved.');
  }

  function showView(view){
    $('#homeView').classList.toggle('hidden',view!=='home');
    $('#workspaceView').classList.toggle('hidden',view!=='workspace');
    $('#notesView').classList.toggle('hidden',view!=='notes');
    $('#goHome').classList.toggle('hidden',view==='home');
    if(view==='notes')renderNotesManager();
  }
  function showTab(name){ state.tab=name; $$('.tab').forEach(b=>b.classList.toggle('active',b.dataset.tab===name)); $$('.panel').forEach(p=>p.classList.toggle('active-panel',p.id===name)); saveAutosave(); }
  function setFloatingConverter(open){
    $('#floatingConverterPanel').classList.toggle('hidden',!open);
    $('#floatingConverterButton').setAttribute('aria-expanded',String(open));
    if(open)$('#lengthValue').focus();else $('#floatingConverterButton').focus();
  }
  function renderLengthConverter(){
    const from=$('#lengthFrom'),to=$('#lengthTo');
    const options=Object.entries(converterUnits).map(([unit,data])=>`<option value="${unit}">${data.label}</option>`).join('');
    from.innerHTML=options;to.innerHTML=options;from.value='mm';to.value='in';
    updateLengthConversion();
  }
  function updateLengthConversion(){
    const value=$('#lengthValue').value,result=$('#lengthResult');
    if(value===''||!Number.isFinite(Number(value))){result.textContent='Enter a value to convert.';return}
    const from=converterUnits[$('#lengthFrom').value],to=converterUnits[$('#lengthTo').value];
    const converted=Number(value)*from.metres/to.metres;
    result.textContent=`${Number(value).toLocaleString()} ${$('#lengthFrom').value} = ${Number(converted.toPrecision(10)).toLocaleString(undefined,{maximumFractionDigits:8})} ${$('#lengthTo').value}`;
  }
  function renderNotesManager(){
    const host=$('#savedNotesList'),notes=savedNotes();
    host.innerHTML=notes.length?notes.map((note,i)=>`<div class="saved-note-row" data-i="${i}"><textarea class="input saved-note-edit" rows="2">${esc(note)}</textarea><button class="secondary update-saved-note" type="button">Update</button><button class="danger delete-saved-note" type="button">Delete</button></div>`).join(''):'<p class="muted">No saved notes yet.</p>';
  }
  function hasProjectWork(){
    return Boolean(state.projectName||state.items.some(item=>item.name||item.rows.some(row=>row.l||row.w||row.t||row.q))||state.estimationItems.length||state.customer.name||state.quoteItems.some(item=>item.description||item.unitPrice||item.images.length));
  }
  function startNewProject(){
    if(hasProjectWork()&&!window.confirm('Start a new project? Your current unsaved work will be replaced. Save the current project first if you want to keep it.'))return;
    state=defaultState();
    saveAutosave();renderAll();showView('workspace');$('#projectName').focus();
  }
  function renderCalc(){
    const host=$('#calculatorItems'); host.innerHTML='';
    state.items.forEach((item,ii)=>{
      const tot=itemTotals(item); const box=document.createElement('section'); box.className='calc-item';
      box.innerHTML=`<div class="item-head"><label>Item</label><input class="input item-name" data-ii="${ii}" value="${attr(item.name)}" placeholder="Item name"></div><div class="table-wrap"><table class="calc-table unit-calc-table"><thead><tr><th>Length</th><th>Width</th><th>Thickness</th><th>No. of pieces</th><th>Type</th><th>Total</th></tr></thead><tbody>${item.rows.map((r,ri)=>{const x=calcRow(r),lu=r.lUnit||'ft',wu=r.wUnit||'in',tu=r.tUnit||'in',showConv=lu!=='ft'||wu!=='in'||tu!=='in';return `<tr><td><div class="measure-entry"><input type="number" min="0" step="any" class="cell calc-cell" data-ii="${ii}" data-ri="${ri}" data-key="l" value="${attr(r.l)}"><select class="cell measure-unit" data-ii="${ii}" data-ri="${ri}" data-unit-key="lUnit" tabindex="-1">${unitOptions(lu)}</select></div></td><td><div class="measure-entry"><input type="number" min="0" step="any" class="cell calc-cell" data-ii="${ii}" data-ri="${ri}" data-key="w" value="${attr(r.w)}"><select class="cell measure-unit" data-ii="${ii}" data-ri="${ri}" data-unit-key="wUnit" tabindex="-1">${unitOptions(wu)}</select></div></td><td><div class="measure-entry"><input type="number" min="0" step="any" class="cell calc-cell" data-ii="${ii}" data-ri="${ri}" data-key="t" value="${attr(r.t)}"><select class="cell measure-unit" data-ii="${ii}" data-ri="${ri}" data-unit-key="tUnit" tabindex="-1">${unitOptions(tu)}</select></div></td><td><input type="number" min="0" step="any" class="cell calc-cell" data-ii="${ii}" data-ri="${ri}" data-key="q" value="${attr(r.q)}"></td><td><select class="cell row-mode" data-ii="${ii}" data-ri="${ri}" tabindex="-1"><option ${r.mode==='CFT'?'selected':''}>CFT</option><option ${r.mode==='KOL'?'selected':''}>KOL</option></select></td><td class="row-total"><b>${x.val?x.val.toFixed(3)+' '+x.mode:'-'}</b>${showConv?`<small class="converted-measure">Converted: ${fmtMeasure(x.lFt)} ft × ${fmtMeasure(x.wIn)} in × ${fmtMeasure(x.tIn)} in</small>`:''}</td></tr>`}).join('')}</tbody></table></div><button class="secondary add-row" data-ii="${ii}" style="margin:12px">+ Add row</button><div class="summary item-summary" data-ii="${ii}">Item total: <b>${tot.CFT.toFixed(3)} CFT</b> · <b>${tot.KOL.toFixed(3)} KOL</b></div>`;
      host.appendChild(box);
    });
    const g=state.items.reduce((a,it)=>{const t=itemTotals(it);a.CFT+=t.CFT;a.KOL+=t.KOL;return a},{CFT:0,KOL:0}); $('#calcGrand').textContent=`${g.CFT.toFixed(3)} CFT | ${g.KOL.toFixed(3)} KOL`;
  }

  function refreshCalcNumbers(ii,ri){
    const item=state.items[ii]; if(!item)return;
    const row=item.rows[ri], rowEl=$(`.calc-cell[data-ii="${ii}"][data-ri="${ri}"]`)?.closest('tr');
    if(rowEl&&row){const x=calcRow(row),lu=row.lUnit||'ft',wu=row.wUnit||'in',tu=row.tUnit||'in',showConv=lu!=='ft'||wu!=='in'||tu!=='in';const cell=$('.row-total b',rowEl);if(cell)cell.textContent=x.val?`${x.val.toFixed(3)} ${x.mode}`:'-';const totalCell=$('.row-total',rowEl);if(totalCell){let helper=$('.converted-measure',totalCell);if(showConv){if(!helper){helper=document.createElement('small');helper.className='converted-measure';totalCell.appendChild(helper)}helper.textContent=`Converted: ${fmtMeasure(x.lFt)} ft × ${fmtMeasure(x.wIn)} in × ${fmtMeasure(x.tIn)} in`}else helper?.remove()}}
    const tot=itemTotals(item),summary=$(`.item-summary[data-ii="${ii}"]`);if(summary)summary.innerHTML=`Item total: <b>${tot.CFT.toFixed(3)} CFT</b> · <b>${tot.KOL.toFixed(3)} KOL</b>`;
    const g=state.items.reduce((a,it)=>{const t=itemTotals(it);a.CFT+=t.CFT;a.KOL+=t.KOL;return a},{CFT:0,KOL:0});$('#calcGrand').textContent=`${g.CFT.toFixed(3)} CFT | ${g.KOL.toFixed(3)} KOL`;
  }

  function field(label,key,val,type='number'){return `<label class="field">${label}<input class="input est-input" type="${type}" min="0" step="any" data-key="${key}" value="${attr(val)}"></label>`}
  function measureField(label,key,val,unitKey,unit){return `<label class="field">${label}<div class="measure-entry estimate-measure"><input class="input est-input" type="number" min="0" step="any" data-key="${key}" value="${attr(val)}"><select class="input est-unit" data-unit-key="${unitKey}">${unitOptions(unit||'ft')}</select></div></label>`}
  function renderEstimation(){
    const host=$('#estimationItems'); host.innerHTML='';
    if(!state.estimationItems.length){host.innerHTML='<div class="card" style="padding:16px;margin-bottom:16px;color:#57534e">No calculator items have been sent yet. You can still add an estimation item manually.</div>'}
    state.estimationItems.forEach((item,i)=>{
      if(!state.costings[i]) state.costings[i]=blankCosting(); const c=state.costings[i],r=costingResult(i),card=document.createElement('section'); card.className='estimate-card'; card.dataset.i=i;
      const measures=item.rows.length?`<div class="table-wrap"><table class="calc-table estimate-measure-table"><thead><tr><th>Length</th><th>Width</th><th>Thickness</th><th>Pieces</th><th>Type</th><th>Total</th></tr></thead><tbody>${item.rows.map(x=>{const z=calcRow(x),lu=x.lUnit||'ft',wu=x.wUnit||'in',tu=x.tUnit||'in';return `<tr><td>${originalMeasure(x.l,lu)}${lu!=='ft'?`<small class="converted-measure">${fmtMeasure(z.lFt)} ft</small>`:''}</td><td>${originalMeasure(x.w,wu)}${wu!=='in'?`<small class="converted-measure">${fmtMeasure(z.wIn)} in</small>`:''}</td><td>${originalMeasure(x.t,tu)}${tu!=='in'?`<small class="converted-measure">${fmtMeasure(z.tIn)} in</small>`:''}</td><td>${esc(x.q||'-')}</td><td>${x.mode}</td><td><b>${z.val.toFixed(3)} ${z.mode}</b></td></tr>`}).join('')}</tbody></table></div>`:'';
      card.innerHTML=`<div class="estimate-top"><label class="item-name field">Item<input class="input est-name" value="${attr(item.name)}"></label><div class="estimate-sizes"><b>${r.tot.CFT.toFixed(3)} CFT</b> · <b>${r.tot.KOL.toFixed(3)} KOL</b></div></div>${measures}<div class="estimate-grid"><div class="subcard"><h3>Wood cost</h3>${field('Rate / CFT','cftRate',c.cftRate)}${field('Rate / KOL','kolRate',c.kolRate)}<div class="calc-line est-wood">Wood: <b>${money(r.wood)}</b></div></div><div class="subcard"><h3>Labour</h3><label class="field">Pricing method<select class="input labour-mode" data-key="labourMode"><option value="sqft" ${(c.labourMode||'sqft')==='sqft'?'selected':''}>Per sq.ft</option><option value="lumpsum" ${c.labourMode==='lumpsum'?'selected':''}>Lumpsum</option></select></label>${(c.labourMode||'sqft')==='sqft'?`<p class="muted">Enter finished door/window dimensions in any unit. The app converts both measurements to feet before calculating sq.ft.</p><div class="two-col">${measureField('Length','labourLength',c.labourLength,'labourLengthUnit',c.labourLengthUnit||'ft')}${measureField('Width','labourWidth',c.labourWidth,'labourWidthUnit',c.labourWidthUnit||'ft')}${field('Quantity','labourQty',c.labourQty)}${field('Rate / sq.ft','labourRate',c.labourRate)}</div><div class="calc-line est-labour">Area: <b>${r.labourArea.toFixed(2)} sq.ft</b> · Labour: <b>${money(r.labour)}</b></div>`:`${field('Labour lumpsum','labourLumpsum',c.labourLumpsum)}<div class="calc-line est-labour">Labour: <b>${money(r.labour)}</b></div>`}</div><div class="subcard"><h3>Polish</h3><label class="field">Pricing method<select class="input polish-mode" data-key="polishMode"><option value="sqft" ${c.polishMode==='sqft'?'selected':''}>Per sq.ft</option><option value="lumpsum" ${c.polishMode==='lumpsum'?'selected':''}>Lumpsum</option></select></label>${c.polishMode==='sqft'?`<p class="muted">Timber surface area from Calculator (all faces): <b>${r.surface.toFixed(2)} sq.ft</b>. Mixed input units are converted automatically.</p>${field('Override polish area (optional)','polishAreaOverride',c.polishAreaOverride)}${field('Polish rate / sq.ft','polishRate',c.polishRate)}`:field('Polish lumpsum','polishLumpsum',c.polishLumpsum)}<div class="calc-line est-polish">Polish: <b>${money(r.polish)}</b></div></div></div><div class="estimate-bottom">${field('Consumables','consumables',c.consumables)}${field('Other costs','other',c.other)}<label class="field selling-price-field">Estimated selling price <span class="selling-price-hint">Auto-filled from estimated cost. Edit this amount to add your margin/profit; this is the price sent to Quotation.</span><input class="input est-input selling-price-input" type="number" min="0" step="any" inputmode="decimal" data-key="sellingPrice" value="${attr(c.sellingPrice)}"></label></div><div class="metrics"><div class="metric est-cost"><span>Estimated cost</span><strong>${money(r.cost)}</strong></div><div class="metric est-profit"><span>Profit</span><strong>${money(r.profit)}</strong></div><div class="metric est-markup"><span>Markup</span><strong>${r.markup.toFixed(1)}%</strong></div><div class="metric est-margin"><span>Margin</span><strong>${r.margin.toFixed(1)}%</strong></div></div><div class="estimate-send-row"><span class="muted">When the estimated selling price is ready, send this item to Quotation. Quotation will receive the item description and this estimated selling price; you add Unit and Quantity there.</span><div class="send-quote-actions"><span class="send-quote-status" aria-live="polite"></span><button class="primary send-one-quote" data-i="${i}">Send this item to Quotation →</button></div></div>`;
      host.appendChild(card);
    });
    const g=estimateGrand(); $('#estimateGrand').textContent=`Estimated Selling Price ${money(g.selling)}`; $('#reviewEstimated').textContent=money(g.cost);
  }

  function refreshEstimationNumbers(i){
    const card=$(`.estimate-card[data-i="${i}"]`);if(!card)return;const r=costingResult(i),c=state.costings[i]||blankCosting();
    const wood=$('.est-wood',card);if(wood)wood.innerHTML=`Wood: <b>${money(r.wood)}</b>`;
    const labour=$('.est-labour',card);if(labour)labour.innerHTML=(c.labourMode||'sqft')==='lumpsum'?`Labour: <b>${money(r.labour)}</b>`:`Area: <b>${r.labourArea.toFixed(2)} sq.ft</b> · Labour: <b>${money(r.labour)}</b>`;
    const polish=$('.est-polish',card);if(polish)polish.innerHTML=`Polish: <b>${money(r.polish)}</b>`;
    const cost=$('.est-cost strong',card);if(cost)cost.textContent=money(r.cost);
    const profit=$('.est-profit strong',card);if(profit)profit.textContent=money(r.profit);
    const markup=$('.est-markup strong',card);if(markup)markup.textContent=`${r.markup.toFixed(1)}%`;
    const margin=$('.est-margin strong',card);if(margin)margin.textContent=`${r.margin.toFixed(1)}%`;
    const sellingInput=$('.selling-price-input',card);if(sellingInput&&c.sellingAuto===true&&sellingInput.value!==String(c.sellingPrice||''))sellingInput.value=c.sellingPrice||'';
    const g=estimateGrand();$('#estimateGrand').textContent=`Estimated Selling Price ${money(g.selling)}`;$('#reviewEstimated').textContent=money(g.cost);
  }

  async function resizeImage(file){
    const data=await new Promise((res,rej)=>{const fr=new FileReader();fr.onload=()=>res(fr.result);fr.onerror=rej;fr.readAsDataURL(file)});
    return await new Promise(resolve=>{const img=new Image();img.onload=()=>{const max=1200,scale=Math.min(1,max/Math.max(img.width,img.height));const c=document.createElement('canvas');c.width=Math.round(img.width*scale);c.height=Math.round(img.height*scale);c.getContext('2d').drawImage(img,0,0,c.width,c.height);resolve(c.toDataURL('image/jpeg',.82))};img.onerror=()=>resolve(data);img.src=data});
  }

  function renderQuotation(){
    $('#customerName').value=state.customer.name; $('#customerLocation').value=state.customer.location; $('#discountType').value=state.quote.discountType; $('#discountValue').value=state.quote.discountValue; $('#gstPercent').value=state.quote.gstPercent;
    const date=new Date().toLocaleDateString('en-IN',{day:'2-digit',month:'long',year:'numeric'}); $('#quoteDateScreen').textContent=`Date: ${date}`;
    const host=$('#quotationItems'); host.innerHTML='';
    state.quoteItems.forEach((it,i)=>{const box=document.createElement('div');box.className='quote-item';box.dataset.i=i;box.innerHTML=`<div class="quote-item-head"><strong>Item ${i+1}</strong><button class="danger remove-quote no-print">Remove</button></div><div class="quote-fields"><label class="field desc">Description<textarea class="input q-desc" rows="4" placeholder="Write the item description in your own words">${esc(it.description)}</textarea></label><label class="field">Units<input class="input q-unit" list="unitOptions" placeholder="Set / Piece / Lumpsum" value="${attr(it.unit)}"></label><label class="field">Quantity<input class="input q-qty" type="number" min="0" step="any" value="${attr(it.qty)}"></label><label class="field">Unit price<input class="input q-price" type="number" min="0" step="any" value="${attr(it.unitPrice)}"></label><div class="metric q-row-total"><span>Total</span><strong>${money(num(it.qty)*num(it.unitPrice))}</strong></div></div><div class="quote-image-actions no-print"><label class="secondary file-button">+ Add images<input class="q-images" type="file" accept="image/*" multiple></label> <small class="muted">Up to 6 images per item</small></div>${it.images.length?`<div class="image-grid">${it.images.map((src,j)=>`<div class="image-thumb"><img src="${src}" alt="Quotation item ${i+1} image ${j+1}"><button class="remove-image no-print" data-j="${j}">×</button></div>`).join('')}</div>`:''}`; host.appendChild(box)});
    renderNotes(); renderQuoteTotals(); renderPrintSheet();
  }
  function renderNotes(){const host=$('#notesList');host.innerHTML='';state.quote.notes.forEach((note,i)=>{const d=document.createElement('div');d.className='note-row';d.dataset.i=i;d.innerHTML=`<span class="note-num">${i+1}.</span><textarea class="input note-text" rows="2" placeholder="Add note">${esc(note)}</textarea><div class="note-actions no-print"><button class="secondary save-note">Save</button><button class="secondary remove-note">Remove</button></div>`;host.appendChild(d)});renderSavedNoteOptions()}
  function renderSavedNoteOptions(){const select=$('#savedNoteSelect');if(!select)return;const notes=savedNotes();select.innerHTML='<option value="">Select saved note</option>'+notes.map((x,i)=>`<option value="${i}">${esc(x)}</option>`).join('')}
  function renderQuoteTotals(){const t=quoteTotals();$('#quoteTotalsScreen').innerHTML=`<div class="total-line"><span>Total Price</span><strong>${money(t.subtotal)}</strong></div><div class="total-line"><span>Discount${state.quote.discountType==='percent'&&num(state.quote.discountValue)?` (${num(state.quote.discountValue)}%)`:''}</span><strong>- ${money(t.discount)}</strong></div><div class="total-line"><span>GST (${num(state.quote.gstPercent)}%)</span><strong>${money(t.gst)}</strong></div><div class="total-line grand"><span>Grand Total</span><strong>${money(t.grand)}</strong></div>`}
  function renderPrintSheet(){const t=quoteTotals(),date=new Date().toLocaleDateString('en-IN',{day:'2-digit',month:'long',year:'numeric'}),imgs=state.quoteItems.flatMap(x=>x.images).slice(0,4),notes=state.quote.notes.filter(x=>x.trim());$('#printSheet').innerHTML=`<div class="print-header"><img class="print-logo" src="/mwi-logo.png" alt="MWI"><div><div class="print-company-name">MORAS WOOD INDUSTRIES</div><div class="print-tagline">SAWMILL | CUSTOM FURNITURE | WOODWORKS</div></div><div class="print-company-details"><div>Moorukaveri, Kinnigoli, Mangaluru - 574150</div><div>+91 63616 95416 &nbsp; | &nbsp; info@moraswood.com</div><div>GSTIN: 29AFPPM1242L1Z1</div></div></div><div class="print-title-row"><div>QUOTATION</div><div class="print-date">Date: ${date}</div></div><div class="print-customer-bar"><div class="print-customer-label">CUSTOMER DETAILS</div><div><strong>${esc(state.customer.name||'-')}</strong><div>${esc(state.customer.location||'-')}</div></div></div>${imgs.length?`<div class="print-images ${imgs.length===1?'single':'multiple'}">${imgs.map((s,i)=>`<img src="${s}" alt="Quotation product ${i+1}">`).join('')}</div>`:''}<table class="print-table"><thead><tr><th>Sl. No.</th><th>Description</th><th>Units</th><th>Unit Price</th><th>Total Price</th></tr></thead><tbody>${state.quoteItems.map((x,i)=>`<tr><td class="print-sl">${i+1}</td><td class="print-desc">${esc(x.description||'-')}</td><td class="print-num">${esc([x.qty,x.unit].filter(Boolean).join(' ')||'-')}</td><td class="print-num">${rs(num(x.unitPrice))}</td><td class="print-num">${rs(num(x.qty)*num(x.unitPrice))}</td></tr>`).join('')}</tbody></table><div class="print-totals"><div><span>Total</span><strong>${rs(t.subtotal)}</strong></div>${t.discount>0?`<div><span>Discount${state.quote.discountType==='percent'&&num(state.quote.discountValue)?` (${num(state.quote.discountValue)}%)`:''}</span><strong>- ${rs(t.discount)}</strong></div>`:''}<div><span>GST (${num(state.quote.gstPercent)}%)</span><strong>${rs(t.gst)}</strong></div><div class="print-grand"><span>GRAND TOTAL</span><strong>${rs(t.grand)}</strong></div></div>${notes.length?`<div class="print-notes"><div class="print-notes-title">NOTES</div>${notes.map((x,i)=>`<div>${i+1}. ${esc(x)}</div>`).join('')}</div>`:''}<div class="print-footer"><strong>MORAS WOOD INDUSTRIES</strong><span>Digital copy - no signature required.</span></div>`}

  function renderReview(){ $('#actualMaterials').value=state.actual.materials; $('#actualLabourHours').value=state.actual.labourHours; $('#reviewEstimated').textContent=money(estimateGrand().cost) }
  function renderAll(){ $('#projectName').value=state.projectName; renderCalc(); renderEstimation(); renderQuotation(); renderReview(); showTab(state.tab||'calculator'); $('#continueProjectHome').classList.toggle('hidden',!hasProjectWork()); }

  document.addEventListener('click',async e=>{
    const b=e.target.closest('button'); if(!b)return;
    if(b.id==='goHome'){showView('home');return}
    if(b.id==='newProjectHome'){startNewProject();return}
    if(b.id==='continueProjectHome'){showView('workspace');showTab(state.tab||'calculator');return}
    if(b.id==='savedProjectsHome'){renderSavedProjects();$('#savedProjectsPanel').scrollIntoView({behavior:'smooth',block:'start'});return}
    if(b.id==='notesHome'){showView('notes');return}
    if(b.id==='createSavedNote'){
      const text=$('#newSavedNote').value.trim(),status=$('#notesManagerStatus');
      if(!text){status.textContent='Enter a note before saving.';return}
      const list=savedNotes();
      if(list.some(note=>note.toLowerCase()===text.toLowerCase())){status.textContent='That note is already saved.';return}
      try{list.push(text);writeSavedNotes(list);$('#newSavedNote').value='';status.textContent='Note saved.';renderNotesManager();renderSavedNoteOptions()}
      catch(error){status.textContent=error?.message||'Could not save the note.'}
      return;
    }
    if(b.classList.contains('update-saved-note')){
      const row=b.closest('.saved-note-row'),index=Number(row.dataset.i),text=$('.saved-note-edit',row).value.trim(),list=savedNotes();
      if(!text){$('#notesManagerStatus').textContent='A saved note cannot be empty.';return}
      list[index]=text;writeSavedNotes(list);renderNotesManager();renderSavedNoteOptions();$('#notesManagerStatus').textContent='Note updated.';return;
    }
    if(b.classList.contains('delete-saved-note')){
      const index=Number(b.closest('.saved-note-row').dataset.i),list=savedNotes();list.splice(index,1);writeSavedNotes(list);renderNotesManager();renderSavedNoteOptions();$('#notesManagerStatus').textContent='Note deleted.';return;
    }
    if(b.classList.contains('tab')){showView('workspace');return showTab(b.dataset.tab)}
    if(b.id==='newCalcItem'){state.items.push(blankItem());renderCalc();saveAutosave()}
    if(b.classList.contains('add-row')){state.items[+b.dataset.ii].rows.push(blankRow());renderCalc();saveAutosave()}
    if(b.id==='sendToEstimation'){
      const x=state.items.map((it,i)=>({name:it.name.trim()||`Item ${i+1}`,rows:it.rows.filter(r=>r.l||r.w||r.t||r.q).map(r=>({...r}))})).filter(it=>it.rows.length);
      state.estimationItems=x; state.costings=x.map(()=>blankCosting()); renderEstimation(); showTab('estimation'); saveAutosave();
    }
    if(b.id==='newEstimateItem'){state.estimationItems.push({name:'',rows:[]});state.costings.push(blankCosting());renderEstimation();saveAutosave()}
    if(b.id==='sendToQuotation'){
      // Sync visible selling-price inputs first so mobile keyboard state cannot lag behind.
      $$('.estimate-card').forEach(card=>{const i=+card.dataset.i,input=$('.selling-price-input',card);if(input&&state.costings[i])state.costings[i].sellingPrice=input.value});
      const missing=state.estimationItems.findIndex((_,i)=>num(state.costings[i]?.sellingPrice)<=0);
      if(missing>=0){const card=$(`.estimate-card[data-i="${missing}"]`),input=$('.selling-price-input',card),status=$('.send-quote-status',card);if(status)status.textContent='Enter the estimated selling price before sending to Quotation.';input?.classList.add('input-error');input?.focus();card?.scrollIntoView({behavior:'smooth',block:'center'});return}
      state.quoteItems=state.estimationItems.map((it,i)=>({description:it.name,unit:'',qty:'',unitPrice:String(state.costings[i].sellingPrice),images:[],aiOriginal:''}));if(!state.quoteItems.length)state.quoteItems=[blankQuote()];renderQuotation();showTab('quotation');saveAutosave()
    }
    if(b.classList.contains('send-one-quote')){
      const i=+b.dataset.i,it=state.estimationItems[i],card=b.closest('.estimate-card'),input=$('.selling-price-input',card);if(!it)return;
      if(input&&state.costings[i])state.costings[i].sellingPrice=input.value;
      const price=state.costings[i]?.sellingPrice||'';
      if(num(price)<=0){const status=$('.send-quote-status',card);if(status)status.textContent='Enter the estimated selling price before sending to Quotation.';input?.classList.add('input-error');input?.focus();return}
      input?.classList.remove('input-error');const q={description:it.name,unit:'',qty:'',unitPrice:String(price),images:[],aiOriginal:''};const onlyBlank=state.quoteItems.length===1&&!state.quoteItems[0].description&&!state.quoteItems[0].unit&&!state.quoteItems[0].qty&&!state.quoteItems[0].unitPrice&&!state.quoteItems[0].images.length;if(onlyBlank)state.quoteItems=[q];else state.quoteItems.push(q);renderQuotation();showTab('quotation');saveAutosave()
    }
    if(b.id==='floatingConverterButton'){setFloatingConverter(true)}
    if(b.id==='closeFloatingConverter'){setFloatingConverter(false)}
    if(b.id==='floatingConverterPanel' && e.target===b){setFloatingConverter(false)}
    if(b.id==='swapLengthUnits'){
      const from=$('#lengthFrom'),to=$('#lengthTo'),unit=from.value;from.value=to.value;to.value=unit;updateLengthConversion();
    }
    if(b.id==='addQuoteItem'){state.quoteItems.push(blankQuote());renderQuotation();saveAutosave()}
    if(b.classList.contains('remove-quote')){const i=+b.closest('.quote-item').dataset.i;state.quoteItems=state.quoteItems.length===1?[blankQuote()]:state.quoteItems.filter((_,j)=>j!==i);renderQuotation();saveAutosave()}
    if(b.classList.contains('remove-image')){const box=b.closest('.quote-item'),i=+box.dataset.i,j=+b.dataset.j;state.quoteItems[i].images.splice(j,1);renderQuotation();saveAutosave()}
    if(b.id==='addNote'){state.quote.notes.push('');renderNotes();renderPrintSheet();saveAutosave()}
    if(b.classList.contains('remove-note')){const i=+b.closest('.note-row').dataset.i;state.quote.notes=state.quote.notes.length===1?['']:state.quote.notes.filter((_,j)=>j!==i);renderNotes();renderPrintSheet();saveAutosave()}
    if(b.classList.contains('save-note')){const i=+b.closest('.note-row').dataset.i,note=(state.quote.notes[i]||'').trim();if(note){const list=savedNotes();if(!list.some(x=>x.toLowerCase()===note.toLowerCase())){list.push(note);writeSavedNotes(list)}renderSavedNoteOptions();const st=$('#noteStatus');if(st)st.textContent='Note saved for future quotations.'}}
    if(b.id==='addSavedNote'){const sel=$('#savedNoteSelect');if(sel.value==='')return;const idx=Number(sel.value),list=savedNotes();if(Number.isInteger(idx)&&idx>=0&&list[idx]){if(state.quote.notes.length===1&&!state.quote.notes[0].trim())state.quote.notes[0]=list[idx];else state.quote.notes.push(list[idx]);renderNotes();renderPrintSheet();saveAutosave()}}
    if(b.id==='deleteSavedNote'){const sel=$('#savedNoteSelect');if(sel.value==='')return;const idx=Number(sel.value),list=savedNotes();if(Number.isInteger(idx)&&idx>=0&&list[idx]){list.splice(idx,1);writeSavedNotes(list);renderSavedNoteOptions();const st=$('#noteStatus');if(st)st.textContent='Saved note deleted.'}}
    if(b.id==='printQuotation'){renderPrintSheet();window.print()}
    if(b.id==='saveProject'){saveProject()}
    if(b.id==='showProjects'){showView('home');renderSavedProjects();$('#savedProjectsPanel').scrollIntoView({behavior:'smooth',block:'start'})}
  });

  document.addEventListener('input',e=>{
    const t=e.target;
    if(t.id==='projectName'){state.projectName=t.value;saveAutosave()}
    if(t.classList.contains('item-name')){state.items[+t.dataset.ii].name=t.value;saveAutosave()}
    if(t.classList.contains('calc-cell')){const {ii,ri,key}=t.dataset;state.items[+ii].rows[+ri][key]=t.value;refreshCalcNumbers(+ii,+ri);saveAutosave()}
    const card=t.closest('.estimate-card'); if(card){const i=+card.dataset.i;if(t.classList.contains('est-name'))state.estimationItems[i].name=t.value;if(t.classList.contains('est-input')){state.costings[i][t.dataset.key]=t.value;if(t.dataset.key==='sellingPrice'){state.costings[i].sellingAuto=false;t.classList.remove('input-error');const status=$('.send-quote-status',card);if(status)status.textContent=''}refreshEstimationNumbers(i)}saveAutosave()}
    const qi=t.closest('.quote-item'); if(qi){const i=+qi.dataset.i;if(t.classList.contains('q-desc'))state.quoteItems[i].description=t.value;if(t.classList.contains('q-unit'))state.quoteItems[i].unit=t.value;if(t.classList.contains('q-qty'))state.quoteItems[i].qty=t.value;if(t.classList.contains('q-price'))state.quoteItems[i].unitPrice=t.value;const rowTotal=$('.q-row-total strong',qi);if(rowTotal)rowTotal.textContent=money(num(state.quoteItems[i].qty)*num(state.quoteItems[i].unitPrice));renderQuoteTotals();renderPrintSheet();saveAutosave()}
    if(t.classList.contains('note-text')){state.quote.notes[+t.closest('.note-row').dataset.i]=t.value;renderPrintSheet();saveAutosave()}
    if(t.id==='customerName'){state.customer.name=t.value;renderPrintSheet();saveAutosave()} if(t.id==='customerLocation'){state.customer.location=t.value;renderPrintSheet();saveAutosave()}
    if(t.id==='discountValue'){state.quote.discountValue=t.value;renderQuoteTotals();renderPrintSheet();saveAutosave()} if(t.id==='gstPercent'){state.quote.gstPercent=t.value;renderQuoteTotals();renderPrintSheet();saveAutosave()}
    if(t.id==='actualMaterials'){state.actual.materials=t.value;saveAutosave()} if(t.id==='actualLabourHours'){state.actual.labourHours=t.value;saveAutosave()}
    if(t.id==='lengthValue'){updateLengthConversion()}
  });

  document.addEventListener('change',async e=>{
    const t=e.target;
    if(t.id==='lengthFrom'||t.id==='lengthTo'){updateLengthConversion()}
    if(t.classList.contains('row-mode')){state.items[+t.dataset.ii].rows[+t.dataset.ri].mode=t.value;refreshCalcNumbers(+t.dataset.ii,+t.dataset.ri);saveAutosave()}
    if(t.classList.contains('measure-unit')){const ii=+t.dataset.ii,ri=+t.dataset.ri;state.items[ii].rows[ri][t.dataset.unitKey]=t.value;refreshCalcNumbers(ii,ri);saveAutosave()}
    const ec=t.closest('.estimate-card'); if(ec&&t.classList.contains('est-unit')){state.costings[+ec.dataset.i][t.dataset.unitKey]=t.value;refreshEstimationNumbers(+ec.dataset.i);saveAutosave()}
    if(ec&&t.classList.contains('labour-mode')){state.costings[+ec.dataset.i].labourMode=t.value;renderEstimation();saveAutosave()}
    if(ec&&t.classList.contains('polish-mode')){state.costings[+ec.dataset.i].polishMode=t.value;renderEstimation();saveAutosave()}
    if(t.id==='discountType'){state.quote.discountType=t.value;renderQuoteTotals();renderPrintSheet();saveAutosave()}
    if(t.classList.contains('q-images')){const i=+t.closest('.quote-item').dataset.i,files=[...t.files].slice(0,6);for(const f of files){if(state.quoteItems[i].images.length>=6)break;state.quoteItems[i].images.push(await resizeImage(f))}renderQuotation();saveAutosave()}
  });

  document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('#floatingConverterPanel').classList.contains('hidden'))setFloatingConverter(false);

    const t=e.target;if(!(t instanceof HTMLInputElement)||!t.classList.contains('calc-cell')||e.key!=='Enter')return;e.preventDefault();const ii=+t.dataset.ii,ri=+t.dataset.ri,key=t.dataset.key;state.items[ii].rows[ri][key]=t.value;const seq=['l','w','t','q'],idx=seq.indexOf(key);if(idx<3){const n=$(`.calc-cell[data-ii="${ii}"][data-ri="${ri}"][data-key="${seq[idx+1]}"]`);n?.focus();n?.select()}else{state.items[ii].rows.push(blankRow());renderCalc();setTimeout(()=>{$(`.calc-cell[data-ii="${ii}"][data-ri="${ri+1}"][data-key="l"]`)?.focus()},0);saveAutosave()}
  });

  function saveProject(){const name=(state.projectName||state.customer.name||'').trim();const status=$('#projectStatus');if(!name){status.textContent='Enter a project name before saving.';return}state.projectName=name;const list=savedList(),now=new Date().toISOString(),existing=list.findIndex(x=>x.name.toLowerCase()===name.toLowerCase());let snapshot;try{snapshot=JSON.parse(JSON.stringify(state))}catch{status.textContent='Could not prepare project for saving.';return}const rec={id:existing>=0?list[existing].id:uid(),name,updatedAt:now,snapshot};if(existing>=0)list[existing]=rec;else list.unshift(rec);try{writeSaved(list);const check=savedList().find(x=>x.id===rec.id);if(!check||!check.snapshot)throw new Error('Save verification failed');status.textContent='Project saved successfully on this device.';saveAutosave()}catch{status.textContent='Could not save project. Try fewer/lower-resolution images.'}}
  function renderSavedProjects(){const panel=$('#savedProjectsPanel'),list=savedList();panel.classList.remove('hidden');panel.innerHTML=`<div class="saved-card"><div style="display:flex;justify-content:space-between;gap:12px;align-items:center"><strong>Saved Projects</strong><button id="closeSaved" class="secondary">Close</button></div>${list.length?list.map(x=>`<div class="saved-row" data-id="${x.id}"><div><b>${esc(x.name)}</b><small>${new Date(x.updatedAt).toLocaleString('en-IN')}</small></div><div class="saved-actions"><button class="secondary load-project">Load</button><button class="danger delete-project">Delete</button></div></div>`).join(''):'<p class="muted">No saved projects yet.</p>'}</div>`;$('#closeSaved').onclick=()=>panel.classList.add('hidden');$$('.load-project',panel).forEach(b=>b.onclick=()=>{const id=b.closest('.saved-row').dataset.id,x=savedList().find(p=>p.id===id);if(x){state=normalizeUnits({...defaultState(),...x.snapshot});renderAll();panel.classList.add('hidden');showView('workspace');$('#projectStatus').textContent='Project loaded.'}});$$('.delete-project',panel).forEach(b=>b.onclick=()=>{const id=b.closest('.saved-row').dataset.id;writeSaved(savedList().filter(p=>p.id!==id));renderSavedProjects();const status=$('#projectStatus');if(status)status.textContent='Saved project deleted.'})}

  loadAutosave(); renderAll(); renderLengthConverter(); showView('home'); if('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(()=>{});
})();
