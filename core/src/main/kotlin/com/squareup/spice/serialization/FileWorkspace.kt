package com.squareup.spice.serialization

import com.squareup.spice.model.Dependency
import com.squareup.spice.model.Edge
import com.squareup.spice.model.FindResult
import com.squareup.spice.model.InvalidAddress
import com.squareup.spice.model.InvalidGraph
import com.squareup.spice.model.ModuleDocument
import com.squareup.spice.model.NoSuchAddress
import com.squareup.spice.model.Node
import com.squareup.spice.model.Node.ExternalNode
import com.squareup.spice.model.Node.ModuleNode
import com.squareup.spice.model.Node.TestNode
import com.squareup.spice.model.SimpleEdge
import com.squareup.spice.model.Slice
import com.squareup.spice.model.Workspace
import com.squareup.spice.model.WorkspaceDocument
import com.squareup.spice.model.traversal.BreadthFirstDependencyVisitor
import com.squareup.spice.model.util.toSetMultimap
import com.squareup.spice.model.validation.Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.Collections.newSetFromMap
import java.util.concurrent.ConcurrentHashMap

/**
 * A workspace which reads serialized YAML documents to load metadata about its modules.
 *
 * At present, it also ignores any known external workspaces
 *
 * > Note: [FileWorkspace] is expected to be threadsafe, but not race-free. It does rest on the
 * > assumption that the file loaded will be the same if it's loaded multiple times, so the loading
 * > the file - priming the Module and Test nodes, and then adding them, will be identical if it's
 * > done twice.  It does no work to guarantee this consistency, which is left to calling code.
 */
@FlowPreview
@ExperimentalCoroutinesApi
class FileWorkspace internal constructor(
  override val document: WorkspaceDocument,
  private val root: File,
  private val serializer: Serializer,
  internal val sliceIndex: ConcurrentHashMap<String, FileSlice>,
  internal val nodeIndex: ConcurrentHashMap<String, Node>,
  internal val pathLookupIndex: PathIndex,
  private val dynamicallyLoadModulesAlongPath: Boolean = true,
  private val threadCount: Int = 1,
  private val localOnly: Boolean = true,
  private val tracer: Tracer,
  private val followSymlinks: Boolean,
  /** Filename this workspace will scan for module content. */
  private val moduleFileName: String = "module.spice.yml",
  /**
   * Filename this workspace will scan for workspace content. Note, changing this is dangerous,
   * in that using different names in child workspaces can foil the detection of such children,
   * causing its modules to be loaded inappropriately. This workspace assumes that any child
   * workspaces use the same file scheme.
   */
  private val workspaceFileName: String = "workspace.spice.yml"
) : Workspace {
  var fullyLoaded = false; private set

  private val rootDir: File = root.parentFile!!
  companion object {
    fun fromFile(
      file: File, dynamicallyLoadModulesAlongPath: Boolean = true,
      followSymlinks: Boolean = false,
      threadCount: Int = 1,
      tracer: Tracer = Tracer { _, _ -> }
    ): FileWorkspace {
      val serializer = Serializer()
      val workspaceDocument =
        serializer.unmarshall(file.absoluteFile.readText(), WorkspaceDocument::class.java)
      return FileWorkspace(
        document = workspaceDocument,
        threadCount = threadCount,
        root = file,
        serializer = serializer,
        nodeIndex = ConcurrentHashMap(),
        sliceIndex = ConcurrentHashMap(),
        pathLookupIndex = PathIndex(),
        dynamicallyLoadModulesAlongPath = dynamicallyLoadModulesAlongPath,
        tracer = tracer,
        followSymlinks = followSymlinks
      )
    }
  }
  init {
    document.definitions.variants.keys.forEach { sliceIndex[it] = FileSlice(it, this) }
  }

  override val nodes: Sequence<Node> get() {
    if (!fullyLoaded) doCompleteLoad()
    return nodeIndex.values.asSequence()
  }

  /**
   * Scans the file system hierarchy under the workspace root, loading any found modules.
   */
  fun loadAll() = doCompleteLoad()

  private fun isInChildWorkspace(dir: File): Boolean =
    dir.resolve(workspaceFileName).exists() && dir != rootDir

  /**
   * Scans the file system hierarchy under the workspace root, loading any found modules.
   */
  private fun doCompleteLoad(): FileWorkspace {
    val loaded = nodeIndex.map { node -> node.key.trim('/') }.toSet()
    rootDir.walkTopDown()
      .onEnter { dir: File ->
        // Avoid walking deeper than any spice module.
        val local = dir.relativeTo(rootDir).path
        val followSymlinksOrIsNotSymlink = followSymlinks || !Files.isSymbolicLink(dir.toPath())
        local !in loaded &&
          followSymlinksOrIsNotSymlink &&
          !isInChildWorkspace(dir)
      }
      .filter { file -> file.name == moduleFileName }
      .map { it.relativeTo(rootDir).parentFile!!.path }
      .forEach { nodeAt("/$it") }
    fullyLoaded = true
    return this
  }

  override fun validate(validators: List<Validator>) = runBlocking {
    doCompleteLoad()
    variants.asFlow()
      .flatMapMerge(concurrency = threadCount) {
        flow {
          slice(it).validate(validators, *nodes.toList().toTypedArray())
          emit(Unit)
        }
      }
      .flowOn(Dispatchers.IO)
      .collect()
  }

  override fun nodeAt(address: String): Node {
    nodeIndex[address]?.let { return it } // short-circuit
    return when {
      address.matches(TestNode.VALID_ADDRESS_PATTERN) -> {
        nodeAt(address.split(":")[0]) // make sure the module is loaded first.
        nodeIndex[address] ?: throw NoSuchAddress(address)
      }
      address.startsWith("/") -> nodeIndex.getOrPut(address) { loadModule(address) }
      else -> {
        // TODO: Handle external addresses, presumably by delegation
        throw InvalidAddress(address)
      }
    }
  }

  /*
   * A very dirty method which does all sorts of registering and indexing, and then returns the module
   * node, so it can go inside a getOrPut() call. This may be better behind some clearer locking,
   * but for now it's lock-free, under the assumption (guaranteed by calling code of this workspace)
   * that the filesystem contents will be stable, and that this workspace is not valid if they change.
   */
  private fun loadModule(address: String): ModuleNode {
    val moduleDir = root.resolveSibling(address.substring(1))
    val moduleFile = moduleDir.resolve(moduleFileName)
    return try {
      val documentText = tracer.traceNanos("read") {
        moduleFile.readText()
      }
      val moduleDocument = tracer.traceNanos("parse") {
        serializer.unmarshall(documentText, ModuleDocument::class.java)
      }
      val merged = moduleDocument.mergeDefaults(document.definitions)
      ModuleNode(address, merged).also {
        // Perform indexing on node-load.
        val variants = merged.variants
          .onEach { (variantName, variant) ->
            val slice = slice(variantName)
            slice.registerDeps(it.address, variant.deps)
          }
          .flatMap { (variantName, variant) ->
            variant.srcs.map { src -> src to variantName }
          }.toSetMultimap()
        val tests = merged.variants.flatMap { (variantName, variant) ->
          val slice = slice(variantName)
          variant.tests.map { (testName, testConfig) ->
            TestNode(address, variantName, testName, testConfig).also { testNode ->
              nodeIndex[testNode.address] = testNode
              slice.registerDeps(testNode.address, testNode.config.deps)
            }
          }
        }
        pathLookupIndex.addNode(it, variants, tests)
      }
    } catch (e: InvalidGraph) {
      throw e
    } catch (e: Exception) {
      throw NoSuchAddress(address, e)
    }
  }

  private fun FileSlice.registerDeps(address: String, deps: List<Dependency>) {
    depsIndex[address] = deps.mapNotNull { dep ->
      // TODO: Handle external workspaces procedurally
      if (localOnly && !dep.target.startsWith('/')) null
      else if (dep.target == address) null
      else SimpleEdge(dep.target, dep.tags)
    }
    deps.forEach { dep ->
      // TODO: Handle external workspaces procedurally
      if (!localOnly || dep.target.startsWith('/')) {
        if (dep.target != address) {
          rdepsIndex
            .getOrPut(dep.target) { newSetFromMap(ConcurrentHashMap()) }
            .add(SimpleEdge(address, dep.tags))
        }
      }
    }
  }

  /**
   * Identifies the module in which the given [path], specified as an absolute path relative to the
   * workspace root.
   *
   * e.g. `/foo/bar` is valid, whereas `foo/bar` is not, nor is `foo://bar:baz`, etc.
   */
  override fun findModule(path: String): ModuleNode? {
    if (!path.startsWith('/')) throw IllegalArgumentException(
      "Unsupported path: $path. Must be an an absolute path (relative to the workspace root)."
    )
    return pathLookupIndex.moduleForPath(path) ?: let {
      // No module found - attempt to find one
      if (dynamicallyLoadModulesAlongPath) {
        try {
          dynamicallyLoadModule(path)
        } catch (e: NoSuchAddress) {
          null
        }
      } else null
    }
  }

  override fun findNode(path: String): Sequence<FindResult<*>> {
    if (!path.startsWith('/')) throw IllegalArgumentException(
      "Unsupported path: $path. Must be an an absolute path (relative to the workspace root)."
    )
    return pathLookupIndex.nodeForPath(path).ifEmpty {
      if (dynamicallyLoadModulesAlongPath) {
        try {
          dynamicallyLoadModule(path)
          pathLookupIndex.nodeForPath(path).ifEmpty { sequenceOf() }
        } catch (e: NoSuchAddress) {
          sequenceOf<FindResult<*>>()
        }
      } else sequenceOf()
    }
  }

  @Throws(NoSuchAddress::class)
  private fun dynamicallyLoadModule(path: String): ModuleNode? {
    val elements = path.trim('/').split("/")
    var abs = rootDir
    val modulePath = mutableListOf<String>()
    for (element in elements) {
      modulePath.add(element)
      abs = abs.resolve(element)
      with(abs) {
        if (isDirectory) {
          if (resolve(workspaceFileName).exists()) {
            throw NoSuchAddress(path)
          }
          if (resolve(moduleFileName).exists()) {
            return nodeAt(modulePath.joinToString("/", prefix = "/")) as ModuleNode
          }
        }
      }
    }
    return null
  }

  override val variants: List<String> = document.definitions.variants.keys.toList()

  override fun slice(variant: String): FileSlice {
    require(document.definitions.variants.containsKey(variant)) {
      "No such variant \"$variant\" declared in workspace definitions in $root"
    }
    return sliceIndex.getOrPut(variant) { FileSlice(variant, this) }
  }

  @ExperimentalCoroutinesApi
  @FlowPreview
  class FileSlice(
    override val variant: String,
    override val workspace: FileWorkspace,
    val depsIndex: ConcurrentHashMap<String, List<SimpleEdge>> = ConcurrentHashMap(),
    val rdepsIndex: ConcurrentHashMap<String, MutableSet<SimpleEdge>> = ConcurrentHashMap()
  ) : Slice {

    override fun nodeAt(address: String) = workspace.nodeAt(address)

    /**
     * Performs the function it overrides, but does so using a parallel flow, to allow for parallel
     * loading of nodes from the filesystem.
     */
    override fun validate(validators: List<Validator>, vararg roots: String): Slice = runBlocking {
      val nodes = roots.asFlow()
        .flatMapMerge(concurrency = workspace.threadCount) { flow { emit(nodeAt(it)) } }
        .flowOn(Dispatchers.IO)
        .toList()
      validate(validators, *nodes.toTypedArray())
    }

    /**
     * Performs the function it overrides, but pre-loads the transitive graph of the supplied roots
     * prior to invoking validation.
     */
    override fun validate(validators: List<Validator>, vararg roots: Node): Slice {
      load(*roots) // should be complete loaded at this point.
      return super.validate(validators, *roots)
    }

    fun load(vararg roots: Node) {
      BreadthFirstDependencyVisitor { /* Just visit to force loading */ }
        .visit(this, *roots)
    }

    override fun dependenciesOf(address: String): List<Edge> = dependenciesOf(nodeAt(address))

    override fun dependenciesOf(node: Node): List<Edge> = runBlocking {
      when (node) {
        is ModuleNode -> depsIndex[node.address] ?: listOf()
        is TestNode -> depsIndex[node.address] ?: listOf()
        is ExternalNode -> listOf() // TODO - support external addresses.
        else -> throw IllegalStateException("Unsupported node type ${node::class}")
      }
    }

    override fun dependenciesOn(address: String): List<Edge> = dependenciesOn(nodeAt(address))

    override fun dependenciesOn(node: Node): List<Edge> {
      if (!workspace.fullyLoaded) { workspace.doCompleteLoad() }
      return when (node) {
        is ModuleNode -> rdepsIndex[node.address]?.toList() ?: listOf()
        is TestNode -> rdepsIndex[node.address]?.toList() ?: listOf()
        // TODO - support external addresses.
        else -> throw IllegalStateException("Unsupported node type ${node::class}")
      }
    }
  }

  class Tracer(val consumer: (label: String, millis: Long) -> Unit) {
    inline fun <T> traceNanos(label: String, block: () -> T): T {
      val start = System.nanoTime()
      val t = block.invoke()
      val end = System.nanoTime() - start
      if (label !in setOf("read", "parse"))
        label.toString()
      consumer(label, end)
      return t
    }
  }
}
