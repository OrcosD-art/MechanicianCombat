const fs = require("fs");
const path = require("path");

function arg(name) {
    const index = process.argv.indexOf(name);
    if (index < 0 || index + 1 >= process.argv.length) {
        throw new Error(`missing ${name}`);
    }
    return process.argv[index + 1];
}

function number(value) {
    const parsed = Number(value);
    return Number.isInteger(parsed) ? parsed : parsed;
}

function point(values) {
    return values.map(number);
}

function findGroup(nodes, name) {
    for (const node of nodes || []) {
        if (!node || typeof node !== "object") continue;
        if (node.name === name) return node;
        const found = findGroup(node.children, name);
        if (found) return found;
    }
    return null;
}

function convertedPoint(values, wearable) {
    const [x, y, z] = values.map(Number);
    return wearable
        ? [number(-x - 4), number(y - 24), number(z - 2)]
        : [number(-x), number(y - 12), number(z)];
}

function convertedRotation(values) {
    if (!values) return null;
    return [number(-Number(values[0])), number(values[1]), number(values[2])];
}

function convertCube(element, wearable) {
    const from = element.from.map(Number);
    const to = element.to.map(Number);
    const uv = {};
    for (const [faceName, sourceFace] of Object.entries(element.faces || {})) {
        if (sourceFace.texture === null || sourceFace.texture === undefined) continue;
        const [x1, y1, x2, y2] = sourceFace.uv.map(Number);
        const face = {
            uv: [number(x1), number(y1)],
            uv_size: [number(x2 - x1), number(y2 - y1)],
        };
        if (sourceFace.rotation) face.uv_rotation = Number(sourceFace.rotation);
        uv[faceName] = face;
    }
    const cube = {
        origin: wearable
            ? [number(-to[0] - 4), number(from[1] - 24), number(from[2] - 2)]
            : [number(-to[0]), number(from[1] - 12), number(from[2])],
        size: [0, 1, 2].map(index => number(Math.abs(to[index] - from[index]))),
    };
    if (Object.keys(uv).length) cube.uv = uv;
    const rotation = convertedRotation(element.rotation);
    if (rotation && rotation.some(value => value !== 0)) cube.rotation = rotation;
    return cube;
}

function convertGeometry(source, wearable) {
    const elements = new Map((source.elements || []).map(element => [element.uuid, element]));
    const backpack = findGroup(source.outliner, "combat_backpack");
    if (!backpack) throw new Error("combat_backpack group missing");
    const bones = [];

    function addGroup(group, parent) {
        if (group.name === "sound") return;
        const bone = {};
        if (parent) bone.parent = parent;
        bone.pivot = group.name === "combat_backpack"
            ? (wearable ? [0, 0, 0] : [0, 0, 4])
            : convertedPoint(group.origin, wearable);
        const rotation = convertedRotation(group.rotation);
        if (rotation && rotation.some(value => value !== 0)) bone.rotation = rotation;

        const cubes = [];
        for (const child of group.children || []) {
            if (typeof child === "string") {
                const element = elements.get(child);
                if (element && element.type === "cube") {
                    cubes.push(convertCube(element, wearable));
                } else if (element && element.type === "locator"
                        && (element.name === "front" || element.name === "rear")) {
                    bones.push({
                        name: element.name,
                        parent: group.name,
                        pivot: convertedPoint(element.position, wearable),
                    });
                }
            } else if (child.type === "locator") {
                if (child.name !== "front" && child.name !== "rear") continue;
                const locator = {
                    name: child.name,
                    parent: group.name,
                    pivot: convertedPoint(child.position, wearable),
                };
                const locatorRotation = convertedRotation(child.rotation);
                if (locatorRotation && locatorRotation.some(value => value !== 0)) {
                    locator.rotation = locatorRotation;
                }
                bones.push(locator);
            } else {
                addGroup(child, group.name);
            }
        }
        if (cubes.length) bone.cubes = cubes;
        bones.unshift(bone);
    }

    addGroup(backpack, null);
    return {
        format_version: "1.12.0",
        "minecraft:geometry": [{
            description: {
                identifier: "geometry.unknown",
                texture_width: 48,
                texture_height: 48,
                visible_bounds_width: 3,
                visible_bounds_height: 3.5,
                visible_bounds_offset: [0, 1.25, 0],
            },
            bones,
        }],
    };
}

function animationTime(value) {
    return String(number(Number(value).toFixed(4)));
}

function convertAnimations(source, boneNames) {
    const animations = {};
    for (const animation of source.animations || []) {
        const bones = {};
        const soundEffects = {};
        for (const animator of Object.values(animation.animators || {})) {
            if (animator.type !== "bone" || !boneNames.has(animator.name)) continue;
            const channels = {};
            for (const keyframe of animator.keyframes || []) {
                if (!["rotation", "position", "scale"].includes(keyframe.channel)) continue;
                const first = (keyframe.data_points || [])[0];
                if (!first) continue;
                if (!channels[keyframe.channel]) channels[keyframe.channel] = {};
                channels[keyframe.channel][animationTime(keyframe.time)] = {
                    vector: point([first.x || 0, first.y || 0, first.z || 0]),
                };
            }
            if (Object.keys(channels).length) bones[animator.name] = channels;
        }
        const effectsAnimator = Object.values(animation.animators || {})
            .find(animator => animator.type === "effect");
        for (const keyframe of effectsAnimator?.keyframes || []) {
            if (keyframe.channel !== "sound") continue;
            for (const data of keyframe.data_points || []) {
                if (data.effect) {
                    soundEffects[animationTime(keyframe.time)] = { effect: data.effect };
                }
            }
        }
        const converted = {
            loop: animation.loop === "hold" ? "hold_on_last_frame" : "loop",
            animation_length: number(animation.length || 0),
        };
        if (Object.keys(bones).length) converted.bones = bones;
        if (Object.keys(soundEffects).length) converted.sound_effects = soundEffects;
        animations[animation.name] = converted;
    }
    return { format_version: "1.8.0", animations };
}

function writeJson(file, value) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(value), "utf8");
}

const source = JSON.parse(fs.readFileSync(arg("--source"), "utf8"));
const wearable = convertGeometry(source, true);
const rig = convertGeometry(source, false);
const boneNames = new Set(wearable["minecraft:geometry"][0].bones.map(bone => bone.name));
const animation = convertAnimations(source, boneNames);
writeJson(arg("--wearable-out"), wearable);
writeJson(arg("--rig-out"), rig);
writeJson(arg("--animation-out"), animation);
