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
import androidx.compose.material3.AlertDialog
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
import com.example.reallifetag.model.LobbyData

// SECTION 9: PUBLIC LOBBIES SCREEN
// Purpose: Screen listing active host lobbies for players to join.
@Composable
fun PublicLobbiesScreen(
    savedNickname: String,
    lobbiesList: List<LobbyData>,
    isLoading: Boolean,
    onJoinLobby: (String, String, (Boolean) -> Unit) -> Unit,
    onBackToMain: () -> Unit
) {
    var playerNickname by remember { mutableStateOf(savedNickname) }
    var isJoining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf("") }
    var selectedLobbyId by remember { mutableStateOf<String?>(null) }
    var selectedHostName by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        Text("PUBLIC LOBBIES", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Available Host Matches",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp
        )
        Text(
            "Select an active lobby hosted by another player to join.",
            color = Color.LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // NICKNAME FIELD
        OutlinedTextField(
            value = playerNickname,
            onValueChange = { playerNickname = it },
            label = { Text("Your Nickname", color = Color.Gray) },
            placeholder = { Text("e.g. Sam", color = Color.DarkGray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E676),
                unfocusedBorderColor = Color(0xFF444444),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF222222),
                unfocusedContainerColor = Color(0xFF222222)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (joinError.isNotEmpty()) {
            Text(joinError, color = Color(0xFFFF5252), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isLoading) {
            Text("Loading available lobbies...", color = Color.Gray, fontSize = 14.sp)
        } else if (lobbiesList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No active lobbies found.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ask a friend to host a new match!", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            lobbiesList.forEach { lobby ->
                val lobbyId = lobby.lobbyId
                val hostName = lobby.hostName.ifBlank { "Host" }
                val lobbyName = lobby.lobbyName.ifBlank { "$hostName's Lobby" }
                val playersCount = lobby.players.size

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lobbyName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Players in lobby: $playersCount", color = Color(0xFF00E676), fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                selectedLobbyId = lobbyId
                                selectedHostName = hostName
                                showJoinDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Join", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // CONFIRMATION DIALOG: "Would you like to join [HostName]'s lobby?"
        if (showJoinDialog && selectedLobbyId != null) {
            AlertDialog(
                onDismissRequest = { showJoinDialog = false },
                title = { Text("Join Lobby", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("Would you like to join $selectedHostName's lobby?", color = Color.LightGray) },
                confirmButton = {
                    Button(
                        onClick = {
                            showJoinDialog = false
                            val nick = playerNickname.trim().ifBlank { "Player" }
                            selectedLobbyId?.let { id ->
                                isJoining = true
                                joinError = ""
                                onJoinLobby(id, nick) { success ->
                                    isJoining = false
                                    if (!success) {
                                        joinError = "Failed to join $selectedHostName's lobby. Please try again."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Join", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJoinDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }
    }
}
