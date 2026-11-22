package com.example.reallifetag.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reallifetag.model.Player
import com.example.reallifetag.model.Role

// SECTION 12: GAME OVER / RESULTS SCREEN (Win vs Lose Page)
// Purpose: Dedicated match results screen displaying Victory / Defeat status,
// match statistics, player list summary, and play again / home buttons.
@Composable
fun GameOverResultsScreen(
    myDeviceId: String,
    winnerText: String,
    players: List<Player>,
    matchDurationMinutes: Int,
    onPlayAgain: () -> Unit,
    onBackToMain: () -> Unit
) {
    val myPlayer = players.find { it.id == myDeviceId || it.id == "6" }
    val myRole = myPlayer?.role ?: Role.RUNNER
    val isMyRoleRunner = myRole == Role.RUNNER
    val isMyPlayerTagged = myPlayer?.isTagged ?: false

    // Determine if You Won or Lost
    val isHuntersWin = winnerText.contains("HUNTERS WIN", ignoreCase = true)
    val isRunnersWin = winnerText.contains("RUNNERS WIN", ignoreCase = true)

    val didIWin = if (isMyRoleRunner) {
        isRunnersWin && !isMyPlayerTagged
    } else {
        isHuntersWin
    }

    val totalRunners = players.count { it.role == Role.RUNNER }
    val taggedRunners = players.count { it.role == Role.RUNNER && it.isTagged }
    val survivingRunners = totalRunners - taggedRunners

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // VICTORY / DEFEAT BADGE ICON
        Text(
            text = if (didIWin) "🏆" else "💀",
            fontSize = 72.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // TITLE HEADER
        Text(
            text = if (didIWin) "VICTORY!" else "DEFEAT!",
            color = if (didIWin) Color(0xFF00E676) else Color(0xFFFF5252),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 36.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = winnerText.ifBlank { if (didIWin) "You won the match!" else "You were eliminated!" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // YOUR MATCH PERFORMANCE CARD
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (didIWin) Color(0xFF1E3A2B) else Color(0xFF3A1E1E)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (didIWin) Color(0xFF00E676) else Color(0xFFFF5252)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "YOUR MATCH SUMMARY",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Role", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (isMyRoleRunner) "🏃 Runner" else "🎯 Catcher",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Status", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (isMyRoleRunner) {
                                if (isMyPlayerTagged) "Captured ❌" else "Survived ✅"
                            } else {
                                if (isHuntersWin) "Hunt Master 🏆" else "Time Expired ⏳"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // MATCH STATS BREAKDOWN CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "MATCH STATISTICS",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Duration", color = Color.LightGray, fontSize = 14.sp)
                    Text("$matchDurationMinutes min", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Runners Captured", color = Color.LightGray, fontSize = 14.sp)
                    Text("$taggedRunners / $totalRunners", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Runners Survived", color = Color.LightGray, fontSize = 14.sp)
                    Text("$survivingRunners / $totalRunners", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // PLAY AGAIN BUTTON - RETURNS TO LOBBY PAGE
        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (didIWin) Color(0xFF00E676) else Color(0xFFFF5252)
            )
        ) {
            Text(
                text = "🔄 Return to Lobby",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (didIWin) Color.Black else Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // MAIN MENU BUTTON
        Button(
            onClick = onBackToMain,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            Text("🏠 Back to Main Menu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
