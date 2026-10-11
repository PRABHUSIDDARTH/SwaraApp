// swift-tools-version:5.9
import PackageDescription

// Platform-independent Swara logic: models, shuffle, sorting, artist identity,
// karaoke matching, play-history threshold and the Korokae vocal-removal DSP.
// Builds and tests on macOS and Linux (no UIKit/AVFoundation here).
let package = Package(
    name: "SwaraCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SwaraCore", targets: ["SwaraCore"])
    ],
    targets: [
        .target(name: "SwaraCore"),
        .testTarget(name: "SwaraCoreTests", dependencies: ["SwaraCore"])
    ]
)
