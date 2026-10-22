// SPDX-FileCopyrightText: 2026 VanPaulTek Inc
// SPDX-License-Identifier: AGPL-3.0-or-later
package com.onlytrusted.app.ui.checkin

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds and dispatches the "I'm safe" SMS blast.
 *
 * Reuses [com.onlytrusted.app.ui.duress.DuressSmsSender] under the hood — Pro flow
 * already covered `SEND_SMS` runtime grant via HP1 Duress, so no new permission
 * request is needed here.
 */
object CheckInSmsSender {

    /** "✅ OnlyTrusted check-in from [owner name]: I'm safe. [HH:MM]". */
    fun buildBody(ownerName: String, at: Date = Date()): String {
        val name = ownerName.trim().ifEmpty { "an OnlyTrusted user" }
        val stamp = SimpleDateFormat("HH:mm", Locale.US).format(at)
        return "✅ OnlyTrusted check-in from $name: I'm safe. $stamp"
    }

    /**
     * Blast the message silently to every trusted number. Returns the count of successful
     * dispatches so the caller can show "Sent to N contacts.".
     */
    fun blast(ctx: Context, numbers: List<String>, body: String): Int {
        if (numbers.isEmpty() || body.isBlank()) return 0
        var sent = 0
        for (num in numbers.distinct()) {
            val ok = com.onlytrusted.app.ui.duress.DuressSmsSender.send(ctx, num, body)
            if (ok) sent += 1
        }
        return sent
    }
}
