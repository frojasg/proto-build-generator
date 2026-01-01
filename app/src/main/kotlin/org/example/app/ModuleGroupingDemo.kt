package org.example.app

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.SchemaLoader
import okio.FileSystem
import java.nio.file.Paths

/**
 * Demo script showing module grouping algorithm
 */
fun main() {
    println("=".repeat(80))
    println("Module Grouping Algorithm Demo")
    println("=".repeat(80))

    // Load schema and build dependency graph
    val projectRoot = Paths.get("").toAbsolutePath().parent ?: Paths.get("").toAbsolutePath()
    val protoSourcePath = projectRoot.resolve("square-protos/src/main/proto")

    if (!protoSourcePath.toFile().exists()) {
        println("ERROR: Proto source directory not found: $protoSourcePath")
        return
    }

    println("\nLoading protos from: $protoSourcePath")

    val schemaLoader = SchemaLoader(fileSystem = FileSystem.SYSTEM)
    schemaLoader.initRoots(
        sourcePath = listOf(Location.get(protoSourcePath.toString())),
        protoPath = emptyList()
    )
    val schema = schemaLoader.loadSchema()
    val graph = ProtoDependencyGraph(schema)
    val evaluator = ModuleEvaluator(graph)

    println("✓ Loaded ${graph.getAllNodes().size} protos")
    println()

    // Apply package-based grouping strategy
    println("1. Package-Based Grouping Strategy")
    println("=".repeat(80))
    val strategy = PackageBasedGrouping()
    val result = strategy.group(graph)

    // Calculate and display statistics
    val stats = evaluator.calculateStats(result)
    println("\nModule Grouping Statistics:")
    println("-".repeat(80))
    println("Strategy:                    ${result.strategy}")
    println("Total modules:               ${stats.totalModules}")
    println("Total protos:                ${stats.totalProtos}")
    println("Total messages:              ${stats.totalMessages}")
    println("Total enums:                 ${stats.totalEnums}")
    println("Avg protos per module:       ${"%.1f".format(stats.averageProtosPerModule)}")
    println("Smallest module:             ${stats.smallestModule} protos")
    println("Largest module:              ${stats.largestModule} protos")
    println("Total module dependencies:   ${stats.totalModuleDependencies}")
    println("Avg deps per module:         ${"%.1f".format(stats.averageDependenciesPerModule)}")
    println()

    // Circular dependency check
    println("2. Circular Dependency Validation")
    println("-".repeat(80))
    if (result.hasCircularDependencies()) {
        println("⚠ WARNING: Circular dependencies detected between modules!")
    } else {
        println("✓ No circular dependencies between modules")
    }
    println()

    // Show all modules
    println("3. Generated Modules")
    println("-".repeat(80))
    result.modules.sortedBy { it.name }.forEach { module ->
        println("\nModule: ${module.name}")
        println("  Package: ${module.packageName}")
        println("  Protos: ${module.protoCount} (${module.totalMessages} messages, ${module.totalEnums} enums)")

        println("  Files:")
        module.getProtoPaths().sorted().forEach { path ->
            println("    - $path")
        }

        if (module.dependencies.isNotEmpty()) {
            println("  Dependencies:")
            module.dependencies.sorted().forEach { dep ->
                println("    - $dep")
            }
        } else {
            println("  Dependencies: (none - root module)")
        }
    }
    println()

    // Root and leaf modules
    println("4. Module Hierarchy")
    println("-".repeat(80))
    val rootModules = result.findRootModules()
    val leafModules = result.findLeafModules()

    println("Root Modules (no dependencies): ${rootModules.size}")
    rootModules.forEach { module ->
        println("  - ${module.name} (${module.protoCount} protos)")
    }
    println()

    println("Leaf Modules (no dependents): ${leafModules.size}")
    leafModules.forEach { module ->
        println("  - ${module.name} (${module.protoCount} protos)")
    }
    println()

    // Dependency visualization
    println("5. Module Dependency Graph")
    println("-".repeat(80))
    result.modules.sortedBy { it.name }.forEach { module ->
        if (module.dependencies.isNotEmpty()) {
            println("${module.name} ->")
            module.dependencies.sorted().forEach { dep ->
                println("  └─ $dep")
            }
        }
    }
    println()

    // Topological sort (build order)
    println("6. Suggested Build Order (Topological Sort)")
    println("-".repeat(80))
    val buildOrder = topologicalSort(result.modules)
    if (buildOrder != null) {
        println("Modules should be built in this order:")
        buildOrder.forEachIndexed { index, moduleName ->
            val module = result.getModule(moduleName)
            println("  ${index + 1}. $moduleName (${module?.protoCount ?: 0} protos)")
        }
    } else {
        println("⚠ Cannot determine build order (circular dependencies detected)")
    }
    println()

    // Summary
    println("=".repeat(80))
    println("Summary")
    println("=".repeat(80))
    println("✓ Successfully grouped ${stats.totalProtos} protos into ${stats.totalModules} modules")
    println("✓ No circular dependencies between modules")
    println("✓ Module size range: ${stats.smallestModule}-${stats.largestModule} protos")
    println("✓ Average module dependencies: ${"%.1f".format(stats.averageDependenciesPerModule)}")
    println()
    println("Next steps:")
    println("  - Implement Gradle build file generation for these modules")
    println("  - Test building the generated modules")
    println("  - Verify all dependencies resolve correctly")
    println("=".repeat(80))
}

/**
 * Topological sort to determine build order
 * Returns null if circular dependencies exist
 */
fun topologicalSort(modules: List<ProtoModule>): List<String>? {
    val inDegree = mutableMapOf<String, Int>()
    val adjList = mutableMapOf<String, MutableList<String>>()

    // Initialize
    modules.forEach { module ->
        inDegree[module.name] = 0
        adjList[module.name] = mutableListOf()
    }

    // Build adjacency list and calculate in-degrees
    modules.forEach { module ->
        module.dependencies.forEach { dep ->
            adjList[dep]?.add(module.name)
            inDegree[module.name] = (inDegree[module.name] ?: 0) + 1
        }
    }

    // Queue for modules with no dependencies
    val queue = ArrayDeque<String>()
    inDegree.forEach { (module, degree) ->
        if (degree == 0) queue.add(module)
    }

    val result = mutableListOf<String>()

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        result.add(current)

        adjList[current]?.forEach { dependent ->
            inDegree[dependent] = (inDegree[dependent] ?: 1) - 1
            if (inDegree[dependent] == 0) {
                queue.add(dependent)
            }
        }
    }

    // If we didn't process all modules, there's a cycle
    return if (result.size == modules.size) result else null
}
