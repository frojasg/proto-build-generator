package org.example.app

/**
 * Represents a module grouping of proto files
 */
data class ProtoModule(
    val name: String,
    val protoFiles: List<ProtoNode>,
    val dependencies: Set<String> = emptySet()
) {
    val packageName: String? = protoFiles.firstOrNull()?.packageName

    val totalMessages: Int = protoFiles.sumOf { it.messageCount }
    val totalEnums: Int = protoFiles.sumOf { it.enumCount }
    val protoCount: Int = protoFiles.size

    /**
     * Get all proto file paths in this module
     */
    fun getProtoPaths(): List<String> = protoFiles.map { it.path }

    override fun toString(): String {
        return "Module($name: ${protoCount} protos, ${totalMessages} messages, ${totalEnums} enums)"
    }
}

/**
 * Result of module grouping
 */
data class ModuleGroupingResult(
    val modules: List<ProtoModule>,
    val strategy: String,
    val stats: ModuleGroupingStats
) {
    /**
     * Get a module by name
     */
    fun getModule(name: String): ProtoModule? = modules.find { it.name == name }

    /**
     * Validate no circular dependencies between modules
     */
    fun hasCircularDependencies(): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun hasCycle(moduleName: String): Boolean {
            visited.add(moduleName)
            recursionStack.add(moduleName)

            val module = getModule(moduleName) ?: return false
            for (dep in module.dependencies) {
                if (!visited.contains(dep)) {
                    if (hasCycle(dep)) return true
                } else if (recursionStack.contains(dep)) {
                    return true
                }
            }

            recursionStack.remove(moduleName)
            return false
        }

        for (module in modules) {
            if (!visited.contains(module.name)) {
                if (hasCycle(module.name)) return true
            }
        }

        return false
    }

    /**
     * Find modules with no dependencies (foundation modules)
     */
    fun findRootModules(): List<ProtoModule> {
        return modules.filter { it.dependencies.isEmpty() }
    }

    /**
     * Find modules that no other module depends on (leaf modules)
     */
    fun findLeafModules(): List<ProtoModule> {
        val dependedUpon = modules.flatMap { it.dependencies }.toSet()
        return modules.filter { it.name !in dependedUpon }
    }
}

data class ModuleGroupingStats(
    val totalModules: Int,
    val totalProtos: Int,
    val totalMessages: Int,
    val totalEnums: Int,
    val averageProtosPerModule: Double,
    val smallestModule: Int,
    val largestModule: Int,
    val totalModuleDependencies: Int,
    val averageDependenciesPerModule: Double
)

/**
 * Interface for module grouping strategies
 */
interface ModuleGroupingStrategy {
    val name: String
    fun group(graph: ProtoDependencyGraph): ModuleGroupingResult
}

/**
 * Package-based grouping: Each proto package becomes a module
 */
class PackageBasedGrouping(
    private val namingStrategy: ModuleNamingStrategy = StandardModuleNaming()
) : ModuleGroupingStrategy {
    override val name = "package-based"

    override fun group(graph: ProtoDependencyGraph): ModuleGroupingResult {
        // Group protos by package
        val protosByPackage = graph.groupByPackage()

        // Create modules
        val modules = protosByPackage.map { (packageName, nodes) ->
            val moduleName = namingStrategy.packageToModuleName(packageName)

            // Calculate module dependencies
            val moduleDeps = calculateModuleDependencies(nodes, protosByPackage, graph)

            ProtoModule(
                name = moduleName,
                protoFiles = nodes,
                dependencies = moduleDeps
            )
        }.sortedBy { it.name }

        // Calculate statistics
        val stats = calculateStats(modules, graph)

        return ModuleGroupingResult(
            modules = modules,
            strategy = name,
            stats = stats
        )
    }

    /**
     * Calculate which other modules this module depends on
     */
    private fun calculateModuleDependencies(
        nodes: List<ProtoNode>,
        protosByPackage: Map<String, List<ProtoNode>>,
        graph: ProtoDependencyGraph
    ): Set<String> {
        val thisPackage = nodes.firstOrNull()?.packageName
        val moduleDeps = mutableSetOf<String>()

        // For each proto in this module
        for (node in nodes) {
            // Get its dependencies
            val protoDeps = graph.getDependencies(node.path)

            // Find which packages those dependencies belong to
            for (depPath in protoDeps) {
                val depNode = graph.getNode(depPath)
                val depPackage = depNode?.packageName

                // If dependency is in a different package, add module dependency
                if (depPackage != null && depPackage != thisPackage) {
                    val depModuleName = namingStrategy.packageToModuleName(depPackage)
                    moduleDeps.add(depModuleName)
                }
            }
        }

        return moduleDeps
    }

    private fun calculateStats(modules: List<ProtoModule>, graph: ProtoDependencyGraph): ModuleGroupingStats {
        val protoCounts = modules.map { it.protoCount }
        val graphStats = graph.getStatistics()

        return ModuleGroupingStats(
            totalModules = modules.size,
            totalProtos = graphStats.totalProtos,
            totalMessages = graphStats.totalMessages,
            totalEnums = graphStats.totalEnums,
            averageProtosPerModule = if (modules.isNotEmpty())
                graphStats.totalProtos.toDouble() / modules.size else 0.0,
            smallestModule = protoCounts.minOrNull() ?: 0,
            largestModule = protoCounts.maxOrNull() ?: 0,
            totalModuleDependencies = modules.sumOf { it.dependencies.size },
            averageDependenciesPerModule = if (modules.isNotEmpty())
                modules.sumOf { it.dependencies.size }.toDouble() / modules.size else 0.0
        )
    }
}
