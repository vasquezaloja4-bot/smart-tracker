package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.TrackingRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    CSV("csv", "text/csv", "CSV"),
    EXCEL("xls", "application/vnd.ms-excel", "Excel"),
    PDF("pdf", "application/pdf", "PDF")
}

object ReportExporter {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun exportAndShare(
        context: Context,
        reportTitle: String,
        records: List<TrackingRecord>,
        format: ExportFormat
    ): Result<File> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = reportTitle.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "smarttracker_report" }
        val file = File(exportDir, "${safeName}_$timestamp.${format.extension}")

        when (format) {
            ExportFormat.CSV -> writeCsv(file, records)
            ExportFormat.EXCEL -> writeExcelXml(file, reportTitle, records)
            ExportFormat.PDF -> writePdf(file, reportTitle, records)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SmartTracker — $reportTitle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Export $reportTitle (${format.label})")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        file
    }

    private fun writeCsv(file: File, records: List<TrackingRecord>) {
        val sb = StringBuilder()
        sb.appendLine("Tracking Code,Title,Category,Status,Priority,Progress %,Assignee,Start Date,Due Date")
        for (rec in records) {
            sb.appendLine(
                listOf(
                    csvCell(rec.trackingCode),
                    csvCell(rec.title),
                    csvCell(rec.categoryName),
                    csvCell(rec.parsedStatus.label),
                    csvCell(rec.parsedPriority.label),
                    rec.progressPercent.toString(),
                    csvCell(rec.assigneeName),
                    csvCell(formatDate(rec.startDateMillis)),
                    csvCell(formatDate(rec.dueDateMillis))
                ).joinToString(",")
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun writeExcelXml(file: File, reportTitle: String, records: List<TrackingRecord>) {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0"?>""")
        sb.appendLine("""<?mso-application progid="Excel.Sheet"?>""")
        sb.appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")
        sb.appendLine("""  <Worksheet ss:Name="SmartTracker Report">""")
        sb.appendLine("""    <Table>""")
        sb.appendLine("""      <Row><Cell><Data ss:Type="String">${xmlEsc("SmartTracker: $reportTitle")}</Data></Cell></Row>""")
        val headers = listOf("Tracking Code", "Title", "Category", "Status", "Priority", "Progress %", "Assignee", "Due Date")
        sb.append("      <Row>")
        headers.forEach { sb.append("""<Cell><Data ss:Type="String">${xmlEsc(it)}</Data></Cell>""") }
        sb.appendLine("</Row>")
        for (rec in records) {
            sb.append("      <Row>")
            sb.append("""<Cell><Data ss:Type="String">${xmlEsc(rec.trackingCode)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${xmlEsc(rec.title)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${xmlEsc(rec.categoryName)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${xmlEsc(rec.parsedStatus.label)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${xmlEsc(rec.parsedPriority.label)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="Number">${rec.progressPercent}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${xmlEsc(rec.assigneeName)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${xmlEsc(formatDate(rec.dueDateMillis))}</Data></Cell>""")
            sb.appendLine("</Row>")
        }
        sb.appendLine("""    </Table>""")
        sb.appendLine("""  </Worksheet>""")
        sb.appendLine("""</Workbook>""")
        file.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun writePdf(file: File, reportTitle: String, records: List<TrackingRecord>) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val headerPaint = Paint().apply {
            color = Color.rgb(15, 41, 74)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            color = Color.rgb(5, 150, 105)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 48f

        canvas.drawText("SmartTracker — $reportTitle", 36f, y, headerPaint)
        y += 18f
        canvas.drawText("Generated: ${dateFormatter.format(Date())}   |   Records: ${records.size}", 36f, y, subPaint)
        y += 16f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 22f

        if (records.isEmpty()) {
            canvas.drawText("No tracking records matched this report filter.", 36f, y, bodyPaint)
        } else {
            for (rec in records) {
                if (y > pageHeight - 56f) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = 48f
                }
                val line = "${rec.trackingCode} | ${rec.title.take(28)} | ${rec.parsedStatus.label} (${rec.progressPercent}%) | ${rec.parsedPriority.label} | Due ${formatDate(rec.dueDateMillis)}"
                canvas.drawText(line, 36f, y, bodyPaint)
                y += 18f
            }
        }

        document.finishPage(page)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
    }

    private fun formatDate(millis: Long): String =
        if (millis <= 0L) "-" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    private fun csvCell(raw: String): String =
        "\"${raw.replace("\"", "\"\"")}\""

    private fun xmlEsc(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
