import asset.pipeline.AssetHelper
import grails.converters.JSON
import grails.util.Holders
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.codehaus.groovy.grails.web.json.JSONElement

printMessage = { String message -> event('StatusUpdate', [message]) }
finished = {String message -> event('StatusFinal', [message])}
errorMessage = { String message -> event('StatusError', [message]) }

eventAssetPrecompileComplete = {
    printMessage "Optimizing RequireJS resources"

    def projectPath = new File("").absolutePath
    def targetPath = new File("target").absolutePath
    def targetAssetsPath = new File("target/assets").absolutePath
    def requireJsOutPath = new File("target/requirejsout").absolutePath

    // read build.js
    def requireJsFilePath = Holders.grailsApplication.config?.grails?.assets?.requirejs?.buildFile?:"build.js"
    def buildJsText = this.class.classLoader.getResourceAsStream(requireJsFilePath)?.text
    if (!buildJsText) {
        errorMessage "RequireJS build.js file not found at classpath location [${requireJsFilePath}], skipping RequireJS optimization."
        return
    }

    // remove opening and closing parenthesis (if present) so we can parse file as JSON.
    buildJsText = buildJsText.replaceAll(/^\(/, '')

    // replace tokens with paths
    buildJsText = buildJsText.replaceAll(/\)$/, '')
            .replace('${projectPath}', projectPath)
            .replace('${targetPath}', targetPath)
            .replace('${targetAssetsPath}', targetAssetsPath)

    // replace baseUrl and dir.
    JSONElement jsonElement = JSON.parse(buildJsText)
    jsonElement.baseUrl = jsonElement.baseUrl?:targetAssetsPath
    jsonElement.dir = requireJsOutPath

    // create temporary build.js file.
    def tempBuildJsFile = new File(targetPath, "build.js")
    tempBuildJsFile.write("(${jsonElement.toString()})")

    // run RequireJS optimizer.
    def requireJsPluginPath = GrailsPluginUtils.getPluginDirForName('requirejs-grails-asset-pipeline').file.absolutePath
    def requireOptimizerPath = new File(requireJsPluginPath, 'scripts')
    def buildJsPath = tempBuildJsFile.absolutePath
    def str = "java -cp $requireOptimizerPath/js.jar org.mozilla.javascript.tools.shell.Main $requireOptimizerPath/r.js -o $buildJsPath"
    def proc = str.execute();
    proc.waitFor()
    printMessage "${proc.in.text}"

    // delete assets directory.
    new File(targetAssetsPath).deleteDir()

    // rename RequireJS out path to assets.
    new File(requireJsOutPath).renameTo(new File(targetAssetsPath))

    // load manifest file
    def manifestFile = new File("$targetAssetsPath/manifest.properties")
    def manifestText = manifestFile.text

    // replace asset-pipeline-generated JS resources with RequireJS optimized versions.
    jsonElement?.modules?.each { module ->
        def moduleName = module?.name?.replaceAll(/^\/*/,"")
        def moduleFileName = "${moduleName}.js"
        def moduleFile = new File("$targetAssetsPath/$moduleFileName")

        printMessage "Replacing ${moduleName}"

        // delete asset-pipeline-generated gzip and digest versions of RequireJS module.
        def match = manifestText =~ "$moduleFileName=$moduleName-([0-9a-f]*).js?"
        def oldDigest = match[0][1]
        def oldDigestFileName = "$moduleName-${oldDigest}.js"
        new File("$targetAssetsPath/${oldDigestFileName}.gz").delete()
        new File("$targetAssetsPath/$oldDigestFileName").delete()
        new File("$targetAssetsPath/${moduleName}.js.gz").delete()

        // generate digest and gzipped version of optimized RequireJS module.
        def newDigest = AssetHelper.getByteDigest(moduleFile.bytes)
        def newDigestFileName = "$moduleName-${newDigest}.js"
        def newDigestFile = new File("$targetAssetsPath/$newDigestFileName")
        newDigestFile.write(moduleFile.text)
        createCompressedFiles(moduleFile, newDigestFile)

        // update manifest entry with new digest.
        printMessage "Updating manifest...$oldDigestFileName to $newDigestFileName"
        manifestText = manifestText.replaceAll("$moduleFileName=[^\\n]*\\n?","$moduleFileName=$newDigestFileName\n")
    }

    manifestFile.write(manifestText)

    finished "Finished optimizing RequireJS resources"
}

def createCompressedFiles(outputFile, digestedFile) {
    def targetStream  = new java.io.ByteArrayOutputStream()
    def zipStream     = new java.util.zip.GZIPOutputStream(targetStream)
    def zipFile       = new File("${outputFile.getAbsolutePath()}.gz")
    def zipFileDigest = new File("${digestedFile.getAbsolutePath()}.gz")

    zipStream.write(outputFile.bytes)
    zipFile.createNewFile()
    zipFileDigest.createNewFile()
    zipStream.finish()

    zipFile.bytes = targetStream.toByteArray()
    AssetHelper.copyFile(zipFile, zipFileDigest)
    targetStream.close()
}