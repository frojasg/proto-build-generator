# Proto Build Generator

A CLI tool for generating modularized, deterministic build configurations for Protocol Buffer definitions using [Wire](https://github.com/square/wire).

## Problem Statement

In organizations that use Protocol Buffers extensively across multiple languages and build systems, managing proto compilation becomes challenging:

- **Duplicated Build Logic**: Each language/build system requires custom build configuration, leading to repeated effort
- **Monolithic Artifacts**: Without proper modularization, proto libraries become bloated with unnecessary dependencies
- **Inconsistent Module Boundaries**: Manual module creation leads to arbitrary or inconsistent groupings
- **Configuration Drift**: Build configurations diverge across teams and projects

## Goals

1. **Automated Modularization**: Analyze proto definitions and automatically create logical module boundaries
2. **Deterministic Output**: Given the same input, always generate the same module structure and build files
3. **Granular Artifacts**: Enable fine-grained dependency management to minimize artifact size
4. **Build System Agnostic** (Future): Start with Gradle + Wire, but design for multi-build-system support
5. **Developer-Friendly**: Simple CLI interface that works with directories or JAR files

## Approach

### Input
- Directory containing `.proto` files
- JAR file containing proto definitions

### Output
- Gradle multi-module project structure
- `build.gradle.kts` files configured for Wire
- `settings.gradle.kts` with module definitions
- Dependency declarations based on proto imports

### Module Grouping Strategy

The tool will analyze:
1. **Package Structure**: Proto package declarations
2. **Import Dependencies**: Which protos depend on others
3. **Logical Boundaries**: Group related protos while respecting dependency graph
4. **Size Constraints**: Avoid creating too-small or too-large modules

---

## Milestones & Tasks

### Milestone 1: Project Setup & Example Data ✅
**Goal**: Establish project foundation and create realistic test data

**Status**: COMPLETED

- [x] Initialize Kotlin CLI project structure
- [x] Create example proto collection with realistic complexity
  - [x] Multiple packages with varying dependency relationships
  - [x] Mix of simple and complex message definitions
  - [x] Cross-package dependencies
  - [x] 23 proto files across 7 packages to demonstrate modularization
- [x] Document example proto structure and expected module groupings
- [x] Validate protos compile with Wire (square-protos module)
- [ ] Set up basic CLI framework (argument parsing, help text) - Deferred to later milestone

#### Implementation & Learnings

**What We Built:**
- Created 23 proto files across 7 packages modeling a Square-inspired e-commerce API
- Packages: `common`, `customer`, `catalog`, `operations`, `commerce`, `payments`, `bookings`
- 33 messages, 28 enums with realistic complexity
- Created `square-protos` Gradle module with Wire 5.3.5 to validate all protos compile

**Key Learnings:**

1. **Wire Gradle Plugin Compatibility**
   - Wire 5.3.5+ required for Gradle 9.2.1 compatibility
   - Earlier versions (4.x, 5.0-5.2) had internal Gradle API issues
   - Plugin repository configuration needed in `pluginManagement` block

2. **Proto3 Enum Scoping with Wire**
   - Wire requires enum values to be unique per **package**, not per enum
   - This is more strict than standard proto3 which scopes enums to the file
   - Solution: Prefix all enum values with their enum name (e.g., `ORDER_STATE_DRAFT`)
   - Example conflict: `InvoiceStatus.DRAFT` vs `OrderState.DRAFT` in same package

3. **Import Validation**
   - Wire strictly validates all import statements
   - Missing imports cause build failures (e.g., `payment.proto` needed `common/address.proto`)
   - Better to catch these early rather than during modularization

4. **Realistic Test Data**
   - Modeled after actual Square API (Orders, Payments, Customers, Catalog)
   - Created deliberate cross-package dependencies to test modularization
   - Hub protos: `common/money.proto` imported by 6+ files
   - Dependency chains: `customer.proto → loyalty_account.proto → loyalty_event.proto`

**Files Created:**
- `protos/` - 23 proto files organized by package
- `protos/EXAMPLE_STRUCTURE.md` - Documentation of structure and expected modules
- `square-protos/` - Gradle module proving protos compile as a monolith (baseline)

**Build Results:**
- ✅ All 23 protos compiled successfully
- ✅ Generated 61 Kotlin files from Wire
- ✅ Zero circular dependencies
- ✅ Clean dependency graph validated

### Milestone 2: Proto Analysis & Parsing (Using wire-schema) ✅
**Goal**: Leverage wire-schema library to extract metadata from proto files and understand structure and dependencies

**Status**: COMPLETED

- [x] Explore wire-schema capabilities
  - [x] Study wire-schema API and documentation
  - [x] Understand how to load and parse proto files
  - [x] Identify what metadata is available (packages, messages, services, imports, etc.)
  - [x] Test with example protos to validate understanding
- [x] Integrate wire-schema into project
  - [x] Add wire-schema dependency to build.gradle.kts
  - [x] Create wrapper/facade for wire-schema functionality (`ProtoDependencyGraph`)
  - [x] Extract package names from parsed schema
  - [x] Extract message/service definitions
  - [x] Extract import/dependency information
- [x] Build dependency graph using wire-schema data
  - [x] Map proto-to-proto dependencies via imports
  - [x] Detect circular dependencies
  - [x] Identify root/leaf protos
- [x] Support directory input
  - [x] Recursively scan for `.proto` files
  - [x] Load proto files into wire-schema via SchemaLoader
  - [x] Maintain relative paths
- [ ] Support JAR file input - Deferred to later milestone
  - [ ] Extract protos from JAR
  - [ ] Feed extracted protos to wire-schema
  - [ ] Preserve directory structure

#### Implementation & Learnings

**What We Built:**

1. **WireSchemaExploration.kt** - Initial exploration script
   - Loads protos using `SchemaLoader.initRoots()`
   - Extracts all metadata: packages, types, imports
   - Analyzes cross-package dependencies
   - Detailed type inspection (messages, enums, fields)

2. **ProtoDependencyGraph.kt** - Core dependency analysis engine
   - `ProtoNode`: Represents a proto file with metadata
   - Dependency tracking: forward (imports) and reverse (dependents)
   - Graph algorithms: transitive closure, root/leaf detection, cycle detection
   - Package-level analysis and cross-package dependency mapping

3. **DependencyGraphDemo.kt** - Comprehensive demonstration
   - Statistics: 23 protos, 33 messages, 28 enums, 0 cycles
   - Root protos: 6 foundation files with no dependencies
   - Leaf protos: 11 top-layer files with no dependents
   - Package grouping and dependency visualization

**Key Learnings:**

1. **wire-schema API Structure**
   - `SchemaLoader`: Entry point for loading protos
   - `Schema`: Container for all loaded proto files with resolved type references
   - `ProtoFile`: Individual proto file with `packageName`, `imports`, `types`, `location`
   - Type hierarchy: `MessageType` (fields), `EnumType` (constants), `EnclosingType` (nested)
   - Schema is "linked" - all type references are resolved across files

2. **Dependency Resolution**
   - Imports are stored as simple strings (file paths)
   - Need to map import strings to actual `ProtoFile` objects
   - wire-schema automatically loads transitive dependencies
   - Built-in protos (`google/protobuf/*`, `wire/*`) are included - need to filter

3. **Graph Construction Insights**
   - **Forward dependencies**: Easy - just read `ProtoFile.imports`
   - **Reverse dependencies**: Must build manually by inverting forward deps
   - **Transitive closure**: BFS/DFS traversal of forward deps
   - **Cycle detection**: DFS with recursion stack tracking

4. **Package-Level Analysis**
   - `packageName` field can be null (handle gracefully)
   - Most useful to group by package for modularization
   - Cross-package dependencies are key for module boundary detection
   - Hub packages (like `common`) are heavily depended upon

5. **okio Dependency**
   - wire-schema uses okio for file system operations
   - Must add `com.squareup.okio:okio:3.9.0` dependency
   - `FileSystem.SYSTEM` for accessing local file system

**Analysis Results from Example Protos:**

```
Graph Statistics:
- 23 proto files (excluding Wire built-ins)
- 7 packages, 33 messages, 28 enums
- 33 total dependencies, 14 cross-package
- 6 root protos (foundation layer)
- 11 leaf protos (top layer)
- 0 circular dependencies ✓

Package Dependencies:
  common (3 protos) → [no dependencies]
  customer (3 protos) → common
  catalog (5 protos) → common
  operations (4 protos) → common, catalog
  commerce (2 protos) → common, customer, catalog, operations
  payments (4 protos) → common, customer, commerce, operations
  bookings (2 protos) → customer, operations

Hub Protos (most depended upon):
  - common/money.proto: 7 dependents
  - operations/location.proto: 5 dependents
  - customer/customer.proto: 5 dependents
```

**Files Created:**
- `app/src/main/kotlin/org/example/app/WireSchemaExploration.kt`
- `app/src/main/kotlin/org/example/app/ProtoDependencyGraph.kt`
- `app/src/main/kotlin/org/example/app/DependencyGraphDemo.kt`

**Dependencies Added:**
- `com.squareup.wire:wire-schema:5.3.5`
- `com.squareup.okio:okio:3.9.0`

### Milestone 3: Module Grouping Algorithm
**Goal**: Develop algorithm to partition protos into logical modules

- [ ] Research modularization strategies
  - [ ] Package-based grouping
  - [ ] Dependency-based clustering
  - [ ] Size-based constraints
- [ ] Implement grouping algorithm
  - [ ] Start with package-based strategy (simplest)
  - [ ] Add dependency-aware optimizations
  - [ ] Handle edge cases (orphaned protos, circular deps)
- [ ] Define module naming conventions
- [ ] Create module metadata structure
- [ ] Validate algorithm with example protos
  - [ ] Ensure no circular module dependencies
  - [ ] Verify sensible module sizes
  - [ ] Check that all protos are assigned to modules

### Milestone 4: Gradle Build Generation
**Goal**: Generate working Gradle multi-module projects with Wire configuration

- [ ] Generate `settings.gradle.kts`
  - [ ] Include all discovered modules
  - [ ] Set project name and structure
- [ ] Generate root `build.gradle.kts`
  - [ ] Common Wire plugin configuration
  - [ ] Shared dependencies
  - [ ] Repository declarations
- [ ] Generate per-module `build.gradle.kts`
  - [ ] Wire plugin application
  - [ ] Proto source set configuration
  - [ ] Inter-module dependencies based on proto imports
  - [ ] External dependencies (if needed)
- [ ] Copy proto files to appropriate module directories
  - [ ] Maintain package structure
  - [ ] Preserve file organization
- [ ] Generate deterministic output
  - [ ] Consistent file ordering
  - [ ] Stable module naming
  - [ ] Reproducible dependency ordering

### Milestone 5: Validation & Testing
**Goal**: Ensure generated projects actually build and work correctly

- [ ] Integration tests
  - [ ] Generate project from example protos
  - [ ] Run `./gradlew build` on generated output
  - [ ] Verify all modules compile successfully
- [ ] Unit tests
  - [ ] Parser correctness
  - [ ] Dependency graph construction
  - [ ] Module grouping algorithm
  - [ ] Build file generation
- [ ] End-to-end tests with real-world proto sets
- [ ] Performance testing with large proto collections (100+ files)

### Milestone 6: Configuration & Customization
**Goal**: Allow users to influence module creation strategy

- [ ] Configuration file support (YAML/TOML)
  - [ ] Module naming patterns
  - [ ] Grouping strategy selection
  - [ ] Size constraints
  - [ ] Explicit module definitions/overrides
- [ ] CLI flags for common options
  - [ ] Output directory
  - [ ] Grouping strategy
  - [ ] Dry-run mode
  - [ ] Verbose logging
- [ ] Module merge/split hints
  - [ ] Force certain protos into same module
  - [ ] Force certain protos into separate modules

### Milestone 7: Documentation & Polish
**Goal**: Make the tool production-ready and user-friendly

- [ ] Comprehensive CLI help text
- [ ] User guide with examples
- [ ] Architecture documentation
- [ ] Generated project README explaining structure
- [ ] Migration guide for existing projects
- [ ] Release automation (versioning, publishing)

### Future Considerations (Post-MVP)
- [ ] Support for additional build systems (Bazel, Maven, Buck)
- [ ] Support for additional proto compilers (protoc, buf)
- [ ] Language-specific code generation beyond Wire
- [ ] Incremental updates (modify existing projects)
- [ ] Visualization of module structure and dependencies
- [ ] IDE integration (IntelliJ plugin)

---

## Technical Stack

- **Language**: Kotlin (JVM)
- **CLI Framework**: TBD (clikt, kotlinx-cli, or similar)
- **Proto Parsing**: TBD (custom parser or existing library)
- **Build Tool**: Gradle with Kotlin DSL
- **Proto Compiler**: Square Wire

## Getting Started (Development)

```bash
# Build the project
./gradlew build

# Run the CLI
./gradlew run --args="--help"

# Run tests
./gradlew test
```

## Usage (Planned)

```bash
# Generate from directory
proto-build-generator generate --input ./protos --output ./generated-project

# Generate from JAR
proto-build-generator generate --input protos.jar --output ./generated-project

# Dry-run to preview module structure
proto-build-generator generate --input ./protos --dry-run

# Use custom configuration
proto-build-generator generate --input ./protos --config proto-build-gen.yaml
```

---

## Contributing

This is a research project in early development. Contributions and feedback are welcome as we iterate on the design and implementation.

## License

TBD
