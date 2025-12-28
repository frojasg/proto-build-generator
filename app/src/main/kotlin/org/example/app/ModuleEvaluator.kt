package org.example.app

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Validation results for module grouping post-conditions
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    fun hasErrors() = errors.isNotEmpty()
    fun hasWarnings() = warnings.isNotEmpty()

    fun printReport() {
        if (isValid && !hasWarnings()) {
            println("✓ All validations passed")
            return
        }

        if (hasErrors()) {
            println("\n✗ VALIDATION ERRORS:")
            errors.forEach { println("  - $it") }
        }

        if (hasWarnings()) {
            println("\n⚠ WARNINGS:")
            warnings.forEach { println("  - $it") }
        }
    }
}

/**
 * Comprehensive quality metrics for evaluating module grouping algorithms
 */
data class ModuleQualityMetrics(
    // Granularity Metrics
    val totalModules: Int,
    val totalProtos: Int,
    val minModuleSize: Int,
    val maxModuleSize: Int,
    val averageModuleSize: Double,
    val medianModuleSize: Double,
    val stdDevModuleSize: Double,
    val giniCoefficient: Double, // Measures inequality in module sizes (0 = perfect equality, 1 = maximum inequality)

    // Cohesion Metrics
    val packagesPerModule: Double, // Lower is better (1.0 = perfect cohesion)
    val crossPackageDependenciesWithinModules: Int,

    // Coupling Metrics
    val totalModuleDependencies: Int,
    val averageDependenciesPerModule: Double,
    val maxDependenciesPerModule: Int,
    val maxDependencyDepth: Int, // Longest chain in dependency graph
    val averageFanIn: Double, // How many modules depend on average module
    val maxFanIn: Int, // Most depended-upon module

    // Build Efficiency Metrics
    val rootModules: Int, // Modules with no dependencies
    val leafModules: Int, // Modules with no dependents
    val maxParallelizability: Int, // Max modules that can be built in parallel
    val criticalPathLength: Int, // Longest chain (build time bottleneck)
    val buildLevels: Int, // Number of sequential build stages needed

    // Overall Score (0-100, higher is better)
    val qualityScore: Double
) {
    fun printReport() {
        println("\n=== MODULE QUALITY METRICS ===\n")

        println("GRANULARITY:")
        println("  Total Modules: $totalModules")
        println("  Total Protos: $totalProtos")
        println("  Module Size: min=$minModuleSize, max=$maxModuleSize, avg=%.2f, median=%.1f".format(averageModuleSize, medianModuleSize))
        println("  Size Std Dev: %.2f".format(stdDevModuleSize))
        println("  Gini Coefficient: %.3f (0=balanced, 1=unbalanced)".format(giniCoefficient))

        println("\nCOHESION:")
        println("  Packages per Module: %.2f (1.0=perfect)".format(packagesPerModule))
        println("  Cross-package Dependencies Within Modules: $crossPackageDependenciesWithinModules")

        println("\nCOUPLING:")
        println("  Total Module Dependencies: $totalModuleDependencies")
        println("  Avg Dependencies per Module: %.2f".format(averageDependenciesPerModule))
        println("  Max Dependencies per Module: $maxDependenciesPerModule")
        println("  Max Dependency Depth: $maxDependencyDepth")
        println("  Avg Fan-in: %.2f".format(averageFanIn))
        println("  Max Fan-in: $maxFanIn")

        println("\nBUILD EFFICIENCY:")
        println("  Root Modules: $rootModules")
        println("  Leaf Modules: $leafModules")
        println("  Max Parallelizability: $maxParallelizability modules")
        println("  Critical Path Length: $criticalPathLength modules")
        println("  Build Levels: $buildLevels")

        println("\nOVERALL QUALITY SCORE: %.1f/100".format(qualityScore))
        println("=" * 35)
    }

    private operator fun String.times(n: Int) = repeat(n)
}

/**
 * Evaluates the quality of module grouping algorithms
 */
class ModuleEvaluator(
    private val graph: ProtoDependencyGraph
) {

    /**
     * Validates post-conditions for module grouping
     */
    fun validate(result: ModuleGroupingResult): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Post-condition 1: All files belong to a module
        val allProtos = graph.getAllNodes().map { it.path }
        val protosInModules = result.modules.flatMap { it.protoFiles.map { node -> node.path } }.toSet()

        val missingProtos = allProtos.filter { it !in protosInModules }
        if (missingProtos.isNotEmpty()) {
            errors.add("${missingProtos.size} proto(s) not assigned to any module: ${missingProtos.take(5)}")
        }

        // Post-condition 2: A file exists in only one module
        val protoToModules = mutableMapOf<String, MutableList<String>>()
        result.modules.forEach { module ->
            module.protoFiles.forEach { node ->
                protoToModules.getOrPut(node.path) { mutableListOf() }.add(module.name)
            }
        }

        val duplicateProtos = protoToModules.filter { it.value.size > 1 }
        if (duplicateProtos.isNotEmpty()) {
            duplicateProtos.forEach { (proto, modules) ->
                errors.add("Proto '$proto' exists in ${modules.size} modules: $modules")
            }
        }

        // Post-condition 3: No circular dependencies between modules
        if (result.hasCircularDependencies()) {
            errors.add("Circular dependencies detected between modules")
        }

        // Post-condition 4: All module dependencies are resolvable
        val moduleNames = result.modules.map { it.name }.toSet()
        result.modules.forEach { module ->
            val unresolvedDeps = module.dependencies.filter { it !in moduleNames }
            if (unresolvedDeps.isNotEmpty()) {
                errors.add("Module '${module.name}' has unresolved dependencies: $unresolvedDeps")
            }
        }

        // Warnings: Check for potential issues
        val emptyModules = result.modules.filter { it.protoFiles.isEmpty() }
        if (emptyModules.isNotEmpty()) {
            warnings.add("${emptyModules.size} empty module(s): ${emptyModules.map { it.name }}")
        }

        val isolatedModules = result.modules.filter {
            it.dependencies.isEmpty() &&
            result.modules.none { other -> it.name in other.dependencies }
        }
        if (isolatedModules.isNotEmpty()) {
            warnings.add("${isolatedModules.size} isolated module(s) with no dependencies or dependents")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Calculates comprehensive quality metrics for the module grouping
     */
    fun evaluate(result: ModuleGroupingResult): ModuleQualityMetrics {
        val moduleSizes = result.modules.map { it.protoCount }

        // Granularity metrics
        val minSize = moduleSizes.minOrNull() ?: 0
        val maxSize = moduleSizes.maxOrNull() ?: 0
        val avgSize = if (moduleSizes.isNotEmpty()) moduleSizes.average() else 0.0
        val medianSize = calculateMedian(moduleSizes)
        val stdDev = calculateStdDev(moduleSizes, avgSize)
        val gini = calculateGiniCoefficient(moduleSizes)

        // Cohesion metrics
        val packagesPerModule = result.modules.map { module ->
            module.protoFiles.mapNotNull { it.packageName }.distinct().size
        }.average()

        val crossPackageDepsInModules = result.modules.sumOf { module ->
            val packages = module.protoFiles.mapNotNull { it.packageName }.toSet()
            module.protoFiles.sumOf { node ->
                node.imports.count { importPath ->
                    val importedNode = graph.getNode(importPath)
                    importedNode != null && importedNode.packageName !in packages
                }
            }
        }

        // Coupling metrics
        val dependencyCounts = result.modules.map { it.dependencies.size }
        val totalDeps = dependencyCounts.sum()
        val avgDeps = if (dependencyCounts.isNotEmpty()) dependencyCounts.average() else 0.0
        val maxDeps = dependencyCounts.maxOrNull() ?: 0
        val maxDepth = calculateMaxDependencyDepth(result)

        val fanInCounts = calculateFanIn(result)
        val avgFanIn = if (fanInCounts.isNotEmpty()) fanInCounts.values.average() else 0.0
        val maxFanIn = fanInCounts.values.maxOrNull() ?: 0

        // Build efficiency metrics
        val rootModules = result.findRootModules().size
        val leafModules = result.findLeafModules().size
        val buildLevels = calculateBuildLevels(result)
        val maxParallel = calculateMaxParallelizability(result, buildLevels)
        val criticalPath = maxDepth + 1 // Depth is edges, path is nodes

        // Calculate overall quality score (0-100)
        val score = calculateQualityScore(
            gini = gini,
            avgSize = avgSize,
            packagesPerModule = packagesPerModule,
            avgDeps = avgDeps,
            maxDepth = maxDepth,
            maxParallel = maxParallel,
            totalModules = result.modules.size
        )

        return ModuleQualityMetrics(
            totalModules = result.modules.size,
            totalProtos = result.modules.sumOf { it.protoCount },
            minModuleSize = minSize,
            maxModuleSize = maxSize,
            averageModuleSize = avgSize,
            medianModuleSize = medianSize,
            stdDevModuleSize = stdDev,
            giniCoefficient = gini,
            packagesPerModule = packagesPerModule,
            crossPackageDependenciesWithinModules = crossPackageDepsInModules,
            totalModuleDependencies = totalDeps,
            averageDependenciesPerModule = avgDeps,
            maxDependenciesPerModule = maxDeps,
            maxDependencyDepth = maxDepth,
            averageFanIn = avgFanIn,
            maxFanIn = maxFanIn,
            rootModules = rootModules,
            leafModules = leafModules,
            maxParallelizability = maxParallel,
            criticalPathLength = criticalPath,
            buildLevels = buildLevels.size,
            qualityScore = score
        )
    }

    /**
     * Compares two module grouping algorithms side by side
     */
    fun compare(
        name1: String, result1: ModuleGroupingResult,
        name2: String, result2: ModuleGroupingResult
    ): String {
        val metrics1 = evaluate(result1)
        val metrics2 = evaluate(result2)

        return buildString {
            appendLine("\n=== ALGORITHM COMPARISON ===\n")
            appendLine("%-40s | %-15s | %-15s".format("Metric", name1, name2))
            appendLine("-".repeat(75))

            appendLine("GRANULARITY:")
            appendLine("  %-38s | %-15d | %-15d".format("Total Modules", metrics1.totalModules, metrics2.totalModules))
            appendLine("  %-38s | %-15.2f | %-15.2f".format("Avg Module Size", metrics1.averageModuleSize, metrics2.averageModuleSize))
            appendLine("  %-38s | %-15.3f | %-15.3f %s".format(
                "Gini Coefficient (lower=better)",
                metrics1.giniCoefficient,
                metrics2.giniCoefficient,
                winner(metrics1.giniCoefficient < metrics2.giniCoefficient)
            ))

            appendLine("\nCOHESION:")
            appendLine("  %-38s | %-15.2f | %-15.2f %s".format(
                "Packages per Module (lower=better)",
                metrics1.packagesPerModule,
                metrics2.packagesPerModule,
                winner(metrics1.packagesPerModule < metrics2.packagesPerModule)
            ))

            appendLine("\nCOUPLING:")
            appendLine("  %-38s | %-15.2f | %-15.2f".format("Avg Dependencies", metrics1.averageDependenciesPerModule, metrics2.averageDependenciesPerModule))
            appendLine("  %-38s | %-15d | %-15d %s".format(
                "Max Dependency Depth (lower=better)",
                metrics1.maxDependencyDepth,
                metrics2.maxDependencyDepth,
                winner(metrics1.maxDependencyDepth < metrics2.maxDependencyDepth)
            ))

            appendLine("\nBUILD EFFICIENCY:")
            appendLine("  %-38s | %-15d | %-15d %s".format(
                "Max Parallelizability (higher=better)",
                metrics1.maxParallelizability,
                metrics2.maxParallelizability,
                winner(metrics1.maxParallelizability > metrics2.maxParallelizability)
            ))
            appendLine("  %-38s | %-15d | %-15d %s".format(
                "Critical Path (lower=better)",
                metrics1.criticalPathLength,
                metrics2.criticalPathLength,
                winner(metrics1.criticalPathLength < metrics2.criticalPathLength)
            ))

            appendLine("\n" + "-".repeat(75))
            appendLine("  %-38s | %-15.1f | %-15.1f %s".format(
                "QUALITY SCORE (0-100)",
                metrics1.qualityScore,
                metrics2.qualityScore,
                winner(metrics1.qualityScore > metrics2.qualityScore)
            ))
            appendLine("=".repeat(75))
        }
    }

    private fun winner(condition: Boolean) = if (condition) "✓" else ""

    // Statistical helper functions

    private fun calculateMedian(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2].toDouble()
        }
    }

    private fun calculateStdDev(values: List<Int>, mean: Double): Double {
        if (values.isEmpty()) return 0.0
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    private fun calculateGiniCoefficient(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val n = values.size
        val sorted = values.sorted()
        val sum = sorted.sum().toDouble()

        if (sum == 0.0) return 0.0

        var numerator = 0.0
        sorted.forEachIndexed { index, value ->
            numerator += (2 * (index + 1) - n - 1) * value
        }

        return numerator / (n * sum)
    }

    private fun calculateMaxDependencyDepth(result: ModuleGroupingResult): Int {
        val depths = mutableMapOf<String, Int>()

        fun dfs(moduleName: String): Int {
            if (moduleName in depths) return depths[moduleName]!!

            val module = result.getModule(moduleName) ?: return 0
            if (module.dependencies.isEmpty()) {
                depths[moduleName] = 0
                return 0
            }

            val maxDepth = module.dependencies.maxOf { dfs(it) } + 1
            depths[moduleName] = maxDepth
            return maxDepth
        }

        return result.modules.maxOfOrNull { dfs(it.name) } ?: 0
    }

    private fun calculateFanIn(result: ModuleGroupingResult): Map<String, Int> {
        val fanIn = mutableMapOf<String, Int>()
        result.modules.forEach { module ->
            fanIn[module.name] = 0
        }

        result.modules.forEach { module ->
            module.dependencies.forEach { dep ->
                fanIn[dep] = (fanIn[dep] ?: 0) + 1
            }
        }

        return fanIn
    }

    private fun calculateBuildLevels(result: ModuleGroupingResult): List<Set<String>> {
        val levels = mutableListOf<Set<String>>()
        val processed = mutableSetOf<String>()
        val remaining = result.modules.map { it.name }.toMutableSet()

        while (remaining.isNotEmpty()) {
            val currentLevel = remaining.filter { moduleName ->
                val module = result.getModule(moduleName)
                module?.dependencies?.all { it in processed } ?: false
            }.toSet()

            if (currentLevel.isEmpty()) break // Circular dependency or error

            levels.add(currentLevel)
            processed.addAll(currentLevel)
            remaining.removeAll(currentLevel)
        }

        return levels
    }

    private fun calculateMaxParallelizability(result: ModuleGroupingResult, levels: List<Set<String>>): Int {
        return levels.maxOfOrNull { it.size } ?: 0
    }

    private fun calculateQualityScore(
        gini: Double,
        avgSize: Double,
        packagesPerModule: Double,
        avgDeps: Double,
        maxDepth: Int,
        maxParallel: Int,
        totalModules: Int
    ): Double {
        // Score components (each 0-100, higher is better)

        // Granularity score: Prefer balanced modules (low Gini) and reasonable size (not too small, not too large)
        val granularityScore = (1.0 - gini) * 100 * 0.7 +
            (if (avgSize in 3.0..10.0) 30.0 else 0.0)

        // Cohesion score: Prefer modules with single package (packagesPerModule close to 1.0)
        val cohesionScore = (2.0 - packagesPerModule.coerceAtMost(2.0)) * 50

        // Coupling score: Prefer fewer dependencies and shallower depth
        val couplingScore = (1.0 - (avgDeps / 10.0).coerceAtMost(1.0)) * 50 +
            (1.0 - (maxDepth / 10.0).coerceAtMost(1.0)) * 50

        // Build efficiency score: Prefer higher parallelizability
        val buildScore = (maxParallel.toDouble() / totalModules.coerceAtLeast(1)) * 100

        // Weighted average (granularity and cohesion more important)
        return (granularityScore * 0.35 +
                cohesionScore * 0.30 +
                couplingScore * 0.25 +
                buildScore * 0.10).coerceIn(0.0, 100.0)
    }
}
