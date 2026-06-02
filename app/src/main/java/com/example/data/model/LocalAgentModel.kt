package com.example.data.model

data class LocalAgentModel(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val iconRes: String? = null
)

object LocalAgentRepository {
    val agents = listOf(
        LocalAgentModel(
            id = "openhands",
            name = "OpenHands",
            category = "Coding",
            description = "The most powerful open-source answer to expensive AI software engineers. Plans architecture, writes code, executes it, and fixes bugs autonomously."
        ),
        LocalAgentModel(
            id = "goose",
            name = "Goose",
            category = "Terminal",
            description = "A local agent for desktop and terminal. Reads files, edits code, and executes commands directly locally."
        ),
        LocalAgentModel(
            id = "browseruse",
            name = "Browser-Use",
            category = "Web & Automation",
            description = "Opens real browsers, clicks, scrolls, and fills out forms – exactly like a human."
        ),
        LocalAgentModel(
            id = "openclaw",
            name = "OpenClaw",
            category = "Web & Automation",
            description = "Personal agent that connects with daily apps (WhatsApp, Telegram, Slack) and runs in the background."
        ),
        LocalAgentModel(
            id = "crewai",
            name = "CrewAI",
            category = "Multi-Agent Teams",
            description = "Let AIs collaborate: Researcher, writer, and critic agents share the work and deliver finished results."
        ),
        LocalAgentModel(
            id = "autogen",
            name = "AutoGen",
            category = "Multi-Agent Teams",
            description = "Extremely powerful open-source framework by Microsoft where AIs chat and program to solve complex problems."
        ),
        LocalAgentModel(
            id = "metagpt",
            name = "MetaGPT",
            category = "Multi-Agent Teams",
            description = "Simulates an entire software company with roles like product manager, architect, developer, and tester."
        ),
        LocalAgentModel(
            id = "n8n",
            name = "n8n",
            category = "Visual Builders",
            description = "Open-source automation tool like Zapier. Connect AIs via drag-and-drop to Google Drive, email, or databases."
        ),
        LocalAgentModel(
            id = "langflow",
            name = "Langflow",
            category = "Visual Builders",
            description = "Drag components together (e.g., PDF reader + model + web search) and build custom agents completely without code."
        ),
        LocalAgentModel(
            id = "flowise",
            name = "Flowise",
            category = "Visual Builders",
            description = "Open-source UI visual tool to quickly build custom LLM flows using LangChain.js."
        ),
        LocalAgentModel(
            id = "smolagents",
            name = "Smolagents",
            category = "Minimalism & Efficiency",
            description = "Writes and executes small Python scripts instead of generating long text. Extremely fast and resource-efficient."
        )
    )
}
