import path from "node:path"
import fs from "fs/promises"


export async function getInput() {
    const dirs = await fs.readdir('./src')
    const input = {}

    for (const dir of dirs) {
        const entry = await modelInput(path.join('./src', dir))
        for (const [key, value] of Object.entries(entry)) {
            input[path.join(dir, key)] = path.join("./src", dir, value)
        }
    }
    return input
}

async function modelInput(dir) {
    const entry = { index: 'index.ts' }
    try {
        const data = await fs.readFile(path.join(dir, 'entry.json'), 'utf-8')
        const json = JSON.parse(data)
        Object.assign(entry, json)
    } catch (e) {
    }
    return entry
}