import grails.converters.JSON
import grails.util.Holders
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.codehaus.groovy.grails.web.json.JSONElement

import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

printMessage = { String message -> event('StatusUpdate', [message]) }
finished = {String message -> event('StatusFinal', [message])}
errorMessage = { String message -> event('StatusError', [message]) }

/**
 * Optimizes RequireJS resources.  Must run after asset-pipeline since asset-pipeline puts
 * all JS modules from the web-app and plugins in the same base directory:asset.  All JS modules
 * must be a common, base directory otherwise the RequireJS optimizer will fail.
 *
 * This method loads a project-defined RequireJS config file and sends it to the RequireJS optimizer (r.js)
 *
 * After optimization, this method replaces the asset-pipeline-generated JS modules with their
 * RequireJS-optimized counterparts.
 */
target(optimize: "Optimizes RequireJS") {
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
    def requireOptimizerPath = new File(requirejsGrailsAssetPipelinePluginDir, 'scripts')
    def buildJsPath = tempBuildJsFile.absolutePath
    def str = "java -cp $requireOptimizerPath/js.jar org.mozilla.javascript.tools.shell.Main $requireOptimizerPath/r.js -o $buildJsPath"
    def proc = str.execute()
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
        def newDigest = getByteDigest(moduleFile.bytes)
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

/*
Both getByteDigest and copyFile are copied from asset.pipeline.AssetHelper
due to ClassDefNotFoundErrors during execution the "optimize" target above when referencing
AssetHelper directly.

Shouldn't AssetHelper be in the classpath since asset-pipeline is a plugin dependency?
 */
def getByteDigest(byte[] fileBytes) {
    // Generate Checksum based on the file contents and the configuration settings
    MessageDigest md = MessageDigest.getInstance("MD5")
    md.update(fileBytes)
    def checksum = md.digest()
    return checksum.encodeHex().toString()
}

def void copyFile(File sourceFile, File destFile) throws IOException {
    if(!destFile.exists()) {
        destFile.createNewFile()
    }

    FileChannel source
    FileChannel destination
    try {
        source = new FileInputStream(sourceFile).getChannel()
        destination = new FileOutputStream(destFile).getChannel()
        destination.transferFrom(source, 0, source.size())
        destination.force(true)
    }
    finally {
        source?.close()
        destination?.close()
    }
}

/*
Copied from asset.pipeline.AssetCompiler because the method is private.
 */
def createCompressedFiles(outputFile, digestedFile) {
    def targetStream  = new ByteArrayOutputStream()
    def zipStream     = new GZIPOutputStream(targetStream)
    def zipFile       = new File("${outputFile.getAbsolutePath()}.gz")
    def zipFileDigest = new File("${digestedFile.getAbsolutePath()}.gz")

    zipStream.write(outputFile.bytes)
    zipFile.createNewFile()
    zipFileDigest.createNewFile()
    zipStream.finish()

    zipFile.bytes = targetStream.toByteArray()
    copyFile(zipFile, zipFileDigest)
    targetStream.close()
}

