# Wire Generator

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

### Milestone 1: Project Setup & Example Data
**Goal**: Establish project foundation and create realistic test data

- [x] Initialize Kotlin CLI project structure
- [ ] Create example proto collection with realistic complexity
  - [ ] Multiple packages with varying dependency relationships
  - [ ] Mix of simple and complex message definitions
  - [ ] Cross-package dependencies
  - [ ] At least 15-20 proto files to demonstrate modularization
- [ ] Document example proto structure and expected module groupings
- [ ] Set up basic CLI framework (argument parsing, help text)

### Milestone 2: Proto Analysis & Parsing
**Goal**: Extract metadata from proto files to understand structure and dependencies

- [ ] Implement proto file parser
  - [ ] Extract package names
  - [ ] Extract message/service definitions
  - [ ] Extract import statements
  - [ ] Handle proto2 and proto3 syntax
- [ ] Build dependency graph
  - [ ] Map proto-to-proto dependencies via imports
  - [ ] Detect circular dependencies
  - [ ] Identify root/leaf protos
- [ ] Support JAR file input
  - [ ] Extract protos from JAR
  - [ ] Preserve directory structure
- [ ] Support directory input
  - [ ] Recursively scan for `.proto` files
  - [ ] Maintain relative paths

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
wire-generator generate --input ./protos --output ./generated-project

# Generate from JAR
wire-generator generate --input protos.jar --output ./generated-project

# Dry-run to preview module structure
wire-generator generate --input ./protos --dry-run

# Use custom configuration
wire-generator generate --input ./protos --config wire-gen.yaml
```

---

## Contributing

This is a research project in early development. Contributions and feedback are welcome as we iterate on the design and implementation.

## License

TBD
