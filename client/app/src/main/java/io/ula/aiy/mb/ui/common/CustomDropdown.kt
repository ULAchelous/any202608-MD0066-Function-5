package io.ula.aiy.mb.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDropdown(
    options: List<String>,
    selectedOption: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelect: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            readOnly = true,
            value = selectedOption,
            onValueChange = {},
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(20.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelect(option)
                        onExpandedChange(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
