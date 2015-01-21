RequireJS Grails Asset Pipeline
===============================
![build status](https://travis-ci.org/Grails-Plugin-Consortium/requirejs-grails-asset-pipeline.svg)

The Grails `requirejs-grails-asset-pipeline` is a plugin that provides RequireJS optimizer support for the asset-pipeline static asset management plugin.  It allows you to optimize RequireJS modules that depend on modules defined in Grails plugins.

For more information on how to use asset-pipeline, visit [here](http://www.github.com/bertramdev/asset-pipeline).

**Note** This plugin depends on a pending pull request to the Grails asset-pipeline plugin (211 and 212).  Do not use this plugin until those pull requests are accepted.

Usage
-----
Define a RequireJS optimizer build config file.  By default, the plugin looks for a `build.js` file on the classpath (e.g. `grails-app/conf/build.js`).  You can override the location of the file by specifying the path in the `grails.assets.requirejs.buildFile` property, e.g. `grails.assets.buildjs.buildFile=path/to/my/build.js`

The plugin will default the `baseUrl`property to the project's `target/assets` directory.  You may override `baseUrl` in your build config file.  The `dir` property is always set to a temporary build directory in the project's `target/assets` directory.

You may specify the following tokens in the build config file.  The plugin will create a copy of your build config file with substituted tokens.

| Token | Value  |
| ------------- | ------------- |
| `${projectPath}` | The absolute path to the root of the current project. |
| `${targetPath}` | The absolute path to the target directory of the current project, typically `<product directory>/target`. |
| `${targetAssetsPath}` | The absolute path to the target assets directory of the current project, typically `<product directory>/target/assets`. |

For instance, if the project path is /path/to/project
```js
  ({
    "mainConfigFile":"${targetAssetsPath}/main.js"
  })
```
Then the plugin generates the build config as:
```js
  ({
    "mainConfigFile":"/path/to/project/main.js"
  })
```

For more information on how to define the build config file, visit [here](https://github.com/jrburke/r.js/blob/master/build/example.build.js).
