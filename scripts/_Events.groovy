eventAssetPrecompileComplete = {
    includeTargets << new File(requirejsGrailsAssetPipelinePluginDir, "scripts/_Optimize.groovy")
    optimize()
}
