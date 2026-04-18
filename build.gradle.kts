// After the M7a module split, the root build script is intentionally thin.
// All per-module Java/Spring/JavaFX wiring lives in each subproject's
// build.gradle.kts, shared conventions live in buildSrc, and distribution
// tasks (packageMsi, packageAppImage) live in :cli because they consume
// :cli's bootJar output.
//
// The single exception is `generateIcons`: the DeckSync logo SVG is rendered
// into PNG sizes and a multi-resolution ICO that both :gui (classpath icons
// for the JavaFX window) and :cli packaging (jpackage --icon inputs) consume.
// Registering the task at the root keeps a single source of truth and lets
// both subprojects depend on it without reaching across module boundaries.

import dev.decksync.build.icons.GenerateIconsTask

val generateIcons =
    tasks.register<GenerateIconsTask>("generateIcons") {
        sourceSvg.set(layout.projectDirectory.file("assets/decksync-logo.svg"))
        // Favicon-optimised variant. The canonical SVG's 3-unit stroke goes
        // subpixel at 16/24/32 px output; the small variant thickens the
        // stroke so those sizes stay crisp. Sizes above the threshold render
        // from the canonical SVG unchanged.
        smallSourceSvg.set(layout.projectDirectory.file("assets/decksync-logo-small.svg"))
        smallSizeThreshold.set(32)
        pngOutputDir.set(layout.projectDirectory.dir("gui/src/main/resources/icons"))
        icoOutput.set(layout.projectDirectory.file("packaging/decksync.ico"))
        largePngOutput.set(layout.projectDirectory.file("packaging/decksync.png"))
        // Covers every size the Windows Start Menu, task bar, jpackage, and
        // the JavaFX Stage icon collection may ask for. 1024 is the AppImage
        // source; everything else slots into getIcons() for OS-side picking.
        pngSizes.set(listOf(16, 24, 32, 48, 64, 128, 256, 512, 1024))
        // Multi-res .ico layers per Microsoft's modern recommendation —
        // smaller sizes are still needed for the taskbar and Alt-Tab thumbnail
        // on older Windows settings.
        icoSizes.set(listOf(16, 32, 48, 256))
        largePngSize.set(1024)
    }
