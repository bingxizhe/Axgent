import typescript from '@rollup/plugin-typescript';
import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import replace from '@rollup/plugin-replace'
import terser from '@rollup/plugin-terser';
import json from '@rollup/plugin-json';
import { getInput } from './input.mjs';
import { defineConfig } from 'rollup';
let isDev
if (process.env.NODE_ENV === 'production') {
    isDev = false
} else {
    isDev = true
}

export default defineConfig({
    input: await getInput(),
    output: {
        dir: "dist",
        format: 'es',
        entryFileNames: "[name].js"
    },
    plugins: [
        typescript({
            outDir: "dist"
        }),
        resolve(),
        commonjs(),
        json(), terser(),
        replace({
            __VUE_OPTIONS_API__: 'true',
            __VUE_PROD_DEVTOOLS__: String(isDev),
            __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: String(isDev)
        })
    ],
    external: []
})