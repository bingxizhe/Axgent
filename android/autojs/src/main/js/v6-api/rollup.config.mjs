import typescript from '@rollup/plugin-typescript';
import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import replace from '@rollup/plugin-replace'
import json from '@rollup/plugin-json';
import { babel } from '@rollup/plugin-babel';
import terser from '@rollup/plugin-terser';
import { parseInput } from './input.mjs';

let isDev
if (process.env.NODE_ENV === 'production') {
    isDev = false
} else {
    isDev = true
}


export default {
    input: await parseInput(),
    output: {
        dir: "dist",
        format: 'commonjs',
        entryFileNames: "[name].js"
    },
    plugins: [
        typescript({
            outDir: "dist",
        }),
        resolve(), commonjs(), json(),
        babel({
            targets: { rhino: '1.8.0' },
            babelHelpers: 'bundled',
            extensions: ['.js', '.ts'],
            exclude: 'node_modules/**', // 排除 node_modules 目录
            presets: ['@babel/preset-env'] // 使用 Babel 的 env 预设
        }), //terser(),
    ],
    // external: ['lodash']
}