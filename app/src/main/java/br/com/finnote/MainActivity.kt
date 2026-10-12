package br.com.finnote

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Green=Color(0xFF075B46); private val Mint=Color(0xFFE3F4EA); private val Ink=Color(0xFF17201D); private val Muted=Color(0xFF75817B); private val Red=Color(0xFFC64C4C); private val Bg=Color(0xFFF7F9F7)
data class Entry(val id:String=UUID.randomUUID().toString(),val title:String,val category:String,val cents:Long,val income:Boolean,val author:String,val time:Long=System.currentTimeMillis())
private class Store(a:MainActivity){
 private val p=a.getSharedPreferences("finnote",android.content.Context.MODE_PRIVATE)
 var entries by mutableStateOf(load()); private set
 var monthly by mutableLongStateOf(p.getLong("monthly",0)); private set
 var name by mutableStateOf(p.getString("name","")?:""); private set
 private fun load():List<Entry> = runCatching { val a=JSONArray(p.getString("entries","[]")); (0 until a.length()).map{a.getJSONObject(it).let{j->Entry(j.getString("id"),j.getString("title"),j.getString("category"),j.getLong("cents"),j.getBoolean("income"),j.getString("author"),j.getLong("time"))}}.sortedByDescending{it.time} }.getOrDefault(emptyList())
 fun profile(v:String){name=v.trim().ifBlank{"Pessoa"};p.edit().putString("name",name).apply()}
 fun setMonthly(v:Long){monthly=v.coerceAtLeast(0);p.edit().putLong("monthly",monthly).apply()}
 fun add(e:Entry){entries=(listOf(e)+entries).sortedByDescending{it.time};val a=JSONArray();entries.forEach{a.put(JSONObject().apply{put("id",it.id);put("title",it.title);put("category",it.category);put("cents",it.cents);put("income",it.income);put("author",it.author);put("time",it.time)})};p.edit().putString("entries",a.toString()).apply()}
}
class MainActivity:ComponentActivity(){
 private lateinit var store:Store
 private var shortcut by mutableStateOf(false)
 override fun onCreate(b:Bundle?){super.onCreate(b);store=Store(this);shortcut=intent?.action=="br.com.finnote.ACTION_VOICE_ENTRY";setContent{MaterialTheme(colorScheme=lightColorScheme(primary=Green,background=Bg)){App(store,::speak);LaunchedEffect(shortcut){if(shortcut){kotlinx.coroutines.delay(450);shortcut=false;speak()}}}}}
 override fun onNewIntent(i:Intent){super.onNewIntent(i);setIntent(i);shortcut=i.action=="br.com.finnote.ACTION_VOICE_ENTRY"}
 private fun speak(){val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");putExtra(RecognizerIntent.EXTRA_PROMPT,"Diga o que comprou e o valor")};runCatching{startActivityForResult(i,71)}.onFailure{android.widget.Toast.makeText(this,"Reconhecimento de voz indisponível",1).show()}}
 @Deprecated("Android speech result callback") override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==71&&c==RESULT_OK)pending=d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()}
 companion object{var pending by mutableStateOf<String?>(null)}
}
@Composable private fun App(s:Store,voice:()->Unit){
 var tab by remember{mutableStateOf("Início")};var profile by remember{mutableStateOf(s.name.isBlank())};var settings by remember{mutableStateOf(false)};var form by remember{mutableStateOf(false)};var isIncome by remember{mutableStateOf(false)};var title by remember{mutableStateOf("")};var value by remember{mutableStateOf("")};var cat by remember{mutableStateOf("Alimentação")};var review by remember{mutableStateOf<String?>(null)}
 val spoken=MainActivity.pending;LaunchedEffect(spoken){spoken?.let{title=it;cat=category(it);value=amount(it)?.let{"%.2f".format(Locale.US,it/100.0)}?:"";isIncome=false;review=it;MainActivity.pending=null}}
 val extras=s.entries.filter{it.income}.sumOf{it.cents};val out=s.entries.filter{!it.income}.sumOf{it.cents};val ins=s.monthly+extras;val fmt=remember{NumberFormat.getCurrencyInstance(Locale("pt","BR"))};val month=remember{SimpleDateFormat("MMMM, yyyy",Locale("pt","BR")).format(Date()).replaceFirstChar{it.uppercase()}}
 Scaffold(containerColor=Bg,bottomBar={NavigationBar(containerColor=Color.White){listOf("Início","Lançamentos","Categorias","Ajustes").forEach{NavigationBarItem(selected=tab==it,onClick={tab=it;if(it=="Ajustes")settings=true},icon={Text(when(it){"Início"->"⌂";"Lançamentos"->"≡";"Categorias"->"◈";else->"⚙"})},label={Text(it,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Green,indicatorColor=Mint,selectedTextColor=Green))}}}){pad->
 LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=20.dp),contentPadding=PaddingValues(top=16.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(17.dp)){
 item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("FinNote",fontSize=27.sp,fontWeight=FontWeight.Bold,color=Green);Text("Seu caderno financeiro",fontSize=13.sp,color=Muted)};Box(Modifier.size(42.dp).background(Mint,CircleShape).clickable{profile=true},contentAlignment=Alignment.Center){Text(s.name.take(1).uppercase().ifBlank{"?"},color=Green,fontWeight=FontWeight.Bold)}}}
 item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(month,fontSize=19.sp,fontWeight=FontWeight.SemiBold,color=Ink);Text("▦  Este mês",fontSize=13.sp,color=Green)}}
 item{Column(Modifier.fillMaxWidth().background(Green,RoundedCornerShape(22.dp)).padding(22.dp)){Text("Saldo do mês",color=Color.White.copy(alpha=.8f),fontSize=14.sp);Text(fmt.format((ins-out)/100.0),color=Color.White,fontSize=31.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(16.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Mini("Rendimentos",fmt.format(ins/100.0),true);Mini("Gastos",fmt.format(out/100.0),false)}}}
 item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Action("＋","Renda extra",Modifier.weight(1f)){isIncome=true;title="";value="";cat="Renda extra";form=true};Action("＋","Lançar gasto",Modifier.weight(1f)){isIncome=false;title="";value="";cat="Alimentação";form=true}}}
 item{Row(verticalAlignment=Alignment.CenterVertically){Text("Lançamentos recentes",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink,modifier=Modifier.weight(1f));Text("Ver todos",color=Green,fontSize=13.sp,modifier=Modifier.clickable{tab="Lançamentos"})}}
 if(s.entries.isEmpty())item{Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("Seus lançamentos aparecem aqui",color=Muted);Text("Use o microfone para registrar por voz",color=Muted,fontSize=12.sp)}} else items(s.entries.take(if(tab=="Lançamentos")50 else 8),key={it.id}){RowItem(it,fmt)}
 }}
 Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter){FloatingActionButton(onClick=voice,modifier=Modifier.padding(bottom=74.dp),containerColor=Green,contentColor=Color.White,shape=CircleShape){Text("🎙",fontSize=21.sp)}}
 if(profile)Profile(s.name,{profile=false},{s.profile(it);profile=false})
 if(settings)Settings(s.monthly,s.name,{settings=false},{n,v->s.profile(n);s.setMonthly(v);settings=false})
 if(form)EntryForm(isIncome,title,value,cat,{title=it},{value=it},{cat=it},{form=false;review=null}){val cents=money(value);if(title.isNotBlank()&&cents>0){s.add(Entry(title=title.trim(),category=cat,cents=cents,income=isIncome,author=s.name));title="";value="";form=false;review=null}}
 if(review!=null&&!form)AlertDialog(onDismissRequest={review=null},title={Text("Revisar transcrição")},text={Text("“$review”\nConfira descrição, categoria e valor.")},confirmButton={TextButton(onClick={review=null;form=true}){Text("Revisar lançamento")}},dismissButton={TextButton(onClick={review=null}){Text("Descartar")}})
}
@Composable private fun Mini(l:String,v:String,pos:Boolean){Column{Text(l,color=Color.White.copy(alpha=.76f),fontSize=12.sp);Text(v,color=if(pos)Color(0xFFA7E7C5)else Color(0xFFFFB7B7),fontSize=15.sp,fontWeight=FontWeight.SemiBold)}}
@Composable private fun Action(icon:String,label:String,mod:Modifier,click:()->Unit){Row(mod.background(Color.White,RoundedCornerShape(14.dp)).clickable(onClick=click).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Text(icon,color=Green,fontSize=20.sp);Spacer(Modifier.width(6.dp));Text(label,color=Ink,fontSize=13.sp)}}
@Composable private fun RowItem(e:Entry,f:NumberFormat){val icon=when(e.category){"Alimentação"->"🛒";"Moradia"->"⌂";"Contas"->"▤";"Roupas"->"◇";else->"•"};Row(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(14.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(if(e.income)Mint else Color(0xFFFFEEEE),CircleShape),contentAlignment=Alignment.Center){Text(icon)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(e.title,color=Ink,fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=1);Text("${e.category} · ${e.author}",color=Muted,fontSize=11.sp,maxLines=1)};Column(horizontalAlignment=Alignment.End){Text((if(e.income)"+ "else"− ")+f.format(e.cents/100.0),color=if(e.income)Green else Red,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text(SimpleDateFormat("dd MMM",Locale("pt","BR")).format(Date(e.time)),fontSize=10.sp,color=Muted)}}}
@Composable private fun Profile(n:String,close:()->Unit,save:(String)->Unit){var name by remember{mutableStateOf(n)};AlertDialog(onDismissRequest=close,title={Text("Quem está usando?")},text={Column{Text("Seu nome identifica quem fez cada lançamento.");OutlinedTextField(name,{name=it},label={Text("Nome")},singleLine=true);Text("Perfil salvo neste aparelho.",fontSize=12.sp,color=Muted)}},confirmButton={TextButton(onClick={save(name)}){Text("Salvar")}},dismissButton={if(n.isNotBlank())TextButton(onClick=close){Text("Cancelar")}})}
@Composable private fun Settings(inc:Long,n:String,close:()->Unit,save:(String,Long)->Unit){var salary by remember{mutableStateOf(if(inc==0L)"" else "%.2f".format(Locale.US,inc/100.0))};var person by remember{mutableStateOf(n)};AlertDialog(onDismissRequest=close,title={Text("Configurações")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Rendimento mensal");OutlinedTextField(salary,{salary=it},label={Text("Renda fixa (R$)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);Text("Identificação local");OutlinedTextField(person,{person=it},label={Text("Seu nome")},singleLine=true);Text("Dados locais: login e sincronização entre aparelhos não estão configurados.",fontSize=12.sp,color=Muted)}},confirmButton={TextButton(onClick={save(person,money(salary))}){Text("Salvar")}},dismissButton={TextButton(onClick=close){Text("Fechar")}})}
@Composable private fun EntryForm(inc:Boolean,title:String,value:String,cat:String,onTitle:(String)->Unit,onValue:(String)->Unit,onCat:(String)->Unit,close:()->Unit,save:()->Unit){val cats=if(inc)listOf("Salário","Renda extra","Reembolso","Outros")else listOf("Alimentação","Lanche","Moradia","Água","Luz","Contas","Roupas","Transporte","Saúde","Lazer","Outros");AlertDialog(onDismissRequest=close,title={Text(if(inc)"Adicionar renda extra" else "Novo gasto")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(title,onTitle,label={Text("Descrição")},singleLine=true);OutlinedTextField(value,onValue,label={Text("Valor (R$)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);Text("Categoria",color=Muted,fontSize=12.sp);cats.chunked(3).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){row.forEach{FilterChip(selected=cat==it,onClick={onCat(it)},label={Text(it,fontSize=10.sp)})}}}}},confirmButton={TextButton(onClick=save){Text("Salvar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
private fun category(s:String):String{val t=s.lowercase(Locale("pt","BR"));return when{listOf("aluguel","condomínio","condominio","moradia").any(t::contains)->"Moradia";listOf("água","agua","luz","energia","internet","gás","gas").any(t::contains)->"Contas";listOf("lanche","salgado","hambúrguer","hamburguer","ifood","delivery","café","cafe").any(t::contains)->"Lanche";listOf("mercado","comida","arroz","feijão","feijao","supermercado","fruta").any(t::contains)->"Alimentação";listOf("roupa","calça","calca","camisa","sapato").any(t::contains)->"Roupas";listOf("uber","ônibus","onibus","gasolina").any(t::contains)->"Transporte";listOf("remédio","remedio","farmácia","farmacia").any(t::contains)->"Saúde";else->"Outros"}}
private fun amount(s:String):Long?=Regex("""(?:r\$\s*)?(\d+(?:[.,]\d{1,2})?)""",RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.let(::money)?.takeIf{it>0}
private fun money(s:String):Long{val n=s.trim().replace("R$","",true).replace(" ","");if(n.isBlank())return 0;return runCatching{val d=when{n.contains(',')->n.replace(".","").replace(',','.');n.count{it=='.'}>1->n.replace(".","");else->n}.toBigDecimal();d.movePointRight(2).toLong()}.getOrDefault(0).coerceAtLeast(0)}
