package com.moraswood.qande

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

private data class QuoteItem(val description: String, val wood: String, val length: Double, val width: Double, val thickness: Double, val qty: Int, val rate: Double) {
    val unit get() = if (width <= 6) "CFT" else "KOL"
    val volume get() = TimberMath.volume(length,width,thickness,qty)
    val cost get() = volume * rate
}
private data class Quote(val id:String,val customer:String,val notes:String,val date:String,val items:List<QuoteItem>) {
    val total get() = items.sumOf { it.cost }
}
private fun loadQuotes(ctx:Context):List<Quote> = runCatching {
    val a=JSONArray(ctx.getSharedPreferences("native_quotes",0).getString("data","[]"))
    (0 until a.length()).map { i ->
        val q=a.getJSONObject(i);val items=q.getJSONArray("items")
        Quote(q.getString("id"),q.getString("customer"),q.optString("notes"),q.getString("date"),
            (0 until items.length()).map { j -> val x=items.getJSONObject(j)
                QuoteItem(x.getString("description"),x.optString("wood"),x.getDouble("length"),x.getDouble("width"),x.getDouble("thickness"),x.getInt("qty"),x.getDouble("rate"))
            })
    }
}.getOrDefault(emptyList())
private fun storeQuotes(ctx:Context,quotes:List<Quote>) {
    val a=JSONArray()
    quotes.forEach { q ->
        val items=JSONArray()
        q.items.forEach { x -> items.put(JSONObject().put("description",x.description).put("wood",x.wood).put("length",x.length).put("width",x.width).put("thickness",x.thickness).put("qty",x.qty).put("rate",x.rate)) }
        a.put(JSONObject().put("id",q.id).put("customer",q.customer).put("notes",q.notes).put("date",q.date).put("items",items))
    }
    ctx.getSharedPreferences("native_quotes",0).edit().putString("data",a.toString()).apply()
}
private fun exportQuote(ctx:Context,q:Quote):File {
    val pdf=PdfDocument()
    val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,1).create())
    val canvas=page.canvas
    val paint=Paint(3).apply { textSize=13f; color=android.graphics.Color.BLACK }
    var y=48f
    fun line(s:String) {
        if(y>800f) return
        canvas.drawText(s.take(85),40f,y,paint);y+=24f
    }
    paint.textSize=19f;line("MORAS WOOD INDUSTRIES");paint.textSize=12f
    line("Quotation: ${q.date}   Customer: ${q.customer}")
    line("---------------------------------------------------------------")
    q.items.forEachIndexed { index,x ->
        line("${index+1}. ${x.description} / ${x.wood}")
        line("   ${x.length} ft x ${x.width} in x ${x.thickness} in  Qty ${x.qty}")
        line("   %.3f %s @ Rs %.2f = Rs %.2f".format(x.volume,x.unit,x.rate,x.cost))
    }
    line("---------------------------------------------------------------")
    line("TOTAL: Rs %.2f".format(q.total))
    q.notes.split("\n").forEach { line(it) }
    pdf.finishPage(page)
    val folder=File(ctx.cacheDir,"shared").apply { mkdirs() }
    val file=File(folder,"MWI-quotation-${q.id}.pdf")
    file.outputStream().use { pdf.writeTo(it) };pdf.close()
    return file
}
@Composable
fun NativeQuotationScreen(ctx:Context,dao:MwiDao,mode:String,onProjectCreated:()->Unit) {
    val scope=rememberCoroutineScope()
    var saved by remember { mutableStateOf(loadQuotes(ctx)) }
    var customer by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("50% advance. Hardware excluded unless specified.") }
    var description by remember { mutableStateOf("") }
    var wood by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("") }
    var thickness by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var rate by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<QuoteItem>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    val share:(Quote)->Unit = { q ->
        try {
            val f=exportQuote(ctx,q)
            val uri=FileProvider.getUriForFile(ctx,"${ctx.packageName}.files",f)
            val intent=Intent(Intent.ACTION_SEND).apply { type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            ctx.startActivity(Intent.createChooser(intent,"Share quotation PDF"))
        } catch(e:Exception) { message="PDF error: ${e.message}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(mode,style=MaterialTheme.typography.headlineSmall)
        if(message.isNotBlank()) Text(message)
        if(mode=="Saved Quotations") {
            saved.forEach { q ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${q.customer} • ${q.date} • ₹%.2f".format(q.total))
                        Text("${q.items.size} items")
                        Row {
                            TextButton(onClick={share(q)}) { Text("Share PDF") }
                            TextButton(onClick={
                                scope.launch {
                                    dao.putProject(ProjectEntity(q.id,q.customer,q.customer,q.total,notes=q.notes))
                                    message="Project created: ${q.customer}";onProjectCreated()
                                }
                            }) { Text("Convert to Project") }
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(customer,{customer=it},label={Text("Customer / quotation name")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(description,{description=it},label={Text("Furniture / item description")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(wood,{wood=it},label={Text("Wood species")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(length,{length=it},label={Text("Length (feet)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(width,{width=it},label={Text("Width (inches)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(thickness,{thickness=it},label={Text("Thickness (inches)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(qty,{qty=it},label={Text("Quantity")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(rate,{rate=it},label={Text("Rate per CFT/KOL (₹)")},modifier=Modifier.fillMaxWidth())
            val valid=description.isNotBlank() && (length.toDoubleOrNull()?:0.0)>0 && (width.toDoubleOrNull()?:0.0)>0 && (thickness.toDoubleOrNull()?:0.0)>0 && (qty.toIntOrNull()?:0)>0 && (rate.toDoubleOrNull()?:-1.0)>=0
            Button(enabled=valid,onClick={
                items=items+QuoteItem(description.trim(),wood.trim(),length.toDouble(),width.toDouble(),thickness.toDouble(),qty.toInt(),rate.toDouble())
                description="";length="";width="";thickness="";qty="1";rate=""
            }) { Text("Add item") }
            items.forEachIndexed { index,x ->
                Text("${index+1}. ${x.description} (${x.wood}) • %.3f %s • ₹%.2f".format(x.volume,x.unit,x.cost))
                TextButton(onClick={items=items.filterIndexed { i,_ -> i!=index }}) { Text("Remove item") }
            }
            Text("Total: ₹%.2f".format(items.sumOf { it.cost }),style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(notes,{notes=it},label={Text("Quotation notes and terms")},modifier=Modifier.fillMaxWidth())
            Button(enabled=customer.isNotBlank() && items.isNotEmpty(),onClick={
                val q=Quote(UUID.randomUUID().toString(),customer.trim(),notes,LocalDate.now().toString(),items)
                saved=saved+q;storeQuotes(ctx,saved);message="Quotation saved"
                customer="";items=emptyList()
            }) { Text("Save quotation") }
            Text("Save a quotation, then open Saved Quotations to export PDF or convert to a project.")
        }
    }
}
