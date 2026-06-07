import 'package:ui/models/execution_record.dart';

class TaskExecutionInfo {
  final int id;
  final String appName;
  final String packageName;
  final String title;
  final String nodeId;              // 标识 suggestion
  final String suggestionId;        // 标识 suggestion
  final String? iconUrl;            // suggestion 图标 URL
  final ExecutionRecordType type;   // 执行记录类型: system/learning/vlm/summary/unknown
  final String? content;            // 总结任务的 Markdown 内容
  final int count;                  // 不包括running的任务
  final int lastExecutionTime;

  TaskExecutionInfo({
    required this.id,
    required this.appName,
    required this.packageName,
    required this.title,
    required this.nodeId,
    required this.suggestionId,
    this.iconUrl,
    this.type = ExecutionRecordType.unknown,
    this.content,
    required this.count,
    required this.lastExecutionTime,
  });

  factory TaskExecutionInfo.fromMap(Map<dynamic, dynamic> map) {
    return TaskExecutionInfo(
      id: map['id'] as int,
      appName: map['appName'] as String,
      packageName: map['packageName'] as String,
      title: map['title'] as String,
      nodeId: map['nodeId'] as String? ?? '',
      suggestionId: map['suggestionId'] as String? ?? '',
      iconUrl: map['iconUrl'] as String?,
      type: ExecutionRecordTypeX.fromValue(map['type'] as String?),
      content: map['content'] as String?,
      count: map['count'] as int,
      lastExecutionTime: map['lastExecutionTime'] as int,
    );
  }
}
