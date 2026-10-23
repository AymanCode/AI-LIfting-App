package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.agent.AgentMutationRepos
import com.ayman.ecolift.agent.FakeReadyEngine
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.test.runVmTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VisibleChatSendTest {
    @Test
    fun send_uses_the_visible_text_when_the_model_input_is_empty() = runVmTest {
        val vm = OrchestratorViewModel(
            AgentMutationRepos(listOf(Exercise(1, "Bench Press", "Chest"))),
            FakeReadyEngine(),
        )
        vm.sendMessage("row 5x5")
        assertEquals("row 5x5", vm.sessionMessages.value.last { it.isUser }.text)
    }
}
