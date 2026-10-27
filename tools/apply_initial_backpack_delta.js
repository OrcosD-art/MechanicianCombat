const fs = require("fs");

function arg(name) {
    const index = process.argv.indexOf(name);
    if (index < 0 || index + 1 >= process.argv.length) {
        throw new Error(`missing ${name}`);
    }
    return process.argv[index + 1];
}

function readJson(file) {
    return JSON.parse(fs.readFileSync(file, "utf8"));
}

function writeJson(file, value) {
    fs.writeFileSync(file, JSON.stringify(value), "utf8");
}

function number(value) {
    const parsed = Number(value);
    return Number.isInteger(parsed) ? parsed : parsed;
}

function convertedPoint(values, wearable) {
    const [x, y, z] = values.map(Number);
    return wearable
        ? [number(-x - 4), number(y - 24), number(z - 2)]
        : [number(-x), number(y - 12), number(z)];
}

function convertedCubeOrigin(element, wearable) {
    const from = element.from.map(Number);
    const to = element.to.map(Number);
    return wearable
        ? [number(-to[0] - 4), number(from[1] - 24), number(from[2] - 2)]
        : [number(-to[0]), number(from[1] - 12), number(from[2])];
}

function convertedRotation(values) {
    return values
        ? [number(-Number(values[0])), number(values[1]), number(values[2])]
        : null;
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

function findBone(geometry, name) {
    const bone = geometry["minecraft:geometry"][0].bones.find(candidate => candidate.name === name);
    if (!bone) throw new Error(`missing bone ${name}`);
    return bone;
}

function updateCube(cube, sourceElement, wearable) {
    const from = sourceElement.from.map(Number);
    const to = sourceElement.to.map(Number);
    cube.origin = convertedCubeOrigin(sourceElement, wearable);
    cube.size = [0, 1, 2].map(index => number(Math.abs(to[index] - from[index])));
    const rotation = convertedRotation(sourceElement.rotation);
    if (rotation && rotation.some(value => value !== 0)) cube.rotation = rotation;
    else delete cube.rotation;
}

function applyGeometry(source, geometry, wearable) {
    const elements = new Map(source.elements.map(element => [element.uuid, element]));
    const c2 = findBone(geometry, "C2");
    const c3 = findBone(geometry, "C3");
    updateCube(c2.cubes[0], elements.get("3062b74a-97f6-05c6-0829-4b65b82f7ae0"), wearable);
    updateCube(c3.cubes[0], elements.get("e1bca0de-9f48-a095-2f33-d76e771e3f85"), wearable);

    const c3Group = findGroup(source.outliner, "C3");
    if (!c3Group) throw new Error("C3 group missing");
    c3.pivot = convertedPoint(c3Group.origin, wearable);
    const c3Rotation = convertedRotation(c3Group.rotation);
    if (c3Rotation && c3Rotation.some(value => value !== 0)) c3.rotation = c3Rotation;
    else delete c3.rotation;

    const bones = geometry["minecraft:geometry"][0].bones;
    for (let index = bones.length - 1; index >= 0; index--) {
        if (bones[index].name === "front" || bones[index].name === "rear") {
            bones.splice(index, 1);
        }
    }
    for (const child of c3Group.children || []) {
        const locator = typeof child === "string" ? elements.get(child) : child;
        if (!locator || locator.type !== "locator") continue;
        if (locator.name !== "front" && locator.name !== "rear") continue;
        bones.push({
            name: locator.name,
            parent: "C3",
            pivot: convertedPoint(locator.position, wearable),
        });
    }
}

function animationTime(value) {
    return String(number(Number(value).toFixed(4)));
}

function convertAnimations(source, bones) {
    const boneNames = new Set(bones.map(bone => bone.name));
    const animations = {};
    for (const animation of source.animations || []) {
        const convertedBones = {};
        const soundEffects = {};
        for (const animator of Object.values(animation.animators || {})) {
            if (animator.type === "bone" && boneNames.has(animator.name)) {
                const channels = {};
                for (const keyframe of animator.keyframes || []) {
                    if (!["rotation", "position", "scale"].includes(keyframe.channel)) continue;
                    const first = (keyframe.data_points || [])[0];
                    if (!first) continue;
                    if (!channels[keyframe.channel]) channels[keyframe.channel] = {};
                    channels[keyframe.channel][animationTime(keyframe.time)] = {
                        vector: [Number(first.x || 0), Number(first.y || 0), Number(first.z || 0)],
                    };
                }
                if (Object.keys(channels).length) convertedBones[animator.name] = channels;
            }
            if (animator.type === "effect") {
                for (const keyframe of animator.keyframes || []) {
                    if (keyframe.channel !== "sound") continue;
                    for (const data of keyframe.data_points || []) {
                        if (data.effect) {
                            soundEffects[animationTime(keyframe.time)] = { effect: data.effect };
                        }
                    }
                }
            }
        }
        const converted = {
            loop: animation.loop === "hold" ? "hold_on_last_frame" : "loop",
            animation_length: number(animation.length || 0),
        };
        if (Object.keys(convertedBones).length) converted.bones = convertedBones;
        if (Object.keys(soundEffects).length) converted.sound_effects = soundEffects;
        animations[animation.name] = converted;
    }
    return { format_version: "1.8.0", animations };
}

const source = readJson(arg("--source"));
const wearablePath = arg("--wearable");
const rigPath = arg("--rig");
const animationPath = arg("--animation");
const wearable = readJson(wearablePath);
const rig = readJson(rigPath);

applyGeometry(source, wearable, true);
applyGeometry(source, rig, false);
writeJson(wearablePath, wearable);
writeJson(rigPath, rig);
writeJson(animationPath, convertAnimations(source, wearable["minecraft:geometry"][0].bones));
