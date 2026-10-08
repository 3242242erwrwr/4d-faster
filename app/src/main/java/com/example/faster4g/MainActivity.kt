package com.example.faster4g

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.sp
import com.example.faster4g.ui.theme.FASTER4GTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    var showPermissionDialog by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            Toast.makeText(this, "Ruxsatlar olindi!", Toast.LENGTH_SHORT).show()
            startLocationService()
        } else {
            showPermissionDialog = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FASTER4GTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartClick = { checkAndRequestPermissions() },
                        onStopClick = { stopLocationService() }
                    )

                    if (showPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { showPermissionDialog = false },
                            title = { Text("Ruxsatlar Zarur 📍") },
                            text = { Text("GPS va SMS oflayn funksiyalari ishlashi uchun ilovaga ruxsat bering.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showPermissionDialog = false
                                    requestAllPermissions()
                                }) {
                                    Text("Ruxsat Berish")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPermissionDialog = false }) {
                                    Text("Bekor qilish")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            startLocationService()
        } else {
            requestAllPermissions()
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationTrackerService::class.java).apply {
            action = LocationTrackerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "FASTER 4G Kuzatuvi Boshlandi 🚀", Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationService() {
        val intent = Intent(this, LocationTrackerService::class.java).apply {
            action = LocationTrackerService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "Kuzatuv To'xtatildi ⏹", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun GpsRadarMap(lat: Double, lon: Double, isRunning: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val center = center
                drawCircle(color = Color(0xFF2E7D32).copy(alpha = 0.3f), radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                drawCircle(color = Color(0xFF2E7D32).copy(alpha = 0.2f), radius = size.minDimension / 4, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))

                // Real position (Center)
                drawCircle(color = Color.Blue, radius = 9f, center = center)

                // 4 Virtual Surrounding points (North, South, East, West)
                if (isRunning) {
                    drawCircle(color = Color.Red, radius = 6f, center = center.copy(y = center.y - 45f)) // North
                    drawCircle(color = Color.Red, radius = 6f, center = center.copy(y = center.y + 45f)) // South
                    drawCircle(color = Color.Red, radius = 6f, center = center.copy(x = center.x + 45f)) // East
                    drawCircle(color = Color.Red, radius = 6f, center = center.copy(x = center.x - 45f)) // West
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(text = "🗺️ 5-Nuqtali GPS Radar (5 km)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(text = "🔵 Real nuqta | 🔴 4 ta virtual atrof-nuqta", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    var isRunning by remember { mutableStateOf(LocationTrackerService.isRunning) }
    var lat by remember { mutableStateOf(LocationTrackerService.currentLatitude) }
    var lon by remember { mutableStateOf(LocationTrackerService.currentLongitude) }
    var unsynced by remember { mutableStateOf(LocationTrackerService.unsyncedCount) }
    var online by remember { mutableStateOf(LocationTrackerService.isOnline) }
    var smsOrder by remember { mutableStateOf(SmsReceiver.lastIncomingOrder) }

    LaunchedEffect(Unit) {
        while (true) {
            isRunning = LocationTrackerService.isRunning
            lat = LocationTrackerService.currentLatitude
            lon = LocationTrackerService.currentLongitude
            unsynced = LocationTrackerService.unsyncedCount
            online = LocationTrackerService.isOnline
            smsOrder = SmsReceiver.lastIncomingOrder
            delay(1000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "FASTER 4G 🚖",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "4-Nuqtali GPS Radar & Oflayn Sinkronizatsiya",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Radar Map Widget
        GpsRadarMap(lat = lat, lon = lon, isRunning = isRunning)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "GPS Xizmati:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) Color.Green else Color.Red)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "Faol" else "To'xtatilgan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isRunning) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Kenglik / Uzunlik:", fontSize = 13.sp)
                    Text(text = "%.4f, %.4f".format(lat, lon), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Keshdagi nuqtalar (1 real + 3 virtual):", fontSize = 13.sp)
                    Text(
                        text = "$unsynced ta",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (unsynced > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Test instructions card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = "🧪 Qanday sinash mumkin?\n1. 'Start' tugmasini bosing.\n2. Yuqoridagi radarda 🔵 markaziy real nuqta va 🔴 3 ta virtual atrof-nuqta paydo bo'ladi.\n3. Internetni o'chirsangiz keshda nuqtalar yig'iladi, yoqsangiz sinxronlashadi!",
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                lineHeight = 16.sp
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(text = "🚀 Kuzatuvni Boshlash (Start)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "⏹ To'xtatish (Stop)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
