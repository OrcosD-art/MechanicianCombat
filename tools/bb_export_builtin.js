const job = globalThis.__BB_JOB;
if (!job) throw new Error("set globalThis.__BB_JOB first");

if (Project && Project.close) {
    try { Project.close(true); } catch (e) { /* ignore */ }
}
setupProject("free");
Codecs.project.parse(job.modelJson, (job.name || "BG") + ".bbmodel");
Formats.bedrock.select();
Project.format = Formats.bedrock;
Canvas.updateAll();

const rect = r => [r[0], r[1], r[0] + r[2], r[1] + r[3]];
let uvFixed = 0;
for (const group of Project.groups) {
    const table = job.skinUv[group.name];
    if (!table) continue;
    for (const cube of group.children) {
        if (!cube || !cube.faces) continue;
        for (const faceName of Object.keys(cube.faces)) {
            const r = table[faceName];
            if (!r) continue;
            cube.faces[faceName].uv = rect(r);
            uvFixed++;
        }
    }
}
Canvas.updateAll();

globalThis.__BB_EXPORTS = [];
if (!globalThis.__BB_EXPORT_STUBBED) {
    Blockbench.export = async opts => {
        globalThis.__BB_EXPORTS.push({
            name: opts && opts.name,
            type: opts && opts.type,
            content: opts && opts.content
        });
        return (opts && opts.name) || "stubbed";
    };
    globalThis.__BB_EXPORT_STUBBED = true;
}

async function fire(id) {
    const act = BarItems[id];
    if (!act) return id + ": missing";
    const before = globalThis.__BB_EXPORTS.length;
    try { act.click(); } catch (e) { /* dialogs can throw after export */ }
    await new Promise(r => setTimeout(r, 1200));
    for (let round = 0; round < 2; round++) {
        if (!Dialog.open) break;
        const visible = [...document.querySelectorAll("button, .btn")]
            .filter(b => b.offsetParent !== null);
        const pick = visible.find(b => /^confirm$/i.test((b.textContent || "").trim()))
            || visible.find(b => /^(export|save)$/i.test((b.textContent || "").trim()))
            || visible.find(b => /^ok$/i.test((b.textContent || "").trim()));
        try {
            if (pick) pick.click();
            else Dialog.open.confirm();
        } catch (e) { /* ignore */ }
        await new Promise(r => setTimeout(r, 1600));
    }
    return id + ": " + (globalThis.__BB_EXPORTS.length - before);
}

const log = [];
log.push(await fire("export_entity"));
const animations = {};
for (const animation of AnimationItem.all) {
    animations[animation.name] = animation.compileBedrockAnimation();
}
return JSON.stringify({
    log,
    uv_fixed: uvFixed,
    animation_document: JSON.stringify({
        format_version: "1.8.0",
        animations
    }),
    exports: globalThis.__BB_EXPORTS.map(e => ({
        name: e.name,
        type: e.type,
        content: e.content
    }))
});
