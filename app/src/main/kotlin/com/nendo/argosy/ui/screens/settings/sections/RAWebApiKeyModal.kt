package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyAction
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyEditor
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyStatus
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * Entry modal for the RetroAchievements web API key. Pure visuals: gamepad input reaches it
 * through the settings modal router, touch through its own buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RAWebApiKeyModal(
    editor: RAWebApiKeyEditor,
    onDraftChange: (String) -> Unit,
    onAction: (RAWebApiKeyAction) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(editor.draft, TextRange(editor.draft.length))) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val fieldFocused = editor.row == TextEntryRow.FIELD
    val validating = editor.status == RAWebApiKeyStatus.VALIDATING
    val canSave = editor.draft.isNotBlank() && !validating

    LaunchedEffect(editor.draft) {
        if (fieldValue.text != editor.draft) fieldValue = TextFieldValue(editor.draft, TextRange(editor.draft.length))
    }

    LaunchedEffect(fieldFocused) {
        if (fieldFocused) focusRequester.requestFocus() else focusManager.clearFocus()
    }

    Modal(title = stringResource(R.string.settings_ra_web_api_key_modal_title), onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.settings_ra_web_api_key_modal_message),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { next ->
                val changed = next.text != fieldValue.text
                fieldValue = next
                if (changed) onDraftChange(next.text)
            },
            label = { Text(stringResource(R.string.settings_ra_web_api_key_field_label)) },
            singleLine = true,
            enabled = !validating,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(Dimens.radiusMd),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onAction(RAWebApiKeyAction.SAVE) })
        )
        statusMessageRes(editor.status)?.let { messageRes ->
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (validating) theme.textDim else MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            editor.actions.forEachIndexed { index, action ->
                ModalActionButton(
                    label = stringResource(labelRes(action, validating)),
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = editor.row == TextEntryRow.BUTTONS && editor.buttonIndex == index,
                    onClick = { onAction(action) },
                    enabled = !validating && (action != RAWebApiKeyAction.SAVE || canSave)
                )
            }
        }
    }
}

@StringRes
private fun labelRes(action: RAWebApiKeyAction, validating: Boolean): Int = when (action) {
    RAWebApiKeyAction.PASTE -> R.string.settings_ra_web_api_key_paste
    RAWebApiKeyAction.CLEAR -> R.string.settings_ra_web_api_key_clear
    RAWebApiKeyAction.CANCEL -> R.string.settings_ra_web_api_key_cancel
    RAWebApiKeyAction.SAVE -> if (validating) R.string.settings_ra_web_api_key_save_busy else R.string.settings_ra_web_api_key_save
}

@StringRes
private fun statusMessageRes(status: RAWebApiKeyStatus): Int? = when (status) {
    RAWebApiKeyStatus.IDLE -> null
    RAWebApiKeyStatus.VALIDATING -> R.string.settings_ra_web_api_key_status_validating
    RAWebApiKeyStatus.REJECTED -> R.string.settings_ra_web_api_key_status_rejected
    RAWebApiKeyStatus.UNREACHABLE -> R.string.settings_ra_web_api_key_status_unreachable
    RAWebApiKeyStatus.CLIPBOARD_EMPTY -> R.string.settings_ra_web_api_key_status_clipboard_empty
}
