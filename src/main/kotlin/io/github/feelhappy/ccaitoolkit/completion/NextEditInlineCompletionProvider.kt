package io.github.feelhappy.ccaitoolkit.completion

import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.github.feelhappy.ccaitoolkit.settings.CodemossSettingsService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Gray-text completion at the caret. Uses a warm messages-API process, not the chat agent.
 */
class NextEditInlineCompletionProvider : DebouncedInlineCompletionProvider() {

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("cc-ai-toolkit.next-edit")

    private val settingsLock = Any()
    private var loadedGeneration = -1
    private var enabled = false
    private var showWithLookup = true
    private var disabledLanguages = CodemossSettingsService.DEFAULT_NEXT_EDIT_DISABLED_LANGUAGES

    override suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration = 200.milliseconds

    private val typingUpdates = InlineCompletionSuggestionUpdateManager.Default()

    override val suggestionUpdateManager: InlineCompletionSuggestionUpdateManager =
        object : InlineCompletionSuggestionUpdateManager {
            override fun update(
                event: InlineCompletionEvent,
                variant: InlineCompletionVariant.Snapshot,
            ): UpdateResult {
                if (event is InlineCompletionEvent.InlineLookupEvent) {
                    refreshSettings()
                    if (!showWithLookup && event is InlineCompletionEvent.LookupChange) {
                        return UpdateResult.Invalidated
                    }
                    return UpdateResult.Same
                }
                return typingUpdates.update(event, variant)
            }

            override fun updateWhileNoVariants(event: InlineCompletionEvent): Boolean {
                refreshSettings()
                if (!showWithLookup && event is InlineCompletionEvent.LookupChange) {
                    return false
                }
                return typingUpdates.updateWhileNoVariants(event)
            }
        }

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        refreshSettings()
        if (!enabled) {
            return false
        }
        val editor = editorOf(event) ?: return false
        if (editor.selectionModel.hasSelection()) {
            return false
        }
        if (!showWithLookup && LookupManager.getActiveLookup(editor) != null) {
            return false
        }
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
        if (file.fileType.isBinary) {
            return false
        }
        return !isDisabledLanguage(file)
    }

    override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion {
        refreshSettings()
        if (!enabled || request.startOffset != request.endOffset) {
            return InlineCompletionSuggestion.Empty
        }
        val input = ReadAction.compute<CodePredictionClient.PredictionInput?, RuntimeException> {
            buildInput(request.document, request.endOffset, request.file)
        } ?: return InlineCompletionSuggestion.Empty
        val client = ApplicationManager.getApplication().getService(CodePredictionClient::class.java)
            ?: return InlineCompletionSuggestion.Empty
        return InlineCompletionSingleSuggestion.build(UserDataHolderBase(), flow {
            coroutineScope {
                val incoming = Channel<String>(Channel.UNLIMITED)
                val producer = launch(Dispatchers.IO) {
                    try {
                        runInterruptible {
                            client.stream(input) { delta ->
                                incoming.trySend(delta)
                            }
                        }
                    } finally {
                        incoming.close()
                    }
                }
                try {
                    for (delta in incoming) {
                        emit(InlineCompletionGrayTextElement(delta))
                    }
                } finally {
                    producer.cancel()
                }
            }
        })
    }

    private fun editorOf(event: InlineCompletionEvent): Editor? {
        return when (event) {
            is InlineCompletionEvent.DocumentChange -> event.editor
            is InlineCompletionEvent.DirectCall -> event.editor
            is InlineCompletionEvent.LookupChange -> event.editor
            else -> null
        }
    }

    private fun buildInput(document: Document, offset: Int, psiFile: PsiFile?): CodePredictionClient.PredictionInput? {
        val safeOffset = offset.coerceIn(0, document.textLength)
        val text = document.immutableCharSequence
        var start = (safeOffset - PREFIX_CHARS).coerceAtLeast(0)
        if (start > 0) {
            val newline = indexOfNewline(text, start, safeOffset)
            if (newline >= start) {
                start = newline + 1
            }
        }
        val suffixEnd = (safeOffset + SUFFIX_CHARS).coerceAtMost(text.length)
        val prefix = text.subSequence(start, safeOffset).toString()
        if (prefix.isBlank()) {
            return null
        }
        val file = psiFile?.virtualFile
        val fileName = file?.name ?: ""
        val language = psiFile?.language?.id ?: file?.fileType?.name ?: ""
        return CodePredictionClient.PredictionInput(
            prefix,
            text.subSequence(safeOffset, suffixEnd).toString(),
            fileName,
            language,
        )
    }

    private fun indexOfNewline(text: CharSequence, start: Int, end: Int): Int {
        var index = start
        while (index < end) {
            if (text[index] == '\n') {
                return index
            }
            index++
        }
        return -1
    }

    private fun isDisabledLanguage(file: VirtualFile): Boolean {
        val tokens = disabledLanguages.split(',')
            .map { it.trim().lowercase().removePrefix(".") }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return false
        }
        val extension = file.extension?.lowercase().orEmpty()
        val typeName = file.fileType.name.lowercase()
        val compactType = typeName.replace("_", "")
        return tokens.any { token ->
            token == extension || token == typeName || token == compactType
        }
    }

    private fun refreshSettings() {
        val generation = CodemossSettingsService.getNextEditSettingsGeneration()
        synchronized(settingsLock) {
            if (generation == loadedGeneration) {
                return
            }
            val settings = CodemossSettingsService()
            try {
                enabled = settings.nextEditEnabled
                showWithLookup = settings.nextEditShowWithLookup
                disabledLanguages = settings.nextEditDisabledLanguages
            } catch (_: Exception) {
                enabled = false
            }
            loadedGeneration = generation
        }
    }

    private companion object {
        const val PREFIX_CHARS = 400
        const val SUFFIX_CHARS = 200
    }
}
