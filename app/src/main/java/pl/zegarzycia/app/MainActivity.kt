package pl.zegarzycia.app

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF0B1114)
private val Panel = Color(0xFF141F23)
private val Panel2 = Color(0xFF1B292E)
private val Mint = Color(0xFF69E6BD)
private val Muted = Color(0xFF9CB0B5)
private val White = Color(0xFFF3F8F7)
private val Orange = Color(0xFFFFC477)
private val defaults = listOf("Alkohol", "Papierosy", "E-papierosy", "Marihuana", "Amfetamina", "Kryształ", "Hazard", "Inny nałóg")
private val icons = mapOf("Alkohol" to "🍺", "Papierosy" to "🚬", "E-papierosy" to "💨", "Marihuana" to "🌿", "Amfetamina" to "⚡", "Kryształ" to "💎", "Hazard" to "🎰", "Inny nałóg" to "🎯")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LifeClockApp() }
    }
}

data class Habit(val name: String, val since: Long, val dailyCost: Double, val bestDays: Int = 0)
data class DiaryEntry(val day: String, val mood: Int, val craving: Int, val note: String)

@Composable
fun LifeClockApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zegar_zycia_data", 0) }
    var initialized by remember { mutableStateOf(prefs.getBoolean("initialized", false)) }
    var chosen by remember { mutableStateOf(loadStringList(prefs.getString("chosen", "[]") ?: "[]")) }
    var customNames by remember { mutableStateOf(loadStringList(prefs.getString("custom", "[]") ?: "[]")) }
    var starts by remember { mutableStateOf(loadLongMap(prefs.getString("starts", "{}") ?: "{}")) }
    var costs by remember { mutableStateOf(loadDoubleMap(prefs.getString("costs", "{}") ?: "{}")) }
    var goal by remember { mutableDoubleStateOf(prefs.getString("goal", "1000")?.toDoubleOrNull() ?: 1000.0) }
    var goalName by remember { mutableStateOf(prefs.getString("goalName", "Mój cel") ?: "Mój cel") }
    var page by remember { mutableIntStateOf(0) }
    var diary by remember { mutableStateOf(loadDiary(prefs.getString("diary", "[]") ?: "[]")) }
    var showNewCategory by remember { mutableStateOf(false) }
    var showCostFor by remember { mutableStateOf<String?>(null) }
    var showResetFor by remember { mutableStateOf<String?>(null) }
    var showDiary by remember { mutableStateOf(false) }
    var mood by remember { mutableIntStateOf(3) }
    var craving by remember { mutableIntStateOf(2) }
    var note by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var editGoal by remember { mutableStateOf(false) }
    var goalInput by remember { mutableStateOf(goal.toString()) }
    var goalNameInput by remember { mutableStateOf(goalName) }
    var showReflection by remember { mutableStateOf(false) }
    var reflectionGain by remember { mutableStateOf("") }
    var reflectionRisk by remember { mutableStateOf("") }

    fun persist() {
        prefs.edit().putBoolean("initialized", initialized)
            .putString("chosen", JSONArray(chosen).toString()).putString("custom", JSONArray(customNames).toString())
            .putString("starts", JSONObject(starts.mapValues { it.value }).toString())
            .putString("costs", JSONObject(costs.mapValues { it.value }).toString())
            .putString("goal", goal.toString()).putString("goalName", goalName)
            .putString("diary", JSONArray().also { a -> diary.forEach { a.put(JSONObject().put("day",it.day).put("mood",it.mood).put("craving",it.craving).put("note",it.note)) } }.toString()).apply()
    }
    LaunchedEffect(initialized, chosen, customNames, starts, costs, goal, goalName, diary) { persist() }
    val allCategories = (defaults + customNames).distinct()
    val now = System.currentTimeMillis()
    val totalSaved = chosen.sumOf { name -> ((now - (starts[name] ?: now)).coerceAtLeast(0) / 86_400_000.0) * (costs[name] ?: 0.0) }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Mint, onPrimary = Bg, onBackground = White, onSurface = White, secondary = Orange)) {
        if (!initialized) {
            Onboarding(chosen, allCategories, { name -> chosen = if (name in chosen) chosen - name else chosen + name }, { showNewCategory = true }, { if (chosen.isEmpty()) chosen = listOf("Alkohol"); chosen.forEach { if (starts[it] == null) starts = starts + (it to now) }; initialized = true; page = 0 })
        } else {
            Scaffold(containerColor = Bg, bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Muted) {
                    listOf("Start", "Dziennik", "Wsparcie", "Postępy", "Ustawienia").forEachIndexed { index, label ->
                        val ico = listOf(Icons.Default.Home, Icons.Default.MenuBook, Icons.Default.Favorite, Icons.Default.EmojiEvents, Icons.Default.Settings)[index]
                        NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Icon(ico, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint, indicatorColor = Panel2, unselectedIconColor = Muted, unselectedTextColor = Muted))
                    }
                }
            }) { pad ->
                Box(Modifier.fillMaxSize().padding(pad)) {
                    when(page) {
                        0 -> HomePage(chosen, starts, costs, totalSaved, goal, goalName, now, onDiary = { page = 1 }, onSupport = { page = 2 }, onReset = { showResetFor = it }, onCost = { showCostFor = it })
                        1 -> DiaryPage(diary, onAdd = { showDiary = true }, onDelete = { diary = diary - it })
                        2 -> SupportPage(chosen, reflectionGain, { reflectionGain = it }, reflectionRisk, { reflectionRisk = it })
                        3 -> ProgressPage(chosen, starts, costs, totalSaved, goal, goalName, diary.size, now, onGoal = { goalInput = goal.toString(); goalNameInput = goalName; editGoal = true })
                        else -> SettingsPage(chosen, allCategories, customNames, costs, onToggle = { name -> chosen = if (name in chosen) chosen - name else { if (starts[name] == null) starts = starts + (name to now); chosen + name } }, onAdd = { showNewCategory = true }, onCost = { categoryName: String -> showCostFor = categoryName }, onStart = { categoryName: String -> showResetFor = categoryName }, onResetOnboarding = { initialized = false })
                    }
                }
            }
        }
    }
    if (showNewCategory) AlertDialog(onDismissRequest = { showNewCategory = false }, containerColor = Panel, title = { Text("Własna kategoria") }, text = { OutlinedTextField(newCategory, { newCategory = it }, label = { Text("Nazwa nałogu lub nawyku") }, singleLine = true) }, confirmButton = { TextButton(onClick = { val n = newCategory.trim(); if (n.isNotEmpty()) { customNames = (customNames + listOf(n)).distinct(); chosen = (chosen + listOf(n)).distinct(); if (starts[n] == null) starts = starts + mapOf(n to System.currentTimeMillis()); newCategory = ""; showNewCategory = false } }) { Text("Dodaj", color = Mint) } }, dismissButton = { TextButton(onClick = { showNewCategory = false }) { Text("Anuluj") } })
    showCostFor?.let { name -> var input by remember(name) { mutableStateOf((costs[name] ?: 0.0).toString()) }; AlertDialog(onDismissRequest = { showCostFor = null }, containerColor = Panel, title = { Text("Oszczędność: $name") }, text = { Column { Text("Ile średnio wydawałeś dziennie? Kwota służy do szacunków.", color = Muted); Spacer(Modifier.height(10.dp)); OutlinedTextField(input, { input = it }, label = { Text("Kwota dziennie (zł)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { costs = costs + (name to (input.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)); showCostFor = null }) { Text("Zapisz", color = Mint) } }, dismissButton = { TextButton(onClick = { showCostFor = null }) { Text("Anuluj") } }) }
    showResetFor?.let { name -> AlertDialog(onDismissRequest = { showResetFor = null }, containerColor = Panel, title = { Text("Ustaw początek licznika") }, text = { Text("Ustawienie nowej daty rozpocznie nowy licznik dla kategorii „$name”. To nie usuwa wpisów dziennika.") }, confirmButton = { TextButton(onClick = { val cal = Calendar.getInstance(); DatePickerDialog(context, { _, y, m, d -> cal.set(y,m,d,0,0,0); cal.set(Calendar.MILLISECOND,0); starts = starts + (name to cal.timeInMillis); showResetFor = null }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show() }) { Text("Wybierz datę", color = Mint) } }, dismissButton = { TextButton(onClick = { showResetFor = null }) { Text("Anuluj") } }) }
    if (showDiary) AlertDialog(onDismissRequest = { showDiary = false }, containerColor = Panel, title = { Text("Jak się dziś czujesz?") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Nastrój", color = Muted); Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { listOf("😞","🙁","😐","🙂","😁").forEachIndexed { i, e -> Text(e, fontSize = 27.sp, modifier = Modifier.clip(CircleShape).background(if (mood == i+1) Panel2 else Color.Transparent).clickable { mood = i+1 }.padding(5.dp)) } }; Text("Siła głodu / chęci: $craving na 5", color = Muted); Slider(craving.toFloat(), { craving = it.toInt() }, valueRange = 0f..5f, steps = 4); OutlinedTextField(note, { note = it }, label = { Text("Notatka (opcjonalnie)") }, minLines = 3) } }, confirmButton = { TextButton(onClick = { val f = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()); diary = listOf(DiaryEntry(f,mood,craving,note.trim())) + diary; note = ""; showDiary = false }) { Text("Zapisz wpis", color = Mint) } }, dismissButton = { TextButton(onClick = { showDiary = false }) { Text("Anuluj") } })
    if (editGoal) AlertDialog(onDismissRequest = { editGoal = false }, containerColor = Panel, title = { Text("Cel skarbonki") }, text = { Column { OutlinedTextField(goalNameInput, { goalNameInput = it }, label = { Text("Na co oszczędzasz?") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(goalInput, { goalInput = it }, label = { Text("Kwota celu (zł)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { TextButton(onClick = { goal = goalInput.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0) ?: goal; goalName = goalNameInput.ifBlank { "Mój cel" }; editGoal = false }) { Text("Zapisz", color = Mint) } }, dismissButton = { TextButton(onClick = { editGoal = false }) { Text("Anuluj") } })
}

@Composable private fun Onboarding(chosen: List<String>, categories: List<String>, onToggle: (String)->Unit, onAdd: ()->Unit, onDone: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Bg).padding(horizontal = 22.dp), horizontalAlignment = Alignment.Start) {
        item { Spacer(Modifier.height(38.dp)); Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Panel2), Alignment.Center) { Icon(Icons.Default.HourglassTop, null, tint = Mint, modifier = Modifier.size(34.dp)) }; Spacer(Modifier.height(22.dp)); Text("Zegar Życia", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = White); Spacer(Modifier.height(8.dp)); Text("Każdy dzień to krok w Twoją stronę.", fontSize = 16.sp, color = Muted); Spacer(Modifier.height(24.dp)); Text("Co chcesz śledzić?", fontSize = 21.sp, fontWeight = FontWeight.SemiBold); Text("Wybierz jedną lub kilka kategorii. Możesz je później zmienić.", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(14.dp)) }
        items(categories) { name -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(if (name in chosen) Panel2 else Panel).clickable { onToggle(name) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(icons[name] ?: "🎯", fontSize = 23.sp); Spacer(Modifier.width(12.dp)); Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium); Checkbox(name in chosen, { onToggle(name) }, colors = CheckboxDefaults.colors(checkedColor = Mint, checkmarkColor = Bg)) } }
        item { TextButton(onClick = onAdd) { Icon(Icons.Default.Add, null, tint = Mint); Text("Dodaj własną kategorię", color = Mint) }; Spacer(Modifier.height(8.dp)); Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg)) { Text("Zaczynamy", fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(24.dp)); Text("To narzędzie wspierające, nie oceniające. Potknięcie nie przekreśla Twojego postępu.", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(20.dp)) }
    }
}

@Composable private fun PageHeader(title: String, subtitle: String? = null) { Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp)) { Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = White); if (subtitle != null) { Spacer(Modifier.height(4.dp)); Text(subtitle, color = Muted, fontSize = 13.sp) } } }
@Composable private fun CardBox(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(17.dp), content = content) }
@Composable private fun MintButton(text: String, onClick: ()->Unit, modifier: Modifier = Modifier) { Button(onClick, modifier.height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg)) { Text(text, fontWeight = FontWeight.Bold) } }

@Composable private fun HomePage(chosen: List<String>, starts: Map<String,Long>, costs: Map<String,Double>, saved: Double, goal: Double, goalName: String, now: Long, onDiary: ()->Unit, onSupport: ()->Unit, onReset:(String)->Unit, onCost:(String)->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        item { PageHeader("Dziś wybierasz siebie", "Małe kroki też prowadzą daleko."); CardBox { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("TWOJA DROGA", fontSize = 11.sp, color = Mint, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(Modifier.height(7.dp)); Text("Nie musisz być idealny. Wystarczy, że próbujesz.", fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); Text("Każda chwila bez nałogu ma znaczenie.", color = Muted, fontSize = 12.sp) }; Icon(Icons.Default.Favorite, null, tint = Mint, modifier = Modifier.size(32.dp)) } }; Spacer(Modifier.height(14.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Twoje liczniki", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("${chosen.size} aktywne", color = Muted, fontSize = 12.sp) }; Spacer(Modifier.height(10.dp)) }
        if (chosen.isEmpty()) item { CardBox { Text("Nie wybrano kategorii", color = Muted); Text("Dodaj kategorię w ustawieniach.", color = Muted) } }
        items(chosen) { name -> val since = starts[name] ?: now; val total = (now-since).coerceAtLeast(0); val days = TimeUnit.MILLISECONDS.toDays(total); val hours = TimeUnit.MILLISECONDS.toHours(total) % 24; val mins = TimeUnit.MILLISECONDS.toMinutes(total) % 60
            CardBox { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(Panel2), Alignment.Center) { Text(icons[name] ?: "🎯", fontSize = 23.sp) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text("Czas, który odzyskujesz", color = Muted, fontSize = 12.sp) }; IconButton(onClick = { onReset(name) }) { Icon(Icons.Default.EditCalendar, null, tint = Muted) } }; Spacer(Modifier.height(14.dp)); Row(verticalAlignment = Alignment.Bottom) { Text("$days", fontSize = 43.sp, fontWeight = FontWeight.Bold, color = Mint); Spacer(Modifier.width(7.dp)); Text("dni", color = Muted, modifier = Modifier.padding(bottom = 8.dp)); Spacer(Modifier.width(14.dp)); Text("${hours}h ${mins}m", color = White, modifier = Modifier.padding(bottom = 8.dp)) }; Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Szacowane oszczędności: ", color = Muted, fontSize = 12.sp); Text(String.format(Locale("pl","PL"), "%.2f zł", days * (costs[name] ?: 0.0)), color = Mint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Spacer(Modifier.weight(1f)); TextButton(onClick = { onCost(name) }, contentPadding = PaddingValues(0.dp)) { Text("Ustaw koszt", color = Mint, fontSize = 12.sp) } } }
            itemSpacer()
        }
        item { Spacer(Modifier.height(2.dp)); CardBox { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Savings, null, tint = Mint, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Twoja skarbonka", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(goalName, color = Muted, fontSize = 12.sp) } }; Spacer(Modifier.height(15.dp)); Text(String.format(Locale("pl","PL"), "%.2f zł", saved), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Mint); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { (saved / goal).toFloat().coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Mint, trackColor = Panel2); Spacer(Modifier.height(6.dp)); Text("Cel: ${String.format(Locale("pl","PL"), "%.0f zł", goal)} · ${(saved/goal*100).toInt().coerceAtMost(100)}%", color = Muted, fontSize = 12.sp) }; Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = onDiary, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = White)) { Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(7.dp)); Text("Dziennik") }; Button(onClick = onSupport, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg)) { Icon(Icons.Default.Favorite, null); Spacer(Modifier.width(7.dp)); Text("Trudna chwila") } }; Spacer(Modifier.height(24.dp)) }
    }
}
@Composable private fun LazyListScope.itemSpacer() { item { Spacer(Modifier.height(12.dp)) } }

@Composable private fun DiaryPage(entries: List<DiaryEntry>, onAdd:()->Unit, onDelete:(DiaryEntry)->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) { item { PageHeader("Dziennik", "Zauważaj emocje bez oceniania siebie."); MintButton("+ Dodaj dzisiejszy wpis", onAdd, Modifier.fillMaxWidth()); Spacer(Modifier.height(16.dp)); CardBox { Text("Twoje obserwacje", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(5.dp)); Text("${entries.size} zapisanych wpisów. Brak wpisu nie oznacza złego dnia.", color = Muted, fontSize = 12.sp) }; Spacer(Modifier.height(14.dp)) }; if(entries.isEmpty()) item { CardBox { Icon(Icons.Default.EditNote, null, tint = Mint, modifier = Modifier.size(32.dp)); Spacer(Modifier.height(8.dp)); Text("Tu jest miejsce na Twoje dni", fontWeight = FontWeight.SemiBold); Text("Zapisuj nastrój, głód i to, co pomaga Ci wytrwać.", color = Muted, fontSize = 13.sp) } }; items(entries) { e -> CardBox { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(e.day, color = Mint, fontWeight = FontWeight.SemiBold); Text("Nastrój: ${listOf("😞","🙁","😐","🙂","😁").getOrElse(e.mood-1){"😐"}}   ·   Głód: ${e.craving}/5", fontSize = 13.sp) }; IconButton(onClick = { onDelete(e) }) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) } }; if(e.note.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(e.note) } }; Spacer(Modifier.height(10.dp)) }; item { Spacer(Modifier.height(20.dp)) } }
}

@Composable private fun SupportPage(chosen: List<String>, gain: String, onGain:(String)->Unit, risk:String, onRisk:(String)->Unit) {
    var intensity by remember { mutableIntStateOf(3) }
    var trigger by remember { mutableStateOf("") }
    var showPlan by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) { item { PageHeader("Jestem po Twojej stronie", "Nie musisz rozwiązać całego życia w tej chwili."); CardBox { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Favorite, null, tint = Mint, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)); Column { Text("Przeczekaj falę", fontWeight = FontWeight.Bold, fontSize = 19.sp); Text("Głód i napięcie mogą narastać i opadać.", color = Muted, fontSize = 12.sp) } }; Spacer(Modifier.height(15.dp)); Text("Jak silna jest teraz chęć? $intensity/5", color = White); Slider(intensity.toFloat(), { intensity = it.toInt() }, valueRange = 0f..5f, steps = 4); OutlinedTextField(trigger, { trigger = it }, label = { Text("Co ją wywołało? (opcjonalnie)") }, modifier = Modifier.fillMaxWidth(), minLines = 2); Spacer(Modifier.height(12.dp)); MintButton("Pokaż mi plan na teraz", { showPlan = true }, Modifier.fillMaxWidth()); if(showPlan) { Spacer(Modifier.height(14.dp)); Text("Na najbliższe 10 minut", color = Mint, fontWeight = FontWeight.Bold, fontSize = 17.sp); listOf("Odłóż decyzję o 10 minut — nie musisz rozstrzygać wszystkiego teraz.", "Zmień miejsce: wyjdź na krótki spacer albo przejdź tam, gdzie czujesz się bezpieczniej.", "Napij się wody i zrób 10 spokojnych, wolnych oddechów.", "Napisz lub zadzwoń do zaufanej osoby. Powiedz wprost, że jest Ci trudno.", "Przeczytaj niżej własne powody, dla których chcesz wytrwać.").forEachIndexed { i, t -> Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) { Box(Modifier.size(25.dp).clip(CircleShape).background(Panel2), Alignment.Center) { Text("${i+1}", color = Mint, fontSize = 12.sp) }; Spacer(Modifier.width(9.dp)); Text(t, fontSize = 13.sp, color = White) } }; if(intensity >= 4) Text("Jeśli czujesz, że możesz stracić kontrolę, odezwij się teraz do zaufanej osoby lub lokalnej pomocy kryzysowej. Przy bezpośrednim zagrożeniu życia dzwoń pod 112.", color = Orange, fontSize = 12.sp) } }; Spacer(Modifier.height(14.dp)); CardBox { Text("Co chcę zyskać?", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Moje powody, moje życie.", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(9.dp)); OutlinedTextField(gain, onGain, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Spokój, zdrowie, pieniądze, relacje…") }, minLines = 3) }; Spacer(Modifier.height(12.dp)); CardBox { Text("Co ryzykuję, gdy wracam do nałogu?", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Nie po to, by się karać — by pamiętać o swoich granicach.", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(9.dp)); OutlinedTextField(risk, onRisk, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Co jest dla mnie ważne i co chcę chronić?") }, minLines = 3) }; Spacer(Modifier.height(24.dp)) } }

@Composable private fun ProgressPage(chosen: List<String>, starts: Map<String,Long>, costs: Map<String,Double>, saved:Double, goal:Double, goalName:String, diaryCount:Int, now:Long, onGoal:()->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) { item { PageHeader("Twoje postępy", "Liczą się dni, decyzje i to, czego się uczysz."); CardBox { Text("Łączny szacowany wynik", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(5.dp)); Text(String.format(Locale("pl","PL"), "%.2f zł", saved), color = Mint, fontSize = 34.sp, fontWeight = FontWeight.Bold); Text("To estymacja niewydanych pieniędzy, a nie saldo konta.", color = Muted, fontSize = 11.sp); Spacer(Modifier.height(12.dp)); Divider(color = Panel2); Spacer(Modifier.height(12.dp)); Row { Column(Modifier.weight(1f)) { Text("${chosen.size}", fontSize = 25.sp, color = White, fontWeight = FontWeight.Bold); Text("aktywne liczniki", color = Muted, fontSize = 11.sp) }; Column(Modifier.weight(1f)) { Text("$diaryCount", fontSize = 25.sp, color = White, fontWeight = FontWeight.Bold); Text("wpisy dziennika", color = Muted, fontSize = 11.sp) } } }; Spacer(Modifier.height(14.dp)); CardBox { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Cel: $goalName", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(String.format(Locale("pl","PL"), "%.2f zł z %.0f zł", saved, goal), color = Muted, fontSize = 12.sp) }; IconButton(onClick = onGoal) { Icon(Icons.Default.Edit, null, tint = Mint) } }; Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { (saved/goal).toFloat().coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Mint, trackColor = Panel2) }; Spacer(Modifier.height(14.dp)); Text("Rekordy i etapy", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)) }; items(chosen) { name -> val days = TimeUnit.MILLISECONDS.toDays((now-(starts[name] ?: now)).coerceAtLeast(0)); CardBox { Row(verticalAlignment = Alignment.CenterVertically) { Text(icons[name] ?: "🎯", fontSize = 27.sp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text("${days} dni na Twojej drodze", color = Muted, fontSize = 12.sp) }; val badge = when { days >= 365 -> "Rok"; days >= 100 -> "100+"; days >= 30 -> "30 dni"; days >= 7 -> "Tydzień"; else -> "Start" }; Text(badge, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.height(10.dp)) }; item { Spacer(Modifier.height(20.dp)) } }
}

@Composable private fun SettingsPage(chosen: List<String>, categories: List<String>, custom:List<String>, costs:Map<String,Double>, onToggle:(String)->Unit, onAdd:()->Unit, onCost:(String)->Unit, onStart:(String)->Unit, onResetOnboarding:()->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) { item { PageHeader("Ustawienia", "Dostosuj Zegar Życia do siebie."); CardBox { Text("Aktywne kategorie", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Włączenie kategorii tworzy licznik, wyłączenie ukrywa go na ekranie głównym.", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(12.dp)) }; Spacer(Modifier.height(8.dp)) }; items(categories) { name -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(icons[name] ?: "🎯", fontSize = 21.sp); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Medium); Text(String.format(Locale("pl","PL"), "%.2f zł dziennie", costs[name] ?: 0.0), color = Muted, fontSize = 11.sp) }; Checkbox(name in chosen, { onToggle(name) }, colors = CheckboxDefaults.colors(checkedColor = Mint)); IconButton(onClick = { onCost(name) }) { Icon(Icons.Default.Savings, null, tint = Mint) }; IconButton(onClick = { onStart(name) }) { Icon(Icons.Default.CalendarMonth, null, tint = Muted) } } }; item { Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Mint), colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Dodaj własną kategorię") }; Spacer(Modifier.height(14.dp)); CardBox { Text("Prywatność", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(5.dp)); Text("Wpisy i ustawienia są zapisywane lokalnie na tym urządzeniu. Ta wersja nie synchronizuje danych z chmurą i nie wysyła dziennika na serwer.", color = Muted, fontSize = 12.sp) }; Spacer(Modifier.height(12.dp)); CardBox { Text("Informacja", fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text("Zegar Życia jest narzędziem wspierającym i nie zastępuje leczenia ani profesjonalnej pomocy. Jeśli zdarzy się potknięcie, możesz ustawić nowy początek licznika — wcześniejsze wpisy pozostaną w dzienniku.", color = Muted, fontSize = 12.sp) }; Spacer(Modifier.height(14.dp)); TextButton(onClick = onResetOnboarding) { Text("Pokaż ekran wyboru kategorii ponownie", color = Mint) }; Spacer(Modifier.height(25.dp)) } }
}

private fun loadStringList(raw: String): List<String> = try { val a=JSONArray(raw); (0 until a.length()).map { a.getString(it) } } catch (_: Exception) { emptyList() }
private fun loadLongMap(raw: String): Map<String,Long> = try { val o=JSONObject(raw); o.keys().asSequence().associateWith { o.getLong(it) } } catch (_: Exception) { emptyMap() }
private fun loadDoubleMap(raw: String): Map<String,Double> = try { val o=JSONObject(raw); o.keys().asSequence().associateWith { o.getDouble(it) } } catch (_: Exception) { emptyMap() }
private fun loadDiary(raw: String): List<DiaryEntry> = try { val a=JSONArray(raw); (0 until a.length()).map { val o=a.getJSONObject(it); DiaryEntry(o.optString("day"),o.optInt("mood",3),o.optInt("craving",2),o.optString("note")) } } catch (_: Exception) { emptyList() }
