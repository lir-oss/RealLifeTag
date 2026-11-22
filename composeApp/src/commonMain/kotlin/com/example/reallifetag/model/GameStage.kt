package com.example.reallifetag.model

enum class GameStage {
    MAIN_MENU,   // Landing screen: Host vs Participate choice
    SETUP,       // Host game creation & rule setup
    JOIN_GAME,   // Participate / Join game with room code
    LOBBY,       // Lobby Waiting Room (share Lobby ID, see real joined players)
    HEAD_START,  // Pre-run head start period
    ACTIVE_GAME, // Live match gameplay
    GAME_OVER,   // Match end results screen
}
