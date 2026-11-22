package com.example.reallifetag.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reallifetag.ui.components.RuleStepper
import com.example.reallifetag.ui.components.RunningManLogo

// SECTION 10: HOST GAME SETUP SCREEN
// Purpose: Screen for host to configure rules, zone size, and lobby capacity.
@Composable
fun SetupHomeScreen(
    savedNickname: String,
    isTestMatchMode: Boolean = false,
    matchDurationMinutes: Int,
    onMatchDurationChange: (Int) -> Unit,
    headStartMinutes: Int,
    onHeadStartChange: (Int) -> Unit,
    pingIntervalMinutes: Int,
    onPingIntervalChange: (Int) -> Unit,
    lobbyCapacity: Int,
    onLobbyCapacityChange: (Int) -> Unit,
    hunterCount: Int,
    onHunterCountChange: (Int) -> Unit,
    runnerCount: Int,
    onRunnerCountChange: (Int) -> Unit,
    zoneSizeMeters: Int,
    onZoneSizeChange: (Int) -> Unit,
    isZoneShrinkEnabled: Boolean,
    onZoneShrinkToggle: (Boolean) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCreateLobby: (String) -> Unit,
    onBackToMain: () -> Unit
) {
    var hostNickname by remember { mutableStateOf(savedNickname) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(onClick = onBackToMain) {
                Text("← Back to Main Menu", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        RunningManLogo(sizeDp = 72)

        Spacer(modifier = Modifier.height(12.dp))

        Text("REAL LIFE TAG", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isTestMatchMode) "Configure Test Match" else "Create New Match",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp
        )
        Text(
            text = if (isTestMatchMode)
                "Set host rules, zone size, and player ratios for your solo test run."
            else
                "Configure game rules, zone size, and player capacity for your match.",
            color = Color.LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // HOST NICKNAME FIELD
        OutlinedTextField(
            value = hostNickname,
            onValueChange = { hostNickname = it },
            label = { Text("Your Host Nickname", color = Color.Gray) },
            placeholder = { Text("e.g. Alex", color = Color.DarkGray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFD32F2F),
                unfocusedBorderColor = Color(0xFF444444),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF222222),
                unfocusedContainerColor = Color(0xFF222222)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2211)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📍 GPS Permission Required", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Enable location access to show the real map centered on your position.", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant GPS Access", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("ZONE & LOBBY CONFIGURATION", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // INITIAL ZONE SIZE CONTROL
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📍 Initial Zone Size", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Starting boundary radius in meters", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(200 to "200m", 350 to "350m", 500 to "500m", 800 to "800m")
                    options.forEach { (sizeVal, label) ->
                        Button(
                            onClick = { onZoneSizeChange(sizeVal) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (zoneSizeMeters == sizeVal) Color(0xFFD32F2F) else Color(0xFF333333)
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ZONE SHRINK TOGGLE CONTROL
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🌪️ Shrinking Boundary Zone", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Shrinks 2 times (each time by 15%)", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onZoneShrinkToggle(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isZoneShrinkEnabled) Color(0xFFD32F2F) else Color(0xFF333333)
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("ON (2x Shrinks)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Button(
                        onClick = { onZoneShrinkToggle(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isZoneShrinkEnabled) Color(0xFFD32F2F) else Color(0xFF333333)
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("OFF (Fixed Zone)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // LOBBY CAPACITY
        RuleStepper(
            title = "👥 Lobby Player Capacity",
            subtitle = "Maximum players allowed in match",
            valueText = "$lobbyCapacity Players",
            onDecrease = { onLobbyCapacityChange((lobbyCapacity - 1).coerceAtLeast(2)) },
            onIncrease = { onLobbyCapacityChange((lobbyCapacity + 1).coerceAtMost(20)) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("ROLE RATIOS (Randomly Assigned)", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // CATCHERS RATIO
        RuleStepper(
            title = "🎯 Catchers Ratio",
            subtitle = "Number of catchers randomly chosen",
            valueText = "$hunterCount Catchers",
            onDecrease = { onHunterCountChange(hunterCount - 1) },
            onIncrease = { onHunterCountChange(hunterCount + 1) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // RUNNERS RATIO
        RuleStepper(
            title = "🏃‍♂️ Runners Ratio",
            subtitle = "Number of runners randomly chosen",
            valueText = "$runnerCount Runners",
            onDecrease = { onRunnerCountChange(runnerCount - 1) },
            onIncrease = { onRunnerCountChange(runnerCount + 1) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("GAME TIMERS & RULES", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // MATCH DURATION
        RuleStepper(
            title = "⏱️ Match Duration",
            subtitle = "Total game time before time expires",
            valueText = "$matchDurationMinutes min",
            onDecrease = { onMatchDurationChange((matchDurationMinutes - 5).coerceAtLeast(5)) },
            onIncrease = { onMatchDurationChange((matchDurationMinutes + 5).coerceAtMost(120)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // PRE-RUN / HEAD START DURATION
        RuleStepper(
            title = "🏃 Pre-Run Head Start",
            subtitle = "Time runners get to scatter before catchers start",
            valueText = "$headStartMinutes min",
            onDecrease = { onHeadStartChange((headStartMinutes - 1).coerceAtLeast(0)) },
            onIncrease = { onHeadStartChange((headStartMinutes + 1).coerceAtMost(15)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // PING INTERVAL CONTROL
        RuleStepper(
            title = "📡 Ping Interval",
            subtitle = "Time between runner location updates",
            valueText = "$pingIntervalMinutes min",
            onDecrease = { onPingIntervalChange((pingIntervalMinutes - 1).coerceAtLeast(1)) },
            onIncrease = { onPingIntervalChange((pingIntervalMinutes + 1).coerceAtMost(15)) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                val nick = hostNickname.trim().ifBlank { if (isTestMatchMode) "Tester" else "Host" }
                onCreateLobby(nick)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTestMatchMode) Color(0xFFFF9800) else Color(0xFFD32F2F)
            )
        ) {
            Text(
                text = if (isTestMatchMode) "🧪 Launch Solo Test Match" else "🏠 Open Lobby Waiting Room",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTestMatchMode) Color.Black else Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
