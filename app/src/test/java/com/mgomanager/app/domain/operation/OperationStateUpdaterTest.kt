package com.mgomanager.app.domain.operation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationStateUpdaterTest {

    @Test
    fun `start and complete steps update overall state`() {
        val updater = OperationStateUpdater()
        val steps = listOf(
            OperationStep(OperationStepId.PRECHECK),
            OperationStep(OperationStepId.FINALIZE)
        )
        val initial = OperationState.initial(OperationType.BACKUP, steps)

        val running = updater.startOperation(initial, "session-1")
        assertEquals(OperationOverallStatus.RUNNING, running.overall)
        assertTrue(running.canCancel)

        val precheckRunning = updater.startStep(running, OperationStepId.PRECHECK)
        assertEquals(OperationStepStatus.RUNNING, precheckRunning.steps[0].status)

        val precheckDone = updater.completeStep(precheckRunning, OperationStepId.PRECHECK)
        assertEquals(OperationStepStatus.SUCCESS, precheckDone.steps[0].status)

        val final = updater.finalize(precheckDone, OperationOverallStatus.SUCCESS)
        assertEquals(OperationOverallStatus.SUCCESS, final.overall)
        assertFalse(final.canCancel)
    }
}
