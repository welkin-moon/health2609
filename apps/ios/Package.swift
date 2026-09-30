// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Health2609",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .executable(
            name: "Health2609",
            targets: ["Health2609"]
        ),
    ],
    dependencies: [],
    targets: [
        .executableTarget(
            name: "Health2609",
            dependencies: [],
            path: "Sources/Health2609"
        ),
    ]
)
