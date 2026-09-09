package com.amirsepehr.shokhi

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
        setContent { ShokhiApp() }
    }
}

@Composable
private fun ShokhiApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CalculatorScreen()
        }
    }
}

@Composable
private fun CalculatorScreen() {
    var display by remember { mutableStateOf("0") }
    var stored by remember { mutableStateOf<Double?>(null) }
    var operation by remember { mutableStateOf<Char?>(null) }
    var waiting by remember { mutableStateOf(false) }

    fun inputDigit(digit: String) {
        display = if (waiting || display == "0") digit else display + digit
        waiting = false
    }

    fun inputDecimal() {
        if (waiting) { display = "0."; waiting = false }
        else if (!display.contains('.')) display += "."
    }

    fun clear() { display = "0"; stored = null; operation = null; waiting = false }

    fun chooseOp(op: Char) {
        val current = display.toDoubleOrNull() ?: return
        if (stored != null && operation != null && !waiting) {
            val result = calculate(stored!!, current, operation!!)
            display = formatNumber(result)
            stored = result
        } else stored = current
        operation = op
        waiting = true
    }

    fun equals() {
        val left = stored ?: return
        val right = display.toDoubleOrNull() ?: return
        val op = operation ?: return
        display = formatNumber(calculate(left, right, op))
        stored = null; operation = null; waiting = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text("Shokhi", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.CenterEnd) {
                Text(display, fontSize = 48.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        val rows = listOf(
            listOf("AC", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "=")
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { label ->
                    Button(
                        onClick = {
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
                        },
                        modifier = Modifier.weight(1f).height(68.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) { Text(label, fontSize = 22.sp) }
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
