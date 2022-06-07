package com.squareup.spice.serialization

import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.core.io.IOContext
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.squareup.spice.collections.DeterministicMap
import com.squareup.spice.collections.DeterministicSet
import com.squareup.spice.collections.MutableDeterministicMap
import com.squareup.spice.collections.MutableDeterministicSet
import com.squareup.spice.model.Dependency
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.DumperOptions.NonPrintableStyle.ESCAPE
import org.yaml.snakeyaml.DumperOptions.ScalarStyle
import java.io.IOException
import java.io.Writer
import java.nio.charset.StandardCharsets.UTF_8

class Serializer(
  private val mapper: YAMLMapper = YAMLMapper(CustomYAMLFactory()).apply {
    registerModule(
      KotlinModule().apply {
        addAbstractTypeMapping(
          DeterministicMap::class.java,
          MutableDeterministicMap.ByHash::class.java
        )
        addAbstractTypeMapping(
          MutableDeterministicMap::class.java,
          MutableDeterministicMap.ByHash::class.java
        )
        addAbstractTypeMapping(
          DeterministicSet::class.java,
          MutableDeterministicSet.ByHash::class.java
        )
        addAbstractTypeMapping(
          MutableDeterministicSet::class.java,
          MutableDeterministicSet.ByHash::class.java
        )
      }
    )
    // Want to use this, but can't, as it results in map entries with null values not being written.
    // configOverride(List::class.java).setterInfo = JsonSetter.Value.forContentNulls(Nulls.AS_EMPTY)
    registerModule(
      SimpleModule().apply {
        addDeserializer(Dependency::class.java, DependencyDeserializer())
        addSerializer(Dependency::class.java, DependencySerializer())
      }
    )
  }
) {
  fun <T> marshall(dataObject: T): String {
    return mapper.writeValueAsString(dataObject)
  }

  fun <T : Any> unmarshall(contents: String, type: Class<T>): T {
    return mapper.readValue(contents, type)
  }

  inline fun <reified T : Any> unmarshall(bytes: ByteArray): T {
    return unmarshall(bytes.toString(UTF_8), T::class.java)
  }

  class CustomYAMLGenerator(
    ctx: IOContext,
    jsonFeatures: Int,
    yamlFeatures: Int,
    codec: ObjectCodec,
    out: Writer,
    version: DumperOptions.Version?
  ) : YAMLGenerator(ctx, jsonFeatures, yamlFeatures, codec, out, version) {
    override fun buildDumperOptions(
      jsonFeatures: Int,
      yamlFeatures: Int,
      version: DumperOptions.Version?
    ): DumperOptions {
      return super.buildDumperOptions(jsonFeatures, yamlFeatures, version).apply {
        defaultScalarStyle = ScalarStyle.LITERAL
        defaultFlowStyle = FlowStyle.BLOCK
        indicatorIndent = 2
        nonPrintableStyle = ESCAPE
        indent = 4
        isPrettyFlow = true
        width = 100
        this.version = version
      }
    }
  }

  class CustomYAMLFactory : YAMLFactory() {
    @Throws(IOException::class)
    override fun _createGenerator(out: Writer, ctxt: IOContext): YAMLGenerator {
      val feats = _yamlGeneratorFeatures
      return CustomYAMLGenerator(ctxt, _generatorFeatures, feats, _objectCodec, out, _version)
    }
  }
}
