package com.brpportal.codemotion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodeMotionApp() }
    }
}

enum class Screen { HOME, GALLERY, NEW_SCRIPT, PROJECT }

@Composable
fun CodeMotionApp() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val context = LocalContext.current
            var screen by remember { mutableStateOf(Screen.HOME) }
            val effects = remember { loadEffects(context) }
            val selected = remember { mutableStateListOf<String>() }
            var project by remember { mutableStateOf<Project?>(null) }
            var projectError by remember { mutableStateOf<String?>(null) }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.readText() ?: ""
                        project = parseProject(text)
                        projectError = null
                        screen = Screen.PROJECT
                    } catch (e: Exception) {
                        projectError = "Import error: ${e.message}"
                    }
                }
            }

            when (screen) {
                Screen.HOME -> HomeScreen(
                    onNewScript = { screen = Screen.NEW_SCRIPT },
                    onGallery = { screen = Screen.GALLERY },
                    onImport = {
                        projectError = null
                        importLauncher.launch(arrayOf("application/json"))
                    },
                    onDemo = {
                        try {
                            val text = context.assets.open("sample_project.json")
                                .bufferedReader().readText()
                            project = parseProject(text)
                            projectError = null
                            screen = Screen.PROJECT
                        } catch (e: Exception) {
                            projectError = "Demo load error: ${e.message}"
                        }
                    },
                    error = projectError
                )

                Screen.GALLERY -> EffectsGallery(
                    effects = effects,
                    selected = selected,
                    onBack = { screen = Screen.HOME }
                )

                Screen.NEW_SCRIPT -> NewScriptScreen(
                    effects = effects,
                    selectedIds = selected,
                    onBack = { screen = Screen.HOME }
                )

                Screen.PROJECT -> {
                    val p = project
                    if (p != null) {
                        ProjectViewer(project = p, onBack = { screen = Screen.HOME })
                    } else {
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            Button(onClick = { screen = Screen.HOME }) { Text("Back") }
                            Spacer(Modifier.height(12.dp))
                            Text("Koi project load nahi hai.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onNewScript: () -> Unit,
    onGallery: () -> Unit,
    onImport: () -> Unit,
    onDemo: () -> Unit,
    error: String?
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "CodeMotion",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Code → script.md → Claude Code → project.json → layered video",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onNewScript, modifier = Modifier.fillMaxWidth()) {
            Text("New Script")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
            Text("Effects Gallery")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Import Project (project.json)")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
            Text("Load Demo Project")
        }
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Color(0xFFB00020))
        }
    }
}
