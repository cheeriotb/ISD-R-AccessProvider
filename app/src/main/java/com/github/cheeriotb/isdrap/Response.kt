/*
 *  Copyright (C) 2021 Cheerio <cheerio.the.bear@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the MIT license.
 *  See the license information described in LICENSE file.
 */

package com.github.cheeriotb.isdrap

import java.util.*

class Response(response: String?) {
    companion object {
        private const val REGEX_HEX_STRING = "[a-fA-F0-9]{2,}"
        private const val LENGTH_SW = 2 * 2
        private const val DATA_NONE = ""
        const val SW_INTERNAL_EXCEPTION = "6F00"
    }

    val data: String
    val sw: String

    init {
        if ((response != null) && (response.length >= LENGTH_SW) && (response.length % 2 == 0)
                && Regex(REGEX_HEX_STRING).matches(response)) {
            data = response.substring(0, response.length - LENGTH_SW).toUpperCase(Locale.ROOT)
            sw = response.substring(response.length - LENGTH_SW).toUpperCase(Locale.ROOT)
        } else {
            data = DATA_NONE
            sw = SW_INTERNAL_EXCEPTION
        }
    }

    val bytes: String = data + sw
    val sw2: String = sw.substring(2)
    val isOk: Boolean =
            // Refer to the clause 10.2.1.1 Normal processing in ETSI TS 102 221.
            when (sw.substring(0, 2)) {
                "90" -> true /* Normal ending of the command */
                "91" -> true /* Normal ending of the command, with extra information
                                from the proactive UICC containing a command for the terminal.
                                SW2 is the length of the response data */
                "92" -> true /* Normal ending of the command, with extra information
                                concerning an ongoing data transfer session. */
                else -> false
            }
    val isMore: Boolean = sw.substring(0, 2) == "61"
}
