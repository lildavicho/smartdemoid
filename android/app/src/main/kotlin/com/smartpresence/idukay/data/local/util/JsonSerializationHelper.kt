package com.smartpresence.idukay.data.local.util

import com.smartpresence.idukay.data.remote.dto.AttendanceRecordDto
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object JsonSerializationHelper {
    
    fun encodeAttendanceRecordsToJson(records: List<AttendanceRecordDto>): String {
        return try {
            val jsonArray = JSONArray()
            for (record in records) {
                val jsonObject = JSONObject().apply {
                    put("studentId", record.studentId)
                    put("status", record.status)
                    put("confidence", record.confidence.toDouble())
                    put("confirmedBy", record.confirmedBy)
                    put("detectedAt", record.detectedAt ?: JSONObject.NULL)
                }
                jsonArray.put(jsonObject)
            }
            jsonArray.toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to encode attendance records to JSON")
            "[]"
        }
    }
    
    fun decodeAttendanceRecordsFromJson(json: String): List<AttendanceRecordDto> {
        val records = mutableListOf<AttendanceRecordDto>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                records.add(
                    AttendanceRecordDto(
                        studentId = obj.getString("studentId"),
                        status = obj.getString("status"),
                        confidence = obj.getDouble("confidence").toFloat(),
                        confirmedBy = obj.getString("confirmedBy"),
                        detectedAt = if (obj.isNull("detectedAt")) null else obj.getString("detectedAt")
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode attendance records from JSON")
        }
        return records
    }
}
