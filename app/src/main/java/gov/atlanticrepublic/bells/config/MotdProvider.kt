package gov.atlanticrepublic.bells.config

import java.time.LocalDate

object MotdProvider {
    fun getMotd(messages: List<String>, date: LocalDate = LocalDate.now()): String {
        if (messages.isEmpty()) return ""
        return messages[date.dayOfYear % messages.size]
    }
}
