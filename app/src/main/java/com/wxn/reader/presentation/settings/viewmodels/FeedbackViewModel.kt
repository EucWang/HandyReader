package com.wxn.reader.presentation.settings.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.BuildConfig
import com.wxn.reader.data.remote.api.FeedbackApi
import com.wxn.reader.data.remote.dto.FeedbackRequest
import com.wxn.reader.data.remote.dto.FeedbackType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    application: Application,
    private val feedbackApi: FeedbackApi
) : AndroidViewModel(application) {

    sealed class SubmitState {
        object Idle : SubmitState()
        object Loading : SubmitState()
        data class Success(val message: String) : SubmitState()
        data class Error(val message: String) : SubmitState()
    }

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()
    private val _name = MutableStateFlow<String>("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _emailError = MutableStateFlow<Boolean>(false)
    val emailError: StateFlow<Boolean> = _emailError.asStateFlow()

    private val _email = MutableStateFlow<String>("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _type = MutableStateFlow<Int>(0)
    val type: StateFlow<Int> = _type.asStateFlow()

    private val _content = MutableStateFlow<String>("")
    val content: StateFlow<String> = _content.asStateFlow()


    fun setName(newName: String) {
        _name.value = newName
    }

    fun setEmail(newEmail: String) {
        _email.value = newEmail
        _emailError.value = !isValidEmail(newEmail)
    }

    fun setType(newType: Int) {
        _type.value = newType.coerceIn(0, 2)
    }

    fun setContent(newContent: String) {
        _content.value = newContent
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() &&
                android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidContent(content: String): Boolean {
        return content.length >= 10 && content.length <= 500
    }

    fun submit() {
        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val txtEmail = email.value
            val txtContent = content.value
            val txtName = name.value
            val targetType = when (type.value) {
                0 -> FeedbackType.BUG
                1 -> FeedbackType.FEATURE
                2 -> FeedbackType.OTHER
                else -> FeedbackType.BUG
            }

            // 本地验证
            if (!isValidEmail(txtEmail)) {
                _emailError.value = true
                _submitState.value = SubmitState.Error("Invalid email address")
                return@launch
            }

            if (!isValidContent(txtContent)) {
                _submitState.value = SubmitState.Error("Content must be 10-500 characters")
                return@launch
            }
            val request = FeedbackRequest(
                name = txtName,
                email = txtEmail,
                type = targetType,
                content = txtContent,
                appVersion = BuildConfig.VERSION_NAME,
                source = "android"
            )
            val result = feedbackApi.submitFeedback(request)
            if (result.isSuccess) {
                val response = result.getOrNull()
                _submitState.value = SubmitState.Success(
                    response?.message ?: "Feedback submitted successfully"
                )
            } else {
                val error = result.exceptionOrNull()
                _submitState.value = SubmitState.Error(
                    error?.message ?: "Submission failed"
                )
            }
        }
    }

    fun resetForm() {
        _name.value = ""
        _email.value = ""
        _emailError.value = false
        _type.value = 0
        _content.value = ""
        _submitState.value = SubmitState.Idle
    }
}