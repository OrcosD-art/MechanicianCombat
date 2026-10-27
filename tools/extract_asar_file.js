const fs = require("fs");
const path = require("path");

const archive = process.argv[2];
const target = process.argv[3];
const output = process.argv[4];
if (!archive || !target || !output) {
    throw new Error("usage: extract_asar_file.js <archive> <entry> <output>");
}

const data = fs.readFileSync(archive);
const headerPickleSize = data.readUInt32LE(4);
const headerStringSize = data.readUInt32LE(12);
const header = JSON.parse(
    data.subarray(16, 16 + headerStringSize).toString("utf8")
);
let node = header;
for (const part of target.split("/")) {
    node = node.files?.[part];
    if (!node) throw new Error(`entry not found: ${target}`);
}
if (typeof node.link === "string") {
    throw new Error(`entry is a link: ${node.link}`);
}
const contentOffset = 8 + headerPickleSize;
const start = contentOffset + Number(node.offset || 0);
const end = start + Number(node.size);
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, data.subarray(start, end));
