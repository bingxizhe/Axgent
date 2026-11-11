import assert from "assert"
import { java } from "java"

const f = new java.io.File('/')

assert(f.path === "/")