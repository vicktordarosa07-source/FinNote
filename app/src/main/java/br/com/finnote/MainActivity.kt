package br.com.finnote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Green = Color(0xFF075B46)
private val Mint = Color(0xFFE3F4EA)
private val Ink = Color(0xFF17201D)
private val Muted = Color(0xFF75817B)
private val Red = Color(0xFFC64C4C)
private val Bg = Color(0xFFF7F9F7)
private val Palette = listOf(Color(0xFF087F5B), Color(0xFF4C9AFF), Color(0xFFFFA94D), Color(0xFFB197FC), Color(0xFFFF6B6B), Color(0xFF20C997))

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private var startVoice by mutableStateOf(false)
    private var incomingInvite by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        startVoice = intent?.action == "br.com.finnote.ACTION_VOICE_ENTRY"
        incomingInvite = intent?.data?.toString()?.takeIf { it.startsWith("finnote://connect") }.orEmpty()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Bg)) {
                FinNoteApp(store, ::speak, incomingInvite) { incomingInvite = "" }
                LaunchedEffect(startVoice) { if (startVoice) { kotlinx.coroutines.delay(450); startVoice = false; speak() } }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.action == "br.com.finnote.ACTION_VOICE_ENTRY") startVoice = true
        incomingInvite = intent.data?.toString()?.takeIf { it.startsWith("finnote://connect") }.orEmpty()
    }
    private fun speak() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"); putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o que comprou e o valor")
        }
        runCatching { startActivityForResult(intent, 71) }.onFailure {
            android.widget.Toast.makeText(this, "Reconhecimento de voz indisponível", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    @Deprecated("Android speech result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 71 && resultCode == RESULT_OK) pendingSpeech = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    }
    companion object { var pendingSpeech by mutableStateOf<String?>(null) }
}

@Composable
private fun FinNoteApp(store: Store, voice: () -> Unit, incomingInvite: String, clearInvite: () -> Unit) {
    var tab by remember { mutableStateOf("Início") }
    var showEntry by remember { mutableStateOf(false) }
    var entryIsIncome by remember { mutableStateOf(false) }
    var entryTitleDraft by remember { mutableStateOf("") }
    var entryAmountDraft by remember { mutableStateOf("") }
    var entryCategoryDraft by remember { mutableStateOf("") }
    var showCategory by remember { mutableStateOf(false) }
    var subcategoryFor by remember { mutableStateOf<String?>(null) }
    var speechReview by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("Todos") }
    var status by remember { mutableStateOf("") }
    var inviteInput by remember { mutableStateOf("") }
    var invitePrompt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backend = remember(store) { SupabaseClient(store) }
    val fmt = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val spoken = MainActivity.pendingSpeech
    LaunchedEffect(spoken) { spoken?.let { speechReview = it; MainActivity.pendingSpeech = null } }
    LaunchedEffect(incomingInvite) { if (incomingInvite.isNotBlank()) { inviteInput = incomingInvite; invitePrompt = true } }
    val cleanInvite = incomingInvite.takeIf { it.isNotBlank() }?.let { Uri.parse(it).getQueryParameter("code") }
    LaunchedEffect(cleanInvite) { if (!cleanInvite.isNullOrBlank() && store.accessToken.isNotBlank()) {
        scope.launch { runCatching { backend.acceptInvite(cleanInvite) }.onSuccess { status = "Conexão aceita; lançamentos públicos sincronizados." }.onFailure { status = it.message ?: "Falha ao aceitar convite." }; clearInvite() }
    } }

    Scaffold(containerColor = Bg, bottomBar = {
        NavigationBar(containerColor = Color.White) {
            listOf("Início", "Lançamentos", "Categorias", "Ajustes").forEach { item ->
                NavigationBarItem(selected = tab == item, onClick = { tab = item },
                    icon = { Text(when (item) { "Início" -> "⌂"; "Lançamentos" -> "≡"; "Categorias" -> "◈"; else -> "⚙" }) },
                    label = { Text(item, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, indicatorColor = Mint, selectedTextColor = Green))
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(2.dp))
                Header(store)
                when (tab) {
                    "Início" -> Dashboard(store, fmt,
                        onIncome = { entryIsIncome = true; entryTitleDraft = ""; entryAmountDraft = ""; entryCategoryDraft = "Renda"; showEntry = true },
                        onExpense = { entryIsIncome = false; entryTitleDraft = ""; entryAmountDraft = ""; entryCategoryDraft = "Mercado"; showEntry = true },
                        showAll = { tab = "Lançamentos" })
                    "Lançamentos" -> {
                        Text("Lançamentos", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Todos", "Públicos", "Privados").forEach { FilterChip(filter == it, { filter = it }, label = { Text(it) }) } }
                        val visible = store.entries.filter { when (filter) { "Públicos" -> it.isPublic; "Privados" -> !it.isPublic; else -> true } }
                        if (visible.isEmpty()) Text("Nenhum lançamento neste filtro.", color = Muted)
                        visible.forEach { EntryRow(it, fmt) }
                    }
                    "Categorias" -> CategoriesPane(store, onAdd = { showCategory = true }, onSubcategory = { subcategoryFor = it })
                    else -> SettingsPane(store, backend, status, { status = it }, { shareText ->
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, "Enviar link de conexão"))
                    }, connect = { invitePrompt = true }, scope = scope)
                }
                Spacer(Modifier.height(78.dp))
            }
            if (tab != "Ajustes") FloatingActionButton(onClick = { showEntry = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 12.dp), containerColor = Green, contentColor = Color.White, shape = CircleShape) { Text("＋", fontSize = 26.sp) }
        }
    }

    if (showEntry) EntryDialog(store.categories, entryIsIncome, entryTitleDraft, entryAmountDraft, entryCategoryDraft, onClose = { showEntry = false }) { title, product, amount, category, subcategory, isIncome, isPublic ->
        val entry = Entry(id = UUID.randomUUID().toString(), title = title, category = category, subcategory = subcategory, product = product.ifBlank { title }, cents = money(amount), income = isIncome, author = store.name.ifBlank { "Pessoa" }, ownerId = "", isPublic = isPublic)
        store.add(entry); showEntry = false
        if (isPublic && store.accessToken.isNotBlank()) scope.launch {
            runCatching { backend.sync() }.onSuccess { status = "Lançamento público compartilhado." }.onFailure { status = "Salvo no aparelho; sincronização pendente: ${it.message}" }
        } else if (isPublic) status = "Salvo como público neste aparelho. Entre no Supabase para compartilhar com conexões."
    }
    if (showCategory) TextInputDialog("Nova categoria", "Nome da categoria", { showCategory = false }) { store.addCategory(it); showCategory = false }
    subcategoryFor?.let { cat -> TextInputDialog("Subcategoria: $cat", "Nome da subcategoria", { subcategoryFor = null }) { store.addSubcategory(cat, it); subcategoryFor = null } }
    speechReview?.let { text ->
        val inferred = categoryFor(text, store.categories)
        AlertDialog(onDismissRequest = { speechReview = null }, title = { Text("Revisar transcrição") }, text = { Text("“$text”\nCategoria sugerida: $inferred. Confira descrição e valor no formulário.") },
            confirmButton = { TextButton(onClick = { entryIsIncome = false; entryTitleDraft = text; entryAmountDraft = amountFromText(text)?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty(); entryCategoryDraft = inferred; speechReview = null; showEntry = true }) { Text("Revisar lançamento") } }, dismissButton = { TextButton(onClick = { speechReview = null }) { Text("Descartar") } })
    }
    if (invitePrompt) TextInputDialog("Conectar usuário", "Cole o link/código do convite", close = { invitePrompt = false }, initial = inviteInput) { raw ->
        invitePrompt = false
        scope.launch { runCatching { backend.acceptInvite(raw) }.onSuccess { status = "Conexão aceita; lançamentos públicos sincronizados." }.onFailure { status = it.message ?: "Não foi possível aceitar o convite." } }
    }
    if (status.isNotBlank()) AlertDialog(onDismissRequest = { status = "" }, title = { Text("FinNote") }, text = { Text(status) }, confirmButton = { TextButton(onClick = { status = "" }) { Text("OK") } })
    if (store.name.isBlank()) LaunchedEffect(Unit) { tab = "Ajustes" }
    LaunchedEffect(Unit) { if (store.accessToken.isNotBlank()) runCatching { backend.sync() }.onFailure { status = "Sincronização: ${it.message}" } }
}

@Composable private fun Header(store: Store) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("FinNote", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Green); Text("Seu caderno financeiro", fontSize = 13.sp, color = Muted) }
        Box(Modifier.size(42.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) { Text(store.name.take(1).uppercase().ifBlank { "?" }, color = Green, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun Dashboard(store: Store, fmt: NumberFormat, onIncome: () -> Unit, onExpense: () -> Unit, showAll: () -> Unit) {
    val now = Calendar.getInstance(); val currentKey = SimpleDateFormat("yyyyMM", Locale.ROOT)
    val monthRows = (5 downTo 0).map { delta -> (now.clone() as Calendar).apply { add(Calendar.MONTH, -delta) }.let { it.time to currentKey.format(it.time) } }
    val rows = monthRows.map { (date, key) ->
        val saved = store.entries.filter { currentKey.format(Date(it.time)) == key }
        val expense = saved.filterNot(Entry::income).sumOf(Entry::cents)
        val fixedIncome = if (key == currentKey.format(Date())) store.monthly else store.monthly
        val income = fixedIncome + saved.filter(Entry::income).sumOf(Entry::cents)
        Triple(date, income, expense)
    }
    val today = rows.last(); val month = SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR")).format(Date()).replaceFirstChar { it.uppercase() }
    Text(month, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    Surface(color = Green, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(21.dp)) {
            Text("Saldo do mês", color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
            Text(fmt.format((today.second - today.third) / 100.0), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Mini("Rendimentos", fmt.format(today.second / 100.0), true); Mini("Gastos", fmt.format(today.third / 100.0), false) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryAction("＋", "Renda extra", Modifier.weight(1f), onIncome)
        SummaryAction("＋", "Lançar gasto", Modifier.weight(1f), onExpense)
    }
    Text("Comparativo mensal", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { Legend("Ganhos", Green); Legend("Gastos", Red) }
            Spacer(Modifier.height(8.dp)); MonthlyChart(rows)
        }
    }
    val expenses = store.entries.filter { !it.income && it.time >= monthRows.first().first.time }
    val byCategory = expenses.groupBy { if (it.subcategory.isBlank()) it.category else "${it.category} · ${it.subcategory}" }.mapValues { (_, v) -> v.sumOf(Entry::cents) }.toList().sortedByDescending { it.second }.take(5)
    val byProduct = expenses.groupBy { it.product.ifBlank { it.title } }.mapValues { (_, v) -> v.sumOf(Entry::cents) }.toList().sortedByDescending { it.second }.take(5)
    Text("Gastos por categoria", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
    RankingChart(byCategory, fmt)
    Text("Gastos por produto", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
    RankingChart(byProduct, fmt)
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Lançamentos recentes", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f)); TextButton(onClick = showAll) { Text("Ver todos") } }
    store.entries.take(5).forEach { EntryRow(it, fmt) }
}

@Composable private fun MonthlyChart(rows: List<Triple<Date, Long, Long>>) {
    val max = (rows.maxOfOrNull { maxOf(it.second, it.third) } ?: 1L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(145.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
        rows.forEach { (date, income, expense) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                    Box(Modifier.width(11.dp).fillMaxHeight((income.toFloat() / max).coerceAtLeast(.035f)).background(Green, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                    Box(Modifier.width(11.dp).fillMaxHeight((expense.toFloat() / max).coerceAtLeast(.035f)).background(Red, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
                Spacer(Modifier.height(5.dp)); Text(SimpleDateFormat("MMM", Locale("pt", "BR")).format(date).take(3), fontSize = 10.sp, color = Muted)
            }
        }
    }
}

@Composable private fun RankingChart(rows: List<Pair<String, Long>>, fmt: NumberFormat) {
    if (rows.isEmpty()) { Text("Sem gastos neste período.", color = Muted, fontSize = 13.sp); return }
    val max = rows.maxOf { it.second }.coerceAtLeast(1L)
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEachIndexed { index, row ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row { Text(row.first, color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1); Text(fmt.format(row.second / 100.0), color = Muted, fontSize = 12.sp) }
                    Box(Modifier.fillMaxWidth().height(7.dp).background(Bg, RoundedCornerShape(8.dp))) { Box(Modifier.fillMaxWidth((row.second.toFloat() / max).coerceIn(.04f, 1f)).fillMaxHeight().background(Palette[index % Palette.size], RoundedCornerShape(8.dp))) }
                }
            }
        }
    }
}

@Composable private fun CategoriesPane(store: Store, onAdd: () -> Unit, onSubcategory: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Categorias", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f)); Button(onClick = onAdd) { Text("＋ Categoria") } }
    store.categories.forEach { category ->
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(category.name, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f)); TextButton(onClick = { onSubcategory(category.name) }) { Text("＋ Subcategoria") } }
                if (category.subcategories.isEmpty()) Text("Sem subcategorias", color = Muted, fontSize = 12.sp)
                else FlowChips(category.subcategories)
            }
        }
    }
}

@Composable private fun SettingsPane(store: Store, api: SupabaseClient, status: String, setStatus: (String) -> Unit, share: (String) -> Unit, connect: () -> Unit, scope: kotlinx.coroutines.CoroutineScope) {
    var name by remember(store.name) { mutableStateOf(store.name) }
    var phone by remember(store.phone) { mutableStateOf(store.phone) }
    var url by remember(store.backendUrl) { mutableStateOf(store.backendUrl) }
    var key by remember(store.anonKey) { mutableStateOf(store.anonKey) }
    var monthly by remember(store.monthly) { mutableStateOf(if (store.monthly == 0L) "" else "%.2f".format(Locale.US, store.monthly / 100.0)) }
    var otp by remember { mutableStateOf("") }
    var sentOtp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Text("Configurações", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
    SectionCard("Perfil e orçamento") {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Telefone com código do país") }, placeholder = { Text("+5511999999999") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(monthly, { monthly = it }, label = { Text("Rendimento mensal fixo (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { store.saveProfile(name, phone); store.updateMonthly(money(monthly)); setStatus("Perfil e rendimento salvos neste aparelho.") }) { Text("Salvar perfil") }
    }
    SectionCard("Compartilhamento") {
        Text(if (store.accessToken.isBlank()) "Entre com seu telefone para publicar lançamentos e conectar outros usuários." else "Conectado como ${store.name} · ${store.phone}", color = Muted, fontSize = 13.sp)
        OutlinedTextField(url, { url = it }, label = { Text("URL do projeto Supabase") }, placeholder = { Text("https://seu-projeto.supabase.co") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(key, { key = it }, label = { Text("Chave anon/public do Supabase") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { store.saveBackend(url, key); store.saveProfile(name, phone); setStatus("Configuração salva.") }, modifier = Modifier.fillMaxWidth()) { Text("Salvar configuração") }
        if (store.accessToken.isBlank()) {
            if (!sentOtp) OutlinedButton(enabled = !busy, onClick = {
                store.saveBackend(url, key); store.saveProfile(name, phone)
                busy = true; scope.launch { runCatching { api.sendOtp(phone) }.onSuccess { sentOtp = true; setStatus("Código SMS enviado para $phone.") }.onFailure { setStatus(it.message ?: "Falha ao enviar SMS.") }; busy = false }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Enviando…" else "Enviar código SMS") }
            else {
                OutlinedTextField(otp, { otp = it }, label = { Text("Código recebido por SMS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    busy = true; scope.launch { runCatching { api.verifyOtp(phone, otp, name) }.onSuccess { setStatus("Login confirmado. Lançamentos públicos sincronizados."); sentOtp = false }.onFailure { setStatus(it.message ?: "Código inválido.") }; busy = false }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Verificando…" else "Confirmar telefone") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { busy = true; scope.launch { runCatching { api.sync() }.onSuccess { setStatus("Sincronizados: $it lançamentos públicos.") }.onFailure { setStatus(it.message ?: "Falha na sincronização") }; busy = false } }, modifier = Modifier.weight(1f)) { Text("Sincronizar") }
                OutlinedButton(onClick = { scope.launch { runCatching { api.createInvite() }.onSuccess { share("Conecte-se ao meu FinNote pelo link: $it") }.onFailure { setStatus(it.message ?: "Falha ao criar convite") } } }, modifier = Modifier.weight(1f)) { Text("Criar link") }
            }
            OutlinedButton(onClick = connect, modifier = Modifier.fillMaxWidth()) { Text("Aceitar link de conexão") }
            TextButton(onClick = { store.signOut(); sentOtp = false; setStatus("Sessão encerrada neste aparelho.") }) { Text("Sair da conta") }
        }
        Text("A conexão usa SMS do Supabase. Configure o provedor de SMS, o SQL de tabelas e as políticas RLS antes de ativar compartilhamento.", color = Muted, fontSize = 12.sp)
    }
    if (status.isNotBlank()) Text(status, color = Green, fontSize = 13.sp)
}

@Composable private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(17.dp)) { Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = Ink); content() } }
}

@Composable private fun EntryDialog(categories: List<Category>, initialIncome: Boolean, initialTitle: String, initialAmount: String, initialCategory: String, onClose: () -> Unit, save: (String, String, String, String, String, Boolean, Boolean) -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }; var product by remember { mutableStateOf("") }; var amount by remember { mutableStateOf(initialAmount) }
    var category by remember { mutableStateOf(categories.firstOrNull { it.name.equals(initialCategory, true) }?.name ?: categories.firstOrNull()?.name ?: "Outros") }; var subcategory by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(initialIncome) }; var public by remember { mutableStateOf(false) }
    val subs = categories.firstOrNull { it.name == category }?.subcategories.orEmpty()
    AlertDialog(onDismissRequest = onClose, title = { Text(if (income) "Registrar renda" else "Novo gasto") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { FilterChip(!income, { income = false }, label = { Text("Gasto") }); Spacer(Modifier.width(6.dp)); FilterChip(income, { income = true }, label = { Text("Renda extra") }) }
            OutlinedTextField(title, { title = it }, label = { Text("Descrição") }, singleLine = true)
            OutlinedTextField(product, { product = it }, label = { Text("Produto (para o gráfico)") }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, label = { Text("Valor (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Text("Categoria", color = Muted, fontSize = 12.sp); FlowChips(categories.map(Category::name)) { category = it; subcategory = "" }
            if (subs.isNotEmpty()) { Text("Subcategoria", color = Muted, fontSize = 12.sp); FlowChips(subs, onClick = { subcategory = it }) }
            Text("Visibilidade", color = Muted, fontSize = 12.sp)
            Row { FilterChip(!public, { public = false }, label = { Text("Privado") }); Spacer(Modifier.width(6.dp)); FilterChip(public, { public = true }, label = { Text("Público para conexões") }) }
        } }, confirmButton = { TextButton(enabled = title.isNotBlank() && money(amount) > 0, onClick = { save(title.trim(), product.trim(), amount, category, subcategory, income, public) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

@Composable private fun FlowChips(labels: List<String>, onClick: (String) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { labels.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { row.forEach { label -> SuggestionChip(onClick = { onClick(label) }, label = { Text(label, fontSize = 11.sp) }) } } } }
}

@Composable private fun TextInputDialog(title: String, hint: String, close: () -> Unit, initial: String = "", save: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(hint) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { save(value) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = close) { Text("Cancelar") } })
}

@Composable private fun EntryRow(entry: Entry, format: NumberFormat) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(if (entry.income) Mint else Color(0xFFFFEEEE), CircleShape), contentAlignment = Alignment.Center) { Text(if (entry.income) "＋" else "−", color = if (entry.income) Green else Red) }
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
                Text(entry.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Text(listOfNotNull(entry.category, entry.subcategory.takeIf(String::isNotBlank), entry.author, if (entry.isPublic) "Público" else "Privado").joinToString(" · "), color = Muted, fontSize = 10.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) { Text((if (entry.income) "+ " else "− ") + format.format(entry.cents / 100.0), color = if (entry.income) Green else Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(SimpleDateFormat("dd MMM", Locale("pt", "BR")).format(Date(entry.time)), color = Muted, fontSize = 10.sp) }
        }
    }
}

@Composable private fun SummaryAction(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Text(icon, color = Green, fontSize = 20.sp); Spacer(Modifier.width(6.dp)); Text(label, color = Ink, fontSize = 12.sp) }
}
@Composable private fun Mini(label: String, value: String, positive: Boolean) { Column { Text(label, color = Color.White.copy(alpha = .76f), fontSize = 12.sp); Text(value, color = if (positive) Color(0xFFA7E7C5) else Color(0xFFFFB7B7), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) } }
@Composable private fun Legend(label: String, color: Color) { Row(verticalAlignment = Alignment.CenterVertically) { Canvas(Modifier.size(9.dp)) { drawCircle(color) }; Spacer(Modifier.width(5.dp)); Text(label, color = Muted, fontSize = 11.sp) } }

private fun categoryFor(raw: String, categories: List<Category>): String {
    val text = raw.lowercase(Locale("pt", "BR"))
    val rules = listOf("gastos fixos" to listOf("aluguel", "condomínio", "condominio", "água", "agua", "luz", "energia", "internet", "gás", "gas"),
        "Transporte" to listOf("uber", "ônibus", "onibus", "gasolina", "combustível", "combustivel"),
        "Mercado" to listOf("mercado", "arroz", "feijão", "feijao", "supermercado", "fruta", "comida"))
    val builtin = rules.firstOrNull { (_, terms) -> terms.any(text::contains) }?.first
    return builtin?.let { categories.firstOrNull { c -> c.name.equals(it, true) }?.name ?: it }
        ?: categories.firstOrNull { c -> (listOf(c.name) + c.subcategories).any { text.contains(it.lowercase(Locale("pt", "BR"))) } }?.name
        ?: "Outros"
}

private fun money(raw: String): Long {
    val value = raw.trim().replace("R$", "", true).replace(" ", ""); if (value.isBlank()) return 0
    return runCatching { val normalized = when { value.contains(',') -> value.replace(".", "").replace(',', '.'); value.count { it == '.' } > 1 -> value.replace(".", ""); else -> value }; normalized.toBigDecimal().movePointRight(2).toLong() }.getOrDefault(0).coerceAtLeast(0)
}

private fun amountFromText(raw: String): Long? = Regex("""(?:r\$\s*)?(\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.let(::money)?.takeIf { it > 0 }
