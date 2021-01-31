package entity

import kotlin.random.Random

data class ConfirmationState(val action: Confirm, val key: Int)
fun confirmationStateOf(action: Confirm) = ConfirmationState(action, Random.nextInt())

sealed class Confirm {
    data class Invite(val teamId: Int) : Confirm()
    object Leave : Confirm()
    object StartEvent : Confirm()
    object StopEvent : Confirm()
}

enum class FinishedState {
    FINISHED,
    UNFINISHED,
    EITHER
}
