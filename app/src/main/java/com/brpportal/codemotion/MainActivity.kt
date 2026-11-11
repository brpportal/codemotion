package com.brpportal.codemotion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodeMotionApp() }
    }
}

enum class Screen { HOME, GALLERY, NEW_SCRIPT, PROJECT, MY_PROJECTS, EXPORT }

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
                    onMyProjects = { screen = Screen.MY_PROJECTS },
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
                        ProjectViewer(
                            project = p,
                            effects = effects,
                            onProjectChange = { project = it },
                            onExport = { screen = Screen.EXPORT },
                            onBack = { screen = Screen.HOME }
                        )
                    } else {
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            Button(onClick = { screen = Screen.HOME }) { Text("Back") }
                            Spacer(Modifier.height(12.dp))
                            Text("Koi project load nahi hai.")
                        }
                    }
                }

                Screen.MY_PROJECTS -> MyProjectsScreen(
                    onOpen = { loaded ->
                        project = loaded
                        projectError = null
                        screen = Screen.PROJECT
                    },
                    onBack = { screen = Screen.HOME }
                )

                Screen.EXPORT -> {
                    val p = project
                    if (p != null) {
                        ExportScreen(
                            project = p,
                            onBack = { screen = Screen.PROJECT }
                        )
                    } else {
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            Button(onClick = { screen = Screen.HOME }) { Text("Back") }
                            Spacer(Modifier.height(12.dp))
                            Text("Pehle project kholo, phir export karo.")
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
    onMyProjects: () -> Unit,
    error: String?
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "CodeMotion",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Code → script.md → Claude Code → project.json → editable animated video (MP4)",
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
        Spacer(Modifier.height(12.dp))
        Button(onClick = onMyProjects, modifier = Modifier.fillMaxWidth()) {
            Text("My Projects")
        }
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Color(0xFFB00020))
        }
    }
}

@Composable
fun MyProjectsScreen(
    onOpen: (Project) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(listInternalProjects(context)) }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text(
                "My Projects",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        if (files.isEmpty()) {
            Text("Abhi koi internal save nahi hai. Project khol kar \"Save internal\" dabao.")
        }
        files.forEach { file: File ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(file.nameWithoutExtension, fontWeight = FontWeight.Bold)
                    Text(
                        "${file.length() / 1024} KB",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Button(onClick = {
                            try {
                                onOpen(loadInternalProject(file))
                            } catch (e: Exception) {
                                msg = "Open error: ${e.message}"
                            }
                        }) { Text("Open") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            deleteInternalProject(file)
                            files = listInternalProjects(context)
                        }) { Text("Delete") }
                    }
                }
            }
        }
        if (msg != null) {
            Spacer(Modifier.height(10.dp))
            Text(msg!!, color = Color(0xFFB00020), style = MaterialTheme.typography.bodySmall)
        }
    }
}
