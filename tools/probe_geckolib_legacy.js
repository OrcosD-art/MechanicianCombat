if (Plugins.loading_promise) {
    try { await Plugins.loading_promise; } catch (e) { }
}
const all = Plugins.all || [];
const plugin = all.filter(p => p && p.id === "animation_utils")[0]
    || all["animation_utils"]
    || all.filter(p => p && p.id === "geckolib")[0]
    || all["geckolib"];
const info = {
    blockbench: Blockbench.version,
    catalog: all
        .filter(p => p && /gecko|animation/i.test(p.id || ""))
        .map(p => ({ id: p.id, installed: p.installed, version: p.version })),
    found: !!plugin,
    pluginId: plugin && plugin.id,
    loaded: false,
    actions: [],
    formats: [],
    error: null
};
if (!plugin) {
    return JSON.stringify(info);
}
try {
    await plugin.load("D:/codex/mod/rebuild/tools/geckolib_legacy_412.js");
} catch (e) {
    info.error = String(e && (e.stack || e.message) || e || "undefined");
}
info.loaded = !!plugin.loaded;
info.actions = Object.keys(BarItems || {}).filter(k => /^export_geckolib/.test(k));
info.formats = Object.keys(Formats || {}).filter(k => /geckolib/i.test(k));
return JSON.stringify(info);
