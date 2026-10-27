const job = globalThis.__BB_JOB;
if (!job) throw new Error("globalThis.__BB_JOB missing");

if (Project && Project.close) {
    try { Project.close(true); } catch (e) { }
}
setupProject("java_block");
Codecs.project.parse(job.modelJson, job.name || "item.bbmodel");
Canvas.updateAll();

globalThis.__BB_EXPORTS = [];
if (!globalThis.__BB_EXPORT_STUBBED) {
    Blockbench.export = async function(opts) {
        globalThis.__BB_EXPORTS.push({
            name: opts && opts.name,
            type: opts && opts.type,
            content: opts && opts.content
        });
        return (opts && opts.name) || "stubbed";
    };
    globalThis.__BB_EXPORT_STUBBED = true;
}

const action = BarItems.export_blockmodel;
if (!action) throw new Error("export_blockmodel action missing");
try { action.click(); } catch (e) { }
await new Promise(resolve => setTimeout(resolve, 1200));
if (typeof Dialog !== "undefined" && Dialog.open) {
    const visible = [...document.querySelectorAll("button, .btn")]
        .filter(button => button.offsetParent !== null);
    const confirm = visible.find(button => /^(confirm|export|save|ok)$/i.test(
        (button.textContent || "").trim()));
    try {
        if (confirm) confirm.click();
        else Dialog.open.confirm();
    } catch (e) { }
    await new Promise(resolve => setTimeout(resolve, 1200));
}

return JSON.stringify({
    format: Project.format && Project.format.id,
    exports: globalThis.__BB_EXPORTS.map(entry => ({
        name: entry.name,
        type: entry.type,
        content: typeof entry.content === "string" ? entry.content : null
    }))
});
