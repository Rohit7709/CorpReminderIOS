package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

object TeammateImporter {

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                Log.e("TeammateImporter", "Error resolving filename", e)
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "unnamed_file"
    }

    fun importFromFile(context: Context, uri: Uri): List<Pair<String, String>> {
        val fileName = getFileName(context, uri).lowercase()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()

        return try {
            if (fileName.endsWith(".xlsx")) {
                parseXlsx(inputStream)
            } else {
                parseCsv(inputStream)
            }
        } catch (e: Exception) {
            Log.e("TeammateImporter", "Error importing teammates", e)
            emptyList()
        } finally {
            try {
                inputStream.close()
            } catch (ex: Exception) {
                // ignore
            }
        }
    }

    private fun parseCsv(inputStream: InputStream): List<Pair<String, String>> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val lines = reader.readLines()
        if (lines.isEmpty()) return emptyList()

        // CSV parsing that handles quotes and delimiters
        fun parseCsvLine(line: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    inQuotes = !inQuotes
                } else if ((c == ',' || c == ';') && !inQuotes) {
                    result.add(current.toString().trim())
                    current.setLength(0)
                } else {
                    current.append(c)
                }
                i++
            }
            result.add(current.toString().trim())
            return result
        }

        val parsedRows = lines.map { parseCsvLine(it) }.filter { it.isNotEmpty() }
        if (parsedRows.isEmpty()) return emptyList()

        val headerRow = parsedRows.first().map { it.lowercase().trim().replace("_", " ").replace("employee", "emp") }
        val empIdIndex = headerRow.indexOfFirst { it == "emp id" || it == "id" || it == "empid" || it == "employee id" }
        val empNameIndex = headerRow.indexOfFirst { it == "emp name" || it == "name" || it == "empname" || it == "employee name" }

        val targetIdIdx = if (empIdIndex != -1) empIdIndex else 0
        val targetNameIdx = if (empNameIndex != -1) empNameIndex else if (parsedRows.first().size > 1) 1 else 0

        val finalTeammates = mutableListOf<Pair<String, String>>()
        for (i in 1 until parsedRows.size) {
            val row = parsedRows[i]
            val id = if (targetIdIdx < row.size) row[targetIdIdx].trim() else ""
            val name = if (targetNameIdx < row.size) row[targetNameIdx].trim() else ""
            if (id.isNotEmpty() && name.isNotEmpty()) {
                finalTeammates.add(id to name)
            }
        }
        return finalTeammates
    }

    private fun parseXlsx(inputStream: InputStream): List<Pair<String, String>> {
        val sharedStrings = mutableListOf<String>()
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry
        var sharedStringsBytes: ByteArray? = null
        var sheetBytes: ByteArray? = null

        while (entry != null) {
            if (entry.name == "xl/sharedStrings.xml") {
                sharedStringsBytes = zipStream.readBytes()
            } else if (entry.name == "xl/worksheets/sheet1.xml") {
                sheetBytes = zipStream.readBytes()
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }

        if (sheetBytes == null) return emptyList()

        if (sharedStringsBytes != null) {
            val xmlStr = String(sharedStringsBytes, Charsets.UTF_8)
            val matcher = Pattern.compile("<t[^>]*>(.*?)</t>").matcher(xmlStr)
            while (matcher.find()) {
                sharedStrings.add(matcher.group(1) ?: "")
            }
        }

        val sheetStr = String(sheetBytes, Charsets.UTF_8)
        val rows = mutableListOf<List<String>>()
        val rowMatcher = Pattern.compile("<row[^>]*>(.*?)</row>").matcher(sheetStr)
        
        while (rowMatcher.find()) {
            val rowXml = rowMatcher.group(1) ?: ""
            val cellsInRow = mutableListOf<Pair<String, String>>()
            
            // Regex to find cellular structure: cell row references like r="A1" or r="AB11"
            val cellMatcher = Pattern.compile("<c r=\"([A-Z]+)[0-9]+\"[^>]*>(.*?)</c>").matcher(rowXml)
            while (cellMatcher.find()) {
                val col = cellMatcher.group(1) ?: ""
                val cellInnerXml = cellMatcher.group(2) ?: ""

                // Check if this cell utilizes shared strings: <c r="A1" t="s">
                // Let's check attributes of <c ...> tag matching back in the row content
                val specificCellPattern = Pattern.compile("<c r=\"$col[0-9]+\"([^>]*)>")
                val specMatcher = specificCellPattern.matcher(rowXml)
                var isShared = false
                if (specMatcher.find()) {
                    val attrs = specMatcher.group(1) ?: ""
                    if (attrs.contains("t=\"s\"") || attrs.contains("t='s'")) {
                        isShared = true
                    }
                }

                val valMatcher = Pattern.compile("<v>(.*?)</v>").matcher(cellInnerXml)
                var value = ""
                if (valMatcher.find()) {
                    val rawVal = valMatcher.group(1) ?: ""
                    if (isShared) {
                        val idx = rawVal.toIntOrNull()
                        if (idx != null && idx >= 0 && idx < sharedStrings.size) {
                            value = sharedStrings[idx]
                        }
                    } else {
                        value = rawVal
                    }
                }
                cellsInRow.add(col to value)
            }

            // Convert to ordered columns: A, B, C...
            // Let's sort alphabetically because cell letters (A, B... Z, AA) represent standard progression
            val sortedCells = cellsInRow.sortedWith { o1, o2 -> 
                if (o1.first.length != o2.first.length) {
                    o1.first.length.compareTo(o2.first.length)
                } else {
                    o1.first.compareTo(o2.first)
                }
            }.map { it.second }

            if (sortedCells.isNotEmpty()) {
                rows.add(sortedCells)
            }
        }

        if (rows.isEmpty()) return emptyList()

        val headerRow = rows.first().map { it.lowercase().trim().replace("_", " ").replace("employee", "emp") }
        val empIdIndex = headerRow.indexOfFirst { it == "emp id" || it == "id" || it == "empid" || it == "employee id" }
        val empNameIndex = headerRow.indexOfFirst { it == "emp name" || it == "name" || it == "empname" || it == "employee name" }

        val targetIdIdx = if (empIdIndex != -1) empIdIndex else 0
        val targetNameIdx = if (empNameIndex != -1) empNameIndex else if (rows.first().size > 1) 1 else 0

        val finalTeammates = mutableListOf<Pair<String, String>>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            val id = if (targetIdIdx < row.size) row[targetIdIdx].trim() else ""
            val name = if (targetNameIdx < row.size) row[targetNameIdx].trim() else ""
            if (id.isNotEmpty() && name.isNotEmpty()) {
                finalTeammates.add(id to name)
            }
        }
        return finalTeammates
    }
}
