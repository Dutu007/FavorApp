package com.dutu007.favorapp

import com.dutu007.favorapp.data.AgreementItem
import java.util.UUID

data class AppNotice(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isError: Boolean = false,
    val undoAgreement: AgreementItem? = null,
)

internal fun AppUiState.withNotice(
    text: String,
    isError: Boolean = false,
    undoAgreement: AgreementItem? = null,
): AppUiState = copy(notice = AppNotice(text = text, isError = isError, undoAgreement = undoAgreement))

internal fun AppUiState.withoutNotice(id: String): AppUiState =
    if (notice?.id == id) copy(notice = null) else this
