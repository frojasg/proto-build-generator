package org.example.app

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.SchemaLoader
import okio.FileSystem
import java.nio.file.Paths

/**
 * Demo script showing ProtoDependencyGraph capabilities
 */
fun main() {
    println("=".repeat(80))
    println("Proto Dependency Graph Demo")
    println("=".repeat(80))

    // Load schema
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

    // Build dependency graph
    val graph = ProtoDependencyGraph(schema)

    println("✓ Built dependency graph")
    println()

    // 1. Graph Statistics
    println("1. Dependency Graph Statistics")
    println("-".repeat(80))
    val stats = graph.getStatistics()
    println("Total protos:               ${stats.totalProtos}")
    println("Total packages:             ${stats.totalPackages}")
    println("Total messages:             ${stats.totalMessages}")
    println("Total enums:                ${stats.totalEnums}")
    println("Total dependencies:         ${stats.totalDependencies}")
    println("Root protos (no deps):      ${stats.rootProtos}")
    println("Leaf protos (no dependents): ${stats.leafProtos}")
    println("Circular dependencies:      ${stats.circularDependencies}")
    println("Cross-package deps:         ${stats.crossPackageDependencies}")
    println()

    // 2. Root Protos
    println("2. Root Protos (No Dependencies)")
    println("-".repeat(80))
    val roots = graph.findRoots()
    println("Found ${roots.size} root proto(s):")
    roots.forEach { node ->
        println("  ${node.path}")
        println("    Package: ${node.packageName}")
        println("    Types: ${node.messageCount} messages, ${node.enumCount} enums")
    }
    println()

    // 3. Leaf Protos
    println("3. Leaf Protos (No Dependents)")
    println("-".repeat(80))
    val leaves = graph.findLeaves()
    println("Found ${leaves.size} leaf proto(s):")
    leaves.sortedBy { it.path }.forEach { node ->
        println("  ${node.path}")
    }
    println()

    // 4. Package Grouping
    println("4. Protos Grouped by Package")
    println("-".repeat(80))
    val byPackage = graph.groupByPackage()
    byPackage.entries.sortedBy { it.key }.forEach { (pkg, nodes) ->
        println("$pkg (${nodes.size} protos)")
        nodes.sortedBy { it.path }.forEach { node ->
            val deps = graph.getDependencies(node.path).size
            val dependents = graph.getDependents(node.path).size
            println("  - ${node.path} (deps: $deps, dependents: $dependents)")
        }
        println()
    }

    // 5. Circular Dependencies
    println("5. Circular Dependency Analysis")
    println("-".repeat(80))
    val cycles = graph.detectCircularDependencies()
    if (cycles.isEmpty()) {
        println("✓ No circular dependencies detected!")
    } else {
        println("⚠ Found ${cycles.size} circular dependency cycles:")
        cycles.forEachIndexed { index, cycle ->
            println("  Cycle ${index + 1}:")
            cycle.forEach { proto ->
                println("    - $proto")
            }
        }
    }
    println()

    // 6. Cross-Package Dependencies
    println("6. Cross-Package Dependencies")
    println("-".repeat(80))
    val crossPackageDeps = graph.findCrossPackageDependencies()
    println("Found ${crossPackageDeps.size} unique cross-package dependencies:")
    crossPackageDeps.forEach { (source, target) ->
        println("  $source -> $target")
    }
    println()

    // 7. Transitive Dependencies Example
    println("7. Transitive Dependencies Example")
    println("-".repeat(80))
    val exampleProto = "commerce/order.proto"
    val exampleNode = graph.getNode(exampleProto)
    if (exampleNode != null) {
        println("Analyzing: $exampleProto")
        println("Package: ${exampleNode.packageName}")
        println()

        val directDeps = graph.getDependencies(exampleProto)
        println("Direct dependencies (${directDeps.size}):")
        directDeps.forEach { println("  - $it") }
        println()

        val transitiveDeps = graph.getTransitiveDependencies(exampleProto)
        println("All transitive dependencies (${transitiveDeps.size}):")
        transitiveDeps.sorted().forEach { println("  - $it") }
        println()

        val dependents = graph.getDependents(exampleProto)
        println("Dependents (${dependents.size}):")
        if (dependents.isEmpty()) {
            println("  (none)")
        } else {
            dependents.forEach { println("  - $it") }
        }
    }
    println()

    println("=".repeat(80))
    println("Demo Complete!")
    println("=".repeat(80))
}
