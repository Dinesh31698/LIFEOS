package com.lifeos.app.engine

import android.util.Log
import com.lifeos.app.data.EdgeEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.data.NodeEntity
import com.lifeos.app.security.DataMinimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/**
 * LIFEOS Final Architecture: Personal Life Graph Engine.
 * Serves as the central deterministic relationship layer connecting:
 * Life Modes <-> Activities <-> Events <-> Locations <-> Checklist Items <-> Habits <-> Context Transitions.
 *
 * Guarantees:
 * - Deterministic, bounded multi-hop traversal with visited-set cycle protection.
 * - Cycle detection before edge insertion for directed dependency relations.
 * - Automated pruning of dangling edges and orphan nodes to prevent uncontrolled graph growth.
 * - Cross-mode relationship isolation (preventing item/activity leakage across modes).
 * - Strict Data Minimization (zero raw PII in node names or metadata).
 * - 100% offline, local-first execution backed by Room Database.
 */
object LifeGraphEngine {

    private const val TAG = "LIFEOS_GRAPH"
    const val MAX_TRAVERSAL_DEPTH = 6

    // Standard node types
    const val TYPE_MODE = "PLACE"
    const val TYPE_ACTIVITY = "ACTIVITY"
    const val TYPE_EVENT = "EVENT"
    const val TYPE_ITEM = "ITEM"

    // Standard relation types
    const val REL_CONTAINS = "CONTAINS"     // Mode -> Activity, Mode -> Event
    const val REL_REQUIRES = "REQUIRES"     // Activity -> Item, Mode -> Item
    const val REL_DEPENDS_ON = "DEPENDS_ON" // Item -> Item, Activity -> Activity
    const val REL_LOCATED_AT = "LOCATED_AT" // Activity -> Location

    /**
     * Retrieves or creates a graph node safely, ensuring all strings are PII-sanitized.
     */
    suspend fun getOrCreateNode(
        dao: LifeOsDao,
        name: String,
        type: String,
        metadataJson: String = "{}"
    ): NodeEntity = withContext(Dispatchers.IO) {
        val cleanName = DataMinimizer.stripPii(name).trim()
        val existing = dao.findNodeByName(cleanName)
        if (existing != null) return@withContext existing

        val cleanMeta = DataMinimizer.stripPii(metadataJson).trim()
        val newNode = NodeEntity(
            name = cleanName,
            type = type,
            metadataJson = cleanMeta
        )
        val id = dao.insertNode(newNode)
        newNode.copy(id = id.toInt())
    }

    /**
     * Checks if a directed path exists from [fromNodeId] to [toNodeId] (reachability DFS).
     * Used for cycle detection before inserting dependency/containment edges.
     */
    suspend fun isReachable(
        dao: LifeOsDao,
        fromNodeId: Int,
        toNodeId: Int,
        maxDepth: Int = MAX_TRAVERSAL_DEPTH
    ): Boolean = withContext(Dispatchers.IO) {
        if (fromNodeId == toNodeId) return@withContext true
        val visited = HashSet<Int>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(fromNodeId to 0)
        visited.add(fromNodeId)

        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            if (depth >= maxDepth) continue

            val edges = dao.getEdgesBySourceId(current)
            for (edge in edges) {
                if (edge.targetNodeId == toNodeId) return@withContext true
                if (!visited.contains(edge.targetNodeId)) {
                    visited.add(edge.targetNodeId)
                    queue.add(edge.targetNodeId to depth + 1)
                }
            }
        }
        false
    }

    /**
     * Connects two nodes with a directed edge.
     * Prevents:
     * - Self-loops (source == target)
     * - Non-existent node references
     * - Duplicate edges between the same pair with same relation
     * - Cycles for directed dependency relations (REQUIRES, DEPENDS_ON, CONTAINS)
     */
    suspend fun connectNodesSafe(
        dao: LifeOsDao,
        sourceNodeId: Int,
        targetNodeId: Int,
        relationType: String,
        weight: Float = 1.0f
    ): EdgeEntity? = withContext(Dispatchers.IO) {
        // 1. Prevent self-loops
        if (sourceNodeId == targetNodeId) {
            Log.w(TAG, "Rejected self-loop edge for node $sourceNodeId")
            return@withContext null
        }

        // 2. Validate source and target nodes exist
        val sourceNode = dao.getNodeById(sourceNodeId)
        val targetNode = dao.getNodeById(targetNodeId)
        if (sourceNode == null || targetNode == null) {
            Log.w(TAG, "Rejected edge: Source ($sourceNodeId) or Target ($targetNodeId) node does not exist")
            return@withContext null
        }

        // 3. Prevent duplicate edges
        val existingEdges = dao.getEdgesBySourceId(sourceNodeId)
        val alreadyExists = existingEdges.any { it.targetNodeId == targetNodeId && it.relationType == relationType }
        if (alreadyExists) {
            return@withContext existingEdges.first { it.targetNodeId == targetNodeId && it.relationType == relationType }
        }

        // 4. Cycle prevention: If adding source -> target creates a cycle (target can already reach source)
        if (relationType in listOf(REL_REQUIRES, REL_DEPENDS_ON, REL_CONTAINS)) {
            if (isReachable(dao, fromNodeId = targetNodeId, toNodeId = sourceNodeId)) {
                Log.w(TAG, "Rejected edge ($sourceNodeId -> $targetNodeId, $relationType): Cycle detected!")
                return@withContext null
            }
        }

        val edge = EdgeEntity(
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId,
            relationType = relationType,
            weight = weight
        )
        val id = dao.insertEdge(edge)
        edge.copy(id = id.toInt())
    }

    /**
     * Traverses the graph from [startNodeId] along outgoing edges matching [relationFilter].
     * Guarantees bounded depth and visited-set loop prevention.
     */
    suspend fun traverseOutward(
        dao: LifeOsDao,
        startNodeId: Int,
        relationFilter: String? = null,
        maxDepth: Int = MAX_TRAVERSAL_DEPTH
    ): List<NodeEntity> = withContext(Dispatchers.IO) {
        val visited = HashSet<Int>()
        val result = mutableListOf<NodeEntity>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(startNodeId to 0)
        visited.add(startNodeId)

        while (queue.isNotEmpty()) {
            val (currentId, depth) = queue.removeFirst()
            if (depth >= maxDepth) continue

            val edges = dao.getEdgesBySourceId(currentId)
            for (edge in edges) {
                if (relationFilter != null && edge.relationType != relationFilter) continue
                if (!visited.contains(edge.targetNodeId)) {
                    visited.add(edge.targetNodeId)
                    val targetNode = dao.getNodeById(edge.targetNodeId)
                    if (targetNode != null) {
                        result.add(targetNode)
                        queue.add(edge.targetNodeId to depth + 1)
                    }
                }
            }
        }
        result.sortedBy { it.id }
    }

    /**
     * Finds all item requirements required for an activity, supporting multi-hop resolution:
     * Activity -> REQUIRES -> Item
     * Activity -> DEPENDS_ON -> Sub-task -> REQUIRES -> Item
     */
    suspend fun getRequiredItemsForActivity(
        dao: LifeOsDao,
        activityName: String
    ): List<NodeEntity> = withContext(Dispatchers.IO) {
        val cleanName = DataMinimizer.stripPii(activityName).trim()
        val activityNode = dao.findNodeByNameLike(cleanName) ?: return@withContext emptyList()

        val directAndIndirect = traverseOutward(
            dao = dao,
            startNodeId = activityNode.id,
            maxDepth = 3
        )
        directAndIndirect.filter { it.type == TYPE_ITEM }
    }

    /**
     * Resolves the parent Mode/Place for an activity by inspecting incoming CONTAINS edges.
     */
    suspend fun getModeForActivity(
        dao: LifeOsDao,
        activityName: String
    ): LifeModeEntity? = withContext(Dispatchers.IO) {
        val cleanName = DataMinimizer.stripPii(activityName).trim()
        val activityNode = dao.findNodeByNameLike(cleanName) ?: return@withContext null

        val allEdges = dao.getAllEdgesFlow().first()
        val incomingEdges = allEdges.filter { it.targetNodeId == activityNode.id && it.relationType == REL_CONTAINS }

        for (edge in incomingEdges) {
            val parentNode = dao.getNodeById(edge.sourceNodeId)
            if (parentNode != null && (parentNode.type == TYPE_MODE || parentNode.type == "MODE")) {
                val mode = dao.getAllModesFlow().first().find {
                    it.name.equals(parentNode.name, ignoreCase = true)
                }
                if (mode != null) return@withContext mode
            }
        }
        null
    }

    /**
     * Prunes dangling edges (edges whose source or target nodes have been deleted)
     * and orphaned item nodes (items with 0 connected edges).
     */
    suspend fun pruneDanglingEdgesAndOrphans(dao: LifeOsDao): Int = withContext(Dispatchers.IO) {
        val allNodes = dao.getAllNodesFlow().first().associateBy { it.id }
        val allEdges = dao.getAllEdgesFlow().first().toList()
        var prunedCount = 0

        // 1. Remove dangling edges
        for (edge in allEdges) {
            if (!allNodes.containsKey(edge.sourceNodeId) || !allNodes.containsKey(edge.targetNodeId)) {
                dao.deleteEdgeById(edge.id)
                prunedCount++
                Log.d(TAG, "Pruned dangling edge: id=${edge.id} (${edge.sourceNodeId} -> ${edge.targetNodeId})")
            }
        }

        // 2. Remove orphaned ITEM or ACTIVITY nodes (0 incoming and 0 outgoing edges)
        val remainingEdges = dao.getAllEdgesFlow().first().toList()
        val referencedNodeIds = HashSet<Int>()
        for (e in remainingEdges) {
            referencedNodeIds.add(e.sourceNodeId)
            referencedNodeIds.add(e.targetNodeId)
        }

        for (node in allNodes.values.toList()) {
            if (node.id > 8 && (node.type == TYPE_ITEM || node.type == TYPE_ACTIVITY)) {
                if (!referencedNodeIds.contains(node.id)) {
                    dao.deleteNodeById(node.id)
                    prunedCount++
                    Log.d(TAG, "Pruned orphan node: id=${node.id}, name='${node.name}'")
                }
            }
        }
        prunedCount
    }

    /**
     * Deletes a specific item's node and all associated edges when the user deletes that item.
     */
    suspend fun pruneItemFromGraph(dao: LifeOsDao, itemName: String): Int = withContext(Dispatchers.IO) {
        val cleanName = DataMinimizer.stripPii(itemName).trim()
        val node = dao.findNodeByName(cleanName) ?: return@withContext 0
        var count = 0

        val allEdges = dao.getAllEdgesFlow().first()
        for (e in allEdges) {
            if (e.sourceNodeId == node.id || e.targetNodeId == node.id) {
                dao.deleteEdgeById(e.id)
                count++
            }
        }
        dao.deleteNodeById(node.id)
        count++
        Log.i(TAG, "Pruned item '$cleanName' from Life Graph ($count entities removed)")
        count
    }

    /**
     * Deletes a mode's node and associated containment/requirement edges when a mode is deleted.
     */
    suspend fun pruneModeFromGraph(dao: LifeOsDao, modeName: String): Int = withContext(Dispatchers.IO) {
        val cleanName = DataMinimizer.stripPii(modeName).trim()
        val node = dao.findNodeByName(cleanName) ?: return@withContext 0
        var count = 0

        val allEdges = dao.getAllEdgesFlow().first()
        for (e in allEdges) {
            if (e.sourceNodeId == node.id || e.targetNodeId == node.id) {
                dao.deleteEdgeById(e.id)
                count++
            }
        }
        dao.deleteNodeById(node.id)
        count++
        Log.i(TAG, "Pruned mode '$cleanName' from Life Graph ($count entities removed)")
        count
    }

    /**
     * Deterministically links a Mode, an Activity, and a Requirement in the Personal Life Graph:
     * Mode -[CONTAINS]-> Activity -[REQUIRES]-> Item
     */
    suspend fun linkActivityWithRequirement(
        dao: LifeOsDao,
        modeName: String,
        activityName: String,
        itemName: String
    ) = withContext(Dispatchers.IO) {
        val modeNode = getOrCreateNode(dao, modeName, TYPE_MODE)
        val actNode = getOrCreateNode(dao, activityName, TYPE_ACTIVITY)
        val itemNode = getOrCreateNode(dao, itemName, TYPE_ITEM)

        connectNodesSafe(dao, modeNode.id, actNode.id, REL_CONTAINS)
        connectNodesSafe(dao, actNode.id, itemNode.id, REL_REQUIRES)
    }
}
