# Spice - Design and Context
***Consistent Build metadata for cross-build system definition***

*“He who controls the spice, controls the universe”* - Frank Herbert

> Note: This is a redacted internal design document from Square, so it might read a bit
> oddly. Many things were removed that referenced internal projects, or specific pro/con analyses relevant
> only to internal square projects. It was a working doc, and is not necessarily going to be updated,
> but is provided to add some context to the project.

# Overview
Build systems are highly configurable, have varying degrees of freedom, may be turing complete, and make
differing assumptions about their conditions. This makes translating between build systems fraught and brittle.

Migrations between build systems are often subject to massive churn, varying assumptions requiring a lot of
normalization, etc. Additionally, maintaining control over large codebases with the ability to do heavy logic
work in the build language, means that build-systems' worst-case behaviors are quite often the easiest path for
developers to get un-stuck in teh short run (i.e. adding more and more logic to gradle's buildSrc). Further, some
build systems have very robust graph-management and querying tooling, and others to not. In all of these situations,
having a stable representation of the build metadata, regardless of chosen build tool, can allow for easier migration,
control over large codebases' build graph, or even enable features not easily (or performantly) accessed in one's 
chosen build system.

Some declarative metadata systems exist, such as Cocoapods or Maven, but these have their own challenges.

Spice intends to be a stable declarative metadata that encapsulates all of the things necessary to generate a
gradle or bazel (or any other) build from one stable source, to be the source of truth for build metadata.
From this metadata builds can be generated for developers (e.g. gradle or bazel), for CI (bazel), for graph-aware
tooling (linting, structural validations, unaffected-test-avoidance, etc.).

# Context

Modern software is often large, complex, and modular. That modularity and the relationships between modules is
typically encapsulated in the configuration language of its chosen build system.  Very typically, it’s mixed
together with build step instructions, packaging/release instructions, details of what build extensions are to
be used, and a variety of metadata.  This leads the build system to be the center of the universe, in some ways,
which further leads to relying on the build system for all sorts of use-cases.

In some cases this is handled well, in others it can lead to bloat and degrading builds. The more powerful the
build tool’s DSL, the more uses to which it’s put, the cycle expands, leading to slow tool execution just to
get simple metadata back out for other tools to consume, or forcing those tools deeper into the build system
layers. Such complexity can be constrained, but most build systems don’t have the means to do so, or it can
be worked around with lazy execution and layers of caching.

The authors take the view that the core of the software - what is being built and how it relates to other
things being built - can be best expressed in declarative metadata that is strictly correct, exact, and
clear of entanglements with the build language. With such a declarative metadata system, any build tool,
or any other tool that could benefit from speedy access to the build graph, can be optimized, cleared of
cruft, and constrained to categorical (not ad-hoc) uses.  This, in turn, can be harnessed to much cleaner
build graphs (in any build tool), rationalized CI systems, much faster tooling, and can allow for eliminating
unnecessary computation and I/O for many tasks. For organizations with increasingly large scale development,
this is crucial for managing software and tooling technical debt, as well as stable infrastructure.

# Requirements
  1. Minimally invasive on the developer workflow
  2. Build system generation should be < 15s
  3. Metadata will force few opinions, but will enforce them clearly
  4. Must express enough information to generate a build graph in Gradle and Bazel
  5. Concise and simple to understand build files (notwithstanding sheer length)
     1. Apply the principle of least surprise.
     2. As Simple as Possible and no simpler

# Benefits and Concerns
## Benefits
  - Allows versatility in choosing build tools
  - Enables build/test avoidance for CI (don't build what isn't affected) beyond what build systems are capable of
  - Simplifies repository layout and validation by separating the validation from the build.
    - "Strict" deps (declare only what you use) can be simplified immensely using symbol analysis
      - a complete knowledge of the graph allows better automation
    - Ordering of resources can be managed via symbol analysis and templating
  - Simplify variant "trimming" as done by engBuild
  - Enables low cost experimentation with multiple build systems and approaches, such as composite builds in Gradle.

## Concerns
  - Abstracts the build system from the developers
  - Requires a categorical approach to some build problems (TBD - explanation of category plugins)
  - Initially limited IDE support
    - e.g. Android Studio understands gradle, not Spice.
  - Introduces a new tool in need of maintenance


# Prior Art
A discussion of similar attempts and systems that have been tried.
## Dropbox
  - https://dropbox.tech/mobile/modernizing-our-android-build-system-part-i-the-planning
  - Notes (wip):
    - Without seeing the drop box implementation, we can only speculate based on poor data what the issues were.
    - “BMBF required a very specific file and folder structure.”
    - “ BMBF was not compatible with Gradle incremental builds because build.gradle files were being re-created every time a developer built the app”
      - Build files should only be built at a workspace refresh, pull  from master or module addition.
      - Option 1: A daemon can be used to understand changes in the workspace and only generate the gradle files as necessary.
      - Option 2: Build a gradle plugin that reads spice files and configure the graph.
    - “ It was not uncommon for engineers who had been with the company for 6+ months to still had no idea how to create a new module using BMBF. “
      - Adding new modules should be tool based, rather than bespoke.
        - a simple script to create a module file and generate the build system configuration would go a long way.
    - “BMBF was opinionated, it made it difficult to add functionality to our Gradle scripts that were not built into BMBF.”
      - Developers adding bespoke build scripts without oversight of the repository maintainers is not ideal, as it increases the support burden and costs at-scale.
        - example of bespoke configuration:
          https://git.sqcorp.co/projects/ANDROID/repos/register/browse/reader-sdk-2/mockreader-builder/build.gradle?at=1bac12ab3d0018d93b9f543394520f7683403f35
      - Make the metadata un-opinionated and as simple as possible
      - Codify extension points (i.e. codegen extensions like anvil or proto, analysis extensions, etc.)
        - Use cases to consider:
          * Anvil
          * Custom source locations, e.g. sqldelight
          * Additional metadata, such as Android
          
## Cocoapods
  - How is it related?
    - A stable metadata specification for the dependency graph and module semantics
    - A big part of the success of Square's iOS bazel migration was that cocopods provided such a stable base for
      migration tooling.
  - Could we just use Cocoapods?
    - iOS specific
    - Metadata language offers conditionals and some imperative features with runtime
    - Mixes metadata around build specifics and packaging with the declarative stuff


## Maven
- How is it related?
  - While a build system in its own right, it is both a build system and a metadata system.
  - Maven did have the right idea around build metadata.
  - In theory the POM
    could serve as such a build metadata structure, but there are quite a few issues:
    - xml is human-unreadable
    - the POM has extensive mixing of pure-build metadata and additional build plugin configuration
- Due to maven metadata's prevalence, a direct mapping to maven dependencies will be important, especially in the 
  JVM languages build space (i.e. gradle and bazel) for external (non-project) dependencies
- TBD details: what we like, how we have to differ, etc.

  

# Examples
## Example spice yml workspace
```yaml
tools:
- name: "dagger"
  any: ["kotlin", "java"]
- name: "anvil"
  all: ["kotlin", "dagger"]
- name: "android"
  all: ["kotlin", "java"]
- name: "kotlin"
- name: "sqldelight"
  all: ["kotlin"]
  not: ["sqldelight_legacy"]
- name: "test"

external:
- name: "maven",
  urls: [...repo urls..],
  artifacts: { com.foo.thing : "0.4.1" }
```

## Example spice yml module

```yaml
shared:
srcs: ["src/main"]
deps: ["/other:module", "/maven/com.foo.thing"]
tools: ["android", "hephaestus"]

variant:
- name: "debug"
  srcs: ["src/debug"]
  deps: ["/debug/module"]
- name: "v2"
  overlay: ["debug"]
  deps: ["/other:module"]
- name: "v1"
  overlay: ["debug"]
```

## Example Gradle Kotlin Template

> Note: An early example, from an earlier version of the design. Treat as pseudo code to illustrate.

```kotlin
pluginNames = mapOf(
  "anvil" to "com.squareup.anvil"
)
Cabinet(".").render { spice ->
  spice.workspace.apply {
    Gradle.Workspace { 
      block("plugins") {
        tools.forEach { p ->
          call("invoke", pluginNames[p.name], call("version", p.version)
        }
      }
      block("repositories") {
        external.urls.forEach{ u -> 
          block("maven") { call("url", u.url) }
        }
      }
    }
  }
  spice.modules.forEach { module ->
    Gradle.Build(module.path) {
     module.tools.forEach { p -> 
        id pluginNames[p.name]
     }
     block("android") { 
       module.variants.forEach { v -> 
         block(v.name) { ... variant specific attributes ... }
       }
     }
     block("dependencies") {
       module.shared.deps { d -> 
           when {
             d.external -> "main" implementation d.coordinates
             else -> "main" implementation call("project", d.coordinates)
           }
      }
      module.variants.forEach { v -> 
        when {
          // TODO: fix this
          d.external -> v.name (implementation) (d.coordinates)
          else -> v.name (implementation) call("project", d.coordinates)
        }
      }
    }
  }

```

# Storage Format Options
  - Protocol buffers
    - A bit odd to work with, but has mature tooling support
    - pro: can define types, has ide support
    - con: No easy validation
      - Validation logic can be via custom_options
  - Yaml
    - Common and already in use for configurations
    - pro: can define types, has ide support
    - con: No easy validation
  - Json
    - Commonly known
    - pro: mature tooling support
    - con: No easy validation
  - Xml
    - Hahahahahahahahahahahahahahahahahahaha.  Oh wait you’re serious. Let me laugh harder.
    - con: verbose
  - Custom
    - Unfamiliar syntax
  - Subset of Gradle
    - con: too loose, expectation of kotlin or groovy.
  - Kotlin DSL
    - Compile-type-safe, language known
    - pro: can build validation into the dsl
    - con: need to ban conditionals and other language features, like variables, function definitions, classes, etc. etc.
  - Subset of Starlark
    - con: may be slow to execute.
    - con: increases complexity of metadata by adding functions and conditionals

# Terminology
  - Project (a.k.a. node) - the unit being configured, which is in relationship to other Projects via dependencies, and to which tools are applied.
    - cf: gradle project, bazel target
  - Dependency (a.k.a. edge) - a relationship between Projects, forming a DAG
  - Tool - A label referencing some tooling which will be applied to the node in different situations.
    - Tools are abstract in the metadata, and applied appropriately in whatever context.
    - E.g. “java” implying java compilation in build systems, but may be ignored in query contexts.
    - Another way to look at Tools is as a configuration signal, btu since typically this refers to either a toolchain or a particularly configured toolchain, tool is the term.
    
# Evolving design decisions
> Provisional design decisions which may be changed, but should be noted here so we keep track.

  - Dependency exclusions and deps surgery
    - Bazel-style workspace-level specification
      - Consuming maven/gradle decl-site needs to be a function of the importer
    - Workspace needs add/remove/replaceAll semantic hooks for deps surgery
    - Declaration-site deps surgery needs to be done with new projects which encapsulate the “reformed” node.


