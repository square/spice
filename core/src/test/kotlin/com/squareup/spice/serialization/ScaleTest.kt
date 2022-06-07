package com.squareup.spice.serialization

import com.google.common.truth.Truth.assertThat
import com.squareup.spice.model.validation.DependencyCycleValidator
import com.squareup.spice.model.validation.GraphCompletenessValidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.system.measureTimeMillis

// TODO(MDX-3298) turn this into a caliper microbenchmark or something.
@FlowPreview
@ExperimentalCoroutinesApi
class ScaleTest {
  /** Creates a temp dir either in bazel's test dir, or system temp, and cleans up after the test */
  private val tmpDir by lazy {
    Files.createTempDirectory(ScaleTest::class.simpleName).toFile()
  }

  private val wsText = """
      ---
      name: scale
      definitions:
        tools: [kotlin]
        variants:
          main:
            srcs: [src/main]

      declared_tools: 
        - name: kotlin

  """.trimIndent()

  // These start/end nodes are abstracted mostly to ease tweaking them while debugging the test.
  private val start = 'a'
  private val end = 'j'

  private lateinit var wsFile: File

  /** Prepare a ginormous spice graph */
  @kotlin.ExperimentalStdlibApi
  @BeforeEach fun prepareGraph() {
    val wsDir = tmpDir
    wsFile = wsDir.resolve("workspace.spice.yml")
    wsFile.writeText(wsText)
    val range = start..end
    val outer = range.toMutableList()
    while (outer.isNotEmpty()) {
      val w = outer.removeLast()
      for (x in range) {
        for (y in range) {
          for (z in range) {
            val moduleDir = wsDir.resolve("$w/$x/$y/$z").apply { mkdirs() }
            val deps = when {
              setOf(x, y, z).all { it == end } -> {
                val wSet = outer.reversed() + w
                wSet.flatMap { w1 ->
                  (start..end).flatMap { x1 ->
                    (start..end).flatMap { y1 ->
                      (start..end).flatMap { z1 ->
                        if (listOf(w1, x1, y1, z1) == listOf(w, x, y, z)) listOf()
                        else listOf("/$w1/$x1/$y1/$z1")
                      }
                    }
                  }
                }
              }
              z == start -> {
                if (outer.isEmpty()) listOf() else listOf("/${w - 1}/$end/$end/$end")
              }
              else -> listOf("/$w/$x/$y/${z - 1}")
            }
            moduleDir.resolve("module.spice.yml").apply {
              val moduleText = """
                  --- # Module for /$w/$x/$y/$z
                  name: $z
                  variants:
                    main:
                      deps:

              """.trimIndent() +
                deps.joinToString("") { dep -> "      - $dep\n" }
              writeText(moduleText)
            }
          }
        }
      }
    }
  }

  @Test fun bulkLoadWorkspaceWithFileWalk() {
    val ws = FileWorkspace.fromFile(wsFile)
    val slice = ws.slice("main")
    assertThat(ws.nodeIndex.size).isEqualTo(
      0
    ) // omitting hasSize to avoid loading giant error strings
    assertThat(slice.depsIndex.size).isEqualTo(0)
    assertThat(slice.rdepsIndex.size).isEqualTo(0)

    val loadTime = measureTimeMillis {
      ws.loadAll()
      ws.validate(listOf(GraphCompletenessValidator, DependencyCycleValidator))
    }
    val forwardEdges = slice.depsIndex.entries.flatMap { (_, v) -> v }.count()
    val reverseEdges = slice.rdepsIndex.entries.flatMap { (_, v) -> v }.count()
    System.err.println(
      "\n" +
        "TRACE: Universe of ${ws.nodeIndex.size} nodes, $forwardEdges forward edges, " +
        " and $reverseEdges reverse edges bulk-loaded and validated in $loadTime ms"
    )
    assertThat(ws.nodeIndex.size).isEqualTo(10000)
    // omitting hasSize to avoid loading giant strings
    assertThat(forwardEdges).isEqualTo(64880)
    assertThat(reverseEdges).isEqualTo(64880)
  }

  @Test fun validateGinormousWorkspace() {

    val ws = FileWorkspace.fromFile(wsFile)
    val slice = ws.slice("main")
    val loadTime = measureTimeMillis {
      slice.validate(
        listOf(GraphCompletenessValidator, DependencyCycleValidator),
        "/$end/$end/$end/$end"
      )
    }
    val edgeCount = ws.nodeIndex.values.flatMap { slice.dependenciesOf(it).map { Unit } }.count()
    System.err.println(
      "\n" +
        "TRACE: Universe of ${ws.nodeIndex.size} nodes and $edgeCount edges " +
        "loaded and cycle-checked in $loadTime ms"
    )
    var lookupCount = 0
    val lookupTime = measureTimeMillis {
      for (x in 1..2) {
        for (i in start..end) {
          for (j in start..end) {
            for (k in start..end) {
              for (l in start..end) {
                val pathToLookup = "/$j/$i/$l/$k/src/main/whatever.kt"
                val node = ws.findModule(pathToLookup)
                requireNotNull(node) { "Could not find module for $pathToLookup in workspace" }
                lookupCount++
              }
            }
          }
        }
      }
    }
    val lookupAvg = lookupTime * 1000.0 / lookupCount
    System.err.println("TRACE: Looked up $lookupCount nodes in $lookupTime ms (avg: $lookupAvg µs)")

    var noHitCount = 0
    val noHitTime = measureTimeMillis {
      for (i in start..end) {
        for (j in start..end) {
          for (k in start..end step 2) {
            for (l in start..end step 2) {
              val node = ws.findModule("/no/such/path/$i$j$k$l/src/main/whatever.kt")
              assertThat(node).isNull()
              noHitCount++
            }
          }
        }
      }
    }
    val noHitAvg = noHitTime * 1000.0 / noHitCount
    System.err.println(
      "TRACE: Looked up $noHitCount non-existant nodes in $noHitTime ms (avg: $noHitAvg µs)"
    )
    // omitting hasSize to avoid loading giant strings
    assertThat(ws.nodeIndex.size).isEqualTo(10000)
    assertThat(edgeCount).isEqualTo(64880)
  }
}
