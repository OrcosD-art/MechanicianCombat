return JSON.stringify({
    pluginKeys: Object.keys(Plugin || {}),
    pluginsKeys: Object.keys(Plugins || {}),
    loaders: Object.keys(Plugins || {})
        .filter(key => /load|register|install/i.test(key)),
    blockbenchFile: typeof Blockbench.readFile,
    blockbenchOpenFile: typeof Blockbench.openFile,
});
