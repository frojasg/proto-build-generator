package org.example.app

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.SchemaLoader
import okio.FileSystem
import java.nio.file.Paths

/**
 * Demonstrates complete Gradle multi-module project generation
 */
fun main() {
    println("=".repeat(80))
    println("Gradle Multi-Module Project Generator - Demo")
    println("=".repeat(80))
    println()

    // 1. Load example protos
    println("Step 1: Loading example protos...")
    val projectRoot = Paths.get("").toAbsolutePath().parent ?: Paths.get("").toAbsolutePath()
    val protoSourcePath = projectRoot.resolve("square-protos/src/main/proto")

    if (!protoSourcePath.toFile().exists()) {
        println("ERROR: Proto source directory not found: $protoSourcePath")
        return
    }

    val schemaLoader = SchemaLoader(fileSystem = FileSystem.SYSTEM)
    schemaLoader.initRoots(
        sourcePath = listOf(Location.get(protoSourcePath.toString())),
        protoPath = emptyList()
    )
    val schema = schemaLoader.loadSchema()
    val graph = ProtoDependencyGraph(schema)

    println("  ✓ Loaded ${graph.getAllNodes().size} proto files")
    println()

    // 2. Group protos into modules
    println("Step 2: Grouping protos into modules...")
    val groupingStrategy = PackageBasedGrouping()
    val groupingResult = groupingStrategy.group(graph)

    println("  ✓ Created ${groupingResult.modules.size} modules:")
    groupingResult.modules.forEach { module ->
        println("    - ${module.name} (${module.protoCount} protos, ${module.dependencies.size} deps)")
    }
    println()

    // 3. Generate Gradle project
    println("Step 3: Generating Gradle project...")
    val outputDir = projectRoot.resolve("generated-project")

    val config = GradleProjectConfig(
        projectName = "square-api-protos",
        outputDirectory = outputDir,
        wireVersion = "5.3.5",
        kotlinVersion = "2.2.20",
        javaVersion = 21
    )

    val generator = GradleProjectGenerator(config)
    val result = generator.generate(groupingResult, protoSourcePath)

    // 4. Report results
    println()
    println("=".repeat(80))
    if (result.success) {
        println("✓ SUCCESS: ${result.message}")
    } else {
        println("✗ FAILURE: ${result.message}")
    }
    println("=".repeat(80))
    println()

    println("Generated ${result.filesGenerated.size} files in: ${result.outputDirectory}")
    println()

    println("Modules generated:")
    result.modulesGenerated.sorted().forEach { module ->
        println("  - $module")
    }
    println()

    println("Key files generated:")
    val keyFiles = listOf(
        "settings.gradle.kts",
        "build.gradle.kts",
        ".gitignore"
    )
    result.filesGenerated.filter { file ->
        keyFiles.any { key -> file.endsWith(key) }
    }.sorted().forEach { file ->
        println("  - $file")
    }
    println()

    // 5. Validate no circular dependencies
    if (groupingResult.hasCircularDependencies()) {
        println("⚠ WARNING: Circular dependencies detected!")
    } else {
        println("✓ No circular dependencies detected")
    }
    println()

    // 6. Show build order
    val buildOrder = topologicalSort(groupingResult.modules)
    if (buildOrder != null) {
        println("Build order (topological sort):")
        buildOrder.forEachIndexed { index, moduleName ->
            println("  ${index + 1}. $moduleName")
        }
    } else {
        println("⚠ WARNING: Cannot determine build order (circular dependencies)")
    }
    println()

    // 7. Next steps
    println("=".repeat(80))
    println("Next Steps:")
    println("=".repeat(80))
    println()
    println("To test the generated project:")
    println("  1. cd ${result.outputDirectory}")
    println("  2. ./gradlew build")
    println()
    println("Note: You'll need to add gradle-wrapper files first:")
    println("  1. cd ${result.outputDirectory}")
    println("  2. gradle wrapper --gradle-version 9.2.1")
    println("  3. ./gradlew build")
    println()
}
