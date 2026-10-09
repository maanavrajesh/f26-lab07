package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.MembershipTier;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.domain.TimeSlot;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins behavior of the shipped workflow that the original suite leaves open. */
class BookingWorkflowCharacterizationTest {

    private static final LocalDateTime MON_9AM = LocalDateTime.of(2026, 10, 5, 9, 0);
    private static final LocalDateTime MON_10AM = LocalDateTime.of(2026, 10, 5, 10, 0);
    private static final LocalDateTime MON_11AM = LocalDateTime.of(2026, 10, 5, 11, 0);

    @Test
    void recurringSubmitSkipsAnOccurrenceThatOnlyTouchesAnExistingBooking() {
        BookingStore store = new BookingStore();
        store.addRoom(new Room("C-200", "Cedar Hall", 20));
        store.addMember(new Member("m-1", "Ada", "ada@rooms.example.edu", MembershipTier.BASIC));
        store.addMember(new Member("m-2", "Grace", "grace@rooms.example.edu",
                MembershipTier.PREMIER));
        NotificationHub hub = new NotificationHub();
        BookingWorkflow workflow = new BookingWorkflow(store, new PriceCalculator(), hub);

        // Grace holds 10:00-11:00 in week one; a regular 9:00-10:00 request would be accepted.
        workflow.submit(BookingRequest.regular("C-200", "m-2", MON_10AM, MON_11AM, 2));

        BookingOutcome outcome = workflow.submit(
                BookingRequest.recurring("C-200", "m-1", MON_9AM, MON_10AM, 2, 6));

        assertTrue(outcome.isAccepted());
        assertEquals(1, outcome.getBooked().size());
        assertEquals(2, outcome.getBooked().get(0).getOccurrenceIndex());
        assertEquals(List.of(new TimeSlot(MON_9AM, MON_10AM)), outcome.getSkipped());
        assertEquals("series S-1: 1 booked, 1 skipped", outcome.getMessage());
        assertEquals(2, hub.getOutbox().size());
    }
}
