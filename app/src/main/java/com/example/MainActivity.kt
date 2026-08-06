package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ChessClockScreen
import com.example.ui.ChessGameScreen
import com.example.ui.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ChessGameViewModel
import com.example.viewmodel.ChessTimerViewModel

private enum class AppScreen { HOME, PLAY, CLOCK }

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }

        BackHandler(enabled = screen != AppScreen.HOME) { screen = AppScreen.HOME }

        when (screen) {
          AppScreen.HOME -> HomeScreen(
              onPlayChess = { screen = AppScreen.PLAY },
              onOpenClock = { screen = AppScreen.CLOCK },
              modifier = Modifier.fillMaxSize()
          )

          AppScreen.PLAY -> {
            val gameViewModel: ChessGameViewModel = viewModel()
            ChessGameScreen(
                viewModel = gameViewModel,
                modifier = Modifier.fillMaxSize(),
                onExit = { screen = AppScreen.HOME }
            )
          }

          AppScreen.CLOCK -> {
            val timerViewModel: ChessTimerViewModel = viewModel()
            ChessClockScreen(
                viewModel = timerViewModel,
                modifier = Modifier.fillMaxSize(),
                onExit = { screen = AppScreen.HOME }
            )
          }
        }
      }
    }
  }
}
