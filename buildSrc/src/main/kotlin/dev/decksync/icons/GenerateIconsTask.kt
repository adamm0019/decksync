package dev.decksync.icons

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Rasterises `assets/decksync-logo.svg` into the full set of PNG sizes the GUI + packaging tasks
 * need, plus a multi-resolution Windows `.ico`. Pure Gradle task — no external tools on PATH, so
 * Windows and Linux CI produce bit-identical output.
 *
 * <p>Gradle's up-to-date checking keys off the SVG input, so a clean `./gradlew build` skips the
 * work entirely once icons are committed. Editing the SVG forces a regen the next build.
 *
 * <p>The ICO writer is hand-rolled because Batik doesn't emit ICO and no actively-maintained tiny
 * library does either. The format is trivial: one ICONDIR header, one ICONDIRENTRY per frame, then
 * PNG payloads concatenated. PNG-encoded ICO frames have been supported since Windows Vista, which
 * predates anything we target.
 */
abstract class GenerateIconsTask : DefaultTask() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceSvg: RegularFileProperty

  /**
   * Optional favicon-optimised SVG used for sizes at or below [smallSizeThreshold]. The canonical
   * SVG is used for every size when unset.
   */
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:org.gradle.api.tasks.Optional
  abstract val smallSourceSvg: RegularFileProperty

  @get:Input abstract val smallSizeThreshold: org.gradle.api.provider.Property<Int>

  @get:OutputDirectory abstract val pngOutputDir: DirectoryProperty

  @get:OutputFile abstract val icoOutput: RegularFileProperty

  @get:OutputFile abstract val largePngOutput: RegularFileProperty

  @get:Input abstract val pngSizes: ListProperty<Int>

  @get:Input abstract val icoSizes: ListProperty<Int>

  @get:Input abstract val largePngSize: org.gradle.api.provider.Property<Int>

  init {
    group = "build"
    description = "Rasterises the DeckSync SVG into PNG + ICO packaging and resource assets."
    smallSizeThreshold.convention(Int.MAX_VALUE)
  }

  @TaskAction
  fun generate() {
    val svg = sourceSvg.get().asFile
    require(svg.exists()) { "Source SVG not found: $svg" }
    val mainSvgBytes = Files.readAllBytes(svg.toPath())

    val smallSvgFile = smallSourceSvg.orNull?.asFile
    val smallSvgBytes = smallSvgFile?.takeIf { it.exists() }?.let { Files.readAllBytes(it.toPath()) }
    val threshold = smallSizeThreshold.get()

    val pngDir = pngOutputDir.get().asFile
    pngDir.mkdirs()
    pngDir.listFiles()?.forEach { if (it.name.startsWith("icon-") && it.extension == "png") it.delete() }

    fun svgFor(size: Int): ByteArray =
        if (smallSvgBytes != null && size <= threshold) smallSvgBytes else mainSvgBytes

    val renderedBySize = mutableMapOf<Int, ByteArray>()
    pngSizes.get().forEach { size ->
      val bytes = renderPng(svgFor(size), size)
      renderedBySize[size] = bytes
      val target = pngDir.resolve("icon-$size.png")
      target.writeBytes(bytes)
      logger.lifecycle("icon: wrote {} ({} bytes)", target.relativeTo(project.rootDir), bytes.size)
    }

    val largeSize = largePngSize.get()
    val largeBytes = renderedBySize.getOrPut(largeSize) { renderPng(svgFor(largeSize), largeSize) }
    val largeFile = largePngOutput.get().asFile
    largeFile.parentFile.mkdirs()
    largeFile.writeBytes(largeBytes)
    logger.lifecycle("icon: wrote {}", largeFile.relativeTo(project.rootDir))

    val icoLayers =
        icoSizes.get().map { size ->
          size to (renderedBySize.getOrPut(size) { renderPng(svgFor(size), size) })
        }
    val icoBytes = buildIco(icoLayers)
    val icoFile = icoOutput.get().asFile
    icoFile.parentFile.mkdirs()
    icoFile.writeBytes(icoBytes)
    logger.lifecycle(
        "icon: wrote {} ({} layers, {} bytes)",
        icoFile.relativeTo(project.rootDir),
        icoLayers.size,
        icoBytes.size)
  }

  private fun renderPng(svgBytes: ByteArray, size: Int): ByteArray {
    val transcoder = PNGTranscoder()
    transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, size.toFloat())
    transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, size.toFloat())
    val output = ByteArrayOutputStream()
    svgBytes.inputStream().use { input ->
      transcoder.transcode(TranscoderInput(input), TranscoderOutput(output))
    }
    return output.toByteArray()
  }

  private fun buildIco(layers: List<Pair<Int, ByteArray>>): ByteArray {
    require(layers.isNotEmpty()) { "ICO requires at least one layer" }
    val out = ByteArrayOutputStream()
    val dos = DataOutputStream(out)

    // ICONDIR (6 bytes): reserved, type=1 (icon), image count. All
    // multi-byte fields in ICO are little-endian.
    writeLeShort(dos, 0)
    writeLeShort(dos, 1)
    writeLeShort(dos, layers.size)

    val headerSize = 6 + 16 * layers.size
    var runningOffset = headerSize

    layers.forEach { (size, png) ->
      // Widths / heights >= 256 are encoded as 0 per spec.
      val widthByte = if (size >= 256) 0 else size
      val heightByte = if (size >= 256) 0 else size
      dos.writeByte(widthByte)
      dos.writeByte(heightByte)
      dos.writeByte(0) // color palette count (0 for >= 8-bit)
      dos.writeByte(0) // reserved
      writeLeShort(dos, 1) // color planes
      writeLeShort(dos, 32) // bits per pixel (PNG carries this but ICO wants it populated)
      writeLeInt(dos, png.size)
      writeLeInt(dos, runningOffset)
      runningOffset += png.size
    }

    layers.forEach { (_, png) -> dos.write(png) }
    return out.toByteArray()
  }

  private fun writeLeShort(dos: DataOutputStream, value: Int) {
    dos.writeByte(value and 0xff)
    dos.writeByte((value ushr 8) and 0xff)
  }

  private fun writeLeInt(dos: DataOutputStream, value: Int) {
    dos.writeByte(value and 0xff)
    dos.writeByte((value ushr 8) and 0xff)
    dos.writeByte((value ushr 16) and 0xff)
    dos.writeByte((value ushr 24) and 0xff)
  }
}
