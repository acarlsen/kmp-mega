package dev.carlsen.mega.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.carlsen.mega.Mega
import dev.carlsen.mega.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MegaClientViewModel: ViewModel() {

    private val _uiState = MutableStateFlow(MegaClientUiState())
    val uiState: StateFlow<MegaClientUiState> = _uiState.asStateFlow()

    private var megaClient: Mega? = null

    fun onLogin(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val newClient = Mega()
                newClient.login(email, password)
                var files = newClient.getFileSystem().root?.getChildren() ?: listOf()
                files = files.sortedBy { it.name }.sortedBy { it.isFile }
                megaClient = newClient
                _uiState.update { it.copy(isLoading = false, requiresLogin = false, data = files, parent = null, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toString()) }
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                megaClient?.logout()
                megaClient = null
                _uiState.update { it.copy(requiresLogin = true, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.toString()) }
            }
        }
    }

    fun onClickNode(node: Node) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (node.isFile) return@launch

                megaClient?.let { client ->
                    _uiState.update { it.copy(isLoading = true) }
                    var files = client.getNodeByHash(node.hash)?.getChildren() ?: listOf()
                    files = files.sortedBy { it.name }.sortedBy { it.isFile }
                    _uiState.update { it.copy(isLoading = false, data = files, parent = node.parent, error = null) }
                } ?: run {
                    _uiState.update { it.copy(requiresLogin = true, error = null) }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toString()) }
            }
        }
    }
}

data class MegaClientUiState(
    val isLoading: Boolean = false,
    val requiresLogin: Boolean = true,
    val error: String? = null,
    val parent: Node? = null,
    val data: List<Node> = listOf()
)