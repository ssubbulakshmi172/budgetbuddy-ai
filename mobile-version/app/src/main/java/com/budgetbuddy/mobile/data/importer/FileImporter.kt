package com.budgetbuddy.mobile.data.importer

import android.content.Context
import android.net.Uri
import com.budgetbuddy.mobile.data.model.Transaction
import com.opencsv.CSVReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Service for importing transactions from CSV and Excel files
 */
class FileImporter(private val context: Context) {
    
    data class ImportResult(
        val success: Boolean,
        val transactions: List<Transaction>,
        val errors: List<String> = emptyList()
    )
    
    /**
     * Import transactions from CSV file
     */
    suspend fun importFromCSV(uri: Uri, userId: Long): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult(false, emptyList(), listOf("Could not open file"))
            
            val reader = CSVReader(InputStreamReader(inputStream))
            val transactions = mutableListOf<Transaction>()
            val errors = mutableListOf<String>()
            
            // Skip header row
            reader.readNext()
            
            var lineNumber = 1
            reader.forEach { row ->
                lineNumber++
                try {
                    if (row.size >= 3) {
                        val transaction = parseCSVRow(row, userId)
                        if (transaction != null) {
                            transactions.add(transaction)
                        } else {
                            errors.add("Line $lineNumber: Invalid data format")
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Line $lineNumber: ${e.message}")
                }
            }
            
            reader.close()
            
            ImportResult(true, transactions, errors)
        } catch (e: Exception) {
            ImportResult(false, emptyList(), listOf("Error reading CSV: ${e.message}"))
        }
    }
    
    /**
     * Import transactions from Excel file (XLSX or XLS)
     */
    suspend fun importFromExcel(uri: Uri, userId: Long): ImportResult = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var workbook: Workbook? = null
        try {
            android.util.Log.d("FileImporter", "Starting Excel import for URI: $uri")
            inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult(false, emptyList(), listOf("Could not open file. Check file permissions."))
            
            android.util.Log.d("FileImporter", "File opened successfully, detecting format...")
            
            // Auto-detect and create workbook (supports both XLSX and XLS)
            workbook = try {
                // Try XLSX first (newer format)
                try {
                    XSSFWorkbook(inputStream)
                } catch (e1: Exception) {
                    android.util.Log.d("FileImporter", "XLSX failed, trying XLS: ${e1.message}")
                    // Close and reopen stream for XLS
                    inputStream.close()
                    inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext ImportResult(false, emptyList(), listOf("Could not reopen file for XLS format"))
                    HSSFWorkbook(inputStream)
                }
            } catch (e: Exception) {
                android.util.Log.e("FileImporter", "Failed to create workbook: ${e.message}", e)
                return@withContext ImportResult(false, emptyList(), listOf("Invalid Excel file format. Please use .xlsx or .xls files. Error: ${e.message}"))
            }
            
            if (workbook == null) {
                return@withContext ImportResult(false, emptyList(), listOf("Failed to create workbook"))
            }
            
            val sheet = workbook.getSheetAt(0) // First sheet
            
            android.util.Log.d("FileImporter", "Sheet has ${sheet.lastRowNum} rows")
            val transactions = mutableListOf<Transaction>()
            val errors = mutableListOf<String>()
            
            // Find the actual data start row (skip empty rows and header rows)
            var dataStartRow = 1
            for (i in 0..minOf(10, sheet.lastRowNum)) {
                val row = sheet.getRow(i)
                if (row != null && !isRowEmpty(row) && !isHeaderRow(row)) {
                    dataStartRow = i
                    break
                }
            }
            
            android.util.Log.d("FileImporter", "Starting data extraction from row $dataStartRow (sheet has ${sheet.lastRowNum + 1} total rows)")
            
            var rowsProcessed = 0
            var rowsSkipped = 0
            var rowsWithData = 0
            
            // Process rows starting from dataStartRow
            for (rowIndex in dataStartRow..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                rowsProcessed++
                
                // Skip completely empty rows (silently)
                if (isRowEmpty(row)) {
                    rowsSkipped++
                    continue
                }
                
                // Skip header/footer rows (silently)
                if (isHeaderRow(row)) {
                    rowsSkipped++
                    continue
                }
                
                rowsWithData++
                
                try {
                    // Log first few rows for debugging
                    if (rowsWithData <= 5) {
                        val dateCell = row.getCell(0)
                        val narrationCell = row.getCell(1)
                        android.util.Log.d("FileImporter", "Row ${rowIndex + 1}: date='${getCellValueAsString(dateCell)}', narration='${getCellValueAsString(narrationCell)}'")
                    }
                    
                    val transaction = parseExcelRow(row, userId)
                    if (transaction != null) {
                        transactions.add(transaction)
                        
                        // Log first transaction details for debugging
                        if (transactions.size == 1) {
                            android.util.Log.d("FileImporter", "✓ First transaction: date=${transaction.date}, narration='${transaction.narration}', amount=${transaction.amount}")
                        }
                        
                        // Log every 10th transaction to avoid spam
                        if (transactions.size % 10 == 0) {
                            android.util.Log.d("FileImporter", "✓ Parsed ${transactions.size} transactions so far...")
                        }
                    } else {
                        // Only log warnings for first few errors to avoid spam
                        if (errors.size < 5) {
                            android.util.Log.w("FileImporter", "✗ Row ${rowIndex + 1}: Invalid data format - skipping")
                        }
                        errors.add("Row ${rowIndex + 1}: Invalid data format")
                    }
                } catch (e: Exception) {
                    // Only log first few errors
                    if (errors.size < 5) {
                        android.util.Log.e("FileImporter", "Error parsing row ${rowIndex + 1}: ${e.message}")
                    }
                    errors.add("Row ${rowIndex + 1}: ${e.message}")
                }
            }
            
            android.util.Log.d("FileImporter", "Processing complete: $rowsProcessed rows processed, $rowsWithData rows with data, $rowsSkipped skipped, ${transactions.size} transactions created")
            
            workbook.close()
            inputStream?.close()
            android.util.Log.d("FileImporter", "Import complete: ${transactions.size} transactions, ${errors.size} errors")
            
            ImportResult(true, transactions, errors)
        } catch (e: Exception) {
            android.util.Log.e("FileImporter", "Excel import error: ${e.message}", e)
            android.util.Log.e("FileImporter", "Stack trace:", e)
            try {
                workbook?.close()
                inputStream?.close()
            } catch (closeEx: Exception) {
                android.util.Log.e("FileImporter", "Error closing resources: ${closeEx.message}")
            }
            val errorMsg = when {
                e.message?.contains("Could not open", ignoreCase = true) == true -> 
                    "Could not open file. Please check file permissions and try again."
                e.message?.contains("format", ignoreCase = true) == true -> 
                    "Invalid file format. Please ensure the file is a valid Excel (.xlsx or .xls) file."
                e.message?.contains("permission", ignoreCase = true) == true -> 
                    "Permission denied. Please grant file access permission."
                else -> "Error reading Excel file: ${e.message ?: "Unknown error"}"
            }
            ImportResult(false, emptyList(), listOf(errorMsg))
        }
    }
    
    private fun parseCSVRow(row: Array<String>, userId: Long): Transaction? {
        try {
            // Match Spring Boot CSV format: Date, Narration/Description, Amount (required columns)
            // Optional: Withdrawal, Deposit, Closing Balance
            val dateStr = row.getOrNull(0)?.trim() ?: return null
            val narrationStr = row.getOrNull(1)?.trim() ?: ""
            
            // Strict validation: both date and narration must be present and non-empty
            if (dateStr.isBlank() || narrationStr.isBlank()) {
                return null // Skip rows with missing required data
            }
            
            // Additional check: narration should not be very short (likely invalid)
            val trimmedNarration = narrationStr.trim()
            if (trimmedNarration.length < 2) {
                return null
            }
            
            // Parse date - skip if parsing fails (no default to today)
            val date = parseDate(dateStr) ?: return null
            
            val narration = trimmedNarration
            val amount = row.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            val withdrawal = row.getOrNull(3)?.toDoubleOrNull()
            val deposit = row.getOrNull(4)?.toDoubleOrNull()
            val closingBalance = row.getOrNull(5)?.toDoubleOrNull()
            
            // Match Spring Boot amount calculation: if amount provided use it, else calculate from withdrawal/deposit
            val finalAmount = if (amount != 0.0) {
                amount
            } else {
                val withdrawalAmt = withdrawal ?: 0.0
                val depositAmt = deposit ?: 0.0
                if (withdrawalAmt <= 0.0) {
                    depositAmt
                } else {
                    -1 * withdrawalAmt
                }
            }
            
            // Skip transactions with no financial data
            if (finalAmount == 0.0 && withdrawal == null && deposit == null) {
                return null
            }
            
            return Transaction(
                date = date,
                narration = narration,
                chequeRefNo = null,
                withdrawalAmt = withdrawal,
                depositAmt = deposit,
                closingBalance = closingBalance,
                userId = userId,
                predictedCategory = null,
                predictedTransactionType = null,
                predictedIntent = null,
                predictionConfidence = null,
                categoryName = null,
                amount = finalAmount
            )
        } catch (e: Exception) {
            android.util.Log.e("FileImporter", "Error parsing CSV row: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Check if a row is completely empty
     */
    private fun isRowEmpty(row: Row): Boolean {
        for (cellIndex in 0 until row.lastCellNum) {
            val cell = row.getCell(cellIndex)
            if (cell != null) {
                val value = getCellValueAsString(cell)
                if (!value.isNullOrBlank()) {
                    return false
                }
            }
        }
        return true
    }
    
    /**
     * Check if a row looks like a header/footer row
     */
    private fun isHeaderRow(row: Row): Boolean {
        val firstCell = getCellValueAsString(row.getCell(0)) ?: return false
        val upperValue = firstCell.trim().uppercase()
        return upperValue.contains("STATEMENT") ||
               upperValue.contains("SUMMARY") ||
               upperValue.contains("OPENING BALANCE") ||
               upperValue.contains("CLOSING BALANCE") ||
               upperValue.contains("DATE") ||
               upperValue.contains("NARRATION") ||
               upperValue.matches(Regex("^[*=]+$")) ||
               upperValue.matches(Regex("^[-=]+$"))
    }
    
    /**
     * Safely extract cell value as string
     */
    private fun getCellValueAsString(cell: Cell?): String? {
        if (cell == null) return null
        
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    // Format date as string
                    cell.dateCellValue.toString()
                } else {
                    // Format number as string (remove trailing zeros if decimal)
                    val numValue = cell.numericCellValue
                    if (numValue == numValue.toLong().toDouble()) {
                        numValue.toLong().toString()
                    } else {
                        numValue.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                when (cell.cachedFormulaResultType) {
                    CellType.STRING -> cell.stringCellValue.trim()
                    CellType.NUMERIC -> {
                        if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                            cell.dateCellValue.toString()
                        } else {
                            val numValue = cell.numericCellValue
                            if (numValue == numValue.toLong().toDouble()) {
                                numValue.toLong().toString()
                            } else {
                                numValue.toString()
                            }
                        }
                    }
                    CellType.BOOLEAN -> cell.booleanCellValue.toString()
                    else -> cell.toString().trim()
                }
            }
            CellType.BLANK -> null
            else -> cell.toString().trim()
        }?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Safely extract numeric cell value
     */
    private fun getCellValueAsDouble(cell: Cell?): Double? {
        if (cell == null) return null
        
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> cell.numericCellValue
                CellType.STRING -> {
                    val str = cell.stringCellValue.trim()
                    if (str.isEmpty()) null else str.toDoubleOrNull()
                }
                CellType.FORMULA -> {
                    when (cell.cachedFormulaResultType) {
                        CellType.NUMERIC -> cell.numericCellValue
                        CellType.STRING -> {
                            val str = cell.stringCellValue.trim()
                            if (str.isEmpty()) null else str.toDoubleOrNull()
                        }
                        else -> null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseExcelRow(row: Row, userId: Long): Transaction? {
        try {
            // Match Spring Boot format: Date(0), Narration(1), ChequeRefNo(2), skip(3), Withdrawal(4), Deposit(5), ClosingBalance(6)
            val dateCell = row.getCell(0)
            val narrationCell = row.getCell(1)
            val chequeRefNoCell = row.getCell(2)
            // Column 3 is skipped (matches Spring Boot)
            val withdrawalCell = row.getCell(4)
            val depositCell = row.getCell(5)
            val closingBalanceCell = row.getCell(6)
            
            // Validate required fields - both must be present and meaningful
            val dateStr = getCellValueAsString(dateCell)
            val narrationStr = getCellValueAsString(narrationCell)
            
            // Strict validation: both date and narration must be present and non-empty
            if (dateStr.isNullOrBlank() || narrationStr.isNullOrBlank()) {
                return null // Skip rows with missing required data
            }
            
            // Additional check: narration should not be just whitespace or single characters
            val trimmedNarration = narrationStr.trim()
            if (trimmedNarration.length < 2) {
                return null // Skip rows with very short narration (likely invalid)
            }
            
            val parsedDate = when {
                dateCell?.cellType == CellType.NUMERIC -> {
                    // Excel dates are stored as numeric values
                    if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(dateCell)) {
                        // Use Apache POI's date utility for proper conversion
                        try {
                            dateCell.dateCellValue.toInstant()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                        } catch (e: Exception) {
                            // Fallback to string parsing if date conversion fails
                            parseDate(dateStr)
                        }
                    } else {
                        // If not formatted as date, try to parse as string
                        parseDate(dateStr)
                    }
                }
                dateCell?.cellType == CellType.STRING -> {
                    parseDate(dateStr)
                }
                dateCell?.cellType == CellType.FORMULA -> {
                    // Handle formula cells
                    when (dateCell.cachedFormulaResultType) {
                        CellType.STRING -> parseDate(dateStr)
                        CellType.NUMERIC -> {
                            if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(dateCell)) {
                                dateCell.dateCellValue.toInstant()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                            } else {
                                parseDate(dateStr)
                            }
                        }
                        else -> parseDate(dateStr)
                    }
                }
                else -> parseDate(dateStr)
            }
            
            // If date parsing failed, skip this row
            val date = parsedDate ?: return null
            
            val narration = trimmedNarration
            val chequeRefNo = getCellValueAsString(chequeRefNoCell)?.trim() ?: ""
            val withdrawal = getCellValueAsDouble(withdrawalCell)
            val deposit = getCellValueAsDouble(depositCell)
            val closingBalance = getCellValueAsDouble(closingBalanceCell)
            
            // Match Spring Boot amount calculation: withdrawalAmt <= 0.0 ? depositAmt : (-1 * withdrawalAmt)
            val withdrawalAmt = withdrawal ?: 0.0
            val depositAmt = deposit ?: 0.0
            val finalAmount = if (withdrawalAmt <= 0.0) {
                depositAmt
            } else {
                -1 * withdrawalAmt
            }
            
            // Skip transactions with no financial data
            if (finalAmount == 0.0 && withdrawal == null && deposit == null) {
                return null
            }
            
            return Transaction(
                date = date,
                narration = narration,
                chequeRefNo = if (chequeRefNo.isNotEmpty()) chequeRefNo else null,
                withdrawalAmt = withdrawal,
                depositAmt = deposit,
                closingBalance = closingBalance,
                userId = userId,
                predictedCategory = null,
                predictedTransactionType = null,
                predictedIntent = null,
                predictionConfidence = null,
                categoryName = null,
                amount = finalAmount
            )
        } catch (e: Exception) {
            android.util.Log.e("FileImporter", "Error parsing Excel row ${row.rowNum + 1}: ${e.message}", e)
            return null
        }
    }
    
    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return null
        
        // Match Spring Boot date formats - prioritize dd/MM/yy (Spring Boot's primary format)
        val formatters = listOf(
            DateTimeFormatter.ofPattern("dd/MM/yy"),  // Spring Boot primary format
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),  // ISO format (CSV)
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ISO_LOCAL_DATE
        )
        
        for (formatter in formatters) {
            try {
                return LocalDate.parse(trimmed, formatter)
            } catch (e: Exception) {
                // Try next formatter
            }
        }
        
        // Return null if parsing fails - let caller handle default
        android.util.Log.w("FileImporter", "Could not parse date: '$dateStr'")
        return null
    }
}

