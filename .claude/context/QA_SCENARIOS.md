# QA Scenarios

- **Date:** 2026-10-27
- **Planned against:** `main` @ `256ae95` + uncommitted changes
- **Focus:** Full acceptance
- **Scenarios:** 20 (High 17, Medium 2, Low 1)

## Accounts
- **Receptionist:** `reception` / `reception123` (seed data)
- **Veterinarian A:** `vet.ana` / `vet123`, Dr. Ana Torres, cost 20.00 (seed data)
- **Veterinarian B:** `vet.luis` / `vet123`, Dr. Luis Gomez, cost 30.00 (seed data)
- **Owner 1:** phone `555-0101` / `qaowner1`, "QA P1 Owner One", pets "QA Rex" (Dog, Labrador Retriever) and "QA Mia" (Cat, Siamese) (created in SC-01, SC-02)
- **Owner 2:** phone `555-0202` / `qaowner2`, "QA P1 Owner Two", pet "QA Bolt" (Dog, Bulldog) (created in SC-01)
- **Veterinarian C:** `qa.vet` / `qavet123`, "QA P1 Vet" (created in SC-04)

Owners sign in with their phone number as the username (the backend creates the owner account with the phone as username).

Services (seed): General consultation 30 min 40.00; Grooming 60 min 35.00; Vaccination 15 min 25.00; Hospitalization 240 min 150.00.

## Acceptance Criteria
- **AC-1:** Reception can register an owner with pets and a veterinarian with a weekly schedule; invalid data (missing name or phone, breed not matching species, overlapping schedule blocks) is rejected with a clear message: SC-01, SC-02, SC-03, SC-04, SC-05
- **AC-2:** The breed picker only offers breeds of the selected species: SC-02
- **AC-3:** An owner sees only free slots for the chosen service, veterinarian and date, and the slots refresh when any of them change: SC-06, SC-07
- **AC-4:** An owner can reserve and confirm a slot; an unconfirmed reservation expires and the slot becomes free again: SC-07, SC-08 (expiry needs a 10-minute wait, reservation TTL `PT10M`; SC-08 is Low priority for that reason)
- **AC-5:** Two bookings for the same veterinarian and overlapping time cannot both succeed: SC-09
- **AC-6:** A walk-in booked by reception is scheduled immediately and records reception as the booker: SC-10
- **AC-7:** Each status transition in the table works for the listed roles, and every other transition is rejected: SC-07, SC-11, SC-12, SC-13, SC-14, SC-15, SC-16, SC-17
- **AC-8:** Cancelling without a reason is rejected: SC-12, SC-13
- **AC-9:** Rescheduling or reassigning marks the original as rescheduled and creates a linked scheduled appointment: SC-14
- **AC-10:** A payment is accepted only for a finished appointment and only for its exact total; a second payment is rejected: SC-15
- **AC-11:** A finished appointment closed without payment appears as a debt: SC-16
- **AC-12:** An owner sees all their appointments across pets and days with estimated duration and cannot see other owners' data: SC-18, SC-19
- **AC-13:** Reception sees all ongoing appointments with cancel and payment actions, and can open a pet's service history: SC-17, SC-20
- **AC-14:** Each status shows a consistent badge, and only allowed actions are offered: SC-11, SC-15, SC-16, SC-17
- **AC-15:** The backend builds and its tests pass; the frontend builds and lints cleanly: **Not in browser**, build/test/lint checks (`mvnw verify`, `npm run lint`, `npm run build`)

## Scenarios

### SC-01: Reception registers two owners, one with a first pet

- **Covers:** AC-1
- **Priority:** High
- **Role / Route:** Receptionist on `/reception/owners`
- **Preconditions:** signed out, fresh backend

#### Steps
1. Open `/login`, sign in with `reception` / `reception123` → reception area loads, header shows Reception navigation (Owners, Veterinarians, Appointments, Debts).
2. Open Owners → "Register an owner" form with Name, Phone, Password (min. 6 characters) and "First pet (optional)".
3. Fill Name "QA P1 Owner One", Phone `555-0101`, Password `qaowner1`, Pet name "QA Rex", Species Dog, Breed Labrador Retriever; click "Register owner" → owner appears in the list (or its page opens) with pet QA Rex.
4. Register "QA P1 Owner Two", `555-0202`, `qaowner2`, pet "QA Bolt", Dog, Bulldog → owner listed.
5. Type `555-0202` in "Search by name or phone" → only QA P1 Owner Two is shown.

#### Evidence
- Screenshot at step 3 (owner with pet)

### SC-02: Reception adds a cat to an existing owner; breed picker follows species

- **Covers:** AC-1, AC-2
- **Priority:** High
- **Role / Route:** Receptionist on `/reception/owners/[id]`
- **Preconditions:** Owner One from SC-01

#### Steps
1. Sign in as `reception`, open Owners, open QA P1 Owner One → owner page with "Add a pet" form.
2. Before choosing a species, open Breed → it shows "Select a species first" and offers no breeds.
3. Choose Species Cat → Breed offers exactly Siamese, Persian, Maine Coon, Bengal, Mixed breed (no dog breeds).
4. Choose Species Dog → Breed offers exactly Labrador Retriever, German Shepherd, Golden Retriever, Bulldog, Mixed breed, and any previously chosen cat breed is cleared.
5. Choose Cat again, Breed Siamese, Pet name "QA Mia", click "Add pet" → QA Mia (Cat, Siamese) appears in Pets.

#### Evidence
- Screenshot at step 3 and step 4

### SC-03: Owner registration rejects missing fields and duplicate phone

- **Covers:** AC-1
- **Priority:** High
- **Role / Route:** Receptionist on `/reception/owners`
- **Preconditions:** SC-01 done

#### Steps
1. Sign in as `reception`, open Owners.
2. Leave Name empty, fill Phone `555-0303`, Password `qaowner3`, click "Register owner" → rejected with a clear message on Name; no owner created.
3. Fill Name "QA P1 Owner Three", clear Phone, submit → rejected with a clear message on Phone.
4. Fill Phone `555-0101` (already used) → rejected with "This phone number is already registered."
5. Search `555-0303` → "No owners match your search." (nothing partially created).

#### Evidence
- Screenshot at steps 2 and 4

### SC-04: Reception registers a veterinarian with a weekly schedule

- **Covers:** AC-1
- **Priority:** High
- **Role / Route:** Receptionist on `/reception/veterinarians`
- **Preconditions:** signed out

#### Steps
1. Sign in as `reception`, open Veterinarians → list shows Dr. Ana Torres and Dr. Luis Gomez with schedules, and a "Register a veterinarian" form.
2. Fill Name "QA P1 Vet", Specialization "Dermatology", Cost per service `15.00`, Username `qa.vet`, Password `qavet123`.
3. Set block 1 Monday 09:00–12:00; click "Add block", set Monday 13:00–17:00 → two blocks visible.
4. Click "Register veterinarian" → QA P1 Vet listed with both Monday blocks and cost 15.00.
5. Sign out, sign in as `qa.vet` / `qavet123` → vet area "My assigned appointments" shows "You have no assigned appointments."

#### Evidence
- Screenshot at step 4

### SC-05: Veterinarian registration rejects invalid schedule and data

- **Covers:** AC-1
- **Priority:** High
- **Role / Route:** Receptionist on `/reception/veterinarians`
- **Preconditions:** SC-04 done

#### Steps
1. Sign in as `reception`, open Veterinarians; fill Name "QA P1 Vet Bad", Specialization "X", Cost `10.00`, Username `qa.vet.bad`, Password `qavet123`.
2. Set Monday 09:00–12:00 and add Monday 11:00–14:00, submit → rejected with a clear overlap message; vet not listed.
3. Change block 2 to Tuesday 14:00–10:00, submit → "Each schedule block must have a start time before its end time."
4. Remove all blocks, submit → "Add at least one schedule block."
5. Restore one valid block, set Cost per service `-5`, submit → rejected (prices are zero or more).
6. Set cost `10.00`, Username `qa.vet` → "This username is already taken."
7. Clear Name, submit → rejected with a clear message on Name.

#### Evidence
- Screenshot at steps 2 and 5

### SC-06: Owner sees only free slots, refreshed on every selection change

- **Covers:** AC-3
- **Priority:** High
- **Role / Route:** Owner One on `/owner/book`
- **Preconditions:** SC-01, SC-02 done

#### Steps
1. Sign in with `555-0101` / `qaowner1`, open "Book an appointment" → Pet offers only QA Rex and QA Mia (not QA Bolt).
2. Pick QA Rex, General consultation, Dr. Ana Torres, a future date on one of her working weekdays → "Free slots" lists times; each slot of 30 min fits inside her schedule.
3. Change Service to Hospitalization → slot list refreshes (fewer slots, each 240 min fitting the block; "Loading slots..." may flash).
4. Change Veterinarian to Dr. Luis Gomez → slots refresh to his schedule.
5. Change Date to a weekday he does not work → "No free slots for that selection."
6. Pick a past date (if the picker allows it) → no slots offered (appointments are booked in the future).
7. Choose "Any veterinarian" → slots from both vets are offered with the vet shown.

#### Evidence
- GIF over steps 2–5 (list must change between selections, never stale)

### SC-07: Owner reserves and confirms a slot; total fixed at booking

- **Covers:** AC-4, AC-7 (Reserved → Scheduled, owner)
- **Priority:** High
- **Role / Route:** Owner One on `/owner/book`, `/owner/appointments`
- **Preconditions:** SC-06

#### Steps
1. Sign in as Owner One, open "Book an appointment"; pick QA Rex, General consultation, Dr. Ana Torres, a future working date.
2. Click a free slot, then "Reserve" → "Slot reserved" panel with a countdown and "Confirm"; total 60.00 (40.00 + 20.00).
3. In another fresh session (reception) or after reload, the same vet/date/service no longer lists that slot.
4. Click "Confirm" → appointment confirmed; open "My appointments" → it shows status badge Scheduled, service, Dr. Ana Torres, time, Duration 30 min, Total 60.00.
5. Open the appointment → Booked by shows Owner.

#### Evidence
- Screenshot at steps 2 and 4

### SC-08: Unconfirmed reservation expires and frees the slot

- **Covers:** AC-4, AC-7 (Reserved → Cancelled, system)
- **Priority:** Low
- **Role / Route:** Owner One on `/owner/book`
- **Preconditions:** SC-01; requires waiting the 10-minute reservation TTL plus the 30-second sweep

#### Steps
1. Sign in as Owner One, reserve a slot for QA Mia, Grooming, Dr. Luis Gomez, note the time → "Slot reserved" with countdown.
2. Wait until the countdown reaches zero → "Reservation expired, pick a slot again"; "Confirm" disabled.
3. Wait ~30 s more and reload slots for the same selection → the noted slot is free again.
4. Open "My appointments" → that appointment shows the Cancelled badge (or is absent); no Confirm action offered.

#### Evidence
- GIF over steps 1–3

### SC-09: Two bookings for the same slot cannot both succeed

- **Covers:** AC-5
- **Priority:** High
- **Role / Route:** Owner One on `/owner/book`, Receptionist on `/reception/book`
- **Preconditions:** SC-01, SC-02

#### Steps
1. Session A: sign in as Owner One, pick QA Rex, Vaccination, Dr. Luis Gomez, a future working date; note a free slot but do not click Reserve yet.
2. Session B (close browser, new context): sign in as `reception`, "Book a walk-in" for QA Bolt (search `555-0202`), Vaccination, Dr. Luis Gomez, same date and same slot → booked, Scheduled.
3. Session A again (sign in as Owner One, same selection, select the same slot if still shown from a stale list, or else repeat with a slot overlapping: e.g. reception books General consultation 30 min starting at the same time and owner then tries the 15-min slot) → the second booking is rejected with "That slot was just taken. Pick another one." and the slot list refreshes without that time.
4. Try overlapping rather than identical: reception books Grooming (60 min) at 10:00 for Dr. Luis Gomez; owner looks for Vaccination at 10:30 → 10:30 not offered.

#### Evidence
- GIF over steps 2–3 (rejection message and refreshed list)

### SC-10: Walk-in booking is scheduled immediately with reception as booker

- **Covers:** AC-6
- **Priority:** High
- **Role / Route:** Receptionist on `/reception/book`
- **Preconditions:** SC-01

#### Steps
1. Sign in as `reception`, open "Book a walk-in" → "Search for the owner to start."
2. Search `555-0202`, pick QA P1 Owner Two, Pet QA Bolt, Service Grooming, Dr. Ana Torres, a future working date, a free slot; click "Book walk-in".
3. Appointment opens/lists with badge Scheduled immediately (no Reserved step or countdown), Total 55.00 (35.00 + 20.00).
4. Booked by shows Reception.
5. Search an owner with no pets (if available) → "This owner has no pets yet. Add a pet first."

#### Evidence
- Screenshot at steps 3–4

### SC-11: Veterinarian starts and finishes an assigned visit with observations

- **Covers:** AC-7 (Scheduled → In progress, In progress → Finished, vet), AC-14
- **Priority:** High
- **Role / Route:** Veterinarian `vet.ana` on `/vet/appointments`, `/appointments/[id]`
- **Preconditions:** Scheduled appointment for Dr. Ana Torres (SC-07 or SC-10)

#### Steps
1. Sign in as `vet.ana` / `vet123`, open "My assigned appointments" → lists only Dr. Ana Torres's appointments (none of Dr. Luis Gomez).
2. Open the Scheduled appointment → badge Scheduled; actions "Start visit" and observations; no Record payment, Reschedule or Close.
3. Click "Start visit" → badge In progress; "Finish visit" offered, "Start visit" gone.
4. Type "QA P1 obs" in Observations, "Save observations" → text persists after reload.
5. Click "Finish visit" → badge Finished; vet sees no Record payment / Close actions.
6. Sign in as `vet.luis` and try opening that appointment URL → not offered actions / access refused.

#### Evidence
- Screenshot at steps 3 and 5

### SC-12: Owner cancels own appointment; reason required

- **Covers:** AC-7 (Reserved/Scheduled → Cancelled, owner), AC-8
- **Priority:** High
- **Role / Route:** Owner One on `/owner/appointments`
- **Preconditions:** Owner One has a Scheduled appointment (book a fresh one, QA Mia, Vaccination)

#### Steps
1. Sign in as Owner One, open "My appointments", open the Scheduled appointment → "Cancel" offered.
2. Click "Cancel", leave "Cancel reason" empty, click "Confirm cancellation" → rejected with a clear message; status still Scheduled.
3. Enter "QA P1 owner cancel", confirm → badge Cancelled; no actions remain (no Cancel, no Edit observations).
4. Reserve a slot (not confirmed), then in "My appointments" cancel that Reserved one with a reason → Cancelled.
5. For an appointment that is In progress (from SC-11, if Owner One's), owner is not offered "Cancel" (only reception may cancel In progress).

#### Evidence
- Screenshot at steps 2 and 3

### SC-13: Reception cancels Scheduled and In progress appointments; reason required

- **Covers:** AC-7 (Scheduled/In progress → Cancelled, reception), AC-8
- **Priority:** High
- **Role / Route:** Receptionist on `/reception/appointments`
- **Preconditions:** two walk-ins for QA Bolt (create in step 1)

#### Steps
1. Sign in as `reception`, book two walk-ins for QA Bolt (Vaccination, Dr. Luis Gomez, different slots).
2. On "Ongoing appointments", click "Cancel" on walk-in 1, submit with an empty reason → rejected; still Scheduled.
3. Submit with whitespace only "   " → rejected.
4. Cancel with "QA P1 reception cancel" → Cancelled; disappears from ongoing list.
5. Open walk-in 2, "Start visit" → In progress; "Cancel" with reason → Cancelled.
6. Open the cancelled appointment → no actions offered, observations read-only ("Observations can no longer be edited." if attempted).

#### Evidence
- Screenshot at steps 2 and 6

### SC-14: Reschedule a Scheduled and reassign an In progress appointment

- **Covers:** AC-7 (→ Rescheduled, reception), AC-9
- **Priority:** High
- **Role / Route:** Receptionist on `/appointments/[id]/reschedule`
- **Preconditions:** a Scheduled walk-in and an In progress walk-in (create as in SC-13)

#### Steps
1. Sign in as `reception`, open the Scheduled appointment, click "Reschedule" → reschedule page with Veterinarian, Date and Free slots.
2. Pick a different free slot (same vet), confirm → original shows badge Rescheduled with "Rescheduled to" link; new appointment shows Scheduled with "Rescheduled from" link back.
3. Open the original → no actions offered (final).
4. Open the In progress appointment, "Reschedule", choose the other veterinarian and a free slot → original Rescheduled; new linked appointment Scheduled with the new vet.
5. New appointment total: check whether it uses the new vet's cost. Open question: TASK.md fixes the total "at booking" but does not say whether a reassignment recalculates it.
6. Try rescheduling a Finished appointment → "Reschedule" not offered.

#### Evidence
- Screenshot at steps 2 and 4

### SC-15: Payment only for Finished, exact total, once; then close

- **Covers:** AC-7 (Finished → Paid, Paid → Closed), AC-10, AC-14
- **Priority:** High
- **Role / Route:** Receptionist on `/reception/appointments`, `/appointments/[id]`
- **Preconditions:** a Finished appointment (SC-11) and a Scheduled one

#### Steps
1. Sign in as `reception`; on a Scheduled appointment, check "Record payment" is not offered.
2. Open the Finished appointment, click "Record payment" → dialog prefilled with the total (e.g. 60.00).
3. Change amount to total minus 0.01, "Confirm payment" → "The amount must equal the appointment total exactly."; still Finished.
4. Try total plus 1 → same rejection.
5. Enter the exact total → badge Paid; "Record payment" no longer offered; "Close" offered.
6. Try a second payment (back button / stale page and resubmit) → "This appointment was already paid."
7. Click "Close", accept "Close this appointment? This is final." → badge Closed; no actions; does not appear in Debts.

#### Evidence
- Screenshot at steps 3, 5, 7; GIF over step 6 if the stale dialog is used

### SC-16: Close unpaid creates a debt

- **Covers:** AC-7 (Finished → Closed), AC-11, AC-14
- **Priority:** High
- **Role / Route:** Receptionist on `/appointments/[id]`, `/reception/debts`
- **Preconditions:** a Finished appointment (walk-in → Start visit → Finish visit)

#### Steps
1. Sign in as `reception`, open the Finished appointment → actions "Record payment" and "Close unpaid (debt)".
2. Click "Close unpaid (debt)", confirm → badge Closed; no actions.
3. Open Debts → appointment listed with owner, pet, service and amount owed equal to the total.
4. A Paid-then-Closed appointment from SC-15 is not listed.

#### Evidence
- Screenshot at step 3

### SC-17: Ongoing list and badge/action consistency across statuses

- **Covers:** AC-7 (other transitions rejected), AC-13, AC-14
- **Priority:** Medium
- **Role / Route:** Receptionist on `/reception/appointments`
- **Preconditions:** appointments in Reserved, Scheduled, In progress, Finished, Paid from earlier scenarios

#### Steps
1. Sign in as `reception`, open "Ongoing appointments" → lists active appointments (Reserved, Scheduled, In progress, Finished, Paid); Cancelled, Rescheduled, Closed not listed.
2. Check badges: each status label has the same color/text on the list, detail page and history.
3. Check actions per row: Reserved → Confirm, Cancel; Scheduled → Start visit, Reschedule, Cancel; In progress → Finish visit, Reschedule, Cancel; Finished → Record payment, Close unpaid (debt); Paid → Close. No Cancel on Finished/Paid; no payment before Finished.
4. Filter by "All veterinarians"/one vet if available → list updates.

#### Evidence
- Screenshot at step 3

### SC-18: Owner sees all own appointments across pets and days with duration

- **Covers:** AC-12
- **Priority:** High
- **Role / Route:** Owner One on `/owner/appointments`, `/owner/pets`
- **Preconditions:** Owner One has appointments for QA Rex and QA Mia on at least two dates

#### Steps
1. Sign in as Owner One, open "My appointments" → Upcoming and past sections show both pets' appointments with service, veterinarian, date and time, Duration and status badge.
2. Durations match services (Consultation 30, Grooming 60, Vaccination 15, Hospitalization 240 min).
3. No appointment for QA Bolt (Owner Two) appears.
4. Open "My pets" → only QA Rex and QA Mia; open QA Rex's history → only QA Rex's appointments.

#### Evidence
- Screenshot at step 1

### SC-19: Owner cannot reach other owners' or reception data

- **Covers:** AC-12
- **Priority:** High
- **Role / Route:** Owner One on direct URLs
- **Preconditions:** IDs of QA Bolt's pet and one of its appointments (note them as reception first)

#### Steps
1. Sign in as `reception`, note the pet ID of QA Bolt (from owner page link) and an appointment ID of QA Bolt; close browser.
2. Sign in as Owner One, open `/owner/pets/<QA Bolt id>/history` → access refused/not found, no Bolt data.
3. Open `/appointments/<Bolt appointment id>` → refused/not found.
4. Open `/reception/owners` and `/reception/debts` → redirected or refused (no reception data).
5. Open `/vet/appointments` → refused.
6. Run `document.cookie` via evaluate → session JWT cookie not visible (HttpOnly).
7. Sign out → back at sign-in; opening `/owner/appointments` redirects to `/login`.

#### Evidence
- Screenshot at steps 2 and 3

### SC-20: Reception opens a pet's service history

- **Covers:** AC-13
- **Priority:** Medium
- **Role / Route:** Receptionist on `/reception/pets/[id]/history`
- **Preconditions:** QA Bolt with several appointments in various statuses

#### Steps
1. Sign in as `reception`, open Owners → QA P1 Owner Two → QA Bolt "Service history".
2. "Pet service history" shows Upcoming and past appointments with service, vet, time, status badge, total.
3. Cancelled, Rescheduled and Closed appointments are included in history.
4. A pet with no appointments shows "No upcoming appointments." / "No past appointments."

#### Evidence
- Screenshot at step 2
