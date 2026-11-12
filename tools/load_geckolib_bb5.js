const result = { error: null, actions: [], formats: [] };
try {
    if (typeof Plugin.load === "function") {
        await Plugin.load("D:/codex/mod/rebuild/tools/geckolib_bb5.js");
    } else {
        throw new Error("Plugin.load is unavailable");
    }
} catch (exception) {
    result.error = String(exception && (exception.stack || exception));
}
result.actions = Object.keys(BarItems || {})
    .filter(key => /^export_geckolib/.test(key));
result.formats = Object.keys(Formats || {})
    .filter(key => /geckolib/i.test(key));
return JSON.stringify(result);
