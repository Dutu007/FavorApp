package com.dutu007.favorapp

import com.dutu007.favorapp.data.AgreementItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNoticeTest {
    @Test
    fun repeatedTextCreatesDistinctEvents() {
        val first = AppUiState().withNotice("保存成功")
        val second = first.withNotice("保存成功")

        assertEquals(first.notice?.text, second.notice?.text)
        assertNotEquals(requireNotNull(first.notice).id, requireNotNull(second.notice).id)
    }

    @Test
    fun expiredEventCannotDismissNewCompletionOrItsUndo() {
        val first = AppUiState().withNotice("我们完成啦", undoAgreement = completedAgreement)
        val current = first.withNotice("我们完成啦", undoAgreement = completedAgreement)

        val afterOldTimeout = current.withoutNotice(requireNotNull(first.notice).id)

        assertEquals(current, afterOldTimeout)
        assertEquals(completedAgreement, afterOldTimeout.notice?.undoAgreement)
    }

    @Test
    fun dismissingMatchingCompletionRemovesItsUndoAndPreservesFormState() {
        val current = formState.withNotice("我们完成啦", undoAgreement = completedAgreement)

        val dismissed = current.withoutNotice(requireNotNull(current.notice).id)

        assertNull(dismissed.notice)
        assertEquals(formState, dismissed)
    }

    @Test
    fun ordinaryAndErrorEventsReplaceUndoWithoutClearingFormState() {
        val completion = formState.withNotice("我们完成啦", undoAgreement = completedAgreement)

        for (isError in listOf(false, true)) {
            val replacement = completion.withNotice("新的提示", isError = isError)
            val notice = requireNotNull(replacement.notice)

            assertEquals("新的提示", notice.text)
            assertEquals(isError, notice.isError)
            assertNotEquals(requireNotNull(completion.notice).id, notice.id)
            assertNull(notice.undoAgreement)
            assertEquals(formState.agreementConflictItem, replacement.agreementConflictItem)
            assertEquals(formState.agreementsError, replacement.agreementsError)
            assertEquals(formState.agreementSavedFormKey, replacement.agreementSavedFormKey)
            assertEquals(formState.agreementDeletedId, replacement.agreementDeletedId)
            assertEquals(formState.giftTitle, replacement.giftTitle)
            assertEquals(formState.giftNote, replacement.giftNote)
        }
    }

    private val completedAgreement = AgreementItem(
        id = "agreement-1",
        creatorId = "user-1",
        creatorName = "小明",
        title = "一起去看海",
        note = "看日出\n记得带相机",
        dueDate = null,
        completed = true,
        completedAt = "2026-10-08T08:00:00Z",
        createdAt = "2026-10-01T08:00:00Z",
        updatedAt = "2026-10-08T08:00:00Z",
        version = 2,
    )

    private val formState = AppUiState(
        agreementConflictItem = completedAgreement.copy(note = "对方更新了备注", version = 3),
        agreementsError = "请确认对方的最新修改",
        agreementSavedFormKey = "form-1",
        agreementDeletedId = "agreement-2",
        giftTitle = "保留标题草稿",
        giftNote = "保留多行草稿\n继续编辑",
    )
}
