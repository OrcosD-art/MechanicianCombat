// 用**官方 GeckoLib 插件**（BB5 的 "GeckoLib Models & Animations"）导出：
//   * export_geckolib_model       -> geo（Bedrock/GeckoLib 几何）
//   * export_geckolib_animations  -> animations（作者那 8 段）
// 两个文件出自同一个模型、同一套工具 → 天然配套（不像 PAL 插件那样会把父级烘平）。
//
// 唯一的"内容修正"：作者模型里身体 6 块用的是 PAL 参考骨架的占位 UV（全指向贴图左上角，
// 而那份贴图只有枪的画），拿玩家皮肤渲染会采到透明区 → 身体直接看不见。
// 所以这里把它们的 UV 改成**标准 64x64 皮肤版式**（在 Blockbench 里改，属于内容编辑，不是转换）。
const job = globalThis.__BB_JOB;
if (!job) throw new Error("set globalThis.__BB_JOB first");

if (Project && Project.close) {
    try { Project.close(true); } catch (e) { /* ignore */ }
}
setupProject("free");
Codecs.project.parse(job.modelJson, (job.name || "BG") + ".bbmodel");
Canvas.updateAll();

// 插件的动画导出动作有 condition：必须是 GeckoLib 模型（isGeckoLibModel()），
// 而它查的是**全局 Format.id**（不是 Project.format）——所以必须用 Format.select() 切格式，
// 光给 Project.format 赋值没用（插件里的 onBedrockCompile 也不会跑，geo 的 format_version
// 就停在 1.21.0，而 GeckoLib 4.8 只认 1.12.0）。
const animsBefore = (typeof AnimationItem !== "undefined" && AnimationItem.all) ? AnimationItem.all.length : -1;
let formatError = null;
try {
    if (Formats && Formats.geckolib_model) {
        // 正确的切法是**格式实例自己的** select()；Format.select(fmt) 那个签名不生效（实测 Format.id 还是 free）
        if (typeof Formats.geckolib_model.select === "function") {
            Formats.geckolib_model.select();
        }
        Project.format = Formats.geckolib_model;
    }
} catch (e) {
    formatError = String(e && (e.stack || e.message) || e);
}
Canvas.updateAll();
const animsAfter = (typeof AnimationItem !== "undefined" && AnimationItem.all) ? AnimationItem.all.length : -1;

// 皮肤版式**不手写**：由 python 侧从官方插件自带的玩家几何（source/plugin_player.geo.json）
// 逐面算好注入进来（job.skinUv）。之前手写那版把 east/west 写反了（官方是 east=皮肤右侧、
// west=皮肤左侧），身体两侧就贴着错误的皮肤区域 —— 用户看到的"贴图混乱"就是这个。
const SKIN_UV = job.skinUv;
if (!SKIN_UV) throw new Error("job.skinUv 缺失（应由 gen_geckolib_official.py 注入）");
const rect = (r) => [r[0], r[1], r[0] + r[2], r[1] + r[3]];

let uvFixed = 0;
const bodyBones = [];
for (const group of Project.groups) {
    const tbl = SKIN_UV[group.name];
    if (!tbl) continue;
    bodyBones.push(group.name + ":" + group.children.length);
    for (const cube of group.children) {
        if (!cube || !cube.faces) continue;
        for (const faceName of Object.keys(cube.faces)) {
            const r = tbl[faceName];
            if (!r) continue;
            cube.faces[faceName].uv = rect(r);
            uvFixed++;
        }
    }
}
Canvas.updateAll();

// 渲染进程没有 fs：把 Blockbench.export 拦截下来，内容留在内存里
globalThis.__BB_EXPORTS = [];
if (!globalThis.__BB_EXPORT_STUBBED) {
    Blockbench.export = async function (opts) {
        globalThis.__BB_EXPORTS.push({
            name: opts && opts.name,
            type: opts && opts.type,
            content: opts && opts.content,
        });
        return (opts && opts.name) || "stubbed";
    };
    globalThis.__BB_EXPORT_STUBBED = true;
}

async function fire(id) {
    const act = BarItems[id];
    if (!act) return id + ": 没有这个动作";
    const before = globalThis.__BB_EXPORTS.length;
    try { act.click(); } catch (e) { /* 有对话框会抛，内容照样进 __BB_EXPORTS */ }
    await new Promise(r => setTimeout(r, 1500));
    // 导出会弹"选哪些动画"的对话框。⚠️ 页面上还有别的对话框（插件列表那个也有 OK），
    // 所以必须挑**当前这个可见对话框里**的按钮：优先 Confirm（这个版本就是它），再退 OK。
    for (let round = 0; round < 2; round++) {
        if (!Dialog.open) break;
        const vis = [...document.querySelectorAll("button, .btn")].filter(b => b.offsetParent !== null);
        const pick = vis.find(b => /^confirm$/i.test((b.textContent || "").trim()))
            || vis.find(b => /^(export|save)$/i.test((b.textContent || "").trim()))
            || vis.find(b => /^ok$/i.test((b.textContent || "").trim()));
        try {
            if (pick) pick.click();
            else Dialog.open.confirm();
        } catch (e) { /* ignore */ }
        await new Promise(r => setTimeout(r, 2000));
    }
    return id + ": 新导出 " + (globalThis.__BB_EXPORTS.length - before) + " 个";
}

const log = [];
log.push(await fire("export_geckolib_model"));
log.push(await fire("export_geckolib_animations"));
// 插件那个动作只是 `BarItems['export_animation_file'].trigger()` 的壳，trigger 会查 condition；
// 条件不满足时兜底直接调 Blockbench 自带的导出动作（导出的是同一个 Bedrock 动画文件）。
log.push(await fire("export_animation_file"));

const out = globalThis.__BB_EXPORTS.map(e => ({
    name: e.name,
    type: e.type,
    bytes: (typeof e.content === "string") ? e.content.length : -1,
    content: (typeof e.content === "string") ? e.content : null,
}));
return JSON.stringify({
    uv_fixed: uvFixed,
    body_bones: bodyBones,
    format: Project.format && Project.format.id,
    format_global: (typeof Format !== "undefined" && Format) ? Format.id : null,
    format_error: formatError,
    animations: { before: animsBefore, after: animsAfter,
        names: (typeof AnimationItem !== "undefined" && AnimationItem.all)
            ? AnimationItem.all.map(a => a.name) : [] },
    log,
    exports: out,
});
