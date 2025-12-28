package org.example.app

import com.squareup.wire.schema.ProtoFile
import com.squareup.wire.schema.Schema

/**
 * Represents a node in the proto dependency graph
 */
data class ProtoNode(
    val path: String,
    val packageName: String?,
    val imports: List<String>,
    val protoFile: ProtoFile
) {
    val messageCount: Int get() = protoFile.types.count { it is com.squareup.wire.schema.MessageType }
    val enumCount: Int get() = protoFile.types.count { it is com.squareup.wire.schema.EnumType }
}

/**
 * Represents the dependency graph of proto files
 */
class ProtoDependencyGraph(schema: Schema) {

    // Create nodes (excluding Wire's built-in protos) of the proto file name as key and the ProtoNode
    private val nodes: Map<String, ProtoNode> = schema.protoFiles
        .filter { !it.location.path.startsWith("google/") && !it.location.path.startsWith("wire/") }
        .associate { protoFile ->
            protoFile.location.path to ProtoNode(
                path = protoFile.location.path,
                packageName = protoFile.packageName,
                imports = protoFile.imports,
                protoFile = protoFile
            )
        }

    // Build dependencies map (proto -> protos it imports)
    private val dependencies: Map<String, List<String>> = nodes.mapValues { (_, node) ->
        // filtering filter files we don't know about ... for some reason.
        // TODO: figure out better error handing unknown imports.
        node.imports.filter { import -> nodes.containsKey(import) }
    }


    private val dependents: Map<String, List<String>>

    init {

        // Build reverse dependencies map (proto -> protos that import it)
        val mutableDependents = mutableMapOf<String, MutableList<String>>()
        dependencies.forEach { (proto, deps) ->
            deps.forEach { dep ->
                mutableDependents.getOrPut(dep) { mutableListOf() }.add(proto)
            }
        }
        dependents = mutableDependents.mapValues { it.value.toList() }
    }

    /**
     * Get all proto nodes
     */
    fun getAllNodes(): List<ProtoNode> = nodes.values.toList()

    /**
     * Get a proto node by path
     */
    fun getNode(path: String): ProtoNode? = nodes[path]

    /**
     * Get direct dependencies of a proto (protos it imports)
     */
    fun getDependencies(path: String): List<String> = dependencies[path] ?: emptyList()

    /**
     * Get direct dependents of a proto (protos that import it)
     */
    fun getDependents(path: String): List<String> = dependents[path] ?: emptyList()

    /**
     * Find root protos (protos with no dependencies)
     */
    fun findRoots(): List<ProtoNode> {
        return nodes.values.filter { node ->
            getDependencies(node.path).isEmpty()
        }
    }

    /**
     * Find leaf protos (protos with no dependents)
     */
    fun findLeaves(): List<ProtoNode> {
        return nodes.values.filter { node ->
            getDependents(node.path).isEmpty()
        }
    }

    /**
     * Get all transitive dependencies of a proto (recursive)
     */
    fun getTransitiveDependencies(path: String): Set<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque(getDependencies(path))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (visited.add(current)) {
                queue.addAll(getDependencies(current))
            }
        }

        return visited
    }

    /**
     * Detect circular dependencies
     * Returns list of cycles, where each cycle is a list of proto paths forming a loop
     */
    fun detectCircularDependencies(): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val pathStack = mutableListOf<String>()

        fun dfs(proto: String) {
            visited.add(proto)
            recursionStack.add(proto)
            pathStack.add(proto)

            for (dep in getDependencies(proto)) {
                if (!visited.contains(dep)) {
                    dfs(dep)
                } else if (recursionStack.contains(dep)) {
                    // Found a cycle
                    val cycleStart = pathStack.indexOf(dep)
                    val cycle = pathStack.subList(cycleStart, pathStack.size).toList()
                    cycles.add(cycle)
                }
            }

            pathStack.removeAt(pathStack.lastIndex)
            recursionStack.remove(proto)
        }

        for (proto in nodes.keys) {
            if (!visited.contains(proto)) {
                dfs(proto)
            }
        }

        return cycles
    }

    /**
     * Group protos by package
     */
    fun groupByPackage(): Map<String, List<ProtoNode>> {
        return nodes.values
            .groupBy { it.packageName ?: "" }
            .filterKeys { it.isNotEmpty() }
    }

    /**
     * Find cross-package dependencies
     * Returns pairs of (sourcePackage, targetPackage)
     */
    fun findCrossPackageDependencies(): List<Pair<String, String>> {
        val crossPackageDeps = mutableListOf<Pair<String, String>>()

        nodes.values.forEach { node ->
            val sourcePackage = node.packageName ?: return@forEach

            node.imports.forEach { import ->
                val importedNode = nodes[import]
                val targetPackage = importedNode?.packageName

                if (targetPackage != null && targetPackage != sourcePackage) {
                    crossPackageDeps.add(sourcePackage to targetPackage)
                }
            }
        }

        return crossPackageDeps.distinct().sortedBy { "${it.first}->${it.second}" }
    }

    /**
     * Get statistics about the dependency graph
     */
    fun getStatistics(): DependencyGraphStats {
        return DependencyGraphStats(
            totalProtos = nodes.size,
            totalPackages = nodes.values.mapNotNull { it.packageName }.distinct().size,
            totalMessages = nodes.values.sumOf { it.messageCount },
            totalEnums = nodes.values.sumOf { it.enumCount },
            totalDependencies = dependencies.values.sumOf { it.size },
            rootProtos = findRoots().size,
            leafProtos = findLeaves().size,
            circularDependencies = detectCircularDependencies().size,
            crossPackageDependencies = findCrossPackageDependencies().size
        )
    }
}

data class DependencyGraphStats(
    val totalProtos: Int,
    val totalPackages: Int,
    val totalMessages: Int,
    val totalEnums: Int,
    val totalDependencies: Int,
    val rootProtos: Int,
    val leafProtos: Int,
    val circularDependencies: Int,
    val crossPackageDependencies: Int
)
