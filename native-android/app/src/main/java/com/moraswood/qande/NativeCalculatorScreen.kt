package com.moraswood.qande

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

private fun feet(value:Double,unit:String):Double = when(unit) {
    "in" -> value/12.0; "cm" -> value/30.48; "mm" -> value/304.8; "m" -> value/0.3048; else -> value
}
private fun inches(value:Double,unit:String):Double = feet(value,unit)*12.0
private data class CalcRow(val id:String,val l:Double,val lu:String,val w:Double,val wu:String,val t:Double,val tu:String,val qty:Int,val mode:String,val rate:Double,val group:String) {
    val volume:Double get() = if(mode=="KOL") TimberMath.kol(feet(l,lu),inches(w,wu),inches(t,tu),qty) else TimberMath.cft(feet(l,lu),inches(w,wu),inches(t,tu),qty)
    val cost:Double get() = volume*rate
}
private fun saved(ctx:Context):JSONArray = runCatching { JSONArray(ctx.getSharedPreferences("native_calculations",0).getString("records","[]")) }.getOrElse { JSONArray() }

@Composable
fun NativeCalculatorScreen(context:Context, showSaved:Boolean=false) {
    var records by remember { mutableStateOf(saved(context).toString()) }
    var rows by remember { mutableStateOf<List<CalcRow>>(emptyList()) }
    var l by remember { mutableStateOf("") };var w by remember { mutableStateOf("") };var t by remember { mutableStateOf("") }
    var lu by remember { mutableStateOf("ft") };var wu by remember { mutableStateOf("in") };var tu by remember { mutableStateOf("in") }
    var qty by remember { mutableStateOf("1") };var mode by remember { mutableStateOf("Auto") };var rate by remember { mutableStateOf("") };var group by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    val units=listOf("ft","in","cm","mm","m")
    @Composable fun unitSelector(label:String,current:String,change:(String)->Unit) {
        var expanded by remember { mutableStateOf(false) }
        Row {
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
            Box {
                TextButton(onClick={expanded=true}) { Text(current) }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                    units.forEach { u -> DropdownMenuItem(text={Text(u)},onClick={change(u);expanded=false}) }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(showSaved)"Saved Calculations" else "CFT / KOL Calculator",style=MaterialTheme.typography.headlineSmall)
        if(showSaved) {
            val array=remember(records) { JSONArray(records) }
            (0 until array.length()).forEach { index ->
                val entry=array.getJSONObject(index)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(entry.optString("title"))
                        Text(entry.optString("date"))
                        Text("CFT: %.3f • KOL: %.3f • ₹%.2f".format(entry.optDouble("cft"),entry.optDouble("kol"),entry.optDouble("total")))
                        TextButton(onClick={
                            val next=JSONArray()
                            (0 until array.length()).filter { it!=index }.forEach { next.put(array.getJSONObject(it)) }
                            records=next.toString()
                            context.getSharedPreferences("native_calculations",0).edit().putString("records",records).apply()
                        }) { Text("Delete") }
                    }
                }
            }
        } else {
            Text("Timber size", style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(l,{l=it},label={Text("Length")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                    unitSelector("Unit",lu,{lu=it})
                }
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(w,{w=it},label={Text("Width")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                    unitSelector("Unit",wu,{wu=it})
                }
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(t,{t=it},label={Text("Thick.")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                    unitSelector("Unit",tu,{tu=it})
                }
            }
            OutlinedTextField(qty,{qty=it},label={Text("Quantity")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
            Row {
                listOf("Auto","CFT","KOL").forEach { option -> TextButton(onClick={mode=option}) { Text(if(mode==option)"✓ $option" else option) } }
            }
            OutlinedTextField(rate,{rate=it},label={Text("Rate per CFT / KOL (₹)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp,max=64.dp))
            OutlinedTextField(group,{group=it},label={Text("Group / wood species (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp,max=64.dp))
            val valid=(l.toDoubleOrNull()?:0.0)>0 && (w.toDoubleOrNull()?:0.0)>0 && (t.toDoubleOrNull()?:0.0)>0 && (qty.toIntOrNull()?:0)>0 && (rate.toDoubleOrNull()?:0.0)>=0
            Button(enabled=valid,onClick={
                val actual=if(mode=="Auto") if(inches(w.toDouble(),wu)<=6.0)"CFT" else "KOL" else mode
                rows=rows+CalcRow(UUID.randomUUID().toString(),l.toDouble(),lu,w.toDouble(),wu,t.toDouble(),tu,qty.toInt(),actual,rate.toDoubleOrNull()?:0.0,group)
                l="";w="";t="";qty="1"
            }) { Text("Add timber row") }
            rows.groupBy { it.group.ifBlank { "Ungrouped" } }.forEach { (name,groupRows) ->
                Text(name,style=MaterialTheme.typography.titleMedium)
                groupRows.forEach { row ->
                    Text("${row.l} ${row.lu} × ${row.w} ${row.wu} × ${row.t} ${row.tu} × ${row.qty} = %.3f ${row.mode} • ₹%.2f".format(row.volume,row.cost))
                    TextButton(onClick={rows=rows.filterNot { it.id==row.id }}) { Text("Remove") }
                }
            }
            val cft=rows.filter { it.mode=="CFT" }.sumOf { it.volume }
            val kol=rows.filter { it.mode=="KOL" }.sumOf { it.volume }
            val total=rows.sumOf { it.cost }
            Text("Total: %.3f CFT | %.3f KOL".format(cft,kol),style=MaterialTheme.typography.titleLarge)
            Text("Value: ₹%.2f".format(total))
            OutlinedTextField(title,{title=it},label={Text("Calculation name")},singleLine=true,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp,max=64.dp))
            Button(enabled=rows.isNotEmpty() && title.isNotBlank(),onClick={
                val a=saved(context)
                a.put(JSONObject().put("title",title.trim()).put("date",LocalDate.now().toString()).put("cft",cft).put("kol",kol).put("total",total))
                records=a.toString()
                context.getSharedPreferences("native_calculations",0).edit().putString("records",records).apply()
                title="";rows=emptyList()
            }) { Text("Save calculation") }
        }
    }
}
