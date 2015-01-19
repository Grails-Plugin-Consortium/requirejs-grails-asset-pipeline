includeTargets << new File(requirejsGrailsAssetPipelinePluginDir, "scripts/_Optimize.groovy")

eventAssetPrecompileComplete = {
    optimize()
}
