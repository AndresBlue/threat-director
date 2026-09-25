package com.andresblue.threatdirector.bloodmoon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class BloodMoonScheduleTest {
    @Test
    void deterministicIntervalsAlwaysStayWithinFourToSixNights() {
        Set<Integer> observed = new HashSet<>();
        for (long ordinal = 0; ordinal < 512; ordinal++) {
            int interval = BloodMoonSchedule.interval(0x1234ABCDL, ordinal);
            assertTrue(interval >= 4 && interval <= 6);
            observed.add(interval);
        }
        assertEquals(Set.of(4, 5, 6), observed);
    }

    @Test
    void activeWindowStartsAtDuskAndEndsWithTheScheduledDay() {
        long scheduledNight = 5L;
        long dayStart = scheduledNight * BloodMoonSchedule.TICKS_PER_DAY;

        assertTrue(BloodMoonSchedule.isAnnouncementWindow(scheduledNight, dayStart));
        assertTrue(BloodMoonSchedule.isAnnouncementWindow(
                scheduledNight, dayStart + BloodMoonSchedule.NIGHT_START_TICK - 1L));
        assertFalse(BloodMoonSchedule.isActive(
                scheduledNight, dayStart + BloodMoonSchedule.NIGHT_START_TICK - 1L));
        assertTrue(BloodMoonSchedule.isActive(
                scheduledNight, dayStart + BloodMoonSchedule.NIGHT_START_TICK));
        assertTrue(BloodMoonSchedule.isActive(
                scheduledNight, dayStart + BloodMoonSchedule.TICKS_PER_DAY - 1L));
        assertFalse(BloodMoonSchedule.isActive(
                scheduledNight, dayStart + BloodMoonSchedule.TICKS_PER_DAY));
    }

    @Test
    void calendarArithmeticHandlesNegativeTimeWithoutInvalidTicks() {
        assertEquals(-1L, BloodMoonSchedule.dayIndex(-1L));
        assertEquals(23_999L, BloodMoonSchedule.tickWithinDay(-1L));
    }

    @Test
    void messageSelectionIsStableAndBounded() {
        int selected = BloodMoonSchedule.messageIndex(99L, 7L, 6);
        assertEquals(selected, BloodMoonSchedule.messageIndex(99L, 7L, 6));
        assertTrue(selected >= 0 && selected < 6);
        assertThrows(IllegalArgumentException.class,
                () -> BloodMoonSchedule.messageIndex(0L, 0L, 0));
    }

    @Test
    void persistentStateKeepsTheEventAcrossReloadAndAdvancesByAnotherFourToSixNights() {
        BloodMoonPersistentState original = new BloodMoonPersistentState();
        original.initialize(7_331L, 20L, 6);
        long firstNight = original.nextNight();
        assertTrue(firstNight >= 24L && firstNight <= 26L);

        original.markAnnounced();
        NbtCompound saved = original.writeNbt(new NbtCompound());
        BloodMoonPersistentState loaded = BloodMoonPersistentState.fromNbt(saved);
        assertEquals(firstNight, loaded.nextNight());
        assertTrue(loaded.wasAnnounced());

        // A restart during the event must not roll the calendar forward.
        loaded.advancePast(firstNight, 6);
        assertEquals(firstNight, loaded.nextNight());

        loaded.advancePast(firstNight + 1L, 6);
        assertTrue(loaded.nextNight() - firstNight >= 4L);
        assertTrue(loaded.nextNight() - firstNight <= 6L);
        assertFalse(loaded.wasAnnounced());
    }

    @Test
    void shrinkingConfiguredOmenListRepairsPersistedMessageIndex() {
        BloodMoonPersistentState state = new BloodMoonPersistentState();
        state.initialize(123L, 10L, 64);

        state.initialize(123L, 10L, 1, 4, 6);

        assertEquals(0, state.messageIndex());
    }
}
