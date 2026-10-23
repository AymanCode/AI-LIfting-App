package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.Exercise
import kotlin.test.Test
import kotlin.test.assertEquals

class LearnedNamingTest {
    private val model = LearnedSplitsEngine.compute(WorkedExample.inputs())
    private val byId = WorkedExample.exercises.associateBy { it.id }

    @Test
    fun aNamedGroupKeepsItsNameAndUnnamedGroupsReadAsRegions() {
        assertEquals("Upper 1", learnedGroupLabel(model.group(WorkedExample.key(WorkedExample.UPPER))!!, byId))
        assertEquals("Chest · Arms", learnedGroupLabel(model.group(WorkedExample.key(WorkedExample.CHEST_ARMS))!!, byId))
        assertEquals("Legs", learnedGroupLabel(model.group(WorkedExample.key(WorkedExample.LEGS_HACK))!!, byId))
    }

    @Test
    fun unknownRegionsFallBackToGroup() {
        val group = model.group(WorkedExample.key(WorkedExample.LEGS_EXTENSION))!!
        val noRegions = group.members.associateWith { Exercise(it, "Lift $it", "") }
        assertEquals("Group", learnedGroupLabel(group, noRegions))
    }
}
