package com.wxn.reader.presentation.opds

import androidx.lifecycle.ViewModel
import com.wxn.reader.domain.use_case.opds.ValidateOpdsUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ValidateOpdsViewModel @Inject constructor(
    val validateUseCase: ValidateOpdsUrlUseCase
) : ViewModel()
