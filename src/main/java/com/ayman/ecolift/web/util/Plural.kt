package com.ayman.ecolift.web.util

/** "1 session", "3 sessions". [plural] defaults to singular + "s". Works for UPPERCASE words too. */
fun countLabel(count: Int, singular: String, plural: String = singular + if (singular.isNotEmpty() && singular.last().isUpperCase()) "S" else "s"): String =
    "$count ${if (count == 1) singular else plural}"

/** Just the noun, for strings that style the number separately. */
fun noun(count: Int, singular: String, plural: String = singular + if (singular.isNotEmpty() && singular.last().isUpperCase()) "S" else "s"): String =
    if (count == 1) singular else plural
