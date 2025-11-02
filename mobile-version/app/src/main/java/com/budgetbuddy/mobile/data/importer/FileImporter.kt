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
import org.apache.poi.ss.usermodel.WorkbookFactory
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
            
            // Detect file format and create appropriate workbook
            // Try XLSX first (new format), then XLS (old format)
            workbook = try {
                XSSFWorkbook(inputStream) as Workbook
            } catch (e: Exception) {
                android.util.Log.d("FileImporter", "XLSX format failed, trying XLS format: ${e.message}")
                try {
                    // Reset stream for XLS reading
                    inputStream.close()
                    inputStream = context.contentResolver.openInputStream(uri)
                    HSSFWorkbook(inputStream) as Workbook
                } catch (e2: Exception) {
                    android.util.Log.e("FileImporter", "Both XLSX and XLS formats failed", e2)
                    return@withContext ImportResult(false, emptyList(), listOf("Unsupported Excel format. Please use .xlsx or .xls files."))
                }
            }
            
            val sheet = workbook.getSheetAt(0) // First sheet
            
            android.util.Log.d("FileImporter", "Sheet has ${sheet.lastRowNum} rows")
            val transactions = mutableListOf<Transaction>()
            val errors = mutableListOf<String>()
            
            // Skip header row (row 0)
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                
                try {
                    val transaction = parseExcelRow(row, userId)
                    if (transaction != null) {
                        transactions.add(transaction)
                        android.util.Log.d("FileImporter", "Parsed transaction: ${transaction.narration}")
                    } else {
                        errors.add("Row ${rowIndex + 1}: Invalid data format")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FileImporter", "Error parsing row ${rowIndex + 1}: ${e.message}", e)
                    errors.add("Row ${rowIndex + 1}: ${e.message}")
                }
            }
            
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
            // Expected format: Date, Narration, Amount, Withdrawal, Deposit, Closing Balance
            val dateStr = row.getOrNull(0) ?: return null
            val narration = row.getOrNull(1) ?: ""
            val amount = row.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            val withdrawal = row.getOrNull(3)?.toDoubleOrNull()
            val deposit = row.getOrNull(4)?.toDoubleOrNull()
            val closingBalance = row.getOrNull(5)?.toDoubleOrNull()
            
            val date = parseDate(dateStr)
            
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
                amount = amount
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseExcelRow(row: Row, userId: Long): Transaction? {
        try {
            val dateCell = row.getCell(0)
            val narrationCell = row.getCell(1)
            val amountCell = row.getCell(2)
            val withdrawalCell = row.getCell(3)
            val depositCell = row.getCell(4)
            val closingBalanceCell = row.getCell(5)
            
            val date = when {
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
                            parseDate(dateCell.toString())
                        }
                    } else {
                        // If not formatted as date, try to parse as string
                        parseDate(dateCell.toString())
                    }
                }
                dateCell?.cellType == CellType.STRING -> {
                    parseDate(dateCell.stringCellValue)
                }
                dateCell?.cellType == CellType.FORMULA -> {
                    // Handle formula cells
                    when (dateCell.cachedFormulaResultType) {
                        CellType.STRING -> parseDate(dateCell.stringCellValue)
                        CellType.NUMERIC -> {
                            if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(dateCell)) {
                                dateCell.dateCellValue.toInstant()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                            } else {
                                parseDate(dateCell.toString())
                            }
                        }
                        else -> LocalDate.now()
                    }
                }
                else -> LocalDate.now()
            }
            
            val narration = narrationCell?.stringCellValue ?: ""
            val amount = amountCell?.numericCellValue ?: 0.0
            val withdrawal = withdrawalCell?.numericCellValue
            val deposit = depositCell?.numericCellValue
            val closingBalance = closingBalanceCell?.numericCellValue
            
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
                amount = amount
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseDate(dateStr: String): LocalDate {
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
        )
        
        for (formatter in formatters) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter)
            } catch (e: Exception) {
                // Try next formatter
            }
        }
        
        // Default to today if parsing fails
        return LocalDate.now()
    }
}

