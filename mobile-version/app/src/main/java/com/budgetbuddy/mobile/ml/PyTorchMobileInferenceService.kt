package com.budgetbuddy.mobile.ml

import android.content.Context
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStream
import com.budgetbuddy.mobile.ml.TextPreprocessor

/**
 * PyTorch Mobile inference service for DistilBERT model
 * 
 * Uses the converted TorchScript Lite (.ptl) model.
 * Model outputs tuple: (transaction_type, category, intent) logits
 */
class PyTorchMobileInferenceService(
    private val context: Context,
    private val keywordMatcher: com.budgetbuddy.mobile.ml.KeywordMatcher? = null
) {
    
    private var model: Module? = null
    private var tokenizer: Tokenizer? = null
    private var modelInfo: ModelInfo? = null
    
    data class PredictionResult(
        val predictedCategory: String,
        val predictedSubcategory: String? = null,  // Extracted from category string
        val transactionType: String,
        val intent: String,
        val confidence: Double,  // Category confidence
        val transactionTypeConfidence: Double? = null,  // Full confidence scores
        val intentConfidence: Double? = null,
        val keywordMatched: Boolean = false  // Whether keyword matching was used
    )
    
    data class ModelInfo(
        val categories: List<String>,
        val transactionTypes: List<String>,
        val intents: List<String>,
        val numCategories: Int,
        val numTransactionTypes: Int,
        val numIntents: Int,
        val maxLength: Int
    )
    
    companion object {
        private const val MODEL_FILE = "distilbert_model.ptl"
        private const val MODEL_INFO_FILE = "model_info.json"
        private const val VOCAB_FILE = "vocab.txt"
        private const val MAX_SEQUENCE_LENGTH = 128
        private const val VOCAB_SIZE = 30522
    }
    
    /**
     * Initialize the PyTorch Mobile model and tokenizer
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load model info
            loadModelInfo()
            
            // Load PyTorch Mobile model from assets
            val modelPath = getAssetPath(MODEL_FILE)
            model = Module.load(modelPath)
            
            // Load tokenizer vocabulary
            tokenizer = Tokenizer(context)
            
            android.util.Log.d("PyTorchMobile", "Model loaded successfully from $modelPath")
            android.util.Log.d("PyTorchMobile", "Model info: categories=${modelInfo?.numCategories}, types=${modelInfo?.numTransactionTypes}, intents=${modelInfo?.numIntents}")
            true
        } catch (e: Exception) {
            android.util.Log.e("PyTorchMobile", "Failed to load model", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Load model metadata from model_info.json
     */
    private fun loadModelInfo() {
        try {
            val inputStream: InputStream = context.assets.open(MODEL_INFO_FILE)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)
            
            modelInfo = ModelInfo(
                categories = gson.fromJson(jsonObject.getAsJsonArray("categories"), Array<String>::class.java).toList(),
                transactionTypes = gson.fromJson(jsonObject.getAsJsonArray("transaction_types"), Array<String>::class.java).toList(),
                intents = gson.fromJson(jsonObject.getAsJsonArray("intents"), Array<String>::class.java).toList(),
                numCategories = jsonObject.get("num_categories").asInt,
                numTransactionTypes = jsonObject.get("num_transaction_types").asInt,
                numIntents = jsonObject.get("num_intents").asInt,
                maxLength = jsonObject.get("max_length").asInt
            )
            
            android.util.Log.d("PyTorchMobile", "Model info loaded: ${modelInfo?.categories}")
        } catch (e: Exception) {
            android.util.Log.e("PyTorchMobile", "Failed to load model info", e)
            // Fallback to default values
            modelInfo = ModelInfo(
                categories = listOf("Charity", "Dining", "Entertainment", "Fitness", "Groceries", "Healthcare", "Shopping", "Transport", "Travel", "Utilities"),
                transactionTypes = listOf("P2Business", "P2C", "P2P"),
                intents = listOf("bill_payment", "purchase", "refund", "subscription", "transfer"),
                numCategories = 10,
                numTransactionTypes = 3,
                numIntents = 5,
                maxLength = 128
            )
        }
    }
    
    /**
     * Get asset file path (copy to internal storage for PyTorch Mobile)
     */
    private fun getAssetPath(assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        
        context.assets.open(assetName).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }
    
    /**
     * Extract category and subcategory from full category string
     * Format: "TopCategory / Subcategory" -> (category="TopCategory", subcategory="Subcategory")
     */
    private fun extractCategoryParts(fullCategory: String): Pair<String, String?> {
        if (fullCategory.contains(" / ")) {
            val parts = fullCategory.split(" / ", limit = 2)
            if (parts.size == 2) {
                return Pair(parts[0].trim(), parts[1].trim())
            }
        }
        return Pair(fullCategory, null)
    }
    
    /**
     * Predict category, transaction type, and intent
     * 
     * Flow matches Python model:
     * 1. Check keyword matching FIRST (takes precedence)
     * 2. Preprocess text
     * 3. Get model prediction
     * 4. Extract subcategory from category string
     * 5. Return full confidence scores
     */
    suspend fun predict(narration: String): PredictionResult = withContext(Dispatchers.Default) {
        if (narration.isBlank()) {
            return@withContext PredictionResult(
                predictedCategory = "Uncategorized",
                transactionType = "N/A",
                intent = "N/A",
                confidence = 0.0,
                keywordMatched = false
            )
        }
        
        if (model == null || tokenizer == null) {
            return@withContext PredictionResult(
                predictedCategory = "Uncategorized",
                transactionType = "N/A",
                intent = "N/A",
                confidence = 0.0,
                keywordMatched = false
            )
        }
        
        try {
            // STEP 1: Check keyword matching FIRST (takes precedence over model)
            var keywordMatchedCategory: String? = null
            if (keywordMatcher != null && keywordMatcher.isKeywordsLoaded()) {
                // Check both original and cleaned narration
                keywordMatchedCategory = keywordMatcher.matchKeywords(narration)
                if (keywordMatchedCategory == null) {
                    val cleanedNarration = TextPreprocessor.preprocessUpiNarration(narration)
                    if (cleanedNarration.isNotEmpty() && cleanedNarration.lowercase() != narration.lowercase()) {
                        keywordMatchedCategory = keywordMatcher.matchKeywords(cleanedNarration)
                    }
                }
            }
            
            // STEP 2: Preprocess text
            val cleanNarration = TextPreprocessor.preprocessUpiNarration(narration)
            
            // STEP 3: Get model prediction (always get for transaction_type and intent)
            val modelResult = if (cleanNarration.isNotEmpty()) {
                predictWithModel(cleanNarration)
            } else {
                null
            }
            
            // STEP 4: If keyword matched, use keyword category but keep model's type/intent
            if (keywordMatchedCategory != null) {
                val (topCategory, subcategory) = extractCategoryParts(keywordMatchedCategory)
                android.util.Log.d("PyTorchMobile", "✅ Using keyword-matched category '$keywordMatchedCategory' " +
                    "(model would have predicted '${modelResult?.category ?: "N/A"}')")
                
                return@withContext PredictionResult(
                    predictedCategory = topCategory,
                    predictedSubcategory = subcategory,
                    transactionType = modelResult?.transactionType ?: "N/A",
                    intent = modelResult?.intent ?: "N/A",
                    confidence = modelResult?.categoryConfidence ?: 0.0,
                    transactionTypeConfidence = modelResult?.transactionTypeConfidence,
                    intentConfidence = modelResult?.intentConfidence,
                    keywordMatched = true
                )
            }
            
            // STEP 5: No keyword match - use model prediction
            if (modelResult == null) {
                return@withContext PredictionResult(
                    predictedCategory = "Uncategorized",
                    transactionType = "N/A",
                    intent = "N/A",
                    confidence = 0.0,
                    keywordMatched = false
                )
            }
            
            // STEP 6: Extract subcategory from category string
            val (topCategory, subcategory) = extractCategoryParts(modelResult.category)
            
            return@withContext PredictionResult(
                predictedCategory = topCategory,
                predictedSubcategory = subcategory,
                transactionType = modelResult.transactionType,
                intent = modelResult.intent,
                confidence = modelResult.categoryConfidence,
                transactionTypeConfidence = modelResult.transactionTypeConfidence,
                intentConfidence = modelResult.intentConfidence,
                keywordMatched = false
            )
            
        } catch (e: Exception) {
            android.util.Log.e("PyTorchMobile", "Prediction failed", e)
            e.printStackTrace()
            return@withContext PredictionResult(
                predictedCategory = "Uncategorized",
                transactionType = "N/A",
                intent = "N/A",
                confidence = 0.0,
                keywordMatched = false
            )
        }
    }
    
    /**
     * Internal method to get model predictions only (no keyword matching)
     */
    private suspend fun predictWithModel(cleanNarration: String): ModelPrediction? = withContext(Dispatchers.Default) {
        try {
            // Tokenize input
            val tokenIds = tokenizer!!.encode(cleanNarration, MAX_SEQUENCE_LENGTH)
            val attentionMask = LongArray(MAX_SEQUENCE_LENGTH) { if (it < tokenIds.size) 1L else 0L }
            
            // Create input tensors
            // PyTorch Mobile expects Long tensors for integer inputs
            val inputIdsLong = tokenIds.map { it.toLong() }.toLongArray()
            val inputIdsTensor = Tensor.fromBlob(
                inputIdsLong,
                longArrayOf(1, MAX_SEQUENCE_LENGTH.toLong())
            )
            val attentionMaskTensor = Tensor.fromBlob(
                attentionMask,
                longArrayOf(1, MAX_SEQUENCE_LENGTH.toLong())
            )
            
            // Run inference
            // Model expects: (input_ids, attention_mask)
            // PyTorch Mobile forward() accepts vararg IValue - pass each tensor as separate IValue
            val inputIdsIValue = IValue.from(inputIdsTensor)
            val attentionMaskIValue = IValue.from(attentionMaskTensor)
            val output = model!!.forward(inputIdsIValue, attentionMaskIValue)
            
            // Extract outputs from tuple
            // Model returns: (transaction_type, category, intent)
            val outputTuple = output.toTuple()
            val transactionTypeLogits = outputTuple[0].toTensor().dataAsFloatArray
            val categoryLogits = outputTuple[1].toTensor().dataAsFloatArray
            val intentLogits = outputTuple[2].toTensor().dataAsFloatArray
            
            // Get predictions (argmax)
            val categoryIdx = categoryLogits.indices.maxByOrNull { categoryLogits[it] } ?: 0
            val transactionTypeIdx = transactionTypeLogits.indices.maxByOrNull { transactionTypeLogits[it] } ?: 0
            val intentIdx = intentLogits.indices.maxByOrNull { intentLogits[it] } ?: 0
            
            // Convert to probabilities for confidence (all tasks)
            val categoryProbs = softmax(categoryLogits)
            val transactionTypeProbs = softmax(transactionTypeLogits)
            val intentProbs = softmax(intentLogits)
            
            val categoryConfidence = categoryProbs[categoryIdx].toDouble()
            val transactionTypeConfidence = transactionTypeProbs[transactionTypeIdx].toDouble()
            val intentConfidence = intentProbs[intentIdx].toDouble()
            
            val category = getCategoryLabel(categoryIdx)
            val transactionType = getTransactionTypeLabel(transactionTypeIdx)
            val intent = getIntentLabel(intentIdx)
            
            return@withContext ModelPrediction(
                category = category,
                transactionType = transactionType,
                intent = intent,
                categoryConfidence = categoryConfidence,
                transactionTypeConfidence = transactionTypeConfidence,
                intentConfidence = intentConfidence
            )
            
        } catch (e: Exception) {
            android.util.Log.e("PyTorchMobile", "Model prediction failed", e)
            return@withContext null
        }
    }
    
    /**
     * Internal data class for model predictions
     */
    private data class ModelPrediction(
        val category: String,
        val transactionType: String,
        val intent: String,
        val categoryConfidence: Double,
        val transactionTypeConfidence: Double,
        val intentConfidence: Double
    )
    
    /**
     * Batch prediction for multiple transactions (more efficient)
     */
    suspend fun predictBatch(narrations: List<String>): List<PredictionResult> = withContext(Dispatchers.Default) {
        narrations.map { predict(it) }
    }
    
    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exp = logits.map { kotlin.math.exp(it - max) }
        val sum = exp.sum()
        return exp.map { (it / sum).toFloat() }.toFloatArray()
    }
    
    private fun getCategoryLabel(index: Int): String {
        return modelInfo?.categories?.getOrNull(index) ?: "Uncategorized"
    }
    
    private fun getTransactionTypeLabel(index: Int): String {
        return modelInfo?.transactionTypes?.getOrNull(index) ?: "N/A"
    }
    
    private fun getIntentLabel(index: Int): String {
        return modelInfo?.intents?.getOrNull(index) ?: "N/A"
    }
    
    fun close() {
        // PyTorch Mobile models don't need explicit cleanup
        model = null
        tokenizer = null
    }
}

/**
 * Tokenizer for DistilBERT using vocab.txt
 */
class Tokenizer(private val context: Context) {
    private var vocab: Map<String, Int> = emptyMap()
    private val unkToken = "[UNK]"
    private val padToken = "[PAD]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    
    init {
        loadVocab()
    }
    
    private fun loadVocab() {
        try {
            val vocabMap = mutableMapOf<String, Int>()
            context.assets.open("vocab.txt").bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val word = line.trim()
                    if (word.isNotEmpty()) {
                        vocabMap[word] = index
                    }
                }
            }
            vocab = vocabMap
            android.util.Log.d("Tokenizer", "Loaded ${vocab.size} tokens from vocab.txt")
        } catch (e: Exception) {
            android.util.Log.e("Tokenizer", "Failed to load vocabulary", e)
            vocab = emptyMap()
        }
    }
    
    fun encode(text: String, maxLength: Int): IntArray {
        // Simplified tokenization - splits on whitespace and punctuation
        // In production, you should use proper BPE tokenization like DistilBERT's tokenizer
        val words = text.lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.isNotEmpty() }
        
        // Add [CLS] token at start
        val tokens = mutableListOf<Int>()
        tokens.add(vocab[clsToken] ?: vocab["[CLS]"] ?: 101) // 101 is CLS token ID in BERT
        
        // Add word tokens (or subwords if available)
        words.take(maxLength - 2).forEach { word ->
            val tokenId = vocab[word] ?: vocab[unkToken] ?: vocab.getOrDefault("[UNK]", 100) // 100 is UNK token ID
            tokens.add(tokenId)
        }
        
        // Add [SEP] token
        tokens.add(vocab[sepToken] ?: vocab["[SEP]"] ?: 102) // 102 is SEP token ID
        
        // Pad to maxLength
        while (tokens.size < maxLength) {
            tokens.add(vocab[padToken] ?: vocab["[PAD]"] ?: 0) // 0 is PAD token ID
        }
        
        return tokens.take(maxLength).toIntArray()
    }
}

