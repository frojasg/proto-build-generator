package org.example.app

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.SchemaLoader
import java.nio.file.Paths

/**
 * Exploration script to understand wire-schema capabilities by loading
 * and analyzing the example Square protos.
 */
fun main() {
    println("=".repeat(80))
    println("Wire Schema Exploration")
    println("=".repeat(80))

    // Path to our example protos (relative to project root)
    val projectRoot = Paths.get("").toAbsolutePath().parent ?: Paths.get("").toAbsolutePath()
    val protoSourcePath = projectRoot.resolve("square-protos/src/main/proto")

    if (!protoSourcePath.toFile().exists()) {
        println("ERROR: Proto source directory not found: $protoSourcePath")
        println("Current working directory: ${Paths.get("").toAbsolutePath()}")
        println("Project root: $projectRoot")
        return
    }

    println("\n1. Loading protos from: $protoSourcePath")
    println("-".repeat(80))

    try {
        // Create SchemaLoader and load all protos
        val schemaLoader = SchemaLoader(fileSystem = fileSystemOf(protoSourcePath))
        schemaLoader.initRoots(
            sourcePath = listOf(Location.get(protoSourcePath.toString())),
            protoPath = emptyList()
        )
        val schema = schemaLoader.loadSchema()

        println("✓ Successfully loaded schema")
        println()

        // 2. Explore ProtoFiles
        println("2. Proto Files Loaded")
        println("-".repeat(80))
        val protoFiles = schema.protoFiles
        println("Total proto files: ${protoFiles.size}")
        protoFiles.forEach { protoFile ->
            println("  - ${protoFile.location.path}")
        }
        println()

        // 3. Explore Packages
        println("3. Packages Found")
        println("-".repeat(80))
        val packages = protoFiles.mapNotNull { it.packageName }.distinct().sorted()
        println("Total packages: ${packages.size}")
        packages.forEach { pkg ->
            val filesInPackage = protoFiles.count { it.packageName == pkg }
            println("  - $pkg ($filesInPackage files)")
        }
        println()

        // 4. Explore Types (Messages and Enums)
        println("4. Type Definitions")
        println("-".repeat(80))
        println("Total types: ${schema.types.size}")

        val messageTypes = schema.types.filterIsInstance<com.squareup.wire.schema.MessageType>()
        val enumTypes = schema.types.filterIsInstance<com.squareup.wire.schema.EnumType>()

        println("  Messages: ${messageTypes.size}")
        println("  Enums: ${enumTypes.size}")
        println()

        // 5. Explore Imports/Dependencies
        println("5. Import Analysis")
        println("-".repeat(80))
        protoFiles
            .filter { it.imports.isNotEmpty() }
            .sortedBy { it.location.path }
            .forEach { protoFile ->
                println("${protoFile.location.path}")
                println("  Package: ${protoFile.packageName}")
                println("  Imports:")
                protoFile.imports.forEach { import ->
                    println("    - $import")
                }
                println()
            }

        // 6. Detailed example: Analyze one proto file
        println("6. Detailed Analysis: commerce/order.proto")
        println("-".repeat(80))
        val orderProto = protoFiles.find { it.location.path.contains("commerce/order.proto") }
        if (orderProto != null) {
            println("Package: ${orderProto.packageName}")
            println("Syntax: ${orderProto.syntax}")
            println("\nImports:")
            orderProto.imports.forEach { println("  - $it") }

            println("\nTypes defined:")
            orderProto.types.forEach { type ->
                when (type) {
                    is com.squareup.wire.schema.MessageType -> {
                        println("  Message: ${type.type.simpleName}")
                        println("    Fields: ${type.fields.size}")
                        type.fields.take(3).forEach { field ->
                            println("      - ${field.name}: ${field.type}")
                        }
                        if (type.fields.size > 3) {
                            println("      ... and ${type.fields.size - 3} more")
                        }
                    }
                    is com.squareup.wire.schema.EnumType -> {
                        println("  Enum: ${type.type.simpleName}")
                        println("    Constants: ${type.constants.size}")
                        type.constants.take(5).forEach { constant ->
                            println("      - ${constant.name} = ${constant.tag}")
                        }
                    }
                    else -> println("  Other type: ${type.type}")
                }
            }
        }
        println()

        // 7. Dependency Graph Preview
        println("7. Cross-Package Dependencies")
        println("-".repeat(80))
        val crossPackageDeps = protoFiles
            .flatMap { protoFile ->
                protoFile.imports
                    .mapNotNull { import ->
                        val importedFile = protoFiles.find { it.location.path.endsWith(import) }
                        if (importedFile != null && importedFile.packageName != protoFile.packageName) {
                            "${protoFile.packageName} -> ${importedFile.packageName}"
                        } else null
                    }
            }
            .distinct()
            .sorted()

        println("Cross-package dependencies found: ${crossPackageDeps.size}")
        crossPackageDeps.forEach { println("  $it") }
        println()

        println("=".repeat(80))
        println("Exploration Complete!")
        println("=".repeat(80))

    } catch (e: Exception) {
        println("ERROR: Failed to load schema")
        e.printStackTrace()
    }
}

// Helper to create file system - will need to import proper wire FileSystem
private fun fileSystemOf(path: java.nio.file.Path): okio.FileSystem {
    return okio.FileSystem.SYSTEM
}
