package com.hamdan.survey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

data class SurveyPoint(val latitude: Double, val longitude: Double)
data class SurveyProject(val name: String, val points: List<SurveyPoint>, val savedAt: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HamdanApp() }
    }
}

@Composable
fun HamdanApp() {
    val context = LocalContext.current
    var projectName by remember { mutableStateOf("مشروع جديد") }
    var points by remember { mutableStateOf(listOf<SurveyPoint>()) }
    var savedProjects by remember { mutableStateOf(loadProjects(context)) }
    var length by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            captureGps(context) { p -> points = points + p }
        } else {
            Toast.makeText(context, "يلزم السماح بإذن الموقع لاستخدام GPS", Toast.LENGTH_LONG).show()
        }
    }

    val polygonArea = polygonAreaSquareMeters(points)
    val triangleArea = run {
        val x = a.toDoubleOrNull(); val y = b.toDoubleOrNull(); val z = c.toDoubleOrNull()
        if (x != null && y != null && z != null && x > 0 && y > 0 && z > 0 &&
            x + y > z && x + z > y && y + z > x) {
            val s = (x + y + z) / 2.0
            sqrt(s * (s - x) * (s - y) * (s - z))
        } else null
    }
    val rectangleArea = run {
        val x = length.toDoubleOrNull(); val y = width.toDoubleOrNull()
        if (x != null && y != null && x > 0 && y > 0) x * y else null
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF176B5B),
            secondary = androidx.compose.ui.graphics.Color(0xFFE4A64A),
            background = androidx.compose.ui.graphics.Color(0xFFF5F7F6)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("همدان", fontWeight = FontWeight.Bold)
                            Text("مساحة الأراضي وحساب مساحاتها", fontSize = 12.sp)
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("مرحباً بك في همدان", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("احسب المساحات، سجل نقاط GPS، واحفظ أعمال المساحة محلياً.")
                            OutlinedTextField(
                                value = projectName, onValueChange = { projectName = it },
                                label = { Text("اسم المشروع") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    points = emptyList()
                                    Toast.makeText(context, "بدأ مشروع جديد", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Map, null); Spacer(Modifier.width(6.dp)); Text("جديد") }
                                OutlinedButton(onClick = {
                                    val p = SurveyProject(projectName.ifBlank { "مشروع بدون اسم" }, points, System.currentTimeMillis())
                                    savedProjects = (savedProjects.filterNot { it.name == p.name } + p)
                                    saveProjects(context, savedProjects)
                                    Toast.makeText(context, "تم حفظ المشروع على الجهاز", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("حفظ") }
                            }
                        }
                    }
                }
                item {
                    ToolHeader("حساب مساحة مستطيل", Icons.Default.Calculate)
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(length, { length = it }, label = { Text("الطول (متر)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(width, { width = it }, label = { Text("العرض (متر)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text(rectangleArea?.let { "المساحة: ${formatNumber(it)} م²" } ?: "أدخل الطول والعرض كقيم موجبة", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item {
                    ToolHeader("حساب مساحة مثلث", Icons.Default.Straighten)
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(a, { a = it }, label = { Text("الضلع الأول (م)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(b, { b = it }, label = { Text("الضلع الثاني (م)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(c, { c = it }, label = { Text("الضلع الثالث (م)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text(triangleArea?.let { "المساحة: ${formatNumber(it)} م²" } ?: "أدخل ثلاثة أضلاع صالحة لمثلث", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    ToolHeader("المسح بالإحداثيات وGPS", Icons.Default.GpsFixed)
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("النقاط المسجلة: ${points.size}")
                            Text("المساحة التقريبية: ${polygonArea?.let { "${formatNumber(it)} م²" } ?: "أضف 3 نقاط أو أكثر"}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.GpsFixed, null); Spacer(Modifier.width(6.dp)); Text("تسجيل نقطة")
                                }
                                OutlinedButton(onClick = { points = emptyList() }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("مسح النقاط")
                                }
                            }
                            points.forEachIndexed { index, point ->
                                Text("${index + 1}. ${"%.6f".format(Locale.US, point.latitude)}, ${"%.6f".format(Locale.US, point.longitude)}")
                            }
                            OutlinedButton(
                                onClick = {
                                    exportCsv(context, projectName, points)
                                    Toast.makeText(context, "تم تصدير CSV إلى مجلد ملفات التطبيق", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = points.isNotEmpty()
                            ) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("تصدير النقاط CSV") }
                        }
                    }
                }
                item {
                    ToolHeader("المشاريع المحفوظة", Icons.Default.Save)
                    if (savedProjects.isEmpty()) {
                        Text("لا توجد مشاريع محفوظة بعد.")
                    } else {
                        savedProjects.forEach { project ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(project.name, fontWeight = FontWeight.SemiBold)
                                    Text("النقاط: ${project.points.size} • ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(project.savedAt))}")
                                    TextButton(onClick = { projectName = project.name; points = project.points }) { Text("فتح المشروع") }
                                }
                            }
                        }
                    }
                }
                item { Text("تنبيه: حساب GPS تقريبي، ويتأثر بدقة الجهاز وعدد النقاط وطريقة تسجيل حدود الأرض.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ToolHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun formatNumber(value: Double) = String.format(Locale.getDefault(), "%,.2f", value)

private fun polygonAreaSquareMeters(points: List<SurveyPoint>): Double? {
    if (points.size < 3) return null
    val lat0 = Math.toRadians(points.map { it.latitude }.average())
    val earthRadius = 6378137.0
    val xy = points.map {
        val x = earthRadius * Math.toRadians(it.longitude) * cos(lat0)
        val y = earthRadius * Math.toRadians(it.latitude)
        x to y
    }
    var sum = 0.0
    for (i in xy.indices) {
        val j = (i + 1) % xy.size
        sum += xy[i].first * xy[j].second - xy[j].first * xy[i].second
    }
    return abs(sum) / 2.0
}

private fun captureGps(context: Context, onPoint: (SurveyPoint) -> Unit) {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val location = providers.asSequence().mapNotNull { provider ->
            try { manager.getLastKnownLocation(provider) } catch (_: Exception) { null }
        }.maxByOrNull { it.time }
        if (location != null) {
            onPoint(SurveyPoint(location.latitude, location.longitude))
            Toast.makeText(context, "تم تسجيل آخر موقع معروف. تحقق من دقته ميدانياً.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "لا يوجد موقع معروف بعد. فعّل GPS وانتظر ثبات الإشارة ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "تعذر الوصول إلى الموقع", Toast.LENGTH_LONG).show()
    }
}

private fun saveProjects(context: Context, projects: List<SurveyProject>) {
    val array = JSONArray()
    projects.forEach { p ->
        val obj = JSONObject().put("name", p.name).put("savedAt", p.savedAt)
        val points = JSONArray()
        p.points.forEach { points.put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)) }
        obj.put("points", points)
        array.put(obj)
    }
    context.getSharedPreferences("hamdan_projects", Context.MODE_PRIVATE).edit().putString("projects", array.toString()).apply()
}

private fun loadProjects(context: Context): List<SurveyProject> {
    return try {
        val raw = context.getSharedPreferences("hamdan_projects", Context.MODE_PRIVATE).getString("projects", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val pts = obj.getJSONArray("points")
            SurveyProject(obj.getString("name"), (0 until pts.length()).map { j ->
                val point = pts.getJSONObject(j)
                SurveyPoint(point.getDouble("lat"), point.getDouble("lon"))
            }, obj.optLong("savedAt", System.currentTimeMillis()))
        }
    } catch (_: Exception) { emptyList() }
}

private fun exportCsv(context: Context, projectName: String, points: List<SurveyPoint>) {
    try {
        val dir = File(context.getExternalFilesDir(null), "exports")
        dir.mkdirs()
        val safeName = projectName.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").ifBlank { "hamdan" }
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.append("index,latitude,longitude\\n")
            points.forEachIndexed { index, point ->
                writer.append("${index + 1},${point.latitude},${point.longitude}\\n")
            }
        }
        Toast.makeText(context, "المسار: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "تعذر تصدير الملف", Toast.LENGTH_LONG).show()
    }
}
