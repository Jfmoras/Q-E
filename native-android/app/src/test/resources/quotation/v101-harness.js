const fs=require('fs');
// Regenerate: APP_JS=<V10.1 app.js> node v101-harness.js "$(cat cases.json)" > expected-v101.json
// Evaluates app.js lines 5-73 (num … quoteTotals) and the option button handlers (lines 417-419) unchanged.
const lines=fs.readFileSync(process.env.APP_JS,'utf8').split('\n');
const handlers=lines.slice(416,419).join('\n');
const code=lines.slice(4,73).join('\n')+`
const renderQuotation=()=>{},saveAutosave=()=>{};
const alert=()=>{},confirm=()=>true;
const click=(id)=>{const b={id};${handlers}};
const snap=()=>({quoteOptions:JSON.parse(JSON.stringify(state.quoteOptions)),activeQuoteOption:state.activeQuoteOption,quoteItems:JSON.parse(JSON.stringify(state.quoteItems)),quote:{discountType:state.quote.discountType,discountValue:state.quote.discountValue,gstPercent:state.quote.gstPercent},
  totals:state.quoteOptions.map(o=>{const t=totalsForOption(o);return {...t,rs:[rs(t.subtotal),rs(t.discount),rs(t.gst),rs(t.grand)],gstLabel:String(num(o.gstPercent))}}),
  screen:(()=>{const t=quoteTotals();return {...t,money:[money(t.subtotal),money(t.discount),money(t.gst),money(t.grand)],discountLabel:'Discount'+(state.quote.discountType==='percent'&&num(state.quote.discountValue)?' ('+num(state.quote.discountValue)+'%)':'')}})()});
const cases=JSON.parse(process.argv[2]);
const out=cases.map(cs=>{
  state=defaultState();Object.assign(state,JSON.parse(JSON.stringify(cs.state||{})));
  ensureQuoteOptions();syncActiveOption();
  const steps=[snap()];
  for(const op of cs.ops||[]){
    const [k,a,b,c]=op;
    if(k==='click')click(a);
    else if(k==='activate')activateOption(a);
    else if(k==='name'){ensureQuoteOptions();state.quoteOptions[state.activeQuoteOption].name=a}
    else if(k==='field')state.quoteItems[a][b]=c;
    else if(k==='addItem')state.quoteItems.push(blankQuote());
    else if(k==='quote')state.quote[a]=b;
    ensureQuoteOptions();syncActiveOption();
    steps.push(snap());
  }
  return steps;
});
console.log(JSON.stringify(out,null,1));`;
new Function(code)();
