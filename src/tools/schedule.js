/**
 * 定时任务工具：schedule_add / schedule_list / schedule_remove
 *
 * 任务由 App 的 AlarmManager 触发；到点后自动新建对话并执行。
 * 同一时间只跑一个任务，冲突的触发会被跳过并顺延。
 */
var schedule = require('../core/schedule');

module.exports = {
  schedule_add: {
    risk: 'write',
    schema: {
      name: 'schedule_add',
      description: '创建周期任务：每隔 N 分钟自动新建一次对话执行同一段任务文本。用户要求“每天/每小时/每隔…”自动做某事时使用；只创建一次触发点，重复执行由系统闹钟负责。',
      parameters: {
        type: 'object',
        properties: {
          task: { type: 'string', description: '要周期执行的任务文本，写清楚目标与验收条件' },
          interval_minutes: { type: 'integer', description: '间隔分钟数（≥1），如 30、60、1440' }
        },
        required: ['task', 'interval_minutes']
      }
    },
    run: function (args, ctx) {
      var r = schedule.add(args.task, args.interval_minutes);
      if (r.ok && ctx && ctx.logger) {
        ctx.logger.info('创建定时任务：每 ' + r.schedule.intervalMinutes + ' 分钟 → '
          + String(r.schedule.task).slice(0, 40));
      }
      return r;
    }
  },

  schedule_list: {
    risk: 'read',
    schema: {
      name: 'schedule_list',
      description: '列出所有周期任务（含暂停状态与下次触发时间）。修改、删除前先查询 id。',
      parameters: { type: 'object', properties: {} }
    },
    run: function () {
      return schedule.list();
    }
  },

  schedule_remove: {
    risk: 'write',
    schema: {
      name: 'schedule_remove',
      description: '删除一个周期任务（按 id，先用 schedule_list 查询）。不会删除已经产生的对话记录。',
      parameters: {
        type: 'object',
        properties: {
          id: { type: 'string', description: 'schedule_list 返回的任务 id，如 s1710000000000' }
        },
        required: ['id']
      }
    },
    run: function (args) {
      return schedule.remove(args.id);
    }
  }
};
