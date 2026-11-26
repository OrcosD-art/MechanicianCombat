const format = Formats.bedrock;
return JSON.stringify({
    keys: Object.keys(format),
    codecKeys: Object.keys(format.codec),
    codecCompileCube: format.codec.compileCube ?
        format.codec.compileCube.toString() : null,
    codecCompileGroup: format.codec.compileGroup ?
        format.codec.compileGroup.toString() : null,
    compileCube: format.compileCube ? format.compileCube.toString() : null,
    compileGroup: format.compileGroup ? format.compileGroup.toString() : null,
    convert: format.convert ? format.convert.toString() : null
});
