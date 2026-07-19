package com.wxn.reader.data.model.backup

/** 交互态用户决策(HashPartial / ConfirmRestore)。 */
sealed interface UserDecision {
    data object Continue : UserDecision
    data object Cancel : UserDecision
}
