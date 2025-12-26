package org.example.app

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.SchemaLoader
import okio.FileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.nio.file.Paths

@DisplayName("Module Grouping Tests")
class ModuleGroupingTest {

    @Nested
    @DisplayName("Module Naming Convention Tests")
    inner class ModuleNamingTests {

        @Test
        @DisplayName("Should strip 'com.' prefix and replace dots with hyphens")
        fun testComPackageNaming() {
            val strategy = PackageBasedGrouping()
            // Use reflection to access private method for testing
            val method = strategy::class.java.getDeclaredMethod("packageToModuleName", String::class.java)
            method.isAccessible = true

            assertEquals("square-customer", method.invoke(strategy, "com.square.customer"))
            assertEquals("square-payments", method.invoke(strategy, "com.square.payments"))
            assertEquals("example-service", method.invoke(strategy, "com.example.service"))
        }

        @Test
        @DisplayName("Should strip 'org.' prefix")
        fun testOrgPackageNaming() {
            val strategy = PackageBasedGrouping()
            val method = strategy::class.java.getDeclaredMethod("packageToModuleName", String::class.java)
            method.isAccessible = true

            assertEquals("mycompany-api", method.invoke(strategy, "org.mycompany.api"))
        }

        @Test
        @DisplayName("Should strip 'io.' prefix")
        fun testIoPackageNaming() {
            val strategy = PackageBasedGrouping()
            val method = strategy::class.java.getDeclaredMethod("packageToModuleName", String::class.java)
            method.isAccessible = true

            assertEquals("grpc-health-v1", method.invoke(strategy, "io.grpc.health.v1"))
        }

        @Test
        @DisplayName("Should handle single segment package names")
        fun testSingleSegmentPackage() {
            val strategy = PackageBasedGrouping()
            val method = strategy::class.java.getDeclaredMethod("packageToModuleName", String::class.java)
            method.isAccessible = true

            assertEquals("example", method.invoke(strategy, "com.example"))
        }
    }

    @Nested
    @DisplayName("Module Grouping Result Tests")
    inner class ModuleGroupingResultTests {

        @Test
        @DisplayName("Should detect circular dependencies")
        fun testCircularDependencyDetection() {
            // Create modules with circular dependency: A -> B -> C -> A
            val moduleA = ProtoModule("module-a", emptyList(), setOf("module-b"))
            val moduleB = ProtoModule("module-b", emptyList(), setOf("module-c"))
            val moduleC = ProtoModule("module-c", emptyList(), setOf("module-a"))

            val result = ModuleGroupingResult(
                modules = listOf(moduleA, moduleB, moduleC),
                strategy = "test",
                stats = ModuleGroupingStats(3, 0, 0, 0, 0.0, 0, 0, 3, 1.0)
            )

            assertTrue(result.hasCircularDependencies(), "Should detect circular dependency")
        }

        @Test
        @DisplayName("Should not detect circular dependencies in acyclic graph")
        fun testNoCircularDependencies() {
            // Create acyclic dependency: A -> B -> C
            val moduleA = ProtoModule("module-a", emptyList(), setOf("module-b"))
            val moduleB = ProtoModule("module-b", emptyList(), setOf("module-c"))
            val moduleC = ProtoModule("module-c", emptyList(), emptySet())

            val result = ModuleGroupingResult(
                modules = listOf(moduleA, moduleB, moduleC),
                strategy = "test",
                stats = ModuleGroupingStats(3, 0, 0, 0, 0.0, 0, 0, 2, 0.67)
            )

            assertFalse(result.hasCircularDependencies(), "Should not detect circular dependency")
        }

        @Test
        @DisplayName("Should identify root modules correctly")
        fun testRootModuleIdentification() {
            val moduleA = ProtoModule("module-a", emptyList(), emptySet()) // Root
            val moduleB = ProtoModule("module-b", emptyList(), setOf("module-a"))
            val moduleC = ProtoModule("module-c", emptyList(), emptySet()) // Root

            val result = ModuleGroupingResult(
                modules = listOf(moduleA, moduleB, moduleC),
                strategy = "test",
                stats = ModuleGroupingStats(3, 0, 0, 0, 0.0, 0, 0, 1, 0.33)
            )

            val roots = result.findRootModules()
            assertEquals(2, roots.size, "Should find 2 root modules")
            assertTrue(roots.any { it.name == "module-a" })
            assertTrue(roots.any { it.name == "module-c" })
        }

        @Test
        @DisplayName("Should identify leaf modules correctly")
        fun testLeafModuleIdentification() {
            val moduleA = ProtoModule("module-a", emptyList(), emptySet())
            val moduleB = ProtoModule("module-b", emptyList(), setOf("module-a"))
            val moduleC = ProtoModule("module-c", emptyList(), setOf("module-a")) // Leaf

            val result = ModuleGroupingResult(
                modules = listOf(moduleA, moduleB, moduleC),
                strategy = "test",
                stats = ModuleGroupingStats(3, 0, 0, 0, 0.0, 0, 0, 2, 0.67)
            )

            val leaves = result.findLeafModules()
            assertEquals(2, leaves.size, "Should find 2 leaf modules")
            assertTrue(leaves.any { it.name == "module-b" })
            assertTrue(leaves.any { it.name == "module-c" })
        }

        @Test
        @DisplayName("Should retrieve module by name")
        fun testGetModuleByName() {
            val moduleA = ProtoModule("module-a", emptyList(), emptySet())
            val result = ModuleGroupingResult(
                modules = listOf(moduleA),
                strategy = "test",
                stats = ModuleGroupingStats(1, 0, 0, 0, 0.0, 0, 0, 0, 0.0)
            )

            assertNotNull(result.getModule("module-a"))
            assertNull(result.getModule("non-existent"))
        }
    }

    @Nested
    @DisplayName("Topological Sort Tests")
    inner class TopologicalSortTests {

        @Test
        @DisplayName("Should produce valid topological order for acyclic graph")
        fun testValidTopologicalSort() {
            // A -> B -> C
            val moduleA = ProtoModule("A", emptyList(), setOf("B"))
            val moduleB = ProtoModule("B", emptyList(), setOf("C"))
            val moduleC = ProtoModule("C", emptyList(), emptySet())

            val order = topologicalSort(listOf(moduleA, moduleB, moduleC))
            assertNotNull(order)
            assertEquals(3, order!!.size)

            // C should come before B, B should come before A
            assertTrue(order.indexOf("C") < order.indexOf("B"))
            assertTrue(order.indexOf("B") < order.indexOf("A"))
        }

        @Test
        @DisplayName("Should return null for cyclic graph")
        fun testCyclicGraphReturnsNull() {
            // A -> B -> A (cycle)
            val moduleA = ProtoModule("A", emptyList(), setOf("B"))
            val moduleB = ProtoModule("B", emptyList(), setOf("A"))

            val order = topologicalSort(listOf(moduleA, moduleB))
            assertNull(order, "Should return null for cyclic graph")
        }

        @Test
        @DisplayName("Should handle single module")
        fun testSingleModule() {
            val moduleA = ProtoModule("A", emptyList(), emptySet())
            val order = topologicalSort(listOf(moduleA))

            assertNotNull(order)
            assertEquals(1, order!!.size)
            assertEquals("A", order[0])
        }

        @Test
        @DisplayName("Should handle disconnected modules")
        fun testDisconnectedModules() {
            // A and B have no dependencies or relationships
            val moduleA = ProtoModule("A", emptyList(), emptySet())
            val moduleB = ProtoModule("B", emptyList(), emptySet())

            val order = topologicalSort(listOf(moduleA, moduleB))
            assertNotNull(order)
            assertEquals(2, order!!.size)
        }

        @Test
        @DisplayName("Should handle complex DAG")
        fun testComplexDAG() {
            // Diamond dependency: A -> B -> D, A -> C -> D
            val moduleA = ProtoModule("A", emptyList(), setOf("B", "C"))
            val moduleB = ProtoModule("B", emptyList(), setOf("D"))
            val moduleC = ProtoModule("C", emptyList(), setOf("D"))
            val moduleD = ProtoModule("D", emptyList(), emptySet())

            val order = topologicalSort(listOf(moduleA, moduleB, moduleC, moduleD))
            assertNotNull(order)
            assertEquals(4, order!!.size)

            // D should come first (no dependencies)
            assertEquals("D", order[0])
            // A should come last (depends on everything)
            assertEquals("A", order[3])
        }
    }

    @Nested
    @DisplayName("Integration Tests with Real Protos")
    inner class IntegrationTests {

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

        @Test
        @DisplayName("Should create expected number of modules from example protos")
        fun testExpectedModuleCount() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            assertEquals(7, result.modules.size, "Should create 7 modules from example protos")
        }

        @Test
        @DisplayName("Should create all expected module names")
        fun testExpectedModuleNames() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            val expectedNames = setOf(
                "square-common",
                "square-customer",
                "square-catalog",
                "square-operations",
                "square-commerce",
                "square-payments",
                "square-bookings"
            )

            val actualNames = result.modules.map { it.name }.toSet()
            assertEquals(expectedNames, actualNames, "Should create all expected modules")
        }

        @Test
        @DisplayName("Should assign all protos to modules")
        fun testAllProtosAssigned() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            val totalProtosInModules = result.modules.sumOf { it.protoCount }
            assertEquals(
                graph.getAllNodes().size,
                totalProtosInModules,
                "All protos should be assigned to modules"
            )
        }

        @Test
        @DisplayName("Should have no circular dependencies in real protos")
        fun testNoCircularDependencies() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            assertFalse(result.hasCircularDependencies(), "Should have no circular dependencies")
        }

        @Test
        @DisplayName("Should identify square-common as root module")
        fun testCommonIsRoot() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            val rootModules = result.findRootModules()
            assertEquals(1, rootModules.size, "Should have exactly 1 root module")
            assertEquals("square-common", rootModules[0].name, "square-common should be the root")
        }

        @Test
        @DisplayName("Should verify specific module dependencies")
        fun testSpecificModuleDependencies() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            // Test square-payments dependencies
            val payments = result.getModule("square-payments")
            assertNotNull(payments)
            assertTrue(payments!!.dependencies.contains("square-common"))
            assertTrue(payments.dependencies.contains("square-customer"))
            assertTrue(payments.dependencies.contains("square-commerce"))
            assertTrue(payments.dependencies.contains("square-operations"))

            // Test square-customer dependencies
            val customer = result.getModule("square-customer")
            assertNotNull(customer)
            assertEquals(setOf("square-common"), customer!!.dependencies)
        }

        @Test
        @DisplayName("Should produce valid build order")
        fun testValidBuildOrder() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            val buildOrder = topologicalSort(result.modules)
            assertNotNull(buildOrder, "Should produce valid build order")
            assertEquals(7, buildOrder!!.size, "Build order should include all modules")

            // square-common should be first (it's the root)
            assertEquals("square-common", buildOrder[0])
        }

        @Test
        @DisplayName("Should have reasonable module sizes")
        fun testModuleSizes() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            // Verify module sizes are reasonable (between 1 and 10 protos)
            result.modules.forEach { module ->
                assertTrue(module.protoCount >= 1, "${module.name} should have at least 1 proto")
                assertTrue(module.protoCount <= 10, "${module.name} should have at most 10 protos")
            }
        }

        @Test
        @DisplayName("Should calculate statistics correctly")
        fun testStatistics() {
            val graph = loadExampleProtos()
            val strategy = PackageBasedGrouping()
            val result = strategy.group(graph)

            val stats = result.stats

            assertEquals(7, stats.totalModules)
            assertEquals(23, stats.totalProtos)
            assertTrue(stats.totalMessages > 0)
            assertTrue(stats.totalEnums > 0)
            assertTrue(stats.averageProtosPerModule > 0)
            assertEquals(2, stats.smallestModule)
            assertEquals(5, stats.largestModule)
        }
    }

    // Note: ProtoModule functionality is thoroughly tested in integration tests
    // with real proto data, which provides more realistic and valuable test coverage
    // than unit tests with mocked ProtoNodes.
}
