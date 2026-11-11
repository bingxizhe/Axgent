/**
 * checkpoint —— 任务断点：每步写 runs/<id>/checkpoint.json
 */
var cp = {
  save: function (dir, data) {
    try {
      files.ensureDir(dir + '/');
      files.write(dir + '/checkpoint.json', JSON.stringify(data));
      return true;
    } catch (e) { return false; }
  },
  load: function (dir) {
    try {
      var p = dir + '/checkpoint.json';
      if (!files.exists(p)) return null;
      return JSON.parse(files.read(p));
    } catch (e) { return null; }
  }
};

module.exports = cp;
