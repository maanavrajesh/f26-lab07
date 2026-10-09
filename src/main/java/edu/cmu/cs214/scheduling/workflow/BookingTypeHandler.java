package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.Room;

/**
 * The type-specific half of {@link BookingWorkflow}. The workflow does the
 * lookups every type shares, then hands off to the handler for the booking's
 * type.
 */
interface BookingTypeHandler {

    /**
     * Validates and writes a request whose room is already known to exist.
     *
     * @return an outcome naming every booking written and every slot passed over
     */
    BookingOutcome submit(BookingRequest request, Room room);

    /**
     * Releases a booking that exists and is not yet cancelled.
     *
     * @param roomName the room's display name, or its id when the room is gone
     * @param adminOverride set by callers acting with facilities authority
     * @return true when something was released
     */
    boolean cancel(Booking booking, String roomName, boolean adminOverride);

    /** What the holder owes for an existing booking, in dollars. */
    double priceOf(Booking booking);

    /** A one-line summary of an existing booking. */
    String describe(Booking booking, String roomName);
}
