r"""机械师新素材（BG261013.bbmodel）-> GeckoLib rig（geo + animations）。

和 `gen_geckolib_official.py` 同一套路（官方 GeckoLib 插件在无头 Blockbench 里导出），
只是把"源模型"换成新的机械师包，另外多两件事：

  1) **从 bbmodel 里把内嵌贴图解出来**（新包那张 BOWGUN.png 是 64x64 / 1786B，和旧的不一样），
     写进 `textures/entity/bowgun_gun.png`（枪件贴图；身体用各人自己的皮肤）。
  2) **先把官方 GeckoLib 插件装/加载进无头 Blockbench**（干净 profile 里 plugins/ 下的 js 不会自动加载，
     走 CDP 把源码 eval 进去 —— 和 `bb_load_plugin.py` 给 PAL 插件用的办法一样）。

用法（无头 Blockbench 先起好，见 NOTES）：
    python tools\gen_geckolib_mech.py [bbmodel 路径]
"""
import base64
import copy
import json
import os
import struct
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geckolib_official as base          # 复用：skin_table / force_texture_size / png_size

# --- 本机改动（2026-10-16）：D:\Minecraft 开发环境不在这台机器上，路径指向 rebuild 工作区 ---
REBUILD = r"D:\codex\mod\rebuild"
TOOLS = os.path.join(REBUILD, "tools")
JAVA = r"C:\Program Files\Java\jdk-21\bin\java.exe"
W = os.path.join(REBUILD, "mod")
RES = os.path.join(W, "src", "main", "resources", "assets", "tkgun")
OUT_GEO = os.path.join(RES, "geo", "entity", "bowgun_rig.geo.json")
OUT_ANIM = os.path.join(RES, "animations", "entity", "bowgun_rig.animation.json")
OUT_TEX = os.path.join(RES, "textures", "entity", "bowgun_gun.png")
TEX_DIR = os.path.join(RES, "textures", "entity")
PLUGIN_CANDIDATES = (
    os.path.join(TOOLS, "geckolib.js"),
    os.path.join(REBUILD, "bbprofile", "plugins", "geckolib.js"),
    os.path.join(os.environ.get("APPDATA", ""), "Blockbench", "plugins", "geckolib.js"),
)
PORT = os.environ.get("BB_PORT", "9222")


def cdp(expr_file):
    r = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                        os.path.join(TOOLS, "cdp.ps1"),
                        "--port", PORT, "--file", expr_file],
                       capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=900)
    if r.returncode != 0:
        raise SystemExit("Blockbench 调用失败:\n%s\n%s" % (r.stdout[-2000:], r.stderr[-3000:]))
    return r.stdout


def run_js(name, code):
    """把一段 JS 写到 build/ 下当表达式文件跑掉，返回 stdout（脚本用 return 出结果）。"""
    path = os.path.join(W, "build", name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(code)
    return cdp(path)


def ensure_geckolib():
    """确保无头 Blockbench 里有官方 GeckoLib 插件（没有就把 js 源码 eval 进去）。"""
    probe = run_js("_mech_probe_plugin.js", """
return JSON.stringify({
    version: Blockbench.version,
    gecko_actions: Object.keys(BarItems || {}).filter(k => /^export_geckolib/.test(k)),
});
""")
    info = json.loads(probe.strip().splitlines()[-1])
    if info["gecko_actions"]:
        print("GeckoLib 插件已在位:", info["gecko_actions"], "(Blockbench %s)" % info["version"])
        return
    src_path = next((p for p in PLUGIN_CANDIDATES if os.path.exists(p)), None)
    if not src_path:
        raise SystemExit("找不到 geckolib.js（试过 %s）" % (PLUGIN_CANDIDATES,))
    src = open(src_path, encoding="utf-8").read()
    # ⚠️ 本机（2026-10-16）踩到：干净 profile 里**不能**用 `(0,eval)(src)` 注册插件。
    # Blockbench 5 的 `Plugin.register(id, obj)` 只在"加载器已经建好占位对象"时才会生效
    # （见 dist/bundle.js：`var n = Plugins.registered[e]; if (!n) { n = Plugins.registered.unknown; ... }`），
    # 直接 eval 会静默什么都不做。正确姿势是调插件自己的 `Plugin#load(<file>)` —— 它内部
    # 用 `new Function(...)` 执行源码并先建占位对象，注册才有落点。
    # 顺带：`Plugins.all`（= 插件商店目录）是启动后异步抓下来的，必须等它出现 geckolib 条目。
    out = run_js("_mech_plugin_load.js", """
const info = { catalog: false, loaded: false, err: null };
function findGecko() {
    const arr = Plugins.all || [];
    return arr.filter(p => p && p.id === 'geckolib')[0] || arr['geckolib'] || null;
}
try {
    if (Plugins.loading_promise) { try { await Plugins.loading_promise; } catch (e) { /* ignore */ } }
    for (let i = 0; i < 60; i++) {
        info.catalog = !!findGecko();
        if (info.catalog) break;
        await new Promise(r => setTimeout(r, 500));
    }
    const p = findGecko();
    if (!p) throw new Error('geckolib 不在插件商店目录里（网络没通？）');
    await p.load(%s);
    info.loaded = !!p.loaded;
    for (let i = 0; i < 40; i++) {
        if (Object.keys(BarItems).some(k => /^export_geckolib/.test(k))) break;
        await new Promise(r => setTimeout(r, 500));
    }
} catch (e) { info.err = String(e && (e.stack || e.message || e)); }
info.gecko_actions = Object.keys(BarItems).filter(k => /^export_geckolib/.test(k));
info.gecko_formats = Object.keys(Formats).filter(k => /geckolib/i.test(k));
info.registered = Object.keys(Plugins.registered || {});
return JSON.stringify(info);
""" % json.dumps(os.path.abspath(src_path).replace("\\", "/")))
    info2 = json.loads(out.strip().splitlines()[-1])
    print("装载 GeckoLib 插件 %s -> %s (catalog=%s loaded=%s formats=%s err=%s)"
          % (os.path.basename(src_path), info2["gecko_actions"], info2.get("catalog"),
             info2.get("loaded"), info2.get("gecko_formats"), info2.get("err")))
    if not info2["gecko_actions"]:
        raise SystemExit("GeckoLib 插件没装上")


def decode_texture(tex):
    """取出 bbmodel 里内嵌的那张图（data:uri）或磁盘路径上的图。"""
    src = tex.get("source", "")
    if src.startswith("data:"):
        return base64.b64decode(src.split(",", 1)[1])
    return open(src, "rb").read()


def png_dims(raw):
    return struct.unpack(">II", raw[16:24])


def safe_name(name, fallback):
    """资源名只能是小写字母/数字/下划线/中划线。"""
    out = "".join(c if (c.isalnum() and c.isascii() and c.islower()) or c in "_-. " else "_"
                  for c in (name or "").strip().lower())
    out = out.replace(" ", "_").strip("_.-")
    return out or fallback


def extract_texture(model_doc, out_png):
    tex = model_doc["textures"][0]
    raw = decode_texture(tex)
    os.makedirs(os.path.dirname(out_png), exist_ok=True)
    with open(out_png, "wb") as fh:
        fh.write(raw)
    w, h = png_dims(raw)
    print("贴图 %s (%s) %dx%d %d 字节" % (out_png, tex.get("name"), w, h, len(raw)))
    return w, h


def extract_extra_textures(model_doc):
    """第 2 张及以后的贴图也导出成独立资源（v4 的 `airflow.png` 160x32 就是第 2 张）。

    模型里只有"第 1 张"会被 GeckoLib 当主贴图；用第 2 张的方块由客户端单独一层画
    （见 `BowgunAirflowLayer`），所以这里必须把图落地。
    """
    out = []
    os.makedirs(TEX_DIR, exist_ok=True)
    for i, tex in enumerate(model_doc.get("textures", [])[1:], start=1):
        raw = decode_texture(tex)
        w, h = png_dims(raw)
        stem = safe_name(os.path.splitext(str(tex.get("name") or ""))[0], "tex%d" % i)
        path = os.path.join(TEX_DIR, "bowgun_%s.png" % stem)
        with open(path, "wb") as fh:
            fh.write(raw)
        print("副贴图[%d] %s -> %s (%dx%d %d 字节)" % (i, tex.get("name"), path, w, h, len(raw)))
        out.append({"index": i, "name": tex.get("name"), "path": path, "w": w, "h": h})
    return out


def foreign_cubes(model_doc):
    """bbmodel 里"面用到第 2 张及以后贴图"的方块：(元素, 贴图序号)。"""
    out = []
    parents = element_parents(model_doc)
    for el in model_doc.get("elements", []):
        idx = set()
        for face in (el.get("faces") or {}).values():
            if isinstance(face, dict) and face.get("texture") is not None:
                idx.add(face["texture"])
        idx.discard(0)
        if idx:
            out.append((el, sorted(idx), parents.get(el.get("uuid"))))
    return out


def element_parents(model_doc):
    """方块 uuid -> 它所在的组名（bbmodel 的 outliner 里直接记着父子）。"""
    out = {}

    def walk(nodes, parent=None):
        for n in nodes:
            if isinstance(n, str):
                out[n] = parent
                continue
            walk(n.get("children") or [], n.get("name"))
    walk(model_doc.get("outliner") or [])
    return out


def split_foreign_cubes(geo_text, model_doc):
    """把"用副贴图"的方块从原来的骨头上摘下来、挂成 `airflow<N>` 子骨。

    为什么要拆：一张 geo 只有一个 `texture_width/height`，副贴图那块方块的 UV 会越界
    （airflow 是 160x32，而主贴图是 64x64）。拆出来之后：

      * 客户端 `BowgunGunLayer` 跳过 `airflow*`（不拿枪贴图画它）；
      * `BowgunAirflowLayer` 用 `bowgun_airflow.png` + **把烘焙好的 UV 归一化到 [0,1]**
        重新画一遍（见那儿的注释）；
      * 新骨骼**没有自己的动画通道**，父骨（`fire2/3/4`）的 scale/rotation 照样继承 ——
        所以"枪口焰亮到第 N 帧时才出现对应那张气流图"这件事和作者的设计一致
        （先在 `TkGun` 侧验证过：`locator` 就是这种"无通道子骨"，一直跟着 `gun` 走）。
    """
    doc = json.loads(geo_text)
    geo = doc["minecraft:geometry"][0]
    bones = geo["bones"]
    moved = []
    for k, (el, idx, parent) in enumerate(foreign_cubes(model_doc), start=1):
        want_origin = [float(v) for v in el["from"]]
        want_size = [float(el["to"][i]) - float(el["from"][i]) for i in range(3)]
        # ⚠️ 不能拿 origin 逐位对：Blockbench 的 Bedrock 导出会把某些方块整体挪一下
        #    （实测这几块气流/枪口焰平面在 x 上差 -8），而且"挪多少"没有稳定的规律。
        #    所以按 **所在组 + 尺寸 + y/z** 对（y/z 实测是原样的），匹配不上就直接报错。
        host = next((b for b in bones if b.get("name") == parent), None)
        hit = None
        if host is not None:
            cands = [c for c in host.get("cubes", [])
                     if len(c.get("size") or []) == 3 and len(c.get("origin") or []) == 3
                     and all(abs(float(c["size"][i]) - want_size[i]) < 1e-3 for i in range(3))
                     and abs(float(c["origin"][1]) - want_origin[1]) < 1e-3
                     and abs(float(c["origin"][2]) - want_origin[2]) < 1e-3]
            if len(cands) == 1:
                hit = cands[0]
            elif len(cands) > 1:
                hit = min(cands, key=lambda c: abs(float(c["origin"][0]) - want_origin[0]))
        if hit is None:
            raise SystemExit("副贴图方块在导出的 geo 里找不到：组=%s origin=%s size=%s（导出格式变了吗？）"
                             % (parent, want_origin, want_size))
        if abs(float(hit["origin"][0]) - want_origin[0]) > 1e-3:
            print("   （%s 的这块 geo origin.x=%s，源模型 from.x=%s —— Blockbench 导出时挪过，照 geo 的来）"
                  % (host["name"], hit["origin"][0], want_origin[0]))
        host["cubes"].remove(hit)
        name = "airflow%d" % k
        bones.append({"name": name, "parent": host["name"], "pivot": list(host.get("pivot", [0, 0, 0])),
                      "cubes": [hit]})
        moved.append((name, host["name"], want_size, idx))
        print("  副贴图方块 -> 新骨 %s（父骨 %s，尺寸 %s，贴图 %s）" % (name, host["name"], want_size, idx))
    if moved:
        print("  副贴图方块共 %d 个，已拆成独立骨骼" % len(moved))
    return json.dumps(doc, ensure_ascii=False)


# ---------------------------------------------------------------------------
# 动画后处理：① 视角 Molang 的语义修正 ② 音效关键帧改名
# ---------------------------------------------------------------------------

# ⚠️ 为什么要改（2026-10-16 读 GeckoLib 4.8.2 字节码 + yarn 映射逐条确认，见 SKILL.md）：
#   * `query.body_x_rotation` 在 GeckoLib 里的实现是
#     `if (animatable instanceof LivingEntity) return 0; else return entity.getPitch(tick);`
#     —— **对玩家（LivingEntity）恒为 0**，所以作者写 `-query.body_x_rotation / 3`
#     等于没写（枪不跟着俯仰抬）。作者自己在多数段里已经改成了 `head_x_rotation`，
#     这里把剩下那几段（Focus_shooting2..7、Focus_over_shooting 的后半段）补齐。
#   * `query.head_x_rotation` = `LivingEntity.getPitch(tick)` = 实体俯仰 ✓ 这条作者写对了。
#   * `query.head_y_rotation` = `LivingEntity.getYaw(tick)` = **绝对头朝向**（headYaw），
#     而 Bedrock 的语义是"头相对身体的角度"（headYaw - bodyYaw）。GeckoLib 里对应的两个
#     可用 query 是 head_y_rotation（headYaw）和 body_y_rotation（bodyYaw）→ 相减就是作者要的差值。
#     不修的话：上身/躯干会照着**世界朝向**被永久拧一个角度（面向东/西时差 ±60°/±30°）。
# 视角 Molang：**作者怎么写就怎么留着**。
#   GeckoLib 原本对 `GeoReplacedEntity`（tkgun 的 rig 就是）会把这些 query 判成
#   "incompatible animatable type" 并按 0 处理（`(LivingEntity) actor.animatable()` 强转失败），
#   但 `MolangQueries.setActorVariable` 是 **public** 的、而且只在类初始化时注册一遍 ——
#   所以客户端在 `client/TkGunMolang.install()` 里把这 4 个 query 对 tkgun rig 重新实现成
#   "按被替换的那个玩家求值"，**动画里的表达式一个字都不用改**。
#   这张表留着当"万一哪天要把某些表达式清成 0"的开关（目前是空的 = 不改）。
MOLANG_FIXES = ()

# 作者音效关键帧的 `file`（= 素材包里的 ogg）→ 我们给这段动画音效起的名字。
# 客户端 `BowgunRig.onSoundKeyframe` 按名字决定放不放（见那边的注释）。
SOUND_NAMES = {
    "枪击.ogg": "shot",
    "枪击2.ogg": "shot2",
    "枪击3.ogg": "shot3",
    "弹匣.ogg": "mag",
    "换弹.ogg": "reload",
    "换弹2.ogg": "reload2",
    "空弹仓.ogg": "empty",
    "散热.ogg": "vent",
    "栓声.ogg": "bolt",
    "翻滚.ogg": "roll",
}


def _model_sound_times(model_doc):
    """bbmodel -> {动画名: {时间(4 位小数): 音效名}}（音效名取 file 的 basename，认不出来就用 effect）。"""
    out = {}
    for anim in model_doc.get("animations", []):
        table = {}
        for blk in (anim.get("animators") or {}).values():
            if not isinstance(blk, dict) or blk.get("type") != "effect":
                continue
            for kf in blk.get("keyframes") or []:
                if kf.get("channel") != "sound":
                    continue
                for dp in kf.get("data_points") or []:
                    base = os.path.basename(str(dp.get("file") or "").replace("\\", "/"))
                    name = SOUND_NAMES.get(base) or (dp.get("effect") or "").strip()
                    if name:
                        table[round(float(kf.get("time") or 0.0), 4)] = name
        if table:
            out[anim.get("name")] = table
    return out


# ---------------------------------------------------------------------------
# 派生动画：上半身专用变体 + 静止姿势
# ---------------------------------------------------------------------------
# 用户 2026-10-16 原话："换弹和那个持枪动作，这两个动作是只用他们的上半身，下半身继续用那个四向走动。
# 因为他不可能每时刻装弹或者是持枪的时候都是站立原地不动的。"
#
# 做法：把仍在使用的换弹/翻滚收势动画**去掉 leg_left / leg_right 两条腿的通道**
# 复制一份 `<名字>_upper`。专注射击期间当前状态机会直接使用全身动画，因此不再生成
# 已不可达的 Focus_*_upper 变体。
# `body`（胯/躯干根）**保留** —— 作者的架枪姿势是靠 `body.position.y = -8` 蹲下去的，
# 去掉就变成站着架枪了（那正是用户在骂的"没维持住蹲下状态"）。
# 客户端把这条配合"腿那条 walk_*/run 动画"叠在一起（GeckoLib 多控制器：后注册的覆盖先注册的，
# 所以 `body`/上身归 `_upper`、两条腿归 walk_*）。
UPPER_ONLY_ANIMS = (
    "reload", "roll_to_idle",
)
# "举枪"那一遍不要再从头播 `stand_shooting`（作者在它开头埋了后坐/抛壳/枪口焰，看起来就是空放一发，
# 用户 2026-10-16："按右键，它就直接把子弹打出去了，我都还没按左键呢"）→ 单独做一份**静止端枪姿势**：
# 把每个通道的**最后一个关键帧**拎出来当 t=0 的固定值：
# `stand_shooting_hold` 用于腰射举枪时的端枪静姿。
HOLD_POSE_ANIMS = ("stand_shooting",)


def merge_duplicate_focus_variants(doc):
    """Keep one focus-shot clip per distinct chain phase.

    Shots 4..6 only differ in the first chain segment offset, and shots 7..8
    only differ in the second segment offset. The renderer applies those
    offsets from the authoritative shot index.
    """
    anims = doc.get("animations") or {}
    required = ("Focus_shooting_extra", "Focus_shooting1",
                "Focus_shooting4", "Focus_shooting6", "Focus_shooting7")
    if not all(name in anims for name in required):
        return
    for name in ("Focus_shooting", "Focus_shooting2",
                 "Focus_shooting3", "Focus_shooting5"):
        anims.pop(name, None)
    print("  合并重复专注射击动画: 保留 extra / 1 / 4 / 6 / 7")


def derive_upper_variants(doc):
    """在动画表里加上 `*_upper`（去掉腿）和 `*_hold`（静止姿势）。"""
    anims = doc.get("animations") or {}
    made = []
    for name in UPPER_ONLY_ANIMS:
        src = anims.get(name)
        if not src:
            continue
        clone = copy.deepcopy(src)
        bones = clone.get("bones") or {}
        clone["bones"] = {b: ch for b, ch in bones.items() if b not in ("leg_left", "leg_right")}
        clone["name"] = name + "_upper"
        anims[name + "_upper"] = clone
        made.append(name + "_upper")
    for name in HOLD_POSE_ANIMS:
        src = anims.get(name)
        if not src:
            continue
        clone = copy.deepcopy(src)
        clone["name"] = name + "_hold"
        clone["loop"] = "hold"
        for _bone, channels in (clone.get("bones") or {}).items():
            for chan, keys in list(channels.items()):
                if not isinstance(keys, dict) or not keys or "vector" in keys:
                    continue          # 无时间的单值通道（`{"vector": [...]}`）原样
                try:
                    last = max(keys, key=lambda k: float(k))
                except (TypeError, ValueError):
                    continue
                channels[chan] = {"0.0": keys[last]}
        anims[name + "_hold"] = clone
        made.append(name + "_hold")
    print("  派生动画 %d 段: %s" % (len(made), ", ".join(made)))
    return made


def postprocess_animations(anim_text, model_doc, extra_textures):
    """① 视角 Molang 全换成 0（改由客户端渲染器实现）② 音效关键帧改名 ③ 打印"跟随视角"动画清单。"""
    # ④ 2026-10-16 新增：把作者标成 `step` 的关键帧**烘成阶跃**
    raw = anim_text
    hit = 0
    for old, new in MOLANG_FIXES:
        hit += raw.count(old)
        raw = raw.replace(old, new)
    print("  视角 Molang：保留作者原样（改写了 %d 处；由客户端 TkGunMolang 喂真实值，见 MOLANG_FIXES 注释）" % hit)

    sounds = _model_sound_times(model_doc)
    doc = json.loads(raw)

    # 从**原始** JSON 里统计"哪些动画的 chest / body 跟着视角转"（Java 侧那张表就是抄这里的输出）。
    src = json.loads(anim_text).get("animations") or {}
    chest, body = [], []
    for name, anim in src.items():
        bones = anim.get("bones") or {}
        if any("query." in json.dumps((bones.get("chest") or {}).get("rotation", {}))
               for _ in [0]):
            chest.append(name)
        if any("query." in json.dumps((bones.get("body") or {}).get("rotation", {}))
               for _ in [0]):
            body.append(name)
    print("  ↓ 参考：这几段动画的 chest/body 是「跟着视角转」的（作者写的 Molang 生效后自动跟随）：")
    print("    VIEW_FOLLOW_CHEST = %s" % (sorted(chest),))
    print("    VIEW_FOLLOW_BODY  = %s" % (sorted(body),))

    # 音效：以 **bbmodel 的表**为准做一次**合并**（缺的补上、有的改名）。
    #
    # ⚠️ 2026-10-17 用户："将专注相关的音效替换为…模型所引用的音频" —— 查下来发现**一半根本没进包**：
    # 作者每段动画的**第一个**音效关键帧在 Blockbench 里 `effect` 是**空字符串**（后面的写 "2"、"ding"），
    # GeckoLib 插件导出时把这几个**整条丢掉**（导出日志里那句"无效关键帧（指令）… -> xxx"就是它）。
    # 实测丢的是：`Focus_ready` 的 栓声@0.25、`fast_ready` 的 栓声@0.0833、`Overheat` 的 散热@0.4167、
    # `Focus_over` 的 换弹@0.25、`reload` 的 换弹@0.1111、`Focus_over_shooting` 的 弹匣@2.5833 / 散热@3.0。
    # 所以这里不再"只改名"：按 `_model_sound_times(bbmodel)` 补回缺的那几条。
    relabeled, merged = [], []
    for name, anim in (doc.get("animations") or {}).items():
        table = sounds.get(name)
        if not table:
            continue
        se = anim.get("sound_effects")
        if se is None:
            se = anim["sound_effects"] = {}
        for t, snd in table.items():
            # 已经有的（时间对得上，容差 2ms）只改名；不然按 4 位小数补一条新的
            hit = next((k for k in se if abs(float(k) - t) < 0.002), None)
            if hit is not None:
                if se[hit].get("effect") != snd:
                    relabeled.append((name, hit, se[hit].get("effect"), snd))
                    se[hit]["effect"] = snd
            else:
                key = ("%.4f" % t).rstrip("0").rstrip(".")
                se[key] = {"effect": snd, "locator": "locator"}
                merged.append((name, key, snd))
    for name, key, snd in merged:
        print("  音效关键帧补回  %-18s t=%-7s -> %s" % (name, key, snd))
    for name, key, old, new in relabeled:
        print("  音效关键帧改名：%-18s t=%-7s %-6s -> %s" % (name, key, old or "(空)", new))
    if extra_textures:
        print("  副贴图方块（客户端用独立层画）:", [e["index"] for e in extra_textures])
    baked = bake_step_keyframes(doc, model_doc)
    if baked:
        print("  步进关键帧（作者标 step 的）已烘成阶跃：%d 处" % baked)
    merge_duplicate_focus_variants(doc)
    derive_shell_animations(doc)
    derive_upper_variants(doc)
    derive_lower_variants(doc)
    return json.dumps(doc, ensure_ascii=False)
    # 注：`derive_hands_split(doc)`（双手/身体切分）2026-10-17 已按用户要求**停用**，函数留着参考。


# ---------------------------------------------------------------------------
# 站立射击：把"双手 + 枪"和"身体"切开，两端分别驱动 / 分别渲染
# ---------------------------------------------------------------------------
BODY_BONES = ("body", "chest", "head")

# ---------------------------------------------------------------------------
# 位移/待机动画的"只有下半身"变体（上半身叠层在场时用 —— 不让两条控制器抢骨头）
# ---------------------------------------------------------------------------
LOWER_KEEP_BONES = ("body", "leg_left", "leg_right")


def derive_lower_variants(doc):
    """派生 `idle_lower` / `run_lower` / `walk_front_lower` …（只留 `body`+两条腿）。

    用户 2026-10-17 报："在奔跑状态下满弹匣装填动画未生效"。

    原因：作者的 `run`/`walk_*`/`idle` 都是**全身**动画（也写 `chest`/`arm_left`/`arm_right`/`gun`…），
    当上半身叠层（换弹 `reload_upper`、腰射 `stand_shooting*`）在演的时候，两条控制器会抢同一批骨头 ——
    奔跑那条幅度大，把换弹动作盖掉了（走路时幅度小，不容易看出来）。

    做法：这些动画只留 `leg_left`/`leg_right`/`body` 派生成 `*_lower`；客户端在"上半身叠层在场"时
    腿那条只播 `*_lower` —— 两条控制器各管各的骨头，上半身的动作必然可见。
    """
    anims = doc.get("animations") or {}
    made = []
    # 位移动画 + 换弹：`reload_lower` = 作者整段 `reload` 的**下半身**（body + 两条腿）。
    # 站定换弹时腿也走作者那段（整段效果完整），一走起来就让位给走路（腿不再被换弹按在原地）。
    for base in ("idle", "run", "walk_front", "walk_back", "walk_left", "walk_right", "reload"):
        src = anims.get(base)
        if not src:
            continue
        clone = copy.deepcopy(src)
        clone["name"] = base + "_lower"
        bones = clone.get("bones") or {}
        clone["bones"] = {b: ch for b, ch in bones.items() if b in LOWER_KEEP_BONES}
        anims[clone["name"]] = clone
        made.append(clone["name"])
    print("  下半身变体 %d 段: %s" % (len(made), ", ".join(made)))
    return made


def derive_hands_split(doc):
    """把 `stand_shooting` / `stand_shooting_hold` 拆成 `*_body`（身体）与 `*_hands`（双手+枪）。

    用户 2026-10-17："将站立射击动画中的双手和身体进行切分并分别渲染，达成类似原版弓箭在射击时的
    多段旋转效果"。

    切法：
      * `*_body`  —— 只留 `body`/`chest`/`head` 的通道（躯干与头的姿势）
      * `*_hands` —— 其余全部（`arm_left`/`arm_right`/`gun` 及其子骨、枪口焰、弹链、弹壳…）

    客户端：上半身那条控制器播 `*_body`，新加的 "hands" 控制器播 `*_hands`（注册更靠后 → 按骨骼覆盖），
    渲染器再把"双手 + 枪"按"相机 yaw − 身体 yaw"多转一点 —— 手/枪先追上视角、身体随后跟上，
    于是形成两段式旋转。

    ⚠️ 必须在 `derive_upper_variants()` **之后**调用：`stand_shooting_hold` 是那一步派生的。
    """
    anims = doc.get("animations") or {}
    made = []
    for base in ("stand_shooting", "stand_shooting_hold"):
        src = anims.get(base)
        if not src:
            continue
        for suffix, want_body in (("_body", True), ("_hands", False)):
            clone = copy.deepcopy(src)
            clone["name"] = base + suffix
            bones = clone.get("bones") or {}
            clone["bones"] = {b: ch for b, ch in bones.items()
                              if (b in BODY_BONES) == want_body}
            anims[clone["name"]] = clone
            made.append(clone["name"])
    print("  站立射击切分 %d 段: %s" % (len(made), ", ".join(made)))
    return made


# ---------------------------------------------------------------------------
# 弹壳：每根骨头各一份动画（客户端给每根弹壳单独挂一个 AnimationController）
# ---------------------------------------------------------------------------
SHELL_BONES = ("shell", "shell2", "shell3", "shell4", "shell5", "shell6", "shell7")
SHELL_SOURCE_ANIM = "Focus_shooting"      # 模板：这段里 `shell` 的三条通道就是"抛壳"本身
SHELL_SOURCE_BONE = "shell"


def align_aim_pose_yaw(doc):
    """**站立瞄准姿势：让"枪管净偏航 = 0"**（用户 2026-10-17 选 C：从模型这层治本）。

    作者在 `stand_shooting` 里给 `chest.rotation.y = 14.91438`、`gun.rotation.y = -12.56632`，
    净偏航 = +2.348°；再加上"枪口 → 准星点"这条控制环本身的滞后，画面上枪口就一直差几度。

    做法：把 `chest.rotation.y` 设成 `-gun.rotation.y`（这里 = +12.56632），两段抵消后
    **枪管与身体同向**；手臂是 chest 的子骨，跟着一起转，握姿不变。`stand_shooting_hold`
    是从它派生出来的，所以放在派生之前改，两个都吃到。
    """
    fixed = []
    for name in ("stand_shooting",):
        anim = (doc.get("animations") or {}).get(name)
        if not anim:
            continue
        bones = anim.get("bones") or {}
        gun = (((bones.get("gun") or {}).get("rotation") or {}).get("vector"))
        chest = (((bones.get("chest") or {}).get("rotation") or {}).get("vector"))
        if not gun or not chest or len(gun) < 2 or len(chest) < 2:
            continue
        if isinstance(gun[1], str) or isinstance(chest[1], str):
            continue
        old = chest[1]
        chest[1] = -float(gun[1])
        fixed.append((name, old, chest[1]))
    for name, old, new in fixed:
        print("  瞄准姿势偏航配平 %-16s chest.y %.4f -> %.4f（净偏航 = chest + gun = 0）"
              % (name, old, new))
    return fixed


def derive_shell_animations(doc):
    """从射击动画里把"抛壳"摘出来，给**每根弹壳骨**各生成两份动画。

    用户 2026-10-17："将资源文件夹中的 shells 组（原名 bone2）下所有动画单独渲染，
    否则会出现连射是弹壳动画未落地被下一个射击动画覆盖的问题"。

    原因：作者的每发动画（`Focus_shooting*` / `stand_shooting`）都只写**同一根** `shell` 骨，
    抛壳全程 0.875 秒（0.0833 起、0.9583 消失）；连射最快 0.25 秒一发 → 下一发一重播，
    "这一段没有通道的骨骼"就被拉回 geo 初始位，弹壳"啪"一下回到弹夹里。
    作者在 `Focus_over_shooting`（整梭样品）里用的是**每发一根**（shell → shell2 → … → shell7）✔
    我们照这个思路做：

      * `shell_eject_<k>`：抛出运动（沿用作者时间轴 0.0833s → 0.9583s）
      * `shell_hold_<k>` ：单关键帧"在弹夹里"静姿（装填完把弹壳放回去）

    客户端给这 7 根弹壳**各挂一个 AnimationController**（注册在 rig/upper 之后 → 按骨骼覆盖），
    于是每根弹壳的抛出都跑完自己那 0.875 秒，谁也不打断谁；装填时统一切回静姿。
    """
    anims = doc.get("animations") or {}
    src_anim = anims.get(SHELL_SOURCE_ANIM)
    if not src_anim:
        print("  !! 找不到模板动画 %s，跳过弹壳动画派生" % SHELL_SOURCE_ANIM)
        return []
    src_bone = (src_anim.get("bones") or {}).get(SHELL_SOURCE_BONE)
    if not src_bone:
        print("  !! 模板动画 %s 里没有 %s 骨，跳过弹壳动画派生" % (SHELL_SOURCE_ANIM, SHELL_SOURCE_BONE))
        return []
    made = []
    for i, bone in enumerate(SHELL_BONES, start=1):
        # ① 抛出：模板骨骼的通道原样搬到目标骨上（时间轴不动）
        eject = copy.deepcopy(src_anim)
        eject["bones"] = {bone: copy.deepcopy(src_bone)}
        eject["name"] = "shell_eject_%d" % i
        anims[eject["name"]] = eject
        # ② 静姿：每个通道只留"第一个关键帧"（= 弹壳在弹夹里的姿势）
        hold = copy.deepcopy(eject)
        hold["name"] = "shell_hold_%d" % i
        for _b, channels in (hold.get("bones") or {}).items():
            for chan, keys in list(channels.items()):
                if not isinstance(keys, dict) or not keys or "vector" in keys:
                    continue
                try:
                    first = min(keys, key=lambda k: float(k))
                except (TypeError, ValueError):
                    continue
                channels[chan] = {"0.0": keys[first]}
        anims[hold["name"]] = hold
        made.append(eject["name"])
        made.append(hold["name"])
    print("  弹壳动画 %d 段（每根弹壳：抛出 + 静姿）: %s" % (len(made), ", ".join(made)))
    return made


def _step_key_times(model_doc):
    """bbmodel -> {(骨骼名, 通道): {时间(4 位小数)}}：作者把这些关键帧标成了 `step`（阶跃）。"""
    out = {}
    for anim in model_doc.get("animations", []):
        for blk in (anim.get("animators") or {}).values():
            if not isinstance(blk, dict) or blk.get("type") != "bone":
                continue
            bone = blk.get("name")
            for kf in blk.get("keyframes") or []:
                if str(kf.get("interpolation") or "").lower() != "step":
                    continue
                out.setdefault((bone, kf.get("channel")), set()).add(
                    round(float(kf.get("time") or 0.0), 4))
    return out


def _fmt_time(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return s or "0"


def bake_step_keyframes(doc, model_doc):
    """把作者标 `step` 的关键帧烘成"阶跃"（= 保持到下一个关键帧前一刻）。

    ⚠️ 为什么必须做（用户 2026-10-16 报："射击动画中 fire 组的步帧未生效"）：
    Blockbench 导 Bedrock 动画**不写** `lerp_mode`，GeckoLib 于是全按**线性插值**播。作者把
    枪口焰/速度线（`fire1..4` / `dash1..4`）的 scale 关键帧标成了 `step` —— 本意是"这一帧亮、
    下一帧灭"的硬切（一帧只有 1/20 秒），线性插值下却变成"渐显渐隐"的一团糊，看起来就像特效没生效。

    做法：在"下一个关键帧的时间 - 0.0001s"处复制一份**同样的值**，线性插值在视觉上就等于阶跃
    （0.0001 秒的过渡在 20Hz 的动画里看不见）。只动被标 step 的那几个通道，其它一律不碰。
    """
    steps = _step_key_times(model_doc)
    if not steps:
        return 0
    added = 0
    for anim in (doc.get("animations") or {}).values():
        for bone, channels in (anim.get("bones") or {}).items():
            if not isinstance(channels, dict):
                continue
            for chan, keys in channels.items():
                if not isinstance(keys, dict) or not keys:
                    continue
                if "vector" in keys:
                    continue          # 无时间的单值通道（{"vector": [...]}），没有关键帧可烘
                target = steps.get((bone, chan))
                if not target:
                    continue
                try:
                    entries = sorted((float(k), k, v) for k, v in keys.items())
                except (TypeError, ValueError):
                    continue
                times = [e[0] for e in entries]
                inserts = []
                for t, _kstr, val in entries:
                    if round(t, 4) not in target:
                        continue
                    nxt = next((x for x in times if x > t + 1e-9), None)
                    if nxt is None:
                        continue
                    hold = round(nxt - 1.0e-4, 4)
                    if hold <= t or any(abs(hold - x) < 1e-9 for x in times):
                        continue
                    held = copy.deepcopy(val)
                    if isinstance(held, dict) and "post" in held:
                        post = copy.deepcopy(held["post"])
                        held["pre"] = copy.deepcopy(post)
                        held["post"] = post
                    inserts.append((hold, _fmt_time(hold), held))
                if not inserts:
                    continue
                entries.extend(inserts)
                entries.sort(key=lambda e: e[0])
                channels[chan] = {k: v for _t, k, v in entries}
                added += len(inserts)
    return added


def main():
    model_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(W, "source", "BG261013.bbmodel")
    model = json.load(open(model_path, encoding="utf-8"))
    print("源模型:", model_path, "elements=%d animations=%d" % (len(model["elements"]),
                                                               len(model.get("animations", []))))
    ensure_geckolib()
    tex_w, tex_h = extract_texture(model, OUT_TEX)
    extras = extract_extra_textures(model)
    print("副贴图（用独立渲染层画）:", [(e["index"], os.path.basename(e["path"])) for e in extras])
    table = base.skin_table()
    print("皮肤版式（从官方玩家几何算出）:", {k: tuple(table[k]["north"]) for k in sorted(table)})
    job = os.path.join(W, "build", "mech_gl_job.js")
    with open(job, "w", encoding="utf-8") as fh:
        fh.write("globalThis.__BB_JOB = " + json.dumps(
            {"name": "BG261013", "modelJson": model, "skinUv": table}, ensure_ascii=False) + ";\nreturn 1;")
    cdp(job)
    raw = cdp(os.path.join(TOOLS, "bb_export_geckolib.js")).strip()
    info = json.loads(raw)
    print("身体骨骼 UV 改写:", info["uv_fixed"], "面", info["body_bones"])
    print("动作:", info["log"])
    print("导出条目:", [(e["name"], e["type"], e["bytes"]) for e in info["exports"]])
    if info.get("format_error"):
        print("  切格式告警:", info["format_error"])

    wrote = []
    for e in info["exports"]:
        name = (e.get("name") or "").lower()
        content = e.get("content")
        if not content:
            continue
        if name.endswith(".geo") or "geo" in name:
            content = split_foreign_cubes(content, model)
            content, fixed, over = base.force_texture_size(content, tex_w, tex_h)
            for ident, old in fixed:
                print("  geo 贴图尺寸修正: %s %s -> (%d,%d)" % (ident, old, tex_w, tex_h))
            if over:
                print("  UV 越界(会采样到贴图外):", over[:6], "共", len(over), "面")
                print("  （↑ 里面属于 `airflow*` 骨骼的那几面是**故意的** —— 它们用副贴图，"
                      "由 BowgunAirflowLayer 单独画；其余越界才是真问题）")
            os.makedirs(os.path.dirname(OUT_GEO), exist_ok=True)
            with open(OUT_GEO, "w", encoding="utf-8") as fh:
                fh.write(content)
            doc = json.loads(content)["minecraft:geometry"][0]
            print("  geo 骨骼 %d 根: %s" % (len(doc["bones"]), ", ".join(b["name"] for b in doc["bones"])))
            wrote.append(("geo", OUT_GEO, os.path.getsize(OUT_GEO)))
        elif "animation" in name or name.endswith(".json"):
            content = postprocess_animations(content, model, extras)
            os.makedirs(os.path.dirname(OUT_ANIM), exist_ok=True)
            with open(OUT_ANIM, "w", encoding="utf-8") as fh:
                fh.write(content)
            anims = json.loads(content)["animations"]
            print("  动画 %d 段: %s" % (len(anims), ", ".join(anims.keys())))
            wrote.append(("anim", OUT_ANIM, os.path.getsize(OUT_ANIM)))
    for kind, path, size in wrote:
        print("写出 %s: %s (%d 字节)" % (kind, path, size))
    if not wrote:
        raise SystemExit("什么都没导出 —— 看上面的 exports")
    return 0


if __name__ == "__main__":
    sys.exit(main())
