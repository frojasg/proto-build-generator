package org.example.app

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.SchemaLoader
import okio.FileSystem
import java.nio.file.Paths

/**
 * Demonstrates how to use the ModuleEvaluator to assess and compare
 * different module grouping algorithms.
 */
fun main() {
    println("=== Module Grouping Evaluator Demo ===\n")

    // Load proto dependency graph
    val projectRoot = Paths.get("").toAbsolutePath()
    val protoSourcePath = projectRoot.resolve("square-protos/src/main/proto")

    if (!protoSourcePath.toFile().exists()) {
        println("Error: Proto source directory not found: $protoSourcePath")
        return
    }

    println("Loading proto files from: $protoSourcePath")
    val schemaLoader = SchemaLoader(fileSystem = FileSystem.SYSTEM)
    schemaLoader.initRoots(
        sourcePath = listOf(Location.get(protoSourcePath.toString())),
        protoPath = emptyList()
    )
    val schema = schemaLoader.loadSchema()
    val graph = ProtoDependencyGraph(schema)
    val stats = graph.getStatistics()

    println("Loaded ${stats.totalProtos} proto files from ${stats.totalPackages} packages")
    println("  - ${stats.totalMessages} messages")
    println("  - ${stats.totalEnums} enums")
    println("  - ${stats.totalDependencies} dependencies\n")

    // Create evaluator
    val evaluator = ModuleEvaluator(graph)

    // Example 1: Evaluate Package-Based Grouping with Standard Naming
    println("\n" + "=".repeat(70))
    println("EXAMPLE 1: Package-Based Grouping with Standard Naming")
    println("=".repeat(70))

    val packageGrouping = PackageBasedGrouping(StandardModuleNaming())
    val packageResult = packageGrouping.group(graph)

    println("\nValidating module grouping...")
    val validation1 = evaluator.validate(packageResult)
    validation1.printReport()

    if (validation1.isValid) {
        println("\nEvaluating quality metrics...")
        val metrics1 = evaluator.evaluate(packageResult)
        metrics1.printReport()
    }

    // Example 2: Evaluate Package-Based Grouping with Full Package Naming
    println("\n" + "=".repeat(70))
    println("EXAMPLE 2: Package-Based Grouping with Full Package Naming")
    println("=".repeat(70))

    val fullPackageGrouping = PackageBasedGrouping(FullPackageModuleNaming())
    val fullPackageResult = fullPackageGrouping.group(graph)

    println("\nValidating module grouping...")
    val validation2 = evaluator.validate(fullPackageResult)
    validation2.printReport()

    if (validation2.isValid) {
        println("\nEvaluating quality metrics...")
        val metrics2 = evaluator.evaluate(fullPackageResult)
        metrics2.printReport()
    }

    // Example 3: Compare the two algorithms
    println("\n" + "=".repeat(70))
    println("EXAMPLE 3: Algorithm Comparison")
    println("=".repeat(70))

    val comparison = evaluator.compare(
        "Standard Naming", packageResult,
        "Full Package Naming", fullPackageResult
    )
    println(comparison)

    // Example 4: Simulate a monolith (all protos in one module)
    println("\n" + "=".repeat(70))
    println("EXAMPLE 4: Monolith Approach (Baseline)")
    println("=".repeat(70))

    val allNodes = graph.getAllNodes()
    val monolithModule = ProtoModule(
        name = "monolith",
        protoFiles = allNodes
    )
    val monolithResult = ModuleGroupingResult(listOf(monolithModule), "monolith")

    println("\nValidating monolith grouping...")
    val validation3 = evaluator.validate(monolithResult)
    validation3.printReport()

    if (validation3.isValid) {
        println("\nEvaluating monolith quality metrics...")
        val metrics3 = evaluator.evaluate(monolithResult)
        metrics3.printReport()
    }

    // Example 5: Compare package-based vs monolith
    println("\n" + "=".repeat(70))
    println("EXAMPLE 5: Modular vs Monolith Comparison")
    println("=".repeat(70))

    val comparison2 = evaluator.compare(
        "Package-Based (Modular)", packageResult,
        "Monolith", monolithResult
    )
    println(comparison2)

    // Example 6: Maximum granularity (one proto per module)
    println("\n" + "=".repeat(70))
    println("EXAMPLE 6: Maximum Granularity (One Proto Per Module)")
    println("=".repeat(70))

    val maxGranularityModules = allNodes.mapIndexed { index, node ->
        // Calculate dependencies based on imports
        val deps = node.imports
            .mapNotNull { importPath ->
                val importNode = graph.getNode(importPath)
                importNode?.let { "module-${allNodes.indexOf(it)}" }
            }
            .filter { it != "module-$index" } // Remove self-references
            .toSet()

        ProtoModule(
            name = "module-$index",
            protoFiles = listOf(node),
            dependencies = deps
        )
    }
    val maxGranularityResult = ModuleGroupingResult(maxGranularityModules, "max-granularity")

    println("\nValidating maximum granularity grouping...")
    val validation4 = evaluator.validate(maxGranularityResult)
    validation4.printReport()

    if (validation4.isValid) {
        println("\nEvaluating maximum granularity quality metrics...")
        val metrics4 = evaluator.evaluate(maxGranularityResult)
        metrics4.printReport()
    }

    // Example 7: Three-way comparison
    println("\n" + "=".repeat(70))
    println("EXAMPLE 7: Three-Way Comparison Summary")
    println("=".repeat(70))

    val results = listOf(
        "Monolith" to monolithResult,
        "Package-Based" to packageResult,
        "Max Granularity" to maxGranularityResult
    )

    val metricsMap = results.associate { (name, result) ->
        name to evaluator.evaluate(result)
    }

    println("\n%-25s | %8s | %8s | %8s | %8s | %8s".format(
        "Algorithm", "Modules", "Gini", "Cohesion", "MaxDepth", "Score"
    ))
    println("-".repeat(90))

    metricsMap.forEach { (name, metrics) ->
        println("%-25s | %8d | %8.3f | %8.2f | %8d | %8.1f".format(
            name,
            metrics.totalModules,
            metrics.giniCoefficient,
            metrics.packagesPerModule,
            metrics.maxDependencyDepth,
            metrics.qualityScore
        ))
    }

    println("\n" + "=".repeat(70))
    println("Key Insights:")
    println("  - Lower Gini Coefficient = More balanced module sizes")
    println("  - Cohesion closer to 1.0 = Better package cohesion")
    println("  - Lower Max Depth = Faster parallel builds")
    println("  - Higher Score (0-100) = Better overall quality")
    println("=".repeat(70))

    // Example 8: Detailed module breakdown
    println("\n" + "=".repeat(70))
    println("EXAMPLE 8: Detailed Module Breakdown (Package-Based)")
    println("=".repeat(70))

    packageResult.modules.sortedBy { it.name }.forEach { module ->
        println("\nModule: ${module.name}")
        println("  Proto files: ${module.protoCount}")
        println("  Messages: ${module.totalMessages}, Enums: ${module.totalEnums}")
        println("  Dependencies: ${module.dependencies.size} - ${module.dependencies.sorted()}")
        println("  Files:")
        module.protoFiles.forEach { node ->
            println("    - ${node.path} (pkg: ${node.packageName ?: "none"})")
        }
    }

    println("\n=== Demo Complete ===")
}
