package com.squareup.spice.serialization

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.spice.model.CyclicReferenceError
import com.squareup.spice.model.FindResult.GeneralFindResult
import com.squareup.spice.model.FindResult.TestFindResult
import com.squareup.spice.model.FindResult.VariantFindResult
import com.squareup.spice.model.InvalidAddress
import com.squareup.spice.model.NoSuchAddress
import com.squareup.spice.model.traversal.DepsCollector
import com.squareup.spice.model.validation.DependencyCycleValidator
import com.squareup.spice.model.validation.STANDARD_VALIDATORS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileNotFoundException

@FlowPreview
@ExperimentalCoroutinesApi
class FileWorkspaceTest {

  private val runfiles by lazy {
    File("${System.getenv("PWD")}/core/src/test/resources/file_workspaces").takeIf {
      it.exists()
    }
  }

  private val resources by lazy {
    javaClass.classLoader?.getResource("file_workspaces")?.toURI()?.let {
      File(it)
    }?.takeIf {
      it.exists()
    }
  }

  private val workspaces: File by lazy {
    runfiles ?: resources ?: error("Unable to find workspace")
  }

  @Test fun noWorkspaceDirectory() {
    assertThrows(FileNotFoundException::class.java) { workspace("no_such_workspace_dir").loadAll() }
  }

  @Test fun noWorkspaceFile() {
    assertThrows(FileNotFoundException::class.java) { workspace("no_workspace_file").loadAll() }
  }

  @Test fun basicNodeLoading() {
    val ws = workspace("basic")
    // Preconditions
    assertThat(ws.document.definitions)
    assertThat(ws.variants).hasSize(2)

    val a = ws.nodeAt("/a")
    assertThat(a.address).isEqualTo("/a")
    val c = ws.nodeAt("/c")
    assertThat(c.address).isEqualTo("/c")
    val f = ws.nodeAt("/group1/f")
    assertThat(f.address).isEqualTo("/group1/f")

    assertThrows(NoSuchAddress::class.java) {
      ws.nodeAt("/q")
    }
  }

  @Test fun forwardDeps() {
    val ws = workspace("basic")
    // Preconditions
    assertThat(ws.document.definitions)
    assertThat(ws.variants).hasSize(2)

    with(ws.slice("debug")) {
      val cDeps = dependenciesOf("/c")
      assertThat(cDeps).hasSize(1)
      val b = cDeps.first()
      assertThat(b.target).isEqualTo("/b")
      val bDeps = dependenciesOf(b.target)
      assertThat(bDeps).hasSize(1)
      val a = bDeps.first()
      assertThat(a.target).isEqualTo("/a")
      assertThat(dependenciesOf(a.target)).isEmpty()
    }

    with(ws.slice("release")) {
      val cDeps = dependenciesOf("/c")
      assertThat(cDeps).hasSize(1)
      val a = cDeps.first()
      assertThat(a.target).isEqualTo("/a")
      assertThat(dependenciesOf(a.target)).isEmpty()
    }
  }

  @Test fun reverseDeps() {
    val ws = workspace("basic").apply { loadAll() }
    // Preconditions
    assertThat(ws.document.definitions)
    assertThat(ws.variants).hasSize(2)

    with(ws.slice("debug")) {
      val aRDeps = dependenciesOn("/a")
      assertThat(aRDeps).hasSize(2)
      assertThat(aRDeps.map { it.target }.toSet()).isEqualTo(setOf("/b", "/group1/f"))

      val cRDeps = dependenciesOn("/c")
      assertThat(cRDeps).hasSize(1)
      assertThat(cRDeps.first().target).isEqualTo("/group1/e")

      val fRDeps = dependenciesOn("/group1/f")
      assertThat(fRDeps).isEmpty()
    }

    with(ws.slice("release")) {
      val aRDeps = dependenciesOn("/a")
      assertThat(aRDeps).hasSize(2)
      assertThat(aRDeps.map { it.target }.toSet()).isEqualTo(setOf("/b", "/c"))

      val cRDeps = dependenciesOn("/c")
      assertThat(cRDeps).hasSize(1)
      assertThat(cRDeps.first().target).isEqualTo("/group1/f")

      val fRDeps = dependenciesOn("/group1/f")
      assertThat(fRDeps).isEmpty()
    }
  }

  @Test fun loadAll() {
    val ws = workspace("basic")
    assertThat(ws.nodeIndex).isEmpty()
    ws.loadAll()
    ws.validate(STANDARD_VALIDATORS)
    assertThat(ws.nodeIndex.size).isEqualTo(10)
  }

  @Test fun loadAllWithCycles() {
    val ws = workspace("cyclic")
    assertThat(ws.nodeIndex).isEmpty()
    assertThrows(CyclicReferenceError::class.java) {
      ws.loadAll()
      ws.validate(STANDARD_VALIDATORS)
    }
  }

  /**
   * bazel copies data directories and resolves (not copies) symlinks, so extract this one from
   * a tarball, to preserve it.
   */
  fun extractWorkspace(tag: String) {
    val dir = File("$workspaces/$tag")
    val tarball = dir.resolve("$tag.tgz")
    require(tarball.exists()) {
      "No such tarball $tarball"
    }
    val builder = ProcessBuilder().apply {
      command("sh", "-c", "tar xvfz $tarball -C $dir")
      directory(File("/tmp"))
    }
    val exitCode = builder.start().waitFor()
    assertThat(exitCode).isEqualTo(0)
  }

  @Test fun loadAllWithSymlinksEnabled() {
    extractWorkspace("with_symlinks")
    val ws = workspace("with_symlinks", followSymlinks = true)
    assertThat(ws.nodeIndex).isEmpty()
    ws.loadAll()
    assertThat(ws.nodeIndex.size).isEqualTo(6)
  }

  @Test fun loadAllWithSymlinksDisabled() {
    extractWorkspace("with_symlinks")
    val ws = workspace("with_symlinks")
    assertThat(ws.nodeIndex).isEmpty()
    ws.loadAll()
    assertThat(ws.nodeIndex.size).isEqualTo(2)
  }

  @Test fun loadAllWithTests() {
    val ws = workspace("with_tests")
    assertThat(ws.nodeIndex).isEmpty()
    ws.loadAll()
    assertThat(ws.nodeIndex.size).isEqualTo(6)
    ws.validate(STANDARD_VALIDATORS)
    val slice = ws.slice("debug")
    with(DepsCollector()) {
      this.visit(slice, slice.nodeAt("/b:debug:unit"))
      assertThat(this.deps).containsExactly("/a", "/b", "/b:debug:unit")
    }
    with(DepsCollector()) {
      this.visit(slice, slice.nodeAt("/a:debug:unit"))
      assertThat(this.deps).containsExactly("/a", "/b", "/a:debug:unit")
    }
  }

  @Test fun loadAllWithDanglingReferences() {
    val ws = workspace("dangling")
    assertThat(ws.nodeIndex).isEmpty()
    // Because the file workspace does a full load which implicitly tries to visit all nodes, its
    // graph walk trips over the missing reference during loadAll().
    val e = assertThrows(NoSuchAddress::class.java) {
      ws.loadAll()
      ws.validate(STANDARD_VALIDATORS)
    }
    assertThat(e).hasMessageThat().contains("No such address found in graph: /q")
  }

  // Permit self-cycles for now.
  @Test fun cycleSelf() {
    workspace("cyclic").slice("main").validate(listOf(DependencyCycleValidator), "/c")
  }

  @Test fun cyclePair() {
    val e = assertThrows(CyclicReferenceError::class.java) {
      workspace("cyclic").slice("main").validate(listOf(DependencyCycleValidator), "/a")
    }
    assertThat(e).hasMessageThat().contains("Cycle detected in 'main' at \"/a\":")
  }

  @Test fun cycleChain() {
    val e = assertThrows(CyclicReferenceError::class.java) {
      workspace("cyclic").slice("main").validate(listOf(DependencyCycleValidator), "/i")
    }
    assertThat(e).hasMessageThat().contains("Cycle detected in 'main' at \"/g\" from /i:")
  }

  @Test fun badVariant() {
    val ws = workspace("basic")
    val error = assertThrows(IllegalArgumentException::class.java) {
      ws.slice("nope")
    }
    assertThat(error).hasMessageThat().contains("No such variant \"nope\"")
  }

  @Test fun lookupModuleItself() {
    val ws = workspace("basic")
    val actual = requireNotNull(ws.findModule("/a"))
    assertThat(actual.module.name).isEqualTo("a")
  }

  @Test fun lookupNodeModuleItself() {
    val ws = workspace("basic")
    val actual = ws.findNode("/a").only()
    actual as GeneralFindResult
    assertThat(actual.node.module.name).isEqualTo("a")
  }

  @Test fun lookupFileInModule() {
    val ws = workspace("basic")
    val actual = requireNotNull(ws.findModule("/a/src/main/java/blah.kt"))
    assertThat(actual.module.name).isEqualTo("a")
  }

  @Test fun lookupNodeForFileInModule() {
    val ws = workspace("basic")
    val actual = ws.findNode("/a/blah.txt").only()
    actual as GeneralFindResult
    assertThat(actual.node.module.name).isEqualTo("a")
  }

  @Test fun lookupNodeForFileInBothVariants() {
    val ws = workspace("basic")
    val actual =
      (ws.findNode("/a/src/main/java/blah.kt").filterIsInstance<VariantFindResult>()).toList()
    assertThat(actual).hasSize(2)
    assertThat(actual.map { it.variant }).containsExactly("debug", "release")
    assertThat(actual.map { it.node.module.name }.toSet()).containsExactly("a")
  }

  @Test fun lookupNodeForFileInOneVariant() {
    val ws = workspace("basic")
    val actual = ws.findNode("/a/src/debug/java/blah.kt").only()
    actual as VariantFindResult
    assertThat(actual.variant).isEqualTo("debug")
    assertThat(actual.node.module.name).isEqualTo("a")
  }

  @Test fun lookupNodeForFileInTest() {
    val ws = workspace("basic")
    val actual = ws.findNode("/a/src/test/java/blah.kt").only()
    actual as TestFindResult
    assertThat(actual.node.address).isEqualTo("/a:debug:unit")
    assertThat(actual.node.module).isEqualTo("/a")
    assertThat(actual.node.variant).isEqualTo("debug")
    assertThat(actual.node.name).isEqualTo("unit")
  }

  @Test fun lookupFileInDeeperModule() {
    val ws = workspace("basic")
    val actual = requireNotNull(ws.findModule("/group1/e/src/main/java/blah.kt"))
    assertThat(actual.module.name).isEqualTo("e")
  }

  @Test fun lookupFileNotInModule() {
    val ws = workspace("basic")
    val actual = ws.findModule("/q/src/main/java/blah.kt")
    assertThat(actual).isNull()
  }

  @Test fun lookupNodeFileNotInModule() {
    val ws = workspace("basic")
    val actual = ws.findNode("/q/src/main/java/blah.kt")
    assertThat(actual.toList()).isEmpty()
  }

  @Test fun lookupBadAddress() {
    val ws = workspace("basic")
    assertThrows(IllegalArgumentException::class.java) {
      ws.findModule("blah://blah.foo:bar")
    }
  }

  @Test fun lookupNodeBadAddress() {
    val ws = workspace("basic")
    assertThrows(IllegalArgumentException::class.java) {
      ws.findNode("blah://blah.foo:bar")
    }
  }

  @Test fun childModules() {
    val e = assertThrows(InvalidAddress::class.java) { workspace("child_modules").loadAll() }
    assertThat(e).hasMessageThat().contains("Unsupported address: /c")
    assertThat(e).hasMessageThat().isNotNull()
    assertThat(e).hasMessageThat().isNotEmpty()
    with(e.message!!) {
      // loading order is not guaranteed, so the error may come from above or below for nested
      // children. This structure avoids the flakiness inherent in that indeterminacy.
      assertWithMessage(
        "Address should be invalidated either from above or below, depending on load order"
      ).that(
        this.contains("/c/d (Path is below existing address /c)") ||
          this.contains("/c (Path is above existing addresses [d])")
      ).isTrue()
    }
  }

  @Test fun nestedModules() {
    val ws = workspace("nested_workspaces").loadAll()
    assertThat(ws.findNode("/a").only().node.address).isEqualTo("/a")
    assertThat(ws.findNode("/b").only().node.address).isEqualTo("/b")
    assertThat(ws.findNode("/nested/c").toList()).isEmpty()
    assertThat(ws.findNode("/nested/d").toList()).isEmpty()
  }

  private fun workspace(
    tag: String,
    loadOnLookup: Boolean = true,
    followSymlinks: Boolean = false
  ): FileWorkspace =
    FileWorkspace.fromFile(
      workspaces.resolve(tag).resolve("workspace.spice.yml"),
      dynamicallyLoadModulesAlongPath = loadOnLookup,
      followSymlinks = followSymlinks
    )

  private fun <T> Sequence<T>.only(): T {
    if (this.count() != 1) throw AssertionError("Expected iterable to have 1 element: $this")
    return first()
  }
}
