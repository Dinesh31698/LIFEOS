package com.lifeos.app.security

import com.lifeos.app.data.LifeOsDao
import kotlinx.coroutines.flow.first

/**
 * LIFEOS Phase 7: Local-First Structured Data Exporter.
 * Generates a clean, transparent, portable JSON representation of all locally
 * stored user data across Room database entities.
 *
 * Guarantees:
 * - 100% Local: No external networking or cloud transmission.
 * - Zero Raw PII: Evidence records are sanitized before export.
 * - Complete Transparency: Outlines exact counts of all entities retained on-device.
 */
object DataExporter {

    suspend fun exportToJson(dao: LifeOsDao): String {
        val modes = dao.getAllModesFlow().first()
        val items = dao.getAllItemsFlow().first()
        val events = dao.getAllEventsFlow().first()
        val habits = dao.getAllHabits()
        val evidence = dao.getLatestEvidenceFlow().first()
        val nodes = dao.getAllNodesFlow().first()
        val edges = dao.getAllEdgesFlow().first()

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": \"1.0.0\",\n")
        sb.append("  \"exportTimestamp\": ${System.currentTimeMillis()},\n")
        sb.append("  \"architecture\": \"100% Local-First / Zero Cloud Telemetry\",\n")
        sb.append("  \"dataTransparencySummary\": {\n")
        sb.append("    \"modesCount\": ${modes.size},\n")
        sb.append("    \"itemsCount\": ${items.size},\n")
        sb.append("    \"eventsCount\": ${events.size},\n")
        sb.append("    \"habitsCount\": ${habits.size},\n")
        sb.append("    \"evidenceCount\": ${evidence.size},\n")
        sb.append("    \"graphNodesCount\": ${nodes.size},\n")
        sb.append("    \"graphEdgesCount\": ${edges.size}\n")
        sb.append("  },\n")

        // 1. Life Modes
        sb.append("  \"lifeModes\": [\n")
        modes.forEachIndexed { index, m ->
            sb.append("    {\"id\": ${m.id}, \"name\": \"${escape(m.name)}\", \"icon\": \"${escape(m.iconRes)}\", \"isActive\": ${m.isActive}, \"priority\": ${m.priority}}")
            if (index < modes.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // 2. Checklist Items
        sb.append("  \"checklistItems\": [\n")
        items.forEachIndexed { index, item ->
            sb.append("    {\"id\": ${item.id}, \"modeId\": ${item.modeId}, \"itemName\": \"${escape(item.itemName)}\", \"isReady\": ${item.isReady}, \"isTemporary\": ${item.isTemporary}, \"ttlTimestamp\": ${item.ttlTimestamp}, \"confidence\": ${item.confidenceScore}, \"isGhostMode\": ${item.isGhostMode}, \"isWaived\": ${item.isWaived}}")
            if (index < items.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // 3. Events
        sb.append("  \"events\": [\n")
        events.forEachIndexed { index, ev ->
            sb.append("    {\"id\": ${ev.id}, \"title\": \"${escape(ev.title)}\", \"startTime\": ${ev.startTime}, \"endTime\": ${ev.endTime}, \"location\": \"${escape(ev.location ?: "")}\", \"modeId\": ${ev.associatedModeId}, \"isGhostMode\": ${ev.isGhostMode}, \"confidence\": ${ev.confidence}}")
            if (index < events.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // 4. Habits
        sb.append("  \"itemHabits\": [\n")
        habits.forEachIndexed { index, h ->
            sb.append("    {\"id\": ${h.id}, \"itemName\": \"${escape(h.itemName)}\", \"modeId\": ${h.modeId}, \"forgetCount\": ${h.forgetCount}, \"confirmCount\": ${h.confirmCount}, \"waiveCount\": ${h.waiveCount}, \"priorityBoost\": ${h.priorityBoost}}")
            if (index < habits.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // 5. Evidence Logs (Sanitized)
        sb.append("  \"evidenceLogs\": [\n")
        evidence.forEachIndexed { index, ev ->
            val sanitized = DataMinimizer.stripPii(ev.rawText)
            sb.append("    {\"id\": ${ev.id}, \"source\": \"${escape(ev.sourcePackage)}\", \"rawText\": \"${escape(sanitized)}\", \"parsedIntent\": \"${escape(ev.parsedIntent ?: "")}\", \"timestamp\": ${ev.timestamp}, \"confidence\": ${ev.confidenceScore}}")
            if (index < evidence.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // 6. Life Graph Nodes
        sb.append("  \"graphNodes\": [\n")
        nodes.forEachIndexed { index, n ->
            sb.append("    {\"id\": ${n.id}, \"name\": \"${escape(n.name)}\", \"type\": \"${escape(n.type)}\"}")
            if (index < nodes.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // 7. Life Graph Edges
        sb.append("  \"graphEdges\": [\n")
        edges.forEachIndexed { index, ed ->
            sb.append("    {\"id\": ${ed.id}, \"sourceNodeId\": ${ed.sourceNodeId}, \"targetNodeId\": ${ed.targetNodeId}, \"relationType\": \"${escape(ed.relationType)}\", \"weight\": ${ed.weight}}")
            if (index < edges.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")

        sb.append("}\n")
        return sb.toString()
    }

    private fun escape(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
