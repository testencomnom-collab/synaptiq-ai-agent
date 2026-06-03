package com.example.data.model

data class LocalAgentModel(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val iconRes: String? = null,
    val downloadUrl: String = "",
    val fileName: String = ""
)

object LocalAgentRepository {
    val agents = listOf(
        LocalAgentModel(
            id = "gemma4_e4b",
            name = "Gemma 4 (E4B)",
            category = "Logical Reasoning",
            description = "High-performance on-device model optimized for reasoning tasks.",
            downloadUrl = "https://huggingface.co/google/gemma-4-e4b-gguf/resolve/main/gemma-4-e4b-q4_k_m.gguf?download=true",
            fileName = "gemma-4-e4b-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "qwen3_4b",
            name = "Qwen 3 (4B)",
            category = "Logical Reasoning",
            description = "Powerful bilingual model capable of complex logical inferences.",
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/qwen3-4b-q4_k_m.gguf?download=true",
            fileName = "qwen3-4b-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "llama3_2_3b",
            name = "Llama 3.2 (3B)",
            category = "Logical Reasoning",
            description = "Meta's lightweight model optimized for mobile intelligence.",
            downloadUrl = "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct-GGUF/resolve/main/llama-3.2-3b-instruct-q4_k_m.gguf?download=true",
            fileName = "llama-3.2-3b-instruct-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "phi4",
            name = "Phi-4",
            category = "Logical Reasoning",
            description = "Microsoft's state-of-the-art small language model.",
            downloadUrl = "https://huggingface.co/microsoft/phi-4-gguf/resolve/main/phi-4-q4_k_m.gguf?download=true",
            fileName = "phi-4-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "smollm3_3b",
            name = "SmolLM3 (3B)",
            category = "Logical Reasoning",
            description = "Fast and resource-efficient local model for quick tasks.",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM3-3B-Instruct-GGUF/resolve/main/smollm3-3b-instruct-q4_k_m.gguf?download=true",
            fileName = "smollm3-3b-instruct-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "ministral_3b",
            name = "Ministral (3B)",
            category = "Logical Reasoning",
            description = "Mistral's highly capable edge model.",
            downloadUrl = "https://huggingface.co/mistralai/Ministral-3B-Instruct-GGUF/resolve/main/ministral-3b-instruct-q4_k_m.gguf?download=true",
            fileName = "ministral-3b-instruct-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "deepseek_v4_flash",
            name = "DeepSeek-V4 Flash",
            category = "Logical Reasoning",
            description = "Extremely fast iteration of the DeepSeek series.",
            downloadUrl = "https://huggingface.co/deepseek-ai/DeepSeek-V4-Flash-GGUF/resolve/main/deepseek-v4-flash-q4_k_m.gguf?download=true",
            fileName = "deepseek-v4-flash-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "lfm2_5_1_6b",
            name = "LFM2.5 (1.6B)",
            category = "Logical Reasoning",
            description = "Lightweight foundational model.",
            downloadUrl = "https://huggingface.co/liquidai/lfm-2.5-1.6b-gguf/resolve/main/lfm-2.5-1.6b-q4_k_m.gguf?download=true",
            fileName = "lfm-2.5-1.6b-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "mobilellm_flash_1_4b",
            name = "MobileLLM-Flash (1.4B)",
            category = "Logical Reasoning",
            description = "Meta's incredibly fast edge model designed specifically for smartphones.",
            downloadUrl = "https://huggingface.co/meta-llama/MobileLLM-Flash-1.4B-GGUF/resolve/main/mobilellm-flash-1.4b-q4_k_m.gguf?download=true",
            fileName = "mobilellm-flash-1.4b-q4_k_m.gguf"
        ),
        LocalAgentModel(
            id = "stable_lm_2_1_6b",
            name = "Stable LM 2 (1.6B)",
            category = "Logical Reasoning",
            description = "Stability AI's robust lightweight language model.",
            downloadUrl = "https://huggingface.co/stabilityai/stablelm-2-1_6b-gguf/resolve/main/stablelm-2-1.6b-q4_k_m.gguf?download=true",
            fileName = "stablelm-2-1.6b-q4_k_m.gguf"
        )
    )
}
