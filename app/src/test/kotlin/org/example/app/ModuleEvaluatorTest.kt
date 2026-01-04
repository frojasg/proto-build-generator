package org.example.app

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.SchemaLoader
import okio.FileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import java.nio.file.Paths

class ModuleEvaluatorTest {

    private lateinit var graph: ProtoDependencyGraph
    private lateinit var evaluator: ModuleEvaluator

    @BeforeEach
    fun setup() {
        graph = loadExampleProtos()
        evaluator = ModuleEvaluator(graph)
    }

    private fun loadExampleProtos(): ProtoDependencyGraph {
        val projectRoot = Paths.get("").toAbsolutePath().parent ?: Paths.get("").toAbsolutePath()
        val protoSourcePath = projectRoot.resolve("square-protos/src/main/proto")

        if (!protoSourcePath.toFile().exists()) {
            throw IllegalStateException("Proto source directory not found: $protoSourcePath")
        }

        val schemaLoader = SchemaLoader(fileSystem = FileSystem.SYSTEM)
        schemaLoader.initRoots(
            sourcePath = listOf(Location.get(protoSourcePath.toString())),
            protoPath = emptyList()
        )
        val schema = schemaLoader.loadSchema()
        return ProtoDependencyGraph(schema)
    }

    @Nested
    inner class ValidationTests {

        @Test
        fun `should pass for valid package-based grouping`() {
            val grouping = PackageBasedGrouping(StandardModuleNaming())
            val result = grouping.group(graph)

            val validation = evaluator.validate(result)

            assertTrue(validation.isValid, "Validation should pass")
            assertTrue(validation.errors.isEmpty(), "Should have no errors")
        }

        @Test
        fun `should detect missing protos`() {
            // Create a module grouping that intentionally omits some protos
            val nodes = graph.getAllNodes()
            val incompleteModule = ProtoModule(
                name = "incomplete",
                protoFiles = nodes.take(nodes.size - 5) // Omit last 5 protos
            )
            val result = ModuleGroupingResult(listOf(incompleteModule), "test")

            val validation = evaluator.validate(result)

            assertFalse(validation.isValid, "Validation should fail")
            assertTrue(validation.errors.any { it.contains("not assigned to any module") })
        }

        @Test
        fun `should detect duplicate protos in modules`() {
            val nodes = graph.getAllNodes()
            val firstNode = nodes.first()

            val module1 = ProtoModule(
                name = "module1",
                protoFiles = listOf(firstNode)
            )
            val module2 = ProtoModule(
                name = "module2",
                protoFiles = nodes // Contains first node again
            )
            val result = ModuleGroupingResult(listOf(module1, module2), "test")

            val validation = evaluator.validate(result)

            assertFalse(validation.isValid, "Validation should fail")
            assertTrue(validation.errors.any { it.contains("exists in") && it.contains("modules") })
        }

        @Test
        fun `should detect circular dependencies`() {
            val nodes = graph.getAllNodes()
            val node1 = nodes.first()
            val node2 = nodes.drop(1).first()

            // Create circular dependency: module1 -> module2 -> module1
            val module1 = ProtoModule(
                name = "module1",
                protoFiles = listOf(node1),
                dependencies = setOf("module2")
            )
            val module2 = ProtoModule(
                name = "module2",
                protoFiles = listOf(node2),
                dependencies = setOf("module1")
            )
            val result = ModuleGroupingResult(listOf(module1, module2), "test")

            val validation = evaluator.validate(result)

            assertFalse(validation.isValid, "Validation should fail")
            assertTrue(validation.errors.any { it.contains("Circular dependencies") })
        }

        @Test
        fun `should detect unresolved dependencies`() {
            val nodes = graph.getAllNodes()
            val node = nodes.first()

            val module = ProtoModule(
                name = "module1",
                protoFiles = listOf(node),
                dependencies = setOf("nonexistent-module")
            )
            val result = ModuleGroupingResult(listOf(module), "test")

            val validation = evaluator.validate(result)

            assertFalse(validation.isValid, "Validation should fail")
            assertTrue(validation.errors.any { it.contains("unresolved dependencies") })
        }

        @Test
        fun `should warn about empty modules`() {
            val nodes = graph.getAllNodes()

            val module1 = ProtoModule(
                name = "module1",
                protoFiles = nodes
            )
            val module2 = ProtoModule(
                name = "empty-module",
                protoFiles = emptyList()
            )
            val result = ModuleGroupingResult(listOf(module1, module2), "test")

            val validation = evaluator.validate(result)

            assertTrue(validation.hasWarnings(), "Should have warnings")
            assertTrue(validation.warnings.any { it.contains("empty module") })
        }

        @Test
        fun `should warn about isolated modules`() {
            val nodes = graph.getAllNodes()

            val module1 = ProtoModule(
                name = "module1",
                protoFiles = nodes.take(10),
                dependencies = emptySet()
            )
            val module2 = ProtoModule(
                name = "module2",
                protoFiles = nodes.drop(10),
                dependencies = emptySet()
            )
            val result = ModuleGroupingResult(listOf(module1, module2), "test")

            val validation = evaluator.validate(result)

            assertTrue(validation.hasWarnings(), "Should have warnings")
            assertTrue(validation.warnings.any { it.contains("isolated module") })
        }

        @Nested
        inner class ImportValidation {

            @Test
            fun `should detect unsatisfied imports in different modules`() {
                val nodes = graph.getAllNodes()
                // Find a node that has imports
                val importingNode = nodes.find { it.imports.isNotEmpty() } ?: return
                val importedPath = importingNode.imports.first()
                val importedNode = graph.getNode(importedPath) ?: return

                // Put them in different modules without declaring dependency
                val module1 = ProtoModule(
                    name = "module1",
                    protoFiles = listOf(importingNode),
                    dependencies = emptySet() // Missing dependency!
                )
                val module2 = ProtoModule(
                    name = "module2",
                    protoFiles = listOf(importedNode)
                )
                val result = ModuleGroupingResult(listOf(module1, module2), "test")

                val validation = evaluator.validate(result)

                assertFalse(validation.isValid, "Validation should fail")
                assertTrue(validation.errors.any { it.contains("unsatisfied import") })
                assertTrue(validation.errors.any { it.contains(importedPath) })
            }

            @Test
            fun `should pass when imports are in declared dependencies`() {
                // Use the package-based grouping which properly calculates dependencies
                val grouping = PackageBasedGrouping(StandardModuleNaming())
                val result = grouping.group(graph)

                // Find two modules where one depends on the other
                val modulePair = result.modules.find { it.dependencies.isNotEmpty() }

                if (modulePair == null) {
                    // If no module has dependencies, this test doesn't apply
                    return
                }

                // Verify that the validation passes for a properly configured dependency
                val validation = evaluator.validate(result)

                // This should pass because PackageBasedGrouping correctly calculates dependencies
                assertTrue(validation.isValid, "Package-based grouping should have satisfied imports: ${validation.errors}")

                // Now test the opposite: remove a dependency and verify it fails
                val moduleWithDep = modulePair
                val modifiedModule = moduleWithDep.copy(dependencies = emptySet())
                val modifiedModules = result.modules.map {
                    if (it.name == moduleWithDep.name) modifiedModule else it
                }
                val modifiedResult = ModuleGroupingResult(modifiedModules, "test")

                val modifiedValidation = evaluator.validate(modifiedResult)

                // This should potentially fail if the module had imports that required the dependency
                // Check if there are import violations for the modified module
                if (moduleWithDep.protoFiles.any { protoNode ->
                    protoNode.imports.any { importPath ->
                        graph.getNode(importPath)?.let { importedNode ->
                            // Check if imported node is in a different module
                            modifiedModules.find { m -> m.protoFiles.contains(importedNode) }?.name != modifiedModule.name
                        } ?: false
                    }
                }) {
                    // If the module has cross-module imports, validation should fail
                    assertFalse(modifiedValidation.isValid, "Validation should fail when dependencies are removed")
                    assertTrue(modifiedValidation.errors.any { it.contains("unsatisfied import") })
                }
            }

            @Test
            fun `should pass when imports are in same module`() {
                val nodes = graph.getAllNodes()
                // Find a node that has imports to another node we can include
                val importingNode = nodes.find {
                    it.imports.isNotEmpty() && graph.getNode(it.imports.first()) != null
                } ?: return
                val importedPath = importingNode.imports.first()
                val importedNode = graph.getNode(importedPath)!!

                // Put ALL nodes in the same module to avoid import issues
                val module = ProtoModule(
                    name = "module1",
                    protoFiles = nodes,
                    dependencies = emptySet()
                )
                val result = ModuleGroupingResult(listOf(module), "test")

                val validation = evaluator.validate(result)

                // With all nodes in same module, all intra-graph imports should be satisfied
                assertTrue(validation.isValid, "Validation should pass when all nodes are in same module: ${validation.errors}")
                assertTrue(validation.errors.isEmpty())
            }

            @Test
            fun `should report import not found in any module`() {
                val nodes = graph.getAllNodes()
                // Find a node with imports
                val nodeWithImports = nodes.find { it.imports.isNotEmpty() } ?: return

                // Create module with only this node, but not the nodes it imports
                val module = ProtoModule(
                    name = "module1",
                    protoFiles = listOf(nodeWithImports),
                    dependencies = emptySet()
                )
                val result = ModuleGroupingResult(listOf(module), "test")

                val validation = evaluator.validate(result)

                // Should fail because imports are not in any module
                assertFalse(validation.isValid, "Validation should fail")
                assertTrue(validation.errors.any { it.contains("unsatisfied import") })
            }
        }
    }

    @Nested
    inner class MetricsEvaluationTests {

        @Test
        fun `should calculate correct granularity metrics`() {
            val grouping = PackageBasedGrouping(StandardModuleNaming())
            val result = grouping.group(graph)

            val metrics = evaluator.evaluate(result)

            assertTrue(metrics.totalModules > 0, "Should have modules")
            assertTrue(metrics.totalProtos > 0, "Should have protos")
            assertTrue(metrics.minModuleSize > 0, "Min module size should be positive")
            assertTrue(metrics.maxModuleSize >= metrics.minModuleSize, "Max should be >= min")
            assertTrue(metrics.averageModuleSize > 0, "Average should be positive")
            assertTrue(metrics.medianModuleSize > 0, "Median should be positive")
            assertTrue(metrics.giniCoefficient in 0.0..1.0, "Gini should be in [0,1]")
        }

        @Test
        fun `should calculate correct cohesion metrics`() {
            val grouping = PackageBasedGrouping(StandardModuleNaming())
            val result = grouping.group(graph)

            val metrics = evaluator.evaluate(result)

            // Package-based grouping should have perfect cohesion (1.0 packages per module)
            assertTrue(metrics.packagesPerModule >= 1.0, "Should have at least 1 package per module")
            assertTrue(metrics.crossPackageDependenciesWithinModules >= 0, "Cross-package deps should be non-negative")
        }

        @Test
        fun `should calculate correct coupling metrics`() {
            val grouping = PackageBasedGrouping(StandardModuleNaming())
            val result = grouping.group(graph)

            val metrics = evaluator.evaluate(result)

            assertTrue(metrics.totalModuleDependencies >= 0, "Total deps should be non-negative")
            assertTrue(metrics.averageDependenciesPerModule >= 0, "Avg deps should be non-negative")
            assertTrue(metrics.maxDependenciesPerModule >= 0, "Max deps should be non-negative")
            assertTrue(metrics.maxDependencyDepth >= 0, "Max depth should be non-negative")
            assertTrue(metrics.averageFanIn >= 0, "Avg fan-in should be non-negative")
            assertTrue(metrics.maxFanIn >= 0, "Max fan-in should be non-negative")
        }

        @Test
        fun `should calculate correct build efficiency metrics`() {
            val grouping = PackageBasedGrouping(StandardModuleNaming())
            val result = grouping.group(graph)

            val metrics = evaluator.evaluate(result)

            assertTrue(metrics.rootModules > 0, "Should have root modules")
            assertTrue(metrics.leafModules >= 0, "Leaf modules should be non-negative")
            assertTrue(metrics.maxParallelizability > 0, "Max parallel should be positive")
            assertTrue(metrics.criticalPathLength > 0, "Critical path should be positive")
            assertTrue(metrics.buildLevels > 0, "Build levels should be positive")
        }

        @Test
        fun `should calculate quality score in valid range`() {
            val grouping = PackageBasedGrouping(StandardModuleNaming())
            val result = grouping.group(graph)

            val metrics = evaluator.evaluate(result)

            assertTrue(metrics.qualityScore in 0.0..100.0, "Quality score should be in [0,100]")
            assertTrue(metrics.qualityScore > 0, "Quality score should be positive for valid grouping")
        }

        @Test
        fun `perfectly balanced modules should have lower Gini coefficient`() {
            val nodes = graph.getAllNodes()

            // Create balanced modules (all same size)
            val moduleSize = 5
            val balancedModules = nodes.chunked(moduleSize).mapIndexed { index, chunk ->
                ProtoModule(
                    name = "balanced-$index",
                    protoFiles = chunk
                )
            }
            val balancedResult = ModuleGroupingResult(balancedModules, "balanced")

            // Create unbalanced modules (varying sizes)
            val unbalancedModules = listOf(
                ProtoModule(name = "tiny", protoFiles = nodes.take(1)),
                ProtoModule(name = "large", protoFiles = nodes.drop(1))
            )
            val unbalancedResult = ModuleGroupingResult(unbalancedModules, "unbalanced")

            val balancedMetrics = evaluator.evaluate(balancedResult)
            val unbalancedMetrics = evaluator.evaluate(unbalancedResult)

            assertTrue(
                balancedMetrics.giniCoefficient < unbalancedMetrics.giniCoefficient,
                "Balanced modules should have lower Gini coefficient"
            )
        }
    }

    @Nested
    inner class ComparisonAndReportingTests {

        @Test
        fun `comparison should produce formatted output`() {
            val grouping1 = PackageBasedGrouping(StandardModuleNaming())
            val grouping2 = PackageBasedGrouping(FullPackageModuleNaming())

            val result1 = grouping1.group(graph)
            val result2 = grouping2.group(graph)

            val comparison = evaluator.compare("Standard Naming", result1, "Full Package Naming", result2)

            assertTrue(comparison.contains("ALGORITHM COMPARISON"), "Should have header")
            assertTrue(comparison.contains("GRANULARITY"), "Should have granularity section")
            assertTrue(comparison.contains("COHESION"), "Should have cohesion section")
            assertTrue(comparison.contains("COUPLING"), "Should have coupling section")
            assertTrue(comparison.contains("BUILD EFFICIENCY"), "Should have build efficiency section")
            assertTrue(comparison.contains("QUALITY SCORE"), "Should have overall score")
        }

        @Test
        fun `printReport should not throw exceptions`() {
            val grouping = PackageBasedGrouping(StandardModuleNaming())
            val result = grouping.group(graph)

            assertDoesNotThrow {
                val validation = evaluator.validate(result)
                validation.printReport()
            }

            assertDoesNotThrow {
                val metrics = evaluator.evaluate(result)
                metrics.printReport()
            }
        }
    }

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `should handle single module grouping`() {
            val nodes = graph.getAllNodes()
            val singleModule = ProtoModule(
                name = "monolith",
                protoFiles = nodes
            )
            val result = ModuleGroupingResult(listOf(singleModule), "monolith")

            val validation = evaluator.validate(result)
            val metrics = evaluator.evaluate(result)

            assertTrue(validation.isValid, "Single module should be valid")
            assertEquals(1, metrics.totalModules)
            assertEquals(0, metrics.totalModuleDependencies)
            assertEquals(0, metrics.maxDependencyDepth)
            assertEquals(1, metrics.rootModules)
            assertEquals(1, metrics.leafModules)
        }

        @Test
        fun `should handle maximum granularity (one proto per module)`() {
            val nodes = graph.getAllNodes()
            val maxGranularityModules = nodes.mapIndexed { index, node ->
                ProtoModule(
                    name = "module-$index",
                    protoFiles = listOf(node),
                    dependencies = node.imports
                        .mapNotNull { importPath -> graph.getNode(importPath) }
                        .map { importNode -> "module-${nodes.indexOf(importNode)}" }
                        .toSet()
                )
            }
            val result = ModuleGroupingResult(maxGranularityModules, "max-granularity")

            val validation = evaluator.validate(result)
            val metrics = evaluator.evaluate(result)

            assertTrue(validation.isValid, "Max granularity should be valid")
            assertEquals(nodes.size, metrics.totalModules)
            assertEquals(1.0, metrics.averageModuleSize, 0.001)
            assertEquals(1, metrics.minModuleSize)
            assertEquals(1, metrics.maxModuleSize)
        }
    }
}
