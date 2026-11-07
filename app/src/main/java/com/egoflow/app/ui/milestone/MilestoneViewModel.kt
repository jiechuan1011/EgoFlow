package com.egoflow.app.ui.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egoflow.app.EgoFlowApp
import com.egoflow.app.data.repository.Milestone
import com.egoflow.app.data.repository.MilestoneRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MilestoneUiState(
    val milestones: List<Milestone> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingMilestone: Milestone? = null,
    val saved: Boolean = false
)

class MilestoneViewModel(
    private val milestoneRepository: MilestoneRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MilestoneUiState())
    val uiState: StateFlow<MilestoneUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            milestoneRepository.milestones.collect { milestones ->
                _uiState.update { it.copy(milestones = milestones) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingMilestone = null) }
    }

    fun showEditDialog(milestone: Milestone) {
        _uiState.update { it.copy(showAddDialog = true, editingMilestone = milestone) }
    }

    fun hideDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingMilestone = null) }
    }

    fun saveMilestone(
        title: String,
        date: String,
        type: String,
        note: String,
        time: String?
    ) {
        viewModelScope.launch {
            val editing = _uiState.value.editingMilestone
            if (editing != null) {
                milestoneRepository.update(editing.id, title, date, type, note, time)
            } else {
                milestoneRepository.add(title, date, type, note, time)
            }
            _uiState.update { it.copy(showAddDialog = false, editingMilestone = null, saved = true) }
        }
    }

    fun deleteMilestone(id: String) {
        viewModelScope.launch {
            milestoneRepository.remove(id)
        }
    }

    fun clearSavedFlag() {
        _uiState.update { it.copy(saved = false) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = EgoFlowApp.instance
            return MilestoneViewModel(
                milestoneRepository = app.milestoneRepository
            ) as T
        }
    }
}
