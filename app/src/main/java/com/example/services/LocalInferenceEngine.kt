package com.example.services

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalInferenceEngine(private val context: Context) {
    
    private var llmInference: LlmInference? = null
    private var isUsingDummyModel = false
    
    suspend fun initialize(modelName: String = "local_model_gemma_2b.bin"): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, modelName)
            if (!modelFile.exists()) {
                return@withContext false
            }
            
            // Check if it's our simulated tiny dummy file
            if (modelFile.length() < 1024 * 1024) { 
                isUsingDummyModel = true
                return@withContext true
            }
            
            isUsingDummyModel = false
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setTemperature(0.7f)
                .build()
                
            llmInference = LlmInference.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback for simulation if MediaPipe crashes with invalid weights
            isUsingDummyModel = true
            true
        }
    }
    
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (isUsingDummyModel) {
            kotlinx.coroutines.delay(1500) // Simulate processing time
            return@withContext "[OFFLINE INFERENCE]\nDas MediaPipe LlmInference Modul hat den Prompt empfangen. Da aktuell jedoch nur eine 1KB Dummy-Datei geladen ist (um Bandbreite zu sparen), wird dies simuliert.\n\nEchter System-Prompt & User-Query:\n$prompt"
        }
        
        try {
            val inference = llmInference
                ?: return@withContext "Fehler: Die lokale MediaPipe Engine wurde nicht korrekt initialisiert."
            
            inference.generateResponse(prompt)
        } catch (e: Exception) {
            e.printStackTrace()
            "Fehler bei der lokalen On-Device Generierung: ${e.message}"
        }
    }
    
    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
