package com.enil.logez.feature.exercises

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.common.equipmentLabel
import com.enil.logez.core.common.exerciseTypeLabel
import com.enil.logez.core.common.muscleGroupLabel
import com.enil.logez.core.designsystem.LocalImage
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.userSelectable
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExerciseEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CustomExerciseEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isEditMode) R.string.exercise_editor_title_edit else R.string.exercise_editor_title_create,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(96.dp)
                    .clickable { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    val mediaPath = uiState.mediaPath
                    if (mediaPath != null) {
                        LocalImage(
                            absolutePath = File(context.filesDir, mediaPath).absolutePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.md)),
                        )
                    } else {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = stringResource(R.string.exercise_editor_add_photo))
                    }
                }
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.exercise_editor_name)) },
                isError = uiState.nameError,
                supportingText = if (uiState.nameError) {
                    { Text(stringResource(R.string.exercise_editor_name_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )

            EnumDropdown(
                label = stringResource(R.string.exercise_editor_equipment),
                selectedLabel = equipmentLabel(uiState.equipment),
                options = Equipment.entries,
                optionLabel = ::equipmentLabel,
                onSelect = viewModel::onEquipmentChange,
                modifier = Modifier.padding(top = Spacing.md),
            )

            EnumDropdown(
                label = stringResource(R.string.exercise_editor_primary_muscle),
                selectedLabel = muscleGroupLabel(uiState.primaryMuscleGroup),
                options = MuscleGroup.entries,
                optionLabel = ::muscleGroupLabel,
                onSelect = viewModel::onPrimaryMuscleChange,
                modifier = Modifier.padding(top = Spacing.md),
            )

            Text(
                stringResource(R.string.exercise_editor_secondary_muscles),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Column {
                MuscleGroup.entries.filter { it != uiState.primaryMuscleGroup }.forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onSecondaryMuscleToggle(m) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = m in uiState.secondaryMuscleGroups, onCheckedChange = { viewModel.onSecondaryMuscleToggle(m) })
                        Text(muscleGroupLabel(m))
                    }
                }
            }

            EnumDropdown(
                label = stringResource(R.string.exercise_editor_type),
                selectedLabel = exerciseTypeLabel(uiState.exerciseType),
                options = ExerciseType.Companion.userSelectable,
                optionLabel = ::exerciseTypeLabel,
                onSelect = viewModel::onExerciseTypeChange,
                enabled = !uiState.isEditMode,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            if (uiState.isEditMode) {
                Text(
                    stringResource(R.string.exercise_editor_type_immutable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}
