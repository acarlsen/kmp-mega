package dev.carlsen.mega.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.carlsen.mega.model.Node

@Composable
fun Application() {
    Scaffold { innerPadding ->
        MegaClientScreen(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            viewModel = MegaClientViewModel(),
        )
    }
}

@Composable
fun MegaClientScreen(
    modifier: Modifier = Modifier,
    viewModel: MegaClientViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    MegaClientUi(
        modifier = modifier,
        uiState = uiState,
        onClickLogin = viewModel::onLogin,
        onClickLogout = viewModel::onLogout,
        onClickNode = viewModel::onClickNode,
    )
}

@Composable
fun MegaClientUi(
    modifier: Modifier = Modifier,
    uiState: MegaClientUiState,
    onClickLogin: (String, String) -> Unit,
    onClickLogout: () -> Unit,
    onClickNode: (Node) -> Unit,
) {
    val email = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }

    Column {
        if (uiState.isLoading) {
            Box(
                modifier = modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text("Loading...", style = MaterialTheme.typography.titleMedium)
                }
            }
            return
        }

        uiState.error?.let { error ->
            Column(
                modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Error occurred", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.padding(8.dp))
                Text(error)
                Spacer(modifier = Modifier.padding(8.dp))
                HorizontalDivider()
            }
        }

        if (uiState.requiresLogin) {
            Column(
                modifier = modifier.fillMaxSize().padding(32.dp),
            ) {
                Text("Login required", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.padding(8.dp))
                TextField(email.value, onValueChange = { email.value = it }, label = { Text("Email") })
                Spacer(modifier = Modifier.padding(4.dp))
                TextField(password.value, onValueChange = { password.value = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
                Spacer(modifier = Modifier.padding(8.dp))
                ElevatedButton(onClick = {
                    onClickLogin(email.value, password.value)
                }) {
                    Text("Login")
                }
            }
            return
        } else {
            Column(
                modifier = modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Logged in", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.padding(8.dp))
                ElevatedButton(onClick = {
                    onClickLogout()
                }) {
                    Text("Logout")
                }
            }
        }

        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = "MEGA Client",
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .weight(1f),
            ) {
                uiState.parent?.let { parent ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onClickNode(parent) }.padding(16.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "up")
                        }
                    }
                }
                items(uiState.data) { node ->
                    FileListItem(node = node, onClick = onClickNode)
                }
            }
        }
    }
}

@Composable
@OptIn(kotlin.time.ExperimentalTime::class)
fun FileListItem(
    node: Node,
    onClick: (Node) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick(node) }.padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(node.name)
            Text(node.timestamp.toString(), style = MaterialTheme.typography.labelSmall)
            if (node.isFolder) {
                Text("Folder", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Size: ${node.size}", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(node.hash)
    }
}


