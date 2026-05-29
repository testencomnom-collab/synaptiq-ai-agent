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
            description = "Die stärkste Open-Source-Antwort auf teure KI-Programmierer. Plant Architektur, schreibt Code, führt ihn aus und repariert Bugs selbstständig."
        ),
        LocalAgentModel(
            id = "goose",
            name = "Goose",
            category = "Terminal",
            description = "Ein lokaler Agent für Desktop und Terminal. Liest Dateien, bearbeitet Code und führt Befehle direkt lokal aus."
        ),
        LocalAgentModel(
            id = "browseruse",
            name = "Browser-Use",
            category = "Web & Automatisierung",
            description = "Öffnet echte Browser, klickt, scrollt und füllt Formulare aus – exakt wie ein Mensch."
        ),
        LocalAgentModel(
            id = "openclaw",
            name = "OpenClaw",
            category = "Web & Automatisierung",
            description = "Persönlicher Agent, der sich mit täglichen Apps (WhatsApp, Telegram, Slack) verbindet und im Hintergrund arbeitet."
        ),
        LocalAgentModel(
            id = "crewai",
            name = "CrewAI",
            category = "Multi-Agenten-Teams",
            description = "Lass KIs zusammenarbeiten: Recherche-, Schreiber- und Kritiker-Agenten teilen sich die Arbeit und liefern fertige Ergebnisse."
        ),
        LocalAgentModel(
            id = "autogen",
            name = "AutoGen",
            category = "Multi-Agenten-Teams",
            description = "Extrem mächtiges Open-Source-Framework von Microsoft, in dem KIs chatten und programmieren, um komplexe Probleme zu lösen."
        ),
        LocalAgentModel(
            id = "metagpt",
            name = "MetaGPT",
            category = "Multi-Agenten-Teams",
            description = "Simuliert ein komplettes Software-Unternehmen mit Rollen wie Produktmanager, Architekt, Entwickler und Tester."
        ),
        LocalAgentModel(
            id = "n8n",
            name = "n8n",
            category = "Visuelle Baukästen",
            description = "Open Source Automatisierungs-Tool wie Zapier. Verbinde KIs per Drag-and-Drop mit Google Drive, E-Mails oder Datenbanken."
        ),
        LocalAgentModel(
            id = "langflow",
            name = "Langflow",
            category = "Visuelle Baukästen",
            description = "Ziehe Bausteine zusammen (z.B. PDF-Leser + Modell + Websuche) und baue maßgeschneiderte Agenten komplett ohne Code."
        ),
        LocalAgentModel(
            id = "smolagents",
            name = "Smolagents",
            category = "Minimalismus & Effizienz",
            description = "Schreibt und führt kleine Python-Skripte aus anstatt lange Texte zu generieren. Extrem schnell und ressourcenschonend."
        )
    )
}
