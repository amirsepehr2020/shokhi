package com.amirsepehr.shokhi

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import java.net.HttpURLConnection
import java.net.URL

private const val API_URL = "https://shokhi-site.sepehr2sodoury.workers.dev/api/status"

class MainActivity : ComponentActivity() {
    private val reporter by lazy { StatusReporter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShokhiApp(reporter) }
    }

    override fun onResume() {
        super.onResume()
        reporter.start()
    }

    override fun onPause() {
        reporter.sendNow(screenOn = false)
        reporter.stop()
        super.onPause()
    }
}

private class StatusReporter(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("shokhi", Context.MODE_PRIVATE)
    private var running = false
    private val task = object : Runnable {
        override fun run() {
            if (!running || !prefs.getBoolean("sharing", false)) return
            sendNow(isScreenInteractive())
            handler.postDelayed(this, 15_000L)
        }
    }

    fun start() {
        running = true
        handler.removeCallbacks(task)
        if (prefs.getBoolean("sharing", false)) {
            sendNow(isScreenInteractive())
            handler.postDelayed(task, 15_000L)
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(task)
    }

    fun sendNow(screenOn: Boolean = isScreenInteractive()) {
        if (!prefs.getBoolean("sharing", false)) return
        val user = prefs.getString("user", null) ?: return
        Thread {
            try {
                val connection = URL(API_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val payload = "{\"user\":\"$user\",\"screenOn\":$screenOn}"
                connection.outputStream.use { it.write(payload.toByteArray()) }
                connection.inputStream.close()
                connection.disconnect()
            } catch (_: Exception) {
                // Status sharing is best-effort; calculator remains fully offline-capable.
            }
        }.start()
    }

    fun setSharing(enabled: Boolean) {
        prefs.edit().putBoolean("sharing", enabled).apply()
        if (enabled) start() else stop()
    }

    fun setUser(user: String) { prefs.edit().putString("user", user).apply() }
    fun getUser(): String? = prefs.getString("user", null)
    fun isSharing(): Boolean = prefs.getBoolean("sharing", false)

    private fun isScreenInteractive(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }
}

@Composable
private fun ShokhiApp(reporter: StatusReporter) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val user = remember { mutableStateOf(reporter.getUser()) }
            if (user.value == null) {
                IdentityScreen { selected ->
                    reporter.setUser(selected)
                    user.value = selected
                }
            } else {
                CalculatorScreen(reporter)
            }
        }
    }
}

@Composable
private fun IdentityScreen(onSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Shokhi", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text("Choose your profile", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onSelected("sepehr") }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(20.dp)) { Text("Sepehr") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onSelected("amir") }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(20.dp)) { Text("Amir") }
    }
}

@Composable
private fun CalculatorScreen(reporter: StatusReporter) {
    var display by remember { mutableStateOf("0") }
    var stored by remember { mutableStateOf<Double?>(null) }
    var operation by remember { mutableStateOf<Char?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(reporter.isSharing()) }

    fun inputDigit(digit: String) { display = if (waiting || display == "0") digit else display + digit; waiting = false }
    fun inputDecimal() { if (waiting) { display = "0."; waiting = false } else if (!display.contains('.')) display += "." }
    fun clear() { display = "0"; stored = null; operation = null; waiting = false }
    fun chooseOp(op: Char) {
        val current = display.toDoubleOrNull() ?: return
        if (stored != null && operation != null && !waiting) {
            val result = calculate(stored!!, current, operation!!); display = formatNumber(result); stored = result
        } else stored = current
        operation = op; waiting = true
    }
    fun equals() {
        val left = stored ?: return; val right = display.toDoubleOrNull() ?: return; val op = operation ?: return
        display = formatNumber(calculate(left, right, op)); stored = null; operation = null; waiting = true
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp), verticalArrangement = Arrangement.Bottom) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Shokhi", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (sharing) "Sharing" else "Private", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Switch(checked = sharing, onCheckedChange = { sharing = it; reporter.setSharing(it) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.CenterEnd) { Text(display, fontSize = 48.sp, maxLines = 1) }
        }
        Spacer(Modifier.height(14.dp))
        val rows = listOf(listOf("AC", "±", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("0", ".", "⌫", "="))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { label ->
                    Button(onClick = {
                        when (label) {
                            "AC" -> clear()
                            "±" -> display = formatNumber(-(display.toDoubleOrNull() ?: 0.0))
                            "%" -> display = formatNumber((display.toDoubleOrNull() ?: 0.0) / 100.0)
                            "÷", "×", "−", "+" -> chooseOp(label.first().let { if (it == '×') '*' else if (it == '−') '-' else it })
                            "=" -> equals()
                            "." -> inputDecimal()
                            "⌫" -> if (!waiting) display = if (display.length > 1) display.dropLast(1) else "0"
                            else -> inputDigit(label)
                        }
                    }, modifier = Modifier.weight(1f).height(68.dp), shape = RoundedCornerShape(22.dp)) { Text(label, fontSize = 22.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun calculate(a: Double, b: Double, op: Char): Double = when (op) {
    '+' -> a + b
    '-' -> a - b
    '*' -> a * b
    '/' -> if (abs(b) < 1e-12) 0.0 else a / b
    else -> b
}

private fun formatNumber(value: Double): String {
    if (!value.isFinite()) return "Error"
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return "%.10f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
}
