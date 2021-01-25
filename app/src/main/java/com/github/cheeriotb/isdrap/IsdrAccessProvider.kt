/*
 *  Copyright (C) 2021 Cheerio <cheerio.the.bear@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of the MIT license.
 *  See the license information described in LICENSE file.
 */

@file:Suppress("RedundantNullableReturnType")

package com.github.cheeriotb.isdrap

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.telephony.IccOpenLogicalChannelResponse
import android.telephony.TelephonyManager
import android.util.Log

import java.lang.reflect.Method

class IsdrAccessProvider  : ContentProvider() {

    companion object {
        private const val LOG_TAG = "ISD-R AP"

        private const val AUTHORITY = "com.github.cheeriotb.isdrap.provider"
        private const val TABLE_STORE = "store"
        private const val REGEX_HEX_STRING = "[a-fA-F0-9]{2,}"

        // The AID for ISD-R is mentioned in the clause 2.2.3 of GSMA SGP.02.
        private const val AID_ISD_R = "A0000005591010FFFFFFFF8900000100"
        // The physical slot number for eSIM on Google Pixel phones (eg. Pixel 4) is 1.
        private const val PHYSICAL_SLOT_NUM = 1
        private const val LOGICAL_SLOT_NUM = 0

        private const val OPEN_P2 = 0x00
        // Refer to the clause 11.11 "STORE DATA Command" in GP Card Specification.
        private const val STORE_DATA_CLA = 0x80     // GlobalPlatform Command
        private const val STORE_DATA_INS = 0xE2     // STORE DATA
        private const val STORE_DATA_P1_MORE = 0x11 // More blocks (Case 4 can cover Case 3 also)
        private const val STORE_DATA_P1_LAST = 0x91 // Last block (Case 4 can cover Case 3 also)

        private const val GET_RESPONSE_CLA = 0x00
        private const val GET_RESPONSE_INS = 0xC0
        private const val GET_RESPONSE_P1 = 0x00
        private const val GET_RESPONSE_P2 = 0x00

        // Let us limit the max size to 0xFF though the phone might support the extended one.
        private const val DATA_BLOCK_MAX = 0xFF
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, TABLE_STORE, 1)
    }

    private lateinit var open: Method
    private lateinit var transmit: Method
    private lateinit var close: Method

    override fun onCreate(): Boolean {
        open = TelephonyManager::class.java.getDeclaredMethod(
            "iccOpenLogicalChannelBySlot", Int::class.java,
            String::class.java, Int::class.java)
        transmit = TelephonyManager::class.java.getDeclaredMethod(
            "iccTransmitApduLogicalChannelBySlot", Int::class.java,
            Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            Int::class.java, Int::class.java, String::class.java)
        close = TelephonyManager::class.java.getDeclaredMethod(
            "iccCloseLogicalChannelBySlot", Int::class.java,
            Int::class.java)

        val tm = context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
            ?: throw UnsupportedOperationException("Telephony Manager is unavailable")

        val infoArray = tm.uiccCardsInfo
        Log.d(LOG_TAG, "UICC Card Info: $infoArray")
        for (info in infoArray) {
            if (info.isEuicc && info.slotIndex != LOGICAL_SLOT_NUM) {
                val switchSlots = TelephonyManager::class.java.getDeclaredMethod(
                        "switchSlots", IntArray::class.java)
                switchSlots.invoke(tm, if (tm.uiccCardsInfo.size == 1)
                    intArrayOf(PHYSICAL_SLOT_NUM) else intArrayOf(PHYSICAL_SLOT_NUM, 0))
            }
        }

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val cursor = MatrixCursor(arrayOf("response"))
        var transmitRsp : Response?

        val data = uri.lastPathSegment
        if ((data == null) || (data.length % 2 != 0) || !Regex(REGEX_HEX_STRING).matches(data)) {
            throw IllegalArgumentException("Unexpected data in the request")
        }

        val tm = context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
                ?: throw UnsupportedOperationException("Telephony Manager is unavailable")

        val openRsp = open.invoke(tm, PHYSICAL_SLOT_NUM, AID_ISD_R, OPEN_P2)
                as IccOpenLogicalChannelResponse?
        if (openRsp != null && openRsp.channel != IccOpenLogicalChannelResponse.INVALID_CHANNEL) {
            try {
                var seqNumber = 0
                var p1 = STORE_DATA_P1_MORE

                do {
                    val beginIndex = (DATA_BLOCK_MAX * 2) * seqNumber
                    var endIndex = beginIndex + (DATA_BLOCK_MAX * 2)
                    if (data.length <= endIndex) {
                        endIndex = data.length
                        p1 = STORE_DATA_P1_LAST
                    }
                    val block = data.substring(beginIndex, endIndex)
                    transmitRsp = Response(transmit.invoke(tm, PHYSICAL_SLOT_NUM, openRsp.channel,
                            STORE_DATA_CLA, STORE_DATA_INS, p1, seqNumber, block.length / 2, block)
                            as String?)
                    if (!transmitRsp.isOk) {
                        cursor.addRow(arrayOf(transmitRsp.bytes))
                        return cursor
                    }
                    seqNumber++
                } while (p1 != STORE_DATA_P1_LAST)

                val builder = StringBuilder(transmitRsp!!.data)
                while (transmitRsp!!.isMore) {
                    transmitRsp = Response(transmit.invoke(tm, PHYSICAL_SLOT_NUM, openRsp.channel,
                            GET_RESPONSE_CLA, GET_RESPONSE_INS, GET_RESPONSE_P1, GET_RESPONSE_P2,
                            if (transmitRsp.sw2 == "00") 0x100 else transmitRsp.sw2, "") as String?)
                    builder.append(transmitRsp.data)
                }
                builder.append(transmitRsp.sw)

                cursor.addRow(arrayOf(builder.toString()))
                return cursor
            } catch (ex : Exception) {
            } finally {
                close.invoke(tm, PHYSICAL_SLOT_NUM, openRsp.channel)
            }
        }
        cursor.addRow(arrayOf(Response.SW_INTERNAL_EXCEPTION))
        return cursor
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? {
        throw UnsupportedOperationException("The requested operation is not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("The requested operation is not supported")
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("The requested operation is not supported")
    }

    override fun getType(
        uri: Uri
    ): String? {
        return when (uriMatcher.match(uri)) {
            UriMatcher.NO_MATCH -> throw IllegalArgumentException("Unknown URI: $uri")
            else -> "vnd.android.cursor.dir/$AUTHORITY.$TABLE_STORE"
        }
    }
}