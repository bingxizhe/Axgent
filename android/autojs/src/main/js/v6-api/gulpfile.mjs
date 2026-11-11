import { series } from 'gulp'
import { rm } from 'fs/promises'
import fs from 'fs/promises'
import { rollup } from 'rollup'
import { loadConfigFile } from 'rollup/loadConfigFile'
import { copy } from 'fs-extra'

export async function clear(cb) {
    await rm('./dist', { recursive: true, force: true })
    cb()
}
async function copyTypeFile(cb) {
    await copy('../native-types', './dist/types')
    await copy('./types', './dist/types')
    cb()
}

async function createRootPackageFile(cb) {
    const n = JSON.parse(await fs.readFile('./package.json', 'utf8'))
    const packageFile = {
        name: n.name,
        version: n.version,
        type: "commonjs",
        license: n.license,
        author: n.author,
        publishConfig: n.publishConfig,
        dependencies: n.dependencies
    }
    await fs.writeFile('./dist/package.json', JSON.stringify(packageFile, undefined, 2))
}

export const build = series(
    clear,
    async function rollupBuild(cb) {
        const { options, warnings } = await loadConfigFile('./rollup.config.mjs')
        warnings.flush()
        for (const option of options) {
            const bundle = await rollup(option)
            await Promise.all(option.output.map(bundle.write))
        }
    },
    copyTypeFile,
    createRootPackageFile,
    copySrcRaw,
)

export async function copySrcRaw(cb) {
    await copy('./src_raw', './dist')
    cb()
}