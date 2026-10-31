package com.example.mylaundrymanager

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private val categories = listOf("T-shirts", "Shirts", "Jeans / trousers", "Shorts", "Jackets", "Bedsheets", "Towels", "Pillow covers")
private const val CHANNEL_ID = "laundry_reminders"

data class LaundryRecord(val id: Long, val day: String, val date: String, val items: Map<String, Int>, val returned: Set<String>, val notes: String)
data class ClothingItem(val id: Long, val name: String, val category: String, val photo: String)
data class Payment(val id: Long, val amount: Double, val date: String, val note: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContent { LaundryApp() }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Laundry reminders", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

class LaundryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Laundry day"
        val text = intent.getStringExtra("text") ?: "Remember to hand over and check your laundry."
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build()
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(title.hashCode(), notification)
        }
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ReminderScheduler.schedule(context) }
}
object ReminderScheduler {
    fun schedule(context: Context) {
        scheduleOne(context, Calendar.SUNDAY, 14, "Sunday laundry", "Afternoon: hand over laundry and check same-day return.")
        scheduleOne(context, Calendar.WEDNESDAY, 19, "Wednesday laundry", "Evening: hand over laundry and check same-day return.")
    }
    private fun scheduleOne(context: Context, weekday: Int, hour: Int, title: String, message: String) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, weekday); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 7)
        }
        val id = weekday
        val intent = Intent(context, LaundryReminderReceiver::class.java).putExtra("title", title).putExtra("text", message)
        val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY * 7, pending)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaundryApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("laundry_data", 0) }
    var records by remember { mutableStateOf(loadRecords(prefs)) }
    var clothes by remember { mutableStateOf(loadClothes(prefs)) }
    var payments by remember { mutableStateOf(loadPayments(prefs)) }
    var missingTshirt by remember { mutableIntStateOf(prefs.getInt("missing_tshirt", 1)) }
    var missingLower by remember { mutableIntStateOf(prefs.getInt("missing_lower", 1)) }
    var tab by remember { mutableIntStateOf(0) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun persistRecords(v: List<LaundryRecord>) { records = v; prefs.edit().putString("records", encodeRecords(v)).apply() }
    fun persistClothes(v: List<ClothingItem>) { clothes = v; prefs.edit().putString("clothes", encodeClothes(v)).apply() }
    fun persistPayments(v: List<Payment>) { payments = v; prefs.edit().putString("payments", encodePayments(v)).apply() }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF243B53), secondary = androidx.compose.ui.graphics.Color(0xFF627D98), background = androidx.compose.ui.graphics.Color(0xFFF5F7FA), surface = androidx.compose.ui.graphics.Color.White)) {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("My Laundry Manager", fontWeight = FontWeight.SemiBold) }) },
            bottomBar = {
                NavigationBar {
                    val labels = listOf("Home", "New round", "Clothes", "Payments", "More")
                    val icons = listOf(Icons.Default.Home, Icons.Default.LocalLaundryService, Icons.Default.Checkroom, Icons.Default.Payments, Icons.Default.MoreHoriz)
                    labels.forEachIndexed { i, label -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null) }, label = { Text(label) }) }
                }
            }, containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            when(tab) {
                0 -> HomeScreen(records, clothes.size, missingTshirt, missingLower, Modifier.padding(padding))
                1 -> NewRoundScreen({ day, date, quantities, returned, notes ->
                    persistRecords(listOf(LaundryRecord(System.currentTimeMillis(), day, date, quantities, returned, notes)) + records); tab = 0
                }, Modifier.padding(padding))
                2 -> ClothesScreen(clothes, { persistClothes(it) }, Modifier.padding(padding))
                3 -> PaymentsScreen(payments, { persistPayments(it) }, Modifier.padding(padding))
                else -> MoreScreen(
                    records, missingTshirt, missingLower,
                    { missingTshirt = it; prefs.edit().putInt("missing_tshirt", it).apply() },
                    { missingLower = it; prefs.edit().putInt("missing_lower", it).apply() },
                    onReminder = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        ReminderScheduler.schedule(context)
                    },
                    onExport = {
                        val backup = JSONObject().put("records", JSONArray(encodeRecords(records))).put("clothes", JSONArray(encodeClothes(clothes)))
                            .put("payments", JSONArray(encodePayments(payments))).put("missingTshirt", missingTshirt).put("missingLower", missingLower).toString(2)
                        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_SUBJECT, "My Laundry Manager backup").putExtra(Intent.EXTRA_TEXT, backup)
                        context.startActivity(Intent.createChooser(send, "Share or save backup"))
                    }, modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(records: List<LaundryRecord>, clothingCount: Int, missingTshirt: Int, missingLower: Int, modifier: Modifier = Modifier) {
    val latest = records.firstOrNull()
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Your weekly routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScheduleCard("SUNDAY", "Afternoon · 2 PM", "Same-day return", Modifier.weight(1f))
                ScheduleCard("WEDNESDAY", "Evening · 7 PM", "Same-day return", Modifier.weight(1f))
            }
        }
        item {
            Text("Your clothing inventory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) {
                Text("$clothingCount individually registered items", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Categories: ${categories.joinToString(" • ")}", color = MaterialTheme.colorScheme.secondary)
            } }
        }
        item {
            Text("Missing from wardrobe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) {
                Text("${missingTshirt + missingLower} items not yet located", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$missingTshirt T-shirt • $missingLower lower")
                Text("Recorded as not handed to the laundry person.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            } }
        }
        item {
            Text("Latest laundry round", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) {
                if (latest == null) Text("No laundry rounds recorded yet. Tap New round when you hand clothes over.")
                else { Text("${latest.day} • ${latest.date}", fontWeight = FontWeight.SemiBold); Text("${latest.items.values.sum()} items handed over"); Text("${latest.returned.size} categories marked returned") }
            } }
        }
    }
}
@Composable
fun ScheduleCard(day: String, time: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodySmall)
    } }
}
@Composable
fun NewRoundScreen(onSave: (String,String,Map<String,Int>,Set<String>,String)->Unit, modifier: Modifier = Modifier) {
    var day by remember { mutableStateOf("Sunday") }; var date by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    val qty = remember { categories.associateWith { mutableStateOf("0") } }; val returned = remember { categories.associateWith { mutableStateOf(false) } }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Record a laundry round", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=day=="Sunday", onClick={day="Sunday"}, label={Text("Sunday · Afternoon")})
                FilterChip(selected=day=="Wednesday", onClick={day="Wednesday"}, label={Text("Wednesday · Evening")})
            }
            OutlinedTextField(date,{date=it},label={Text("Date (e.g. 1 Nov 2026)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        }
        items(categories) { cat ->
            Card { Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment=Alignment.CenterVertically) {
                Text(cat,Modifier.weight(1f),fontWeight=FontWeight.Medium)
                OutlinedTextField(qty.getValue(cat).value,{v->qty.getValue(cat).value=v.filter(Char::isDigit).take(3)},label={Text("Qty")},modifier=Modifier.width(84.dp),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                Checkbox(returned.getValue(cat).value,{returned.getValue(cat).value=it})
            } }
        }
        item {
            OutlinedTextField(notes,{notes=it},label={Text("Notes / special instructions")},modifier=Modifier.fillMaxWidth(),minLines=2)
            Button(onClick={
                val q=categories.associateWith{qty.getValue(it).value.toIntOrNull()?:0}.filterValues{it>0}
                onSave(day,date.ifBlank{"Date not entered"},q,categories.filter{returned.getValue(it).value}.toSet(),notes)
            },modifier=Modifier.fillMaxWidth(),enabled=categories.any{(qty.getValue(it).value.toIntOrNull()?:0)>0}){Text("Save laundry record")}
        }
    }
}

@Composable
fun ClothesScreen(clothes: List<ClothingItem>, onChange:(List<ClothingItem>)->Unit, modifier:Modifier=Modifier) {
    var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf(categories.first()) }; var photo by remember { mutableStateOf("") }
    val picker= rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> photo=uri?.toString().orEmpty() }
    LazyColumn(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Individual clothing register",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Give each item a name and optionally attach a photo. This helps identify exactly what went to laundry.")
            OutlinedTextField(name,{name=it},label={Text("Clothing name (e.g. navy blue T-shirt)")},modifier=Modifier.fillMaxWidth())
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded,{expanded=it}) {
                OutlinedTextField(value=category,onValueChange={},modifier=Modifier.menuAnchor().fillMaxWidth(),readOnly=true,label={Text("Category")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
                ExposedDropdownMenu(expanded,{expanded=false}) { categories.forEach { c -> DropdownMenuItem(text={Text(c)},onClick={category=c;expanded=false}) } }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Icon(Icons.Default.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text("Add photo")}
                if(photo.isNotBlank()) AsyncImage(model=photo,contentDescription="Selected clothing photo",modifier=Modifier.size(64.dp).padding(6.dp))
            }
            Button(onClick={
                if(name.isNotBlank()) { onChange(listOf(ClothingItem(System.currentTimeMillis(),name.trim(),category,photo))+clothes); name="";photo="" }
            },modifier=Modifier.fillMaxWidth(),enabled=name.isNotBlank()){Text("Save clothing item")}
        }
        item { Text("Saved clothing items (${clothes.size})",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold) }
        items(clothes,key={it.id}) { item ->
            Card { Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                if(item.photo.isNotBlank()) AsyncImage(model=item.photo,contentDescription=item.name,modifier=Modifier.size(56.dp))
                Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(item.name,fontWeight=FontWeight.SemiBold);Text(item.category,style=MaterialTheme.typography.bodySmall)}
                IconButton(onClick={onChange(clothes.filterNot{it.id==item.id})}){Icon(Icons.Default.Delete,"Delete item")}
            } }
        }
    }
}

@Composable
fun PaymentsScreen(payments:List<Payment>,onChange:(List<Payment>)->Unit,modifier:Modifier=Modifier) {
    var amount by remember{mutableStateOf("")};var date by remember{mutableStateOf(today())};var note by remember{mutableStateOf("Laundry")}
    val total=payments.sumOf{it.amount}
    LazyColumn(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Laundry payments",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Card { Column(Modifier.padding(16.dp)){Text("Total recorded",style=MaterialTheme.typography.labelMedium);Text("₹%.2f".format(total),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("${payments.size} payment entries",color=MaterialTheme.colorScheme.secondary)} }
            OutlinedTextField(amount,{amount=it},label={Text("Amount (₹)")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            OutlinedTextField(date,{date=it},label={Text("Date")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(note,{note=it},label={Text("Note")},modifier=Modifier.fillMaxWidth())
            Button(onClick={
                val value=amount.toDoubleOrNull()
                if(value!=null && value>0){onChange(listOf(Payment(System.currentTimeMillis(),value,date,note))+payments);amount=""}
            },modifier=Modifier.fillMaxWidth(),enabled=(amount.toDoubleOrNull()?:0.0)>0){Text("Add payment")}
        }
        items(payments,key={it.id}) { p ->
            Card { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text(p.note,fontWeight=FontWeight.SemiBold);Text(p.date,style=MaterialTheme.typography.bodySmall)}
                Text("₹%.2f".format(p.amount),fontWeight=FontWeight.Bold)
                IconButton(onClick={onChange(payments.filterNot{it.id==p.id})}){Icon(Icons.Default.Delete,"Delete payment")}
            } }
        }
    }
}

@Composable
fun MoreScreen(records:List<LaundryRecord>,tshirt:Int,lower:Int,onTshirt:(Int)->Unit,onLower:(Int)->Unit,onReminder:()->Unit,onExport:()->Unit,modifier:Modifier=Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("More tools",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
        item {
            Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Laundry reminders",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text("Sunday at 2:00 PM and Wednesday at 7:00 PM. These are suggested reminder times; adjust in the code if your usual times differ.")
                Button(onClick=onReminder,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Notifications,null);Spacer(Modifier.width(8.dp));Text("Enable weekly reminders")}
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Missing clothes",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text("These items were reported missing from the wardrobe, not handed to the laundry person.")
                MissingCounter("T-shirt",tshirt,onTshirt);MissingCounter("Lower",lower,onLower)
                Text("Current total missing: ${tshirt+lower}",fontWeight=FontWeight.Bold)
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Monthly report",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                val month=SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(Date())
                val monthRecords=records.filter{it.date.contains(month,ignoreCase=true)||it.date.contains(SimpleDateFormat("MMM yyyy",Locale.getDefault()).format(Date()),ignoreCase=true)}
                Text("Laundry rounds entered for $month: ${monthRecords.size}")
                Text("Items handed over in those rounds: ${monthRecords.sumOf{it.items.values.sum()}}")
                Text("Currently listed missing: ${tshirt+lower}")
                Text("For accurate monthly totals, enter dates consistently (e.g. 31 Oct 2026).",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
            } }
        }
        item {
            Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Backup",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text("Create a shareable JSON backup of your laundry history, clothing register, payments and missing-item counts.")
                OutlinedButton(onClick=onExport,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Backup,null);Spacer(Modifier.width(8.dp));Text("Export / share backup")}
                Text("Keep the backup somewhere safe. Restore/import is not included in this version.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
            } }
        }
    }
}
@Composable
fun MissingCounter(label:String,count:Int,onChange:(Int)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f));IconButton(onClick={onChange((count-1).coerceAtLeast(0))}){Icon(Icons.Default.Remove,"Decrease")};Text("$count");IconButton(onClick={onChange(count+1)}){Icon(Icons.Default.Add,"Increase")} }
}

private fun today()=SimpleDateFormat("dd MMM yyyy",Locale.getDefault()).format(Date())
private fun encodeRecords(records:List<LaundryRecord>):String {
    val a=JSONArray();records.forEach{r->val o=JSONObject().put("id",r.id).put("day",r.day).put("date",r.date).put("notes",r.notes);val its=JSONObject();r.items.forEach{k,v->its.put(k,v)};o.put("items",its);val ret=JSONArray();r.returned.forEach{ret.put(it)};o.put("returned",ret);a.put(o)};return a.toString()
}
private fun loadRecords(p:android.content.SharedPreferences):List<LaundryRecord> = try {
    val a=JSONArray(p.getString("records","[]"));(0 until a.length()).map{i->val o=a.getJSONObject(i);val io=o.getJSONObject("items");val its=categories.mapNotNull{c->if(io.has(c))c to io.getInt(c) else null}.toMap();val ra=o.getJSONArray("returned");LaundryRecord(o.getLong("id"),o.getString("day"),o.getString("date"),its,(0 until ra.length()).map{ra.getString(it)}.toSet(),o.optString("notes"))}
}catch(_:Exception){emptyList()}
private fun encodeClothes(v:List<ClothingItem>):String { val a=JSONArray();v.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("category",it.category).put("photo",it.photo))};return a.toString() }
private fun loadClothes(p:android.content.SharedPreferences):List<ClothingItem> = try { val a=JSONArray(p.getString("clothes","[]"));(0 until a.length()).map{val o=a.getJSONObject(it);ClothingItem(o.getLong("id"),o.getString("name"),o.getString("category"),o.optString("photo"))} }catch(_:Exception){emptyList()}
private fun encodePayments(v:List<Payment>):String { val a=JSONArray();v.forEach{a.put(JSONObject().put("id",it.id).put("amount",it.amount).put("date",it.date).put("note",it.note))};return a.toString() }
private fun loadPayments(p:android.content.SharedPreferences):List<Payment> = try { val a=JSONArray(p.getString("payments","[]"));(0 until a.length()).map{val o=a.getJSONObject(it);Payment(o.getLong("id"),o.getDouble("amount"),o.getString("date"),o.getString("note"))} }catch(_:Exception){emptyList()}
