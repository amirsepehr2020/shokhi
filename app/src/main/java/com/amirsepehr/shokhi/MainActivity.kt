package com.amirsepehr.shokhi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalculatorApp() }
    }

    private fun setSharing(enabled: Boolean) {
        val prefs = getSharedPreferences("shokhi", MODE_PRIVATE)
        prefs.edit().putBoolean("sharing", enabled).apply()
        val intent = Intent(this, StatusService::class.java).setAction(
            if (enabled) StatusService.ACTION_START else StatusService.ACTION_STOP
        )
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } else {
            stopService(intent)
        }
    }

    fun sharingSetter(): (Boolean) -> Unit = ::setSharing
}

@Composable
private fun CalculatorApp() {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val prefs = remember { activity.getSharedPreferences("shokhi", Activity.MODE_PRIVATE) }
    val detectedUser = remember { if (Build.MANUFACTURER.equals("samsung", true)) "Sepehr" else "Amir" }
    val sharingState = remember { mutableStateOf(prefs.getBoolean("sharing", false)) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            CalculatorScreen(
                user = detectedUser,
                sharing = sharingState.value,
                onSharingChanged = {
                    sharingState.value = it
                    (activity as MainActivity).sharingSetter()(it)
                }
            )
        }
    }
}

@Composable
private fun CalculatorScreen(user: String, sharing: Boolean, onSharingChanged: (Boolean) -> Unit) {
    var display by remember { mutableStateOf("0") }
    var stored by remember { mutableStateOf<Double?>(null) }
    var operation by remember { mutableStateOf<Char?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var memory by remember { mutableStateOf(0.0) }
    var history by remember { mutableStateOf(listOf<String>()) }

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
        val result = calculate(left, right, op)
        history = (history + "${formatNumber(left)} $op ${formatNumber(right)} = ${formatNumber(result)}").takeLast(12)
        display = formatNumber(result); stored = null; operation = null; waiting = true
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.Bottom) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("ماشین حساب", style = MaterialTheme.typography.headlineSmall)
                Text("$user  •  ${if (sharing) "اشتراک وضعیت روشن" else "خصوصی"}", fontSize = 12.sp)
            }
            Switch(checked = sharing, onCheckedChange = onSharingChanged)
        }
        Spacer(Modifier.height(8.dp))
        if (history.isNotEmpty()) {
            Text("آخرین محاسبه: ${history.last()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(6.dp))
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.CenterEnd) { Text(display, fontSize = 46.sp, maxLines = 1) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("MC", "MR", "M+", "M-").forEach { label ->
                OutlinedButton(onClick = {
                    when (label) {
                        "MC" -> memory = 0.0
                        "MR" -> { display = formatNumber(memory); waiting = true }
                        "M+" -> memory += display.toDoubleOrNull() ?: 0.0
                        "M-" -> memory -= display.toDoubleOrNull() ?: 0.0
                    }
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text(label, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        val rows = listOf(listOf("AC", "±", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("0", ".", "⌫", "="))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    Button(onClick = {
                        when (label) {
                            "AC" -> clear()
                            "±" -> display = formatNumber(-(display.toDoubleOrNull() ?: 0.0))
                            "%" -> display = formatNumber((display.toDoubleOrNull() ?: 0.0) / 100.0)
                            "÷", "×", "−", "+" -> chooseOp(if (label == "×") '*' else if (label == "−") '-' else label.first())
                            "=" -> equals()
                            "." -> inputDecimal()
                            "⌫" -> if (!waiting) display = if (display.length > 1) display.dropLast(1) else "0"
                            else -> inputDigit(label)
                        }
                    }, modifier = Modifier.weight(1f).height(62.dp), shape = RoundedCornerShape(20.dp)) { Text(label, fontSize = 21.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
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
