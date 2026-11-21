const fs=require('fs');
// Regenerate: APP_JS=<V10.1 app.js> node v101-harness.js "$(cat cases.json)" > expected-v101.json  (evaluates app.js lines 5-54 unchanged)
const lines=fs.readFileSync(process.env.APP_JS,'utf8').split('\n');
const code=lines.slice(4,54).join('\n')+`
const cases=JSON.parse(process.argv[2]);
const out=cases.map(cs=>{state.estimationItems=cs.items;state.costings=cs.costings;const rs=cs.items.map((_,i)=>{const r=costingResult(i);return {wood:r.wood,grouped:r.groupedWood,ungrouped:r.ungroupedWood,labourArea:r.labourArea,labour:r.labour,polish:r.polish,polishArea:r.polishArea,surface:r.surface,cost:r.cost,selling:r.selling,profit:r.profit,markup:r.markup.toFixed(1),margin:r.margin.toFixed(1),sellingPrice:state.costings[i].sellingPrice,sellingAuto:state.costings[i].sellingAuto,money:money(r.cost)}});const g=estimateGrand();return {rs,grand:g,grandMoney:money(g.selling)}});
console.log(JSON.stringify(out,null,1));`;
new Function(code)();
