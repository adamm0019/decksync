// After the M7a module split, the root build script is intentionally empty.
// All per-module Java/Spring/JavaFX wiring lives in each subproject's
// build.gradle.kts, shared conventions live in buildSrc, and distribution
// tasks (packageMsi, packageAppImage) live in :cli because they consume
// :cli's bootJar output.
